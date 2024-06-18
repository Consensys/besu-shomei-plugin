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
package net.consensys.shomei.rpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import net.consensys.shomei.rpc.methods.ShomeiGetTrieLog;
import net.consensys.shomei.rpc.methods.ShomeiGetTrieLogsByRange;
import net.consensys.shomei.trielog.PluginTrieLogLayer;
import net.consensys.shomei.trielog.ZkTrieLogService;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.plugin.services.trielogs.TrieLogProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(VertxExtension.class)
@ExtendWith(MockitoExtension.class)
public class GetShomeiTrieLogsTests {

  ZkTrieLogService trieLogService = spy(ZkTrieLogService.getInstance());
  @Mock TrieLogProvider mockProvider;

  PluginTrieLogLayer mockLayer =
      new PluginTrieLogLayer(Hash.ZERO, Optional.of(0L), Map.of(), Map.of(), Map.of(), true);

  MockJsonRpcHttpVerticle verticle;
  WebClient client;

  @BeforeEach
  public void setup(Vertx vertx, VertxTestContext testContext) {
    verticle =
        new MockJsonRpcHttpVerticle(
            List.of(
                new ShomeiGetTrieLogsByRange(trieLogService),
                new ShomeiGetTrieLog(trieLogService)));
    // start verticle
    vertx.deployVerticle(verticle, testContext.succeedingThenComplete());
    client = WebClient.create(vertx);

    when(trieLogService.getTrieLogProvider()).thenReturn(mockProvider);
  }

  @AfterEach
  public void tearDown(Vertx vertx, VertxTestContext testContext) {
    // stop verticle
    vertx.close(testContext.succeedingThenComplete());
  }

  @Test
  public void testGetTrieLogRangeIsPresent(VertxTestContext testContext) {

    // mock range response
    doAnswer(
            __ ->
                List.of(
                    new TrieLogProvider.TrieLogRangeTuple(Hash.ZERO, 1, mockLayer),
                    new TrieLogProvider.TrieLogRangeTuple(Hash.ZERO, 2, mockLayer)))
        .when(mockProvider)
        .getTrieLogsByRange(1L, 2L);

    Object[] params = {"1", "2"};
    JsonObject requestJson =
        new JsonObject()
            .put("jsonrpc", "2.0")
            .put("id", 1)
            .put("method", "shomei_getTrieLogsByRange")
            .put("params", params);

    assertRequest(
        requestJson,
        testContext,
        response -> {
          // Add assertions for the expected result
          var res = response.getJsonArray("result");
          assertThat(res).hasSize(2);
        });
  }

  @Test
  public void testGetTrieLogRangeIsAbsent(VertxTestContext testContext) {
    Object[] params = {"1", "2"};
    JsonObject requestJson =
        new JsonObject()
            .put("jsonrpc", "2.0")
            .put("id", 1)
            .put("method", "shomei_getTrieLogsByRange")
            .put("params", params);

    assertRequest(
        requestJson,
        testContext,
        response -> {
          // Add assertions for the expected result
          var res = response.getJsonArray("result");
          assertThat(res).hasSize(0);
        });
  }

  @Test
  public void testGetSingleTrieLogIsPresent(VertxTestContext testContext) {
    // mock single log response
    when(mockProvider.getTrieLogLayer(1)).thenReturn(Optional.of(mockLayer));

    JsonObject requestJson =
        new JsonObject()
            .put("jsonrpc", "2.0")
            .put("id", 1)
            .put("method", "shomei_getTrieLog")
            .put("params", List.of("1"));

    assertRequest(
        requestJson,
        testContext,
        response -> {
          // Add assertions for the expected result
          var res = response.getString("result");
          assertThat(res)
              .isEqualTo(
                  Bytes.wrap(trieLogService.getTrieLogFactory().get().serialize(mockLayer))
                      .toHexString());
        });
  }

  @Test
  public void testGetSingleTrieLogIsAbsent(VertxTestContext testContext) {

    JsonObject requestJson =
        new JsonObject()
            .put("jsonrpc", "2.0")
            .put("id", 1)
            .put("method", "shomei_getTrieLog")
            .put("params", List.of("1"));

    assertRequest(
        requestJson,
        testContext,
        response -> {
          // Add assertions for the expected result
          var res = response.getString("result");
          assertThat(res).isEmpty();
        });
  }

  private void assertRequest(
      JsonObject requestJson, VertxTestContext testContext, Consumer<JsonObject> responseConsumer) {
    client
        .post(verticle.getServerPort(), "localhost", "/")
        .sendJsonObject(
            requestJson,
            response -> {
              if (response.succeeded()) {
                JsonObject responseBody = response.result().bodyAsJsonObject();
                assertThat(responseBody.getInteger("id")).isEqualTo(1);
                assertThat(responseBody.getString("jsonrpc")).isEqualTo("2.0");

                // test specific checks:
                responseConsumer.accept(responseBody);
                testContext.completeNow();
              } else {
                testContext.failNow(response.cause());
              }
            });
  }
}
