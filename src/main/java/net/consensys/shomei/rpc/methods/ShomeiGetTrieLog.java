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
package net.consensys.shomei.rpc.methods;

import net.consensys.shomei.trielog.ZkTrieLogFactory;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.plugin.services.TrieLogService;
import org.hyperledger.besu.plugin.services.rpc.PluginRpcRequest;

public class ShomeiGetTrieLog implements PluginRpcMethod {
  private final TrieLogService trieLogService;

  public ShomeiGetTrieLog(final TrieLogService trieLogService) {
    this.trieLogService = trieLogService;
  }

  @Override
  public String getNamespace() {
    return "shomei";
  }

  @Override
  public String getName() {
    return "getTrieLog";
  }

  @Override
  public Object execute(PluginRpcRequest rpcRequest) {
    // todo separate param parsing into method
    var params = rpcRequest.getParams();
    Long blockNumber = Long.parseLong(params[0].toString());

    return trieLogService
        .getTrieLogProvider()
        .getTrieLogLayer(blockNumber)
        .map(t -> Bytes.wrap(ZkTrieLogFactory.INSTANCE.serialize(t)).toHexString())
        .orElse("");
  }
}
