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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import net.consensys.shomei.cli.ShomeiCliOptions;
import net.consensys.shomei.context.TestShomeiContext;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.plugin.data.SyncStatus;
import org.hyperledger.besu.plugin.services.trielogs.TrieLog;
import org.hyperledger.besu.plugin.services.trielogs.TrieLogEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
public class ZkTrieLogObserverTests {

  private int rpcServicePort;

  private static final String JSON_SUCCESS_RESPONSE =
      "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"accepted\"}";

  ShomeiCliOptions testOpts = new ShomeiCliOptions();
  private TestShomeiContext testCtx = TestShomeiContext.create().setCliOptions(testOpts);

  private ZkTrieLogFactory zkTrieLogFactory = new ZkTrieLogFactory(testCtx);
  private PluginTrieLogLayer trieLogFixture =
      new PluginTrieLogLayer(
          Hash.ZERO,
          Optional.of(1337L),
          Map.of(
              Address.ZERO,
              new TrieLogValue<>(
                  null, new ZkAccountValue(1, Wei.fromEth(1), Hash.ZERO, Hash.ZERO), false)),
          new HashMap<>(),
          new HashMap<>(),
          true);

  // should be provided by test method:
  private Consumer<HttpServerRequest> requestVerifier = null;

  @BeforeEach
  public void setUp(Vertx vertx, VertxTestContext context) {

    // Create a router and set up a route to handle JSON-RPC requests
    Router router = Router.router(vertx);
    router.post("/").handler(z -> handleJsonRpcRequest(z, requestVerifier));

    // Start the HTTP server on a random available port
    vertx
        .createHttpServer()
        .requestHandler(router)
        .listen(
            0,
            context.succeeding(
                server -> {
                  rpcServicePort = server.actualPort();
                  context.completeNow();
                }));
    testCtx.setZkTrieLogFactory(new ZkTrieLogFactory(testCtx));
  }

  private void handleJsonRpcRequest(
      final RoutingContext context, final Consumer<HttpServerRequest> requestHandler) {
    if (requestHandler != null) {
      requestHandler.accept(context.request());
    }
    context
        .response()
        .setStatusCode(200)
        .putHeader("Content-Type", "application/json")
        .end(JSON_SUCCESS_RESPONSE);
  }

  @Test
  public void testSendToZk(VertxTestContext context) {
    this.requestVerifier =
        req -> {
          req.bodyHandler(
              body -> {
                var params = body.toJsonObject().getJsonArray("params").getJsonObject(0);

                context.verify(
                    () -> {
                      assertThat(params.getString("blockHash")).isEqualTo(Hash.ZERO.toHexString());
                      assertThat(params.getLong("blockNumber")).isEqualTo(1337L);
                      var trielog =
                          zkTrieLogFactory.deserialize(
                              Bytes.fromHexString(params.getString("trieLog")).toArrayUnsafe());
                      assertThat(trielog).isEqualTo(trieLogFixture);
                    });
                context.completeNow();
              });
        };
    testOpts.shomeiHttpPort = rpcServicePort;
    ZkTrieLogObserver observer = new ZkTrieLogObserver(testCtx);
    TrieLogEvent addEvent = new MockTrieLogEvent(trieLogFixture);

    observer
        .handleShip(addEvent)
        .onComplete(
            context.succeeding(
                response -> {
                  context.verify(
                      () -> {
                        assertThat(response.statusCode()).isEqualTo(200);
                        // assert response
                        assertThat(response.bodyAsJsonObject().getString("result"))
                            .isEqualTo("accepted");
                      });
                  context.completeNow();
                }));
  }

  @Test
  public void assertSyncingHackWorks(VertxTestContext ctx, Vertx vertx) {
    final ZkTrieLogObserver observer = new ZkTrieLogObserver(testCtx);

    var mockEvent = new MockTrieLogEvent(trieLogFixture);
    var mockSyncStatus = mock(SyncStatus.class);
    observer.onSyncStatusChanged(Optional.of(mockSyncStatus));

    // assert that isSyncing is true when we are "in sync"
    assertThat(observer.buildParam(mockEvent).isSyncing()).isFalse();

    // assert that isSyncing is true when we are "out of sync"
    when(mockSyncStatus.getHighestBlock()).thenReturn(51L);
    observer.onSyncStatusChanged(Optional.of(mockSyncStatus));
    assertThat(observer.buildParam(mockEvent).isSyncing()).isTrue();

    // reset syncing status to false:
    when(mockSyncStatus.getHighestBlock()).thenReturn(5L);
    observer.onSyncStatusChanged(Optional.of(mockSyncStatus));

    // assert the hack, that isSyncing is false after 1.2 seconds
    vertx.setTimer(
        1200L,
        id -> {
          ctx.verify(
              () -> {
                assertThat(observer.buildParam(mockEvent).isSyncing()).isFalse();
              });
          ctx.completeNow();
        });
  }

  record MockTrieLogEvent(PluginTrieLogLayer trieLog) implements TrieLogEvent {
    @Override
    public Type getType() {
      return Type.ADDED;
    }

    @Override
    public TrieLog layer() {
      return trieLog;
    }
  }
}
