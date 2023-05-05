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

import org.hyperledger.besu.plugin.BesuContext;
import org.hyperledger.besu.plugin.services.rpc.PluginRpcRequest;

public class GetShomeiTrieLogs {
  BesuContext context;

  public GetShomeiTrieLogs(BesuContext context) {
    this.context = context;
  }

  public String getNamespace() {
    return "shomei";
  }

  public String getName() {
    return "getTrieLogs";
  }

  public Object execute(PluginRpcRequest rpcRequest) {
    return "Hello World!";
  }
}
