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

import net.consensys.shomei.context.ShomeiContext;
import net.consensys.shomei.context.ShomeiContext.ShomeiContextImpl;
import net.consensys.shomei.rpc.methods.ShomeiGetTrieLog;
import net.consensys.shomei.rpc.methods.ShomeiGetTrieLogsByRange;

import java.util.List;

import com.google.auto.service.AutoService;
import org.hyperledger.besu.plugin.BesuPlugin;
import org.hyperledger.besu.plugin.ServiceManager;
import org.hyperledger.besu.plugin.services.RpcEndpointService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@AutoService(BesuPlugin.class)
public class BesuShomeiRpcPlugin implements BesuPlugin {
  private static final Logger LOG = LoggerFactory.getLogger(BesuShomeiRpcPlugin.class);
  private final ShomeiContext ctx = ShomeiContextImpl.getOrCreate();

  @Override
  public void register(final ServiceManager serviceManager) {
    LOG.debug("Registering RPC plugins");
    var methods = List.of(new ShomeiGetTrieLogsByRange(ctx), new ShomeiGetTrieLog(ctx));
    serviceManager
        .getService(RpcEndpointService.class)
        .ifPresent(
            rpcEndpointService ->
                methods.stream()
                    .forEach(
                        method -> {
                          LOG.info(
                              "Registering RPC plugin endpoint {}_{}",
                              method.getNamespace(),
                              method.getName());

                          rpcEndpointService.registerRPCEndpoint(
                              method.getNamespace(), method.getName(), method::execute);
                        }));
  }

  @Override
  public void start() {
    // no-op
  }

  @Override
  public void stop() {
    // no-op
  }
}
