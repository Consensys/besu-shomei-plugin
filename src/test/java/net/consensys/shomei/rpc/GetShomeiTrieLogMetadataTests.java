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
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import net.consensys.shomei.context.ShomeiContext;
import net.consensys.shomei.context.TestShomeiContext;
import net.consensys.shomei.rpc.methods.ShomeiGetTrieLogMetadata;
import net.consensys.shomei.trielog.PluginTrieLogLayer;
import net.consensys.shomei.trielog.TrieLogValue;
import net.consensys.shomei.trielog.ZkAccountValue;
import net.consensys.shomei.trielog.ZkTrieLogFactory;
import net.consensys.shomei.trielog.ZkTrieLogService;

import java.util.HashMap;
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
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.AccountValue;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.plugin.services.trielogs.TrieLog;
import org.hyperledger.besu.plugin.services.trielogs.TrieLogProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(VertxExtension.class)
@ExtendWith(MockitoExtension.class)
public class GetShomeiTrieLogMetadataTests {

  @Mock TrieLogProvider mockProvider;
  ShomeiContext ctx = TestShomeiContext.create();
  ZkTrieLogService trieLogService = spy(new ZkTrieLogService(ctx));

  MockJsonRpcHttpVerticle verticle;
  WebClient client;

  @BeforeEach
  public void setup(Vertx vertx, VertxTestContext testContext) {
    ctx.setZkTrieLogService(trieLogService).setZkTrieLogFactory(new ZkTrieLogFactory(ctx));

    verticle = new MockJsonRpcHttpVerticle(List.of(new ShomeiGetTrieLogMetadata(ctx)));
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
  public void testGetTrieLogMetadataIsPresent(VertxTestContext testContext) {
    // Create a mock layer with some data
    Address addr1 = Address.fromHexString("0x1000000000000000000000000000000000000000");
    Address addr2 = Address.fromHexString("0x2000000000000000000000000000000000000000");

    Map<Address, TrieLog.LogTuple<AccountValue>> accounts = new HashMap<>();
    accounts.put(
        addr1,
        new TrieLogValue<>(
            new ZkAccountValue(0L, Wei.ZERO, Hash.EMPTY, Hash.EMPTY),
            new ZkAccountValue(1L, Wei.ONE, Hash.EMPTY, Hash.EMPTY),
            false));

    Map<Address, TrieLog.LogTuple<Bytes>> code = new HashMap<>();

    Map<Address, Map<StorageSlotKey, TrieLog.LogTuple<UInt256>>> storage = new HashMap<>();
    Map<StorageSlotKey, TrieLog.LogTuple<UInt256>> addr1Storage = new HashMap<>();
    addr1Storage.put(
        new StorageSlotKey(Hash.ZERO, Optional.of(UInt256.ZERO)),
        new TrieLogValue<>(UInt256.ZERO, UInt256.ONE, false));
    addr1Storage.put(
        new StorageSlotKey(Hash.ZERO, Optional.of(UInt256.ONE)),
        new TrieLogValue<>(UInt256.ZERO, UInt256.fromHexString("0x42"), false));
    storage.put(addr1, addr1Storage);

    Map<StorageSlotKey, TrieLog.LogTuple<UInt256>> addr2Storage = new HashMap<>();
    addr2Storage.put(
        new StorageSlotKey(Hash.ZERO, Optional.of(UInt256.valueOf(5))),
        new TrieLogValue<>(UInt256.ZERO, UInt256.valueOf(100), false));
    storage.put(addr2, addr2Storage);

    PluginTrieLogLayer mockLayer =
        new PluginTrieLogLayer(
            Hash.ZERO, Optional.of(123L), accounts, code, storage, true, Optional.of(24));

    // mock single log response
    when(mockProvider.getTrieLogLayer(123)).thenReturn(Optional.of(mockLayer));

    JsonObject requestJson =
        new JsonObject()
            .put("jsonrpc", "2.0")
            .put("id", 1)
            .put("method", "shomei_getTrieLogMetadata")
            .put("params", List.of("123"));

    assertRequest(
        requestJson,
        testContext,
        response -> {
          // Add assertions for the expected result
          var res = response.getJsonObject("result");
          assertThat(res).isNotNull();
          assertThat(res.getString("blockHash"))
              .isEqualTo("0x0000000000000000000000000000000000000000000000000000000000000000");
          assertThat(res.getLong("blockNumber")).isEqualTo(123L);
          assertThat(res.getJsonArray("zkTraceComparisonFeatures"))
              .containsExactlyInAnyOrder("DECORATE_FROM_HUB", "FILTER_FROM_HUB");
          assertThat(res.getInteger("accountChangesCount")).isEqualTo(1);
          assertThat(res.getInteger("codeChangesCount")).isEqualTo(0);
          assertThat(res.getInteger("storageChangesCount")).isEqualTo(2);
        });
  }

  @Test
  public void testGetTrieLogMetadataWithoutZkTraceFeature(VertxTestContext testContext) {
    // Create a mock layer without zkTraceComparisonFeature
    PluginTrieLogLayer mockLayer =
        new PluginTrieLogLayer(
            Hash.fromHexString(
                "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"),
            Optional.of(456L),
            Map.of(),
            Map.of(),
            Map.of(),
            true,
            Optional.empty());

    when(mockProvider.getTrieLogLayer(456)).thenReturn(Optional.of(mockLayer));

    JsonObject requestJson =
        new JsonObject()
            .put("jsonrpc", "2.0")
            .put("id", 1)
            .put("method", "shomei_getTrieLogMetadata")
            .put("params", List.of("456"));

    assertRequest(
        requestJson,
        testContext,
        response -> {
          var res = response.getJsonObject("result");
          assertThat(res).isNotNull();
          assertThat(res.getString("blockHash"))
              .isEqualTo("0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef");
          assertThat(res.getLong("blockNumber")).isEqualTo(456L);
          assertThat(res.getJsonArray("zkTraceComparisonFeatures")).isNull();
          assertThat(res.getInteger("accountChangesCount")).isEqualTo(0);
          assertThat(res.getInteger("codeChangesCount")).isEqualTo(0);
          assertThat(res.getInteger("storageChangesCount")).isEqualTo(0);
        });
  }

  @Test
  public void testGetTrieLogMetadataIsAbsent(VertxTestContext testContext) {
    // No mock setup, so getTrieLogLayer will return Optional.empty()

    JsonObject requestJson =
        new JsonObject()
            .put("jsonrpc", "2.0")
            .put("id", 1)
            .put("method", "shomei_getTrieLogMetadata")
            .put("params", List.of("999"));

    assertRequest(
        requestJson,
        testContext,
        response -> {
          // When trielog is absent, result should be null
          var res = response.getValue("result");
          assertThat(res).isNull();
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
