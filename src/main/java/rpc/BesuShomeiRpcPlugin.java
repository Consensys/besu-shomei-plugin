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
package rpc;

import rpc.methods.GetShomeiTrieLogs;

import com.google.auto.service.AutoService;
import org.hyperledger.besu.plugin.BesuContext;
import org.hyperledger.besu.plugin.BesuPlugin;
import org.hyperledger.besu.plugin.services.RpcEndpointService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@AutoService(BesuPlugin.class)
public class BesuShomeiRpcPlugin implements BesuPlugin {
  private static final Logger LOG = LoggerFactory.getLogger(BesuShomeiRpcPlugin.class);

  @Override
  public void register(final BesuContext context) {
    GetShomeiTrieLogs method = new GetShomeiTrieLogs(context);
    LOG.info("Registering RPC plugin");
    context
        .getService(RpcEndpointService.class)
        .ifPresent(
            rpcEndpointService -> {
              LOG.info("Registering RPC plugin endpoints");
              rpcEndpointService.registerRPCEndpoint(
                  method.getNamespace(), method.getName(), method::execute);
            });
  }

  @Override
  public void start() {}

  @Override
  public void stop() {}
}
