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
import net.consensys.linea.zktracer.Fork;
import net.consensys.linea.zktracer.ZkTracer;

import java.math.BigInteger;
import java.util.function.BiConsumer;

import com.google.common.annotations.VisibleForTesting;
import org.hyperledger.besu.plugin.data.BlockBody;
import org.hyperledger.besu.plugin.data.BlockHeader;

/**
 * LineCountingTracer extends ZkTracer by adding a BiConsumer tracking action.
 *
 * <p>The tracking action occurs after traceEndBlock, signifying the tracer is 'complete'.
 */
public class TrackingWrappedZkTracer extends ZkTracer {

  final BiConsumer<BlockHeader, ZkTracer> trackAction;

  TrackingWrappedZkTracer(
      final Fork fork,
      final LineaL1L2BridgeSharedConfiguration bridgeConfiguration,
      BigInteger chainId,
      BiConsumer<BlockHeader, ZkTracer> trackAction) {
    super(fork, bridgeConfiguration, chainId);
    this.trackAction = trackAction;
  }

  @Override
  public void traceEndBlock(final BlockHeader blockHeader, final BlockBody blockBody) {
    super.traceEndBlock(blockHeader, blockBody);
    this.executeTrackingAction(blockHeader);
  }

  @VisibleForTesting
  void executeTrackingAction(BlockHeader blockHeader) {
    trackAction.accept(blockHeader, this);
  }
}
