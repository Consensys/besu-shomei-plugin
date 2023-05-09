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

import net.consensys.shomei.rpc.methods.PluginRpcMethod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

public class MockJsonRpcHttpVerticle extends AbstractVerticle {

  private final Map<String, PluginRpcMethod> rpcMethods;
  private HttpServer server;
  private int serverPort;

  public MockJsonRpcHttpVerticle(List<PluginRpcMethod> rpcMethodsList) {
    this.rpcMethods = new HashMap<>();
    for (PluginRpcMethod rpcMethod : rpcMethodsList) {
      String key = rpcMethod.getNamespace() + "_" + rpcMethod.getName();
      this.rpcMethods.put(key, rpcMethod);
    }
  }

  @Override
  public void start(Promise<Void> startPromise) {
    HttpServerOptions options = new HttpServerOptions().setPort(0);
    server = vertx.createHttpServer(options);
    Router router = Router.router(vertx);

    // Add the body handler here
    router.route().handler(BodyHandler.create());

    router.post("/").handler(jsonRpcHandler());
    server
        .requestHandler(router)
        .listen(
            result -> {
              if (result.succeeded()) {
                serverPort = server.actualPort();
                startPromise.complete();
              } else {
                startPromise.fail(result.cause());
              }
            });
  }

  private Handler<RoutingContext> jsonRpcHandler() {
    return ctx -> {
      JsonObject requestBody = ctx.getBodyAsJson();
      String method = requestBody.getString("method");
      PluginRpcMethod rpcMethod = rpcMethods.get(method);

      if (rpcMethod == null) {
        ctx.response()
            .setStatusCode(400)
            .putHeader("Content-Type", "application/json")
            .end("Method not found");
        return;
      }

      final Object[] rangeParams = {"1", "1"};
      JsonObject responseBody =
          new JsonObject()
              .put("id", requestBody.getInteger("id"))
              .put("jsonrpc", requestBody.getString("jsonrpc"))
              .put("result", rpcMethod.execute(() -> rangeParams));
      ctx.response().putHeader("Content-Type", "application/json").end(Json.encode(responseBody));
    };
  }

  public int getServerPort() {
    return serverPort;
  }
}
