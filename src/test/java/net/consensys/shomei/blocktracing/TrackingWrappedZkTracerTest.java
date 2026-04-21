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
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import net.consensys.linea.plugins.config.LineaL1L2BridgeSharedConfiguration;
import net.consensys.linea.zktracer.ZkTracer;

import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.HardforkId;
import org.hyperledger.besu.evm.worldstate.WorldView;
import org.hyperledger.besu.plugin.data.BlockBody;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class TrackingWrappedZkTracerTest {

  @Mock BlockHeader mockHeader;
  @Mock BlockBody mockBody;
  @Mock WorldView mockWorldView;

  private TrackingWrappedZkTracer createTracer(final BiConsumer<BlockHeader, ZkTracer> action) {
    return new TrackingWrappedZkTracer(
        fromMainnetHardforkIdToTracerFork(HardforkId.MainnetHardforkId.OSAKA),
        LineaL1L2BridgeSharedConfiguration.EMPTY,
        BigInteger.valueOf(59144L),
        action);
  }

  @Test
  void trackActionIsCalledAfterTraceEndBlock() {
    AtomicReference<BlockHeader> capturedHeader = new AtomicReference<>();
    AtomicReference<ZkTracer> capturedTracer = new AtomicReference<>();

    var tracer =
        createTracer(
            (header, zkTracer) -> {
              capturedHeader.set(header);
              capturedTracer.set(zkTracer);
            });

    tracer.traceEndBlock(mockHeader, mockBody);

    assertThat(capturedHeader.get()).isSameAs(mockHeader);
    assertThat(capturedTracer.get()).isSameAs(tracer);
  }

  @Test
  @SuppressWarnings("unchecked")
  void trackActionIsNotCalledBeforeTraceEndBlock() {
    BiConsumer<BlockHeader, ZkTracer> mockAction = mock(BiConsumer.class);
    var tracer = createTracer(mockAction);

    tracer.traceStartBlock(mockWorldView, mockHeader, Address.fromHexString("0x1337"));

    verify(mockAction, never()).accept(mockHeader, tracer);
  }

  @Test
  @SuppressWarnings("unchecked")
  void trackActionIsCalledExactlyOncePerTraceEndBlock() {
    BiConsumer<BlockHeader, ZkTracer> mockAction = mock(BiConsumer.class);
    var tracer = createTracer(mockAction);

    tracer.traceEndBlock(mockHeader, mockBody);
    tracer.traceEndBlock(mockHeader, mockBody);

    verify(mockAction, times(2)).accept(mockHeader, tracer);
  }
}
