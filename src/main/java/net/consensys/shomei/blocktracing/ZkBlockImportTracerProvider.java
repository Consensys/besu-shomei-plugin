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

import net.consensys.linea.plugins.config.LineaL1L2BridgeSharedConfiguration;
import net.consensys.linea.zktracer.ZkTracer;

import java.math.BigInteger;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import com.google.common.annotations.VisibleForTesting;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.AccountValue;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.StorageSlotKey;
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
  public static final ZkBlockImportTracerProvider INSTANCE = new ZkBlockImportTracerProvider();
  private static final Logger LOG = LoggerFactory.getLogger(ZkBlockImportTracerProvider.class);

  private final AtomicReference<HeaderTracerTuple> currentTracer = new AtomicReference<>();

  /** package private for testing. */
  @VisibleForTesting
  ZkBlockImportTracerProvider() {}

  @Override
  public BlockAwareOperationTracer getBlockImportTracer(final BlockHeader blockHeader) {
    // TODO: if we are just tracking storage, do we need an explicit L1L2Bridge config or chain id?
    //       empty bridge and linea mainnet chain id for now
    ZkTracer zkTracer =
        new ZkTracer(LineaL1L2BridgeSharedConfiguration.EMPTY, BigInteger.valueOf(59144L));

    // TODO: debug level
    LOG.info("returning zkTracer for {}", headerLogString(blockHeader));

    // TODO: do we need conflation if we are only tracing 1 block?
    // zkTracer.traceStartConflation(1L);

    // leave it to abstract block processor to start/end tracing:
    // zkTracer.traceStartBlock(blockHeader, blockHeader.getCoinbase());

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
  public void compareWithTrace(
      final BlockHeader blockHeader, final TrieLogAccumulator accumulator) {

    // bail if genesis, we do not trace genesis block
    if (blockHeader.getNumber() == 0) {
      return;
    }

    var current =
        getCurrentTracerTuple()
            .filter(t -> t.header().getBlockHash().equals(blockHeader.getBlockHash()));

    if (current.isEmpty()) {
      LOG.warn(
          "Trace not found while attempting to compare block {}.  current trace block {}",
          headerLogString(blockHeader),
          current.map(t -> t.header).map(this::headerLogString).orElse("empty"));
      return;
    }

    var zkTracerTuple = current.get();

    // TODO: remove me when upstream besu endsBlock tracing in import ; hacky hacky workaround
    // current.ifPresent(
    //     cur ->
    //         blockchainService
    //             .getBlockByHash(blockHeader.getBlockHash())
    //             .ifPresent(block -> cur.zkTracer.traceEndBlock(blockHeader, null)));

    // TODO: do we need conflation if we are only tracing one block?
    // zkTracerTuple.zkTracer.traceEndConflation(accumulator);

    // use tracer state to compare besu accumulator:
    var currentBlockStack = zkTracerTuple.zkTracer.getHub().blockStack().currentBlock();
    var hubAccountsSeen = currentBlockStack.addressesSeenByHub();
    var hubStorageSeen = currentBlockStack.storagesSeenByHub();
    var storageToUpdate = accumulator.getStorageToUpdate();
    var accountsToUpdate = accumulator.getAccountsToUpdate();

    compareAndWarnAccount(blockHeader, accountsToUpdate, hubAccountsSeen);

    compareAndWarnStorage(blockHeader, storageToUpdate, hubStorageSeen);

    LOG.info("completed comparison for {}", headerLogString(blockHeader));
  }

  @VisibleForTesting
  void compareAndWarnStorage(
      final BlockHeader blockHeader,
      final Map<Address, ? extends Map<StorageSlotKey, ? extends LogTuple<UInt256>>>
          storageToUpdate,
      final Map<Address, Set<Bytes32>> hubSeenStorage) {

    LOG.info(
        "Block {} comparing hubSeen size {} to accumulator storage map size {}",
        blockHeader.getNumber(),
        hubSeenStorage.size(),
        storageToUpdate.size());

    // check everything in hub seen storage is in accumulator:
    for (var hubStorageEntry : hubSeenStorage.entrySet()) {
      var accumulatorEntry = storageToUpdate.get(hubStorageEntry.getKey());
      if (accumulatorEntry == null) {
        alert(
            () ->
                LOG.warn(
                    "block {} hub account {} in missing in accumulator storage slot modifications",
                    blockHeader.getNumber(),
                    hubStorageEntry.getKey().toHexString()));
      } else {
        hubStorageEntry.getValue().stream()
            .filter(
                hubSlotKey ->
                    !accumulatorEntry.containsKey(
                        new StorageSlotKey(UInt256.fromBytes(hubSlotKey))))
            .forEach(
                hubSlotKey ->
                    alert(
                        () ->
                            LOG.warn(
                                "block {} hub account {} slot key {} is missing from accumulator modifications",
                                blockHeader.getNumber(),
                                hubStorageEntry.getKey().toHexString(),
                                hubSlotKey.toHexString())));
      }
    }

    // assert everything in accumulator is in hub seen storage
    for (var accumulatorEntry : storageToUpdate.entrySet()) {
      var hubSeenEntry = hubSeenStorage.get(accumulatorEntry.getKey());
      if (hubSeenEntry == null) {
        alert(
            () ->
                LOG.warn(
                    "block {} accumulator storage account {} is missing from hub seen storage modifications",
                    blockHeader.getNumber(),
                    accumulatorEntry.getKey().toHexString()));
      } else {
        accumulatorEntry.getValue().keySet().stream()
            .filter(
                accumulatorSlotKey ->
                    accumulatorSlotKey.getSlotKey().isPresent()
                        && !hubSeenEntry.contains(accumulatorSlotKey.getSlotKey().get().toBytes()))
            .forEach(
                accumulatorSlotKey ->
                    alert(
                        () ->
                            LOG.warn(
                                "block {} hub account {} slot key {} is missing from accumulator modifications",
                                blockHeader.getNumber(),
                                accumulatorEntry.getKey().toHexString(),
                                accumulatorSlotKey
                                    .getSlotKey()
                                    .map(Bytes::toHexString)
                                    .orElse(
                                        "hash::"
                                            + accumulatorSlotKey.getSlotHash().toHexString()))));
      }
    }
  }

  @VisibleForTesting
  void compareAndWarnAccount(
      final BlockHeader blockHeader,
      final Map<Address, ? extends LogTuple<? extends AccountValue>> accountsToUpdate,
      final Set<Address> hubSeenAddresses) {

    LOG.info(
        "Block {} comparing hubSeen size {} to accumulator account map size {}",
        blockHeader.getNumber(),
        hubSeenAddresses.size(),
        accountsToUpdate.size());

    // assert everything in hub seen addresses is in accumulator
    hubSeenAddresses.stream()
        .filter(hubAddress -> !accountsToUpdate.containsKey(hubAddress))
        .forEach(
            hubAddress ->
                alert(
                    () ->
                        LOG.warn(
                            "block {} hub seen account {} is missing from accumulator updated addresses",
                            blockHeader.getNumber(),
                            hubAddress.toHexString())));

    // assert everything in accumulator is in hub seen addresses
    accountsToUpdate.keySet().stream()
        .filter(accumulatorAddress -> !hubSeenAddresses.contains(accumulatorAddress))
        .forEach(
            accumulatorAddress ->
                alert(
                    () ->
                        LOG.warn(
                            "block {} accumulator address to update {} is missing from hub seen accounts",
                            blockHeader.getNumber(),
                            accumulatorAddress.toHexString())));
  }

  /**
   * here just to make simpler test assertions.
   *
   * @param logLambda runnable that logs.
   */
  @VisibleForTesting
  void alert(Runnable logLambda) {
    logLambda.run();
  }

  public String headerLogString(BlockHeader header) {
    return header.getNumber() + " (" + header.getBlockHash() + ")";
  }

  public record HeaderTracerTuple(BlockHeader header, ZkTracer zkTracer) {}
}
