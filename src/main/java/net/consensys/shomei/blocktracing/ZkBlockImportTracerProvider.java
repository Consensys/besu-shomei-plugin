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
package net.consensys.shomei.blocktracing;

import static net.consensys.linea.zktracer.Fork.fromMainnetHardforkIdToTracerFork;
import static net.consensys.shomei.cli.ShomeiCliOptions.ZkTraceComparisonFeature.ACCUMULATOR_TO_HUB;
import static net.consensys.shomei.cli.ShomeiCliOptions.ZkTraceComparisonFeature.DECORATE_FROM_HUB;
import static net.consensys.shomei.cli.ShomeiCliOptions.ZkTraceComparisonFeature.FILTER_FROM_HUB;
import static net.consensys.shomei.cli.ShomeiCliOptions.ZkTraceComparisonFeature.HUB_TO_ACCUMULATOR;
import static net.consensys.shomei.cli.ShomeiCliOptions.ZkTraceComparisonFeature.MISMATCH_LOGGING;
import static net.consensys.shomei.cli.ShomeiCliOptions.ZkTraceComparisonFeature.anyEnabled;

import net.consensys.linea.plugins.config.LineaL1L2BridgeSharedConfiguration;
import net.consensys.linea.zktracer.ZkTracer;
import net.consensys.shomei.context.ShomeiContext;

import java.math.BigInteger;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import com.google.common.collect.Sets;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.DelegatingBytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.AccountValue;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.HardforkId;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.services.BlockImportTracerProvider;
import org.hyperledger.besu.plugin.services.BlockchainService;
import org.hyperledger.besu.plugin.services.tracer.BlockAwareOperationTracer;
import org.hyperledger.besu.plugin.services.trielogs.TrieLog.LogTuple;
import org.hyperledger.besu.plugin.services.trielogs.TrieLogAccumulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * naive tracking implementation of BlockImportTracerProvider. Presumes currentTracer will be
 * consumed during trielog writing, prior to fetching another block import tracer.
 */
public class ZkBlockImportTracerProvider implements BlockImportTracerProvider {
  private static final Logger LOG = LoggerFactory.getLogger(ZkBlockImportTracerProvider.class);
  private static final int MAX_TRACER_HISTORY_SIZE = 3;

  private final Deque<HeaderTracerTuple> tracerHistory = new ConcurrentLinkedDeque<>();
  private final BlockchainService blockchainService;
  private final Supplier<Optional<BigInteger>> chainIdSupplier;
  private final Supplier<Integer> featureMask;
  private final Supplier<Integer> skipTraceUntil;
  private final Supplier<Boolean> enableZkTracing;

  public ZkBlockImportTracerProvider(
      final ShomeiContext ctx, final BlockchainService blockchainService) {
    this.blockchainService = blockchainService;
    // defer to suppliers for late bound configs and services
    this.chainIdSupplier = Suppliers.memoize(blockchainService::getChainId);
    this.skipTraceUntil = Suppliers.memoize(() -> ctx.getCliOptions().zkSkipTraceUntil);
    this.featureMask = Suppliers.memoize(() -> ctx.getCliOptions().zkTraceComparisonMask);
    this.enableZkTracing = Suppliers.memoize(() -> ctx.getCliOptions().enableZkTracer);
  }

  @Override
  public synchronized BlockAwareOperationTracer getBlockImportTracer(
      final BlockHeader blockHeader) {
    // if blockheader is prior to the configured skip-until param, return no_tracing
    if (!enableZkTracing.get() || (skipTraceUntil.get() > blockHeader.getNumber())) {
      return BlockAwareOperationTracer.NO_TRACING;
    }

    final HardforkId.MainnetHardforkId forkId =
        (HardforkId.MainnetHardforkId) blockchainService.getHardforkId(blockHeader);
    final ZkTracer zkTracer =
        new ZkTracer(
            fromMainnetHardforkIdToTracerFork(forkId),
            LineaL1L2BridgeSharedConfiguration.EMPTY,
            chainIdSupplier.get().orElseThrow(() -> new RuntimeException("Chain Id unavailable")));

    LOG.debug("returning zkTracer for {}", headerLogString(blockHeader));

    // Add to the FIFO list
    tracerHistory.addLast(new HeaderTracerTuple(blockHeader, zkTracer));

    // Evict oldest entries if we exceed the max size
    while (tracerHistory.size() > MAX_TRACER_HISTORY_SIZE) {
      tracerHistory.removeFirst();
    }

    return zkTracer;
  }

