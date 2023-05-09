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
import static org.mockito.Mockito.when;

import net.consensys.shomei.rpc.methods.GetShomeiTrieLogs;
import net.consensys.shomei.trielog.TrieLogLayer;
import net.consensys.shomei.trielog.ZkTrieLogService;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
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

  @Mock ZkTrieLogService mockService;
  @Mock TrieLogProvider mockProvider;

  TrieLogLayer layer =
      new TrieLogLayer(Hash.ZERO, Optional.of(0L), Map.of(), Map.of(), Map.of(), true);

  MockJsonRpcHttpVerticle verticle;

  @BeforeEach
  public void setup(Vertx vertx, VertxTestContext testContext) {
    verticle = new MockJsonRpcHttpVerticle(List.of(new GetShomeiTrieLogs(mockService)));
    vertx.deployVerticle(verticle, testContext.succeedingThenComplete());
    when(mockService.getTrieLogProvider()).thenReturn(mockProvider);
    //    when(mockProvider.getTrieLogLayer(anyInt())).thenReturn(Optional.of(layer));
    doAnswer(__ -> List.of(new TrieLogProvider.TrieLogRangePair(1, layer)))
        .when(mockProvider)
        .getTrieLogsByRange(1L, 1L);
  }

  @AfterEach
  public void tearDown(Vertx vertx, VertxTestContext testContext) {
    vertx.close(testContext.succeedingThenComplete());
  }

  @Test
  public void testGetTrieLogSingleIsPresent(Vertx vertx, VertxTestContext testContext) {
    WebClient client = WebClient.create(vertx);

    JsonObject requestJson =
        new JsonObject()
            .put("jsonrpc", "2.0")
            .put("id", 1)
            .put("method", "shomei_getTrieLogs")
            .put("params", new JsonObject());

    client
        .post(verticle.getServerPort(), "localhost", "/")
        .sendJsonObject(
            requestJson,
            response -> {
              if (response.succeeded()) {
                JsonObject responseBody = response.result().bodyAsJsonObject();
                assertThat(responseBody.getInteger("id")).isEqualTo(1);
                assertThat(responseBody.getString("jsonrpc")).isEqualTo("2.0");

                // Add assertions for the expected result
                assertThat(responseBody.getJsonArray("result")).hasSize(1);

                testContext.completeNow();
              } else {
                testContext.failNow(response.cause());
              }
            });
  }
}
