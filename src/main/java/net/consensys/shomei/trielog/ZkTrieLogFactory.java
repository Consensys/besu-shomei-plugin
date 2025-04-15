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

import net.consensys.shomei.context.ShomeiContext;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.AccountValue;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;
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

  public ZkTrieLogFactory(ShomeiContext ctx) {
    this.ctx = ctx;
  }

  @Override
  @SuppressWarnings("unchecked")
  public TrieLog create(final TrieLogAccumulator accumulator, final BlockHeader blockHeader) {

    if (ctx.getCliOptions().enableZkTraceComparison) {
      LOG.debug(
          "comparing ZkTrieLog with ZkTracer for block {}:{}",
          blockHeader.getNumber(),
          blockHeader.getBlockHash());
      ctx.getBlockImportTraceProvider().compareWithTrace(blockHeader, accumulator);
    }

    var accountsToUpdate = accumulator.getAccountsToUpdate();
    var codeToUpdate = accumulator.getCodeToUpdate();
    var storageToUpdate = accumulator.getStorageToUpdate();

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

  @Override
  public PluginTrieLogLayer deserialize(final byte[] bytes) {
    return readFrom(new BytesValueRLPInput(Bytes.wrap(bytes), false));
  }

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
    if (value.getPrior() == null) {
      output.writeNull();
    } else {
      writer.accept(output, value.getPrior());
    }
    if (value.getUpdated() == null) {
      output.writeNull();
    } else {
      writer.accept(output, value.getUpdated());
    }
    if (!value.isClearedAtLeastOnce()) {
      output.writeNull();
    } else {
      output.writeInt(1);
    }
  }
}