  public Optional<HeaderTracerTuple> getCurrentTracerTuple(final BlockHeader blockheader) {
    // Iterate from the end (most recent) to find the latest tracer for the given block header
    return StreamSupport.stream(
            ((Iterable<HeaderTracerTuple>) () -> tracerHistory.descendingIterator()).spliterator(),
            false)
        .filter(tuple -> tuple.header().getBlockHash().equals(blockheader.getBlockHash()))
        .findFirst();
  }

  public Optional<HeaderTracerTuple> getEarliestTracerTuple(final BlockHeader blockHeader) {
    // Iterate from the beginning (oldest) to find the earliest tracer for the given block header
    return tracerHistory.stream()
        .filter(tuple -> tuple.header().getBlockHash().equals(blockHeader.getBlockHash()))
        .findFirst();
  }

  /**
   * Compares besu accumulator with hub, warns if there is a discrepancy.
   *
   * <p>Using the zktracer at block import *should* result in the besu accumulator having all the
   * necessary state accesses from HUB. This method asserts that expectation, alerts on discrepancy,
   * and will add any missing elements accumulator map.
   *
   * @param blockHeader header for which we are writing a trace
   * @param accumulator bonsai accumulator we are filtering
   */
  public HubDiffTuple compareWithTrace(
      final BlockHeader blockHeader, final TrieLogAccumulator accumulator) {

    // bail if genesis, we do not trace genesis block
    if (blockHeader.getNumber() == 0) {
      return HubDiffTuple.EMPTY;
    }

    var zkTracerTuple =
        getEarliestTracerTuple(blockHeader)
            .filter(t -> t.header().getBlockHash().equals(blockHeader.getBlockHash()))
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        String.format(
                            "Block %s not found in the Tracer while attempting comparison with Hub.",
                            headerLogString(blockHeader))));

    // use tracer state to compare besu accumulator:
    var hubAccountsSeen = zkTracerTuple.zkTracer.getAddressesSeenByHubForRelativeBlock(1);
    var hubStorageSeen = zkTracerTuple.zkTracer.getStoragesSeenByHubForRelativeBlock(1);

    var hubAccountDiffs = compareAndWarnAccount(blockHeader, accumulator, hubAccountsSeen);

    var hubStorageDiff = compareAndWarnStorage(blockHeader, accumulator, hubStorageSeen);

    LOG.debug("completed comparison for {}", headerLogString(blockHeader));
    return new HubDiffTuple(
        new HubSeenDiff(hubAccountDiffs.inHub, hubStorageDiff.inHub),
        new HubSeenDiff(hubAccountDiffs.notInHub, hubStorageDiff.notInHub));
  }

  @VisibleForTesting
  StorageDiffTuple compareAndWarnStorage(
      final BlockHeader blockHeader,
      final TrieLogAccumulator accumulator,
      final Map<Address, Set<Bytes32>> hubSeenStorage) {

    final Map<Address, Set<Bytes32>> hubStorageFoundDiff = new HashMap<>();
    final Map<Address, Set<Bytes32>> hubStorageMissingDiff = new HashMap<>();
    final var storageToUpdate = accumulator.getStorageToUpdate();
    LOG.debug(
        "Block {} comparing hubSeen size {} to accumulator storage map size {}",
        blockHeader.getNumber(),
        hubSeenStorage.size(),
        storageToUpdate.size());

    // First pass: check hubSeen -> storageToUpdate
    if (anyEnabled(featureMask.get(), HUB_TO_ACCUMULATOR, DECORATE_FROM_HUB)) {
      hubSeenStorage.forEach(
          (address, hubSlots) -> {
            var accumulatorSlots = storageToUpdate.get(address);
            if (!hubSlots.isEmpty() && accumulatorSlots == null) {
              alert(
                  () ->
                      LOG.warn(
                          "block {} hub account {} is missing all keys {} in accumulator storage slot modifications",
                          blockHeader.getNumber(),
                          address.toHexString(),
                          hubSlots.stream()
                              .map(Bytes32::toShortHexString)
                              .collect(Collectors.joining(","))));
              if (anyEnabled(featureMask.get(), DECORATE_FROM_HUB)) {
                // add all hubSeenSlots for this address to diff map:
                hubStorageFoundDiff.put(address, hubSlots);
              }
            } else {
              var missingSlots =
                  hubSlots.stream()
                      .filter(
                          slot ->
                              !accumulatorSlots.containsKey(
                                  new StorageSlotKey(UInt256.fromBytes(slot))))
                      .collect(Collectors.toSet());
              missingSlots.forEach(
                  slot ->
                      alert(
                          () ->
                              LOG.warn(
                                  "block {} hub account {} slot key {} is missing from accumulator modifications",
                                  blockHeader.getNumber(),
                                  address.toHexString(),
                                  slot.toHexString())));
              // add missing hubSeenSlots for this address to diff map
              if (!missingSlots.isEmpty() && anyEnabled(featureMask.get(), DECORATE_FROM_HUB)) {
                hubStorageFoundDiff.put(address, missingSlots);
              }
            }
          });
    }

    // Second pass: check storageToUpdate -> hubSeen
    if (anyEnabled(featureMask.get(), ACCUMULATOR_TO_HUB)) {
      storageToUpdate.forEach(
          (address, accumulatorSlots) -> {
            var hubSlots = hubSeenStorage.get(address);
            if (!accumulatorSlots.isEmpty() && hubSlots == null) {
              // account and all slots are missing:
              alert(
                  () -> {
                    LOG.warn(
                        "block {} accumulator storage account {} is missing from hub seen storage modifications",
                        blockHeader.getNumber(),
                        address.toHexString());
                    if (anyEnabled(featureMask.get(), FILTER_FROM_HUB)) {
                      // add all accumulator slots for this address to diff map:
                      hubStorageMissingDiff.put(
                          address,
                          accumulatorSlots.keySet().stream()
                              .map(StorageSlotKey::getSlotKey)
                              .filter(Optional::isPresent)
                              .map(Optional::get)
                              .map(UInt256::toBytes)
                              .collect(Collectors.toSet()));
                    }
                  });
            } else {
              // account present, but some slots are missing:
              var missingSlots =
                  accumulatorSlots.entrySet().stream()
                      .filter(
                          slotEntry ->
                              slotEntry.getKey().getSlotKey().isPresent()
                                  && !hubSlots.contains(
                                      slotEntry.getKey().getSlotKey().get().toBytes()))
                      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

              // add missing hubSeenSlots for this address to accumulator
              if (!missingSlots.isEmpty() && anyEnabled(featureMask.get(), DECORATE_FROM_HUB)) {
                hubStorageMissingDiff.put(
                    address,
                    missingSlots.keySet().stream()
                        .map(StorageSlotKey::getSlotKey)
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .map(UInt256::toBytes)
                        .collect(Collectors.toSet()));
              }

              // alert on missing slots
              missingSlots.forEach(
                  (storageSlotKey, slotVal) ->
                      alert(
                          () ->
                              LOG.warn(
                                  "block {} accumulator storage account {} slot key {} value pre {} post {} is missing from hub seen storage modifications",
                                  blockHeader.getNumber(),
                                  address.toHexString(),
                                  storageSlotKey
                                      .getSlotKey()
                                      .map(Bytes::toHexString)
                                      .orElse(
                                          "hash::" + storageSlotKey.getSlotHash().toHexString()),
                                  Optional.ofNullable(slotVal.getUpdated())
                                      .map(UInt256::toShortHexString)
                                      .orElse("null"),
                                  Optional.ofNullable(slotVal.getPrior())
                                      .map(UInt256::toShortHexString)
                                      .orElse("null"))));
            }
          });
    }

    return new StorageDiffTuple(hubStorageFoundDiff, hubStorageMissingDiff);
  }

  @VisibleForTesting
  AccountDiffTuple compareAndWarnAccount(
      final BlockHeader blockHeader,
      final TrieLogAccumulator accumulator,
      final Set<Address> hubSeenAddresses) {

    Set<Address> hubAccountsFoundDiff = new HashSet<>();
    Set<Address> hubAccountsMissingDiff = new HashSet<>();
    var accountsToUpdate = accumulator.getAccountsToUpdate();
    LOG.debug(
        "Block {} comparing hubSeen size {} to accumulator account map size {}",
        blockHeader.getNumber(),
        hubSeenAddresses.size(),
        accountsToUpdate.size());

    // Accounts in hubSeen but missing from accountsToUpdate
    if (anyEnabled(featureMask.get(), HUB_TO_ACCUMULATOR, DECORATE_FROM_HUB)) {
      Sets.difference(hubSeenAddresses, accountsToUpdate.keySet())
          .forEach(
              hubAddress -> {
                alert(
                    () ->
                        LOG.warn(
                            "block {} hub seen account {} is missing from accumulator updated addresses",
                            blockHeader.getNumber(),
                            hubAddress.toHexString()));
                if (anyEnabled(featureMask.get(), DECORATE_FROM_HUB)) {
                  hubAccountsFoundDiff.add(hubAddress);
                }
              });
    }

    // Accounts in accountsToUpdate but missing from hubSeen
    if (anyEnabled(featureMask.get(), ACCUMULATOR_TO_HUB, FILTER_FROM_HUB)) {

      Sets.difference(accountsToUpdate.keySet(), hubSeenAddresses)
          .forEach(
              accountAddress -> {
                alert(
                    () ->
                        LOG.warn(
                            "block {} accumulator address to update {} is missing from hub seen accounts, diff: {} ",
                            blockHeader.getNumber(),
                            accountAddress.toHexString(),
                            accountDiffString(accountsToUpdate.get(accountAddress))));
                if (anyEnabled(featureMask.get(), FILTER_FROM_HUB)) {
                  hubAccountsMissingDiff.add(accountAddress);
                }
              });
    }

    return new AccountDiffTuple(hubAccountsFoundDiff, hubAccountsMissingDiff);
  }

  /**
   * here just to make simpler test assertions.
   *
   * @param logLambda runnable that logs.
   */
  @VisibleForTesting
  void alert(final Runnable logLambda) {
    if (anyEnabled(featureMask.get(), MISMATCH_LOGGING)) {
      logLambda.run();
    }
  }

  public static String headerLogString(final BlockHeader header) {
    return header.getNumber() + " (" + header.getBlockHash() + ")";
  }

  public String accountDiffString(final LogTuple<? extends AccountValue> accountTuple) {
    // return a string with the account diff:
    StringBuilder logBuilder = new StringBuilder("{");
    var updated = accountTuple.getUpdated();
    var prior = accountTuple.getPrior();
    if (prior == null || updated == null) {
      if (prior != null) {
        logBuilder.append(
            String.format(
                "updated is null;prior _Nonce:%d, _Balance:%s, _CodeHash:%s, _StorageRoot: %s",
                prior.getNonce(),
                Optional.ofNullable(prior.getBalance()).map(Wei::toShortHexString).orElse("null"),
                Optional.ofNullable(prior.getCodeHash())
                    .map(DelegatingBytes::toHexString)
                    .orElse("null"),
                Optional.ofNullable(prior.getStorageRoot())
                    .map(DelegatingBytes::toHexString)
                    .orElse("null")));
      }
      if (updated != null) {
        logBuilder.append(
            String.format(
                "prior is null;updated _Nonce:%d, _Balance:%s, _CodeHash:%s, _StorageRoot: %s",
                updated.getNonce(),
                Optional.ofNullable(updated.getBalance()).map(Wei::toShortHexString).orElse("null"),
                Optional.ofNullable(updated.getCodeHash())
                    .map(DelegatingBytes::toHexString)
                    .orElse("null"),
                Optional.ofNullable(updated.getStorageRoot())
                    .map(DelegatingBytes::toHexString)
                    .orElse("null")));
      }
    } else {
      if (prior.getNonce() != updated.getNonce()) {
        logBuilder.append(
            String.format("_Nonce pre:%d;post:%d", prior.getNonce(), updated.getNonce()));
      }
      if (!prior.getBalance().equals(updated.getBalance())) {
        logBuilder.append(
            String.format(
                "_Balance pre:%s;post:%s",
                Optional.ofNullable(prior.getBalance()).map(Wei::toShortHexString).orElse("null"),
                Optional.ofNullable(updated.getBalance())
                    .map(Wei::toShortHexString)
                    .orElse("null")));
      }
      if (!prior.getCodeHash().equals(updated.getCodeHash())) {
        logBuilder.append(
            String.format(
                "_CodeHash pre:%s;post:%s",
                Optional.ofNullable(prior.getCodeHash())
                    .map(DelegatingBytes::toHexString)
                    .orElse("null"),
                Optional.ofNullable(updated.getCodeHash())
                    .map(DelegatingBytes::toHexString)
                    .orElse("null")));
      }
      if (!prior.getStorageRoot().equals(updated.getStorageRoot())) {
        logBuilder.append(
            String.format(
                "_StorageRoot pre:%s;post:%s",
                Optional.ofNullable(prior.getStorageRoot())
                    .map(DelegatingBytes::toHexString)
                    .orElse("null"),
                Optional.ofNullable(updated.getStorageRoot())
                    .map(DelegatingBytes::toHexString)
                    .orElse("null")));
      }
    }
    return logBuilder.append("}").toString();
  }

  public record HeaderTracerTuple(BlockHeader header, ZkTracer zkTracer) {}

  record AccountDiffTuple(Set<Address> inHub, Set<Address> notInHub) {}

  record StorageDiffTuple(Map<Address, Set<Bytes32>> inHub, Map<Address, Set<Bytes32>> notInHub) {}

  public record HubSeenDiff(Set<Address> adressesDiff, Map<Address, Set<Bytes32>> storageDiff) {
    static final HubSeenDiff EMPTY =
        new HubSeenDiff(Collections.emptySet(), Collections.emptyMap());
  }

  public record HubDiffTuple(HubSeenDiff foundInHub, HubSeenDiff notFoundInHub) {
    static final HubDiffTuple EMPTY = new HubDiffTuple(HubSeenDiff.EMPTY, HubSeenDiff.EMPTY);
  }
}
