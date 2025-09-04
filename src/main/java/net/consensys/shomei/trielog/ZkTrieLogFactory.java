/*
 * Copyright ConsenSys 2023
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package net.consensys.shomei.trielog;

import static net.consensys.shomei.cli.ShomeiCliOptions.ZkTraceComparisonFeature.DECORATE_FROM_HUB;
import static net.consensys.shomei.cli.ShomeiCliOptions.ZkTraceComparisonFeature.FILTER_FROM_HUB;
import static net.consensys.shomei.cli.ShomeiCliOptions.ZkTraceComparisonFeature.isEnabled;

import net.consensys.shomei.context.ShomeiContext;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.AccountValue;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;
import org.hyperledger.besu.ethereum.trie.pathbased.common.PathBasedAccount;
import org.hyperledger.besu.ethereum.trie.pathbased.common.PathBasedValue;
import org.hyperledger.besu.ethereum.trie.pathbased.common.worldview.accumulator.PathBasedWorldStateUpdateAccumulator;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.services.trielogs.TrieLog;
import org.hyperledger.besu.plugin.services.trielogs.TrieLog.LogTuple;
import org.hyperledger.besu.plugin.services.trielogs.TrieLogAccumulator;
import org.hyperledger.besu.plugin.services.trielogs.TrieLogFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZkTrieLogFactory implements TrieLogFactory {

  private static final Logger LOG = LoggerFactory.getLogger(ZkTrieLogFactory.class);
  private final ShomeiContext ctx;
  private final Supplier<Integer> comparisonFeatureMask;

  public ZkTrieLogFactory(ShomeiContext ctx) {
    this.ctx = ctx;
    // defer for late bound config
    comparisonFeatureMask = Suppliers.memoize(() -> ctx.getCliOptions().zkTraceComparisonMask);
  }

  @Override
  @SuppressWarnings("unchecked")
  public TrieLog create(final TrieLogAccumulator accumulator, final BlockHeader blockHeader) {

    var accountsToUpdate = accumulator.getAccountsToUpdate();
    var codeToUpdate = accumulator.getCodeToUpdate();
    var storageToUpdate = accumulator.getStorageToUpdate();

    if (comparisonFeatureMask.get() > 0) {
      LOG.debug(
          "comparing ZkTrieLog with ZkTracer for block {}:{}",
          blockHeader.getNumber(),
          blockHeader.getBlockHash());
      var hubSeenDiff =
          ctx.getBlockImportTraceProvider().compareWithTrace(blockHeader, accumulator);
      if (isEnabled(comparisonFeatureMask.get(), DECORATE_FROM_HUB)) {
        accountsToUpdate =
            decorateAccounts(
                accountsToUpdate, hubSeenDiff.foundInHub().adressesDiff(), accumulator);
        storageToUpdate =
            decorateStorage(storageToUpdate, hubSeenDiff.foundInHub().storageDiff(), accumulator);
      }
      if (isEnabled(comparisonFeatureMask.get(), FILTER_FROM_HUB)) {
        accountsToUpdate =
            filterAccounts(accountsToUpdate, hubSeenDiff.notFoundInHub().adressesDiff());
        storageToUpdate = filterStorage(storageToUpdate, hubSeenDiff.notFoundInHub().storageDiff());
      }
    }

    LOG.debug(
        "creating ZkTrieLog for block {}:{}", blockHeader.getNumber(), blockHeader.getBlockHash());

    return new PluginTrieLogLayer(
        blockHeader.getBlockHash(),
        Optional.of(blockHeader.getNumber()),
        (Map<Address, LogTuple<AccountValue>>) accountsToUpdate,
        (Map<Address, LogTuple<Bytes>>) codeToUpdate,
        (Map<Address, Map<StorageSlotKey, LogTuple<UInt256>>>) storageToUpdate,
        true);
  }

  /* safe map decorator, in case the map we are provided is immutable */
  @SuppressWarnings("unchecked")
  static Map<Address, LogTuple<AccountValue>> decorateAccounts(
      Map<Address, ? extends LogTuple<? extends AccountValue>> accountsToUpdate,
      Set<Address> hubSeenAccounts,
      final TrieLogAccumulator accumulator) {
    final Map<Address, LogTuple<AccountValue>> decorated =
        new HashMap<>((Map<Address, LogTuple<AccountValue>>) accountsToUpdate);
    if (accumulator
        instanceof PathBasedWorldStateUpdateAccumulator<?> worldStateUpdateAccumulator) {
      for (var hubAccount : hubSeenAccounts) {
        decorated.computeIfAbsent(
            hubAccount,
            __ -> {
              final PathBasedAccount account =
                  (PathBasedAccount) worldStateUpdateAccumulator.getAccount(hubAccount);
              return new PathBasedValue<>(account, account, false);
            });
      }
    } else {
      LOG.warn("ignoring incompatible accumulator for account {}", accumulator.getClass());
    }

    return decorated;
  }

  @VisibleForTesting
  static Map<Address, ? extends LogTuple<? extends AccountValue>> filterAccounts(
      Map<Address, ? extends LogTuple<? extends AccountValue>> accountsToUpdate,
      Set<Address> hubNotSeenAccounts) {
    return accountsToUpdate.entrySet().stream()
        .filter(
            accumulatorEntry -> {
              if (hubNotSeenAccounts.contains(accumulatorEntry.getKey())) {
                // if hub hasn't seen this account, before filtering, assert that the account has
                // changed:
                var accountPrior = accumulatorEntry.getValue().getPrior();
                var accountUpdated = accumulatorEntry.getValue().getUpdated();
                return accountHasChanged(accountPrior, accountUpdated);
              }
              return true;
            })
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  /* safe map decorator which also solves challenges with generics */
  @SuppressWarnings("unchecked")
  static Map<Address, Map<StorageSlotKey, LogTuple<UInt256>>> decorateStorage(
      Map<Address, ? extends Map<StorageSlotKey, ? extends TrieLog.LogTuple<UInt256>>>
          storageToUpdate,
      Map<Address, Set<Bytes32>> hubSeenStorage,
      final TrieLogAccumulator accumulator) {

    Map<Address, Map<StorageSlotKey, LogTuple<UInt256>>> result =
        new HashMap<>((Map<Address, Map<StorageSlotKey, LogTuple<UInt256>>>) storageToUpdate);
    if (accumulator
        instanceof PathBasedWorldStateUpdateAccumulator<?> worldStateUpdateAccumulator) {
      for (var seenStorage : hubSeenStorage.entrySet()) {

        Map<StorageSlotKey, LogTuple<UInt256>> storageForAddress =
            new HashMap<>(
                Optional.ofNullable(
                        (Map<StorageSlotKey, LogTuple<UInt256>>)
                            storageToUpdate.get(seenStorage.getKey()))
                    .orElse(new HashMap<>()));

        for (var slotKey : seenStorage.getValue()) {
          final StorageSlotKey storageSlotKey = new StorageSlotKey(UInt256.fromBytes(slotKey));

          storageForAddress.computeIfAbsent(
              storageSlotKey,
              __ -> {
                final UInt256 storageValue =
                    worldStateUpdateAccumulator
                        .getStorageValueByStorageSlotKey(seenStorage.getKey(), storageSlotKey)
                        .orElse(UInt256.ZERO);
                return new PathBasedValue<>(storageValue, storageValue, false);
              });
        }
        result.put(seenStorage.getKey(), storageForAddress);
      }
    } else {
      LOG.warn("ignoring incompatible accumulator for storage {}", accumulator.getClass());
    }
    return result;
  }

  @VisibleForTesting
  static Map<Address, Map<StorageSlotKey, ? extends LogTuple<UInt256>>> filterStorage(
      Map<Address, ? extends Map<StorageSlotKey, ? extends TrieLog.LogTuple<UInt256>>>
          storageToUpdate,
      Map<Address, Set<Bytes32>> hubNotSeenStorage) {

    return storageToUpdate.entrySet().stream()
        .map(
            entry -> {
              Address address = entry.getKey();
              Set<Bytes32> notSeenSlots = hubNotSeenStorage.get(address);
              if (notSeenSlots == null || notSeenSlots.isEmpty()) {
                return entry;
              }

              // Filter the inner storage map by presence in seenSlots
              Map<StorageSlotKey, LogTuple<UInt256>> filteredSlots =
                  entry.getValue().entrySet().stream()
                      .filter(
                          slotEntry -> {
                            var trieLogVal = slotEntry.getValue();
                            var trieLogKey = slotEntry.getKey();
                            var shouldFilter =
                                trieLogKey
                                    .getSlotKey()
                                    .map(UInt256::toBytes)
                                    .filter(notSeenSlots::contains)
                                    .isPresent();

                            if (shouldFilter && !trieLogVal.isUnchanged()) {
                              // refuse to remove a written value and log an error:
                              LOG.error(
                                  "refusing to filter slot value write, "
                                      + "address: {}, slot key: {}, prior: {}, updated: {}",
                                  address.toHexString(),
                                  trieLogKey.getSlotKey().map(UInt256::toHexString).orElse("empty"),
                                  Optional.ofNullable(trieLogVal.getPrior())
                                      .map(UInt256::toHexString)
                                      .orElse("null"),
                                  Optional.ofNullable(trieLogVal.getUpdated())
                                      .map(UInt256::toHexString)
                                      .orElse("null"));
                              return true;
                            }
                            // otherwise, filter from stream:
                            return !shouldFilter;
                          })
                      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

              return Map.entry(address, filteredSlots);
            })
        .filter(e -> !e.getValue().isEmpty())
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  @Override
  public byte[] serialize(final TrieLog layer) {
    final BytesValueRLPOutput rlpLog = new BytesValueRLPOutput();
    writeTo(layer, rlpLog);
    return rlpLog.encoded().toArrayUnsafe();
  }

  public static void writeTo(final TrieLog layer, final RLPOutput output) {
    layer.freeze();

    final Set<Address> addresses = new TreeSet<>();
    addresses.addAll(layer.getAccountChanges().keySet());
    addresses.addAll(layer.getCodeChanges().keySet());
    addresses.addAll(layer.getStorageChanges().keySet());

    output.startList(); // container
    output.writeBytes(layer.getBlockHash());
    // optionally write block number
    layer.getBlockNumber().ifPresent(output::writeLongScalar);

    for (final Address address : addresses) {
      output.startList(); // this change
      output.writeBytes(address);

      final LogTuple<Bytes> codeChange = layer.getCodeChanges().get(address);
      if (codeChange == null || codeChange.isUnchanged()) {
        output.writeNull();
      } else {
        writeRlp(codeChange, output, RLPOutput::writeBytes);
      }

      final LogTuple<AccountValue> accountChange = layer.getAccountChanges().get(address);

      if (accountChange == null) {
        output.writeNull();
      } else {
        writeRlp(accountChange, output, (o, sta) -> sta.writeTo(o));
      }

      // get storage changes for this address:
      final Map<StorageSlotKey, LogTuple<UInt256>> storageChanges =
          layer.getStorageChanges().get(address);

      if (storageChanges == null || storageChanges.isEmpty()) {
        output.writeNull();
      } else {
        output.startList();
        for (final Map.Entry<StorageSlotKey, LogTuple<UInt256>> storageChangeEntry :
            storageChanges.entrySet()) {
          output.startList();

          StorageSlotKey storageSlotKey = storageChangeEntry.getKey();
          output.writeBytes(storageSlotKey.getSlotHash());
          writeInnerRlp(storageChangeEntry.getValue(), output, RLPOutput::writeUInt256Scalar);
          if (storageSlotKey.getSlotKey().isPresent()) {
            output.writeUInt256Scalar(storageSlotKey.getSlotKey().get());
          }
          output.endList();
        }
        output.endList();
      }

      output.endList(); // this change
    }

    output.endList(); // container
  }

  /* here just for testing */
  @Override
  public PluginTrieLogLayer deserialize(final byte[] bytes) {
    return readFrom(new BytesValueRLPInput(Bytes.wrap(bytes), false));
  }

  /*
   * This is here for testing purposes, readFrom cannot defer to existing storage when
   * encountering null values for prior or updated values and should not be considered
   * a zk-state-friendly implementation for that reason.
   *
   * For a state-deferring implementation, see shomei trielog decoding implementation:
   * https://github.com/Consensys/shomei/blob/main/core/src/main/java/net/consensys/shomei/trielog/TrieLogLayerConverter.java
   */
  public static PluginTrieLogLayer readFrom(final RLPInput input) {
    Map<Address, LogTuple<AccountValue>> accounts = new HashMap<>();
    Map<Address, LogTuple<Bytes>> code = new HashMap<>();
    Map<Address, Map<StorageSlotKey, LogTuple<UInt256>>> storage = new HashMap<>();

    input.enterList();
    Hash blockHash = Hash.wrap(input.readBytes32());
    // blockNumber is optional
    Optional<Long> blockNumber =
        Optional.of(!input.nextIsList())
            .filter(isPresent -> isPresent)
            .map(__ -> input.readLongScalar());

    while (!input.isEndOfCurrentList()) {
      input.enterList();
      final Address address = Address.readFrom(input);

      if (input.nextIsNull()) {
        input.skipNext();
      } else {
        input.enterList();
        final Bytes oldCode = nullOrValue(input, RLPInput::readBytes);
        final Bytes newCode = nullOrValue(input, RLPInput::readBytes);
        final boolean isCleared = getOptionalIsCleared(input);
        input.leaveList();
        code.put(address, new TrieLogValue<>(oldCode, newCode, isCleared));
      }

      if (input.nextIsNull()) {
        input.skipNext();
      } else {
        input.enterList();
        final AccountValue oldValue = nullOrValue(input, ZkAccountValue::readFrom);
        final AccountValue newValue = nullOrValue(input, ZkAccountValue::readFrom);
        final boolean isCleared = getOptionalIsCleared(input);
        input.leaveList();
        accounts.put(address, new TrieLogValue<>(oldValue, newValue, isCleared));
      }

      if (input.nextIsNull()) {
        input.skipNext();
      } else {
        final Map<StorageSlotKey, LogTuple<UInt256>> storageChanges = new TreeMap<>();
        input.enterList();
        while (!input.isEndOfCurrentList()) {
          int storageElementlistSize = input.enterList();

          final Hash slotHash = Hash.wrap(input.readBytes32());
          final UInt256 oldValue = nullOrValue(input, RLPInput::readUInt256Scalar);
          final UInt256 newValue = nullOrValue(input, RLPInput::readUInt256Scalar);
          final boolean isCleared = getOptionalIsCleared(input);
          final Optional<UInt256> slotKey =
              Optional.of(storageElementlistSize)
                  .filter(listSize -> listSize == 5)
                  .map(__ -> input.readUInt256Scalar())
                  .or(Optional::empty);

          final StorageSlotKey storageSlotKey = new StorageSlotKey(slotHash, slotKey);

          storageChanges.put(storageSlotKey, new TrieLogValue<>(oldValue, newValue, isCleared));
          input.leaveList();
        }
        input.leaveList();
        storage.put(address, storageChanges);
      }
      // lenient leave list for forward compatible additions.
      input.leaveListLenient();
    }
    input.leaveListLenient();

    return new PluginTrieLogLayer(blockHash, blockNumber, accounts, code, storage, true);
  }

  protected static <T> T nullOrValue(final RLPInput input, final Function<RLPInput, T> reader) {
    if (input.nextIsNull()) {
      input.skipNext();
      return null;
    } else {
      return reader.apply(input);
    }
  }

  protected static boolean getOptionalIsCleared(final RLPInput input) {
    return Optional.of(input.isEndOfCurrentList())
        .filter(isEnd -> !isEnd) // isCleared is optional
        .map(__ -> nullOrValue(input, RLPInput::readInt))
        .filter(i -> i == 1)
        .isPresent();
  }

  public static <T> void writeRlp(
      final LogTuple<T> value, final RLPOutput output, final BiConsumer<RLPOutput, T> writer) {
    output.startList();
    writeInnerRlp(value, output, writer);
    output.endList();
  }

  public static <T> void writeInnerRlp(
      final LogTuple<T> value, final RLPOutput output, final BiConsumer<RLPOutput, T> writer) {
    // value may be null in cases like a zero-read or a 'decorated' value from zktracer hubSeen
    if (value == null || value.getPrior() == null) {
      output.writeNull();
    } else {
      writer.accept(output, value.getPrior());
    }
    if (value == null || value.getUpdated() == null) {
      output.writeNull();
    } else {
      writer.accept(output, value.getUpdated());
    }
    if (value != null && !value.isClearedAtLeastOnce()) {
      output.writeNull();
    } else {
      output.writeInt(1);
    }
  }

  // helper function to safely assert account diff:
  private static boolean accountHasChanged(AccountValue prior, AccountValue updated) {
    return prior.getNonce() != updated.getNonce()
        || !prior.getBalance().equals(updated.getBalance())
        || !prior.getStorageRoot().equals(updated.getStorageRoot())
        || !prior.getCodeHash().equals(updated.getCodeHash());
  }
}
