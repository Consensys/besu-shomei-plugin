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

import static net.consensys.shomei.cli.ShomeiCliOptions.ZkTraceComparisonFeature.ACCUMULATOR_TO_HUB;
import static net.consensys.shomei.cli.ShomeiCliOptions.ZkTraceComparisonFeature.DECORATE_FROM_HUB;
import static net.consensys.shomei.cli.ShomeiCliOptions.ZkTraceComparisonFeature.HUB_TO_ACCUMULATOR;
import static net.consensys.shomei.cli.ShomeiCliOptions.ZkTraceComparisonFeature.MISMATCH_LOGGING;
import static net.consensys.shomei.cli.ShomeiCliOptions.ZkTraceComparisonFeature.anyEnabled;

import net.consensys.linea.plugins.config.LineaL1L2BridgeSharedConfiguration;
import net.consensys.linea.zktracer.Fork;
import net.consensys.linea.zktracer.ZkTracer;
import net.consensys.shomei.context.ShomeiContext;

import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import com.google.common.collect.Sets;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.DelegatingBytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.AccountValue;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.services.BlockImportTracerProvider;
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

  private final AtomicReference<HeaderTracerTuple> currentTracer = new AtomicReference<>();
  private final Supplier<Optional<BigInteger>> chainIdSupplier;
  private final Supplier<Integer> featureMask;
  private final Supplier<Integer> skipTraceUntil;

  public ZkBlockImportTracerProvider(
      final ShomeiContext ctx, final Supplier<Optional<BigInteger>> chainIdSupplier) {
    // defer to suppliers for late bound configs and services
    this.chainIdSupplier = chainIdSupplier;
    this.skipTraceUntil = Suppliers.memoize(() -> ctx.getCliOptions().zkSkipTraceUntil);
    this.featureMask = Suppliers.memoize(() -> ctx.getCliOptions().zkTraceComparisonMask);
  }

  @Override
  public BlockAwareOperationTracer getBlockImportTracer(final BlockHeader blockHeader) {
    // if blockheader is prior to the configured skip-until param, return no_tracing
    if (skipTraceUntil.get() > blockHeader.getNumber()) {
      return BlockAwareOperationTracer.NO_TRACING;
    }

    // TODO: hardcoding LONDON works for now, but will need to be revisited when linea forks.
    //      https://github.com/hyperledger/besu/issues/8535
    ZkTracer zkTracer =
        new ZkTracer(
            Fork.LONDON,
            LineaL1L2BridgeSharedConfiguration.EMPTY,
            chainIdSupplier.get().orElseThrow(() -> new RuntimeException("Chain Id unavailable")));

    LOG.debug("returning zkTracer for {}", headerLogString(blockHeader));
    currentTracer.set(new HeaderTracerTuple(blockHeader, zkTracer));
    return zkTracer;
  }

  public Optional<HeaderTracerTuple> getCurrentTracerTuple() {
    return Optional.ofNullable(currentTracer.get());
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
  public HubSeenDiff compareWithTrace(
      final BlockHeader blockHeader, final TrieLogAccumulator accumulator) {

    // bail if genesis, we do not trace genesis block
    if (blockHeader.getNumber() == 0) {
      return new HubSeenDiff(Collections.emptySet(), Collections.emptyMap());
    }

    var current =
        getCurrentTracerTuple()
            .filter(t -> t.header().getBlockHash().equals(blockHeader.getBlockHash()));

    if (current.isEmpty()) {
      LOG.warn(
          "Trace not found while attempting to compare block {}.  current trace block {}",
          headerLogString(blockHeader),
          current.map(t -> t.header).map(this::headerLogString).orElse("empty"));
      return new HubSeenDiff(Collections.emptySet(), Collections.emptyMap());
    }

    var zkTracerTuple = current.get();

    // use tracer state to compare besu accumulator:
    var hubAccountsSeen = zkTracerTuple.zkTracer.getAddressesSeenByHubForRelativeBlock(1);
    var hubStorageSeen = zkTracerTuple.zkTracer.getStoragesSeenByHubForRelativeBlock(1);

    var hubAccountDiff = compareAndWarnAccount(blockHeader, accumulator, hubAccountsSeen);

    var hubStorageDiff = compareAndWarnStorage(blockHeader, accumulator, hubStorageSeen);

    LOG.debug("completed comparison for {}", headerLogString(blockHeader));
    return new HubSeenDiff(hubAccountDiff, hubStorageDiff);
  }

  @VisibleForTesting
  Map<Address, Set<Bytes32>> compareAndWarnStorage(
      final BlockHeader blockHeader,
      final TrieLogAccumulator accumulator,
      final Map<Address, Set<Bytes32>> hubSeenStorage) {

    final Map<Address, Set<Bytes32>> hubStorageDiff = new HashMap<>();
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
            if (accumulatorSlots == null) {
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
                // add all hubSeenSlots for this address to accumulator:
                hubStorageDiff.put(address, hubSlots);
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
              // add missing hubSeenSlots for this address to accumulator
              hubStorageDiff.put(address, missingSlots);
            }
          });
    }

    // Second pass: check storageToUpdate -> hubSeen
    if (anyEnabled(featureMask.get(), ACCUMULATOR_TO_HUB)) {
      storageToUpdate.forEach(
          (address, accumulatorSlots) -> {
            var hubSlots = hubSeenStorage.get(address);
            if (hubSlots == null) {
              alert(
                  () ->
                      LOG.warn(
                          "block {} accumulator storage account {} is missing from hub seen storage modifications",
                          blockHeader.getNumber(),
                          address.toHexString()));
            } else {
              accumulatorSlots.entrySet().stream()
                  .filter(
                      slotEntry ->
                          slotEntry.getKey().getSlotKey().isPresent()
                              && !hubSlots.contains(
                                  slotEntry.getKey().getSlotKey().get().toBytes()))
                  .forEach(
                      slotEntry ->
                          alert(
                              () ->
                                  LOG.warn(
                                      "block {} hub account {} slot key {} value pre {} post {} is missing from accumulator modifications",
                                      blockHeader.getNumber(),
                                      address.toHexString(),
                                      slotEntry
                                          .getKey()
                                          .getSlotKey()
                                          .map(Bytes::toHexString)
                                          .orElse(
                                              "hash::"
                                                  + slotEntry.getKey().getSlotHash().toHexString()),
                                      Optional.ofNullable(slotEntry.getValue().getUpdated())
                                          .map(UInt256::toShortHexString)
                                          .orElse("null"),
                                      Optional.ofNullable(slotEntry.getValue().getPrior())
                                          .map(UInt256::toShortHexString)
                                          .orElse("null"))));
            }
          });
    }

    return hubStorageDiff;
  }

  @VisibleForTesting
  Set<Address> compareAndWarnAccount(
      final BlockHeader blockHeader,
      final TrieLogAccumulator accumulator,
      final Set<Address> hubSeenAddresses) {

    Set<Address> hubAccountsDiff = new HashSet<>();
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
                  hubAccountsDiff.add(hubAddress);
                }
              });
    }

    // Accounts in accountsToUpdate but missing from hubSeen
    if (anyEnabled(featureMask.get(), ACCUMULATOR_TO_HUB)) {

      Sets.difference(accountsToUpdate.keySet(), hubSeenAddresses)
          .forEach(
              accountAddress ->
                  alert(
                      () ->
                          LOG.warn(
                              "block {} accumulator address to update {} is missing from hub seen accounts, diff: {} ",
                              blockHeader.getNumber(),
                              accountAddress.toHexString(),
                              accountDiffString(accountsToUpdate.get(accountAddress)))));
    }

    return hubAccountsDiff;
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

  public String headerLogString(final BlockHeader header) {
    return header.getNumber() + " (" + header.getBlockHash() + ")";
  }

  public String accountDiffString(final LogTuple<? extends AccountValue> accountTuple) {
    // return a string with the account diff:
    StringBuilder logBuilder = new StringBuilder("{");
    var updated = accountTuple.getUpdated();
    var prior = accountTuple.getPrior();
    if (prior == null || updated == null) {
      if (prior == null) {
        logBuilder.append("prior is null;");
      }
      if (updated == null) {
        logBuilder.append("updated is null;");
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

  public record HubSeenDiff(Set<Address> adressesDiff, Map<Address, Set<Bytes32>> storageDiff) {}
}
