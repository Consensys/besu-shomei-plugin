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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.services.BlockImportTracerProvider;
import org.hyperledger.besu.plugin.services.tracer.BlockAwareOperationTracer;
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
  ZkBlockImportTracerProvider() {}

  @Override
  public BlockAwareOperationTracer getBlockImportTracer(final BlockHeader processableBlockHeader) {
    // TODO: if we are just tracking storage, do we need an explicit L1L2Bridge config or chain id?
    //       empty bridge and linea mainnet chain id for now
    ZkTracer zkTracer =
        new ZkTracer(LineaL1L2BridgeSharedConfiguration.EMPTY, BigInteger.valueOf(59144L));

    zkTracer.traceStartConflation(1L);
    zkTracer.traceStartBlock(processableBlockHeader, processableBlockHeader.getCoinbase());
    currentTracer.set(new HeaderTracerTuple(processableBlockHeader, zkTracer));

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
    var current = getCurrentTracerTuple();
    if (!current
        .filter(t -> t.header().getBlockHash().equals(blockHeader.getBlockHash()))
        .isPresent()) {
      LOG.warn(
          "Trace not found while attempting to filter block {}.  current trace block {}",
          headerLogString(blockHeader),
          current.map(t -> t.header).map(this::headerLogString).orElse("empty"));
    }

    // TODO: need latest zktracer:arithmetization to make this comparison:

    //    // use tracer state to compare besu accumulator:
    //    var state = current.get().zkTracer.getHub().getBlockStack();
    //    var storageToUpdate = accumulator.getStorageToUpdate();
    //    var accountsToUpdate = accumulator.getAccountsToUpdate();

  }

  public String headerLogString(BlockHeader header) {
    return header.getNumber() + " (" + header.getBlockHash() + ")";
  }

  public record HeaderTracerTuple(BlockHeader header, ZkTracer zkTracer) {}
}
