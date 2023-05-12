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
package net.consensys.shomei.rpc.response;

import net.consensys.shomei.trielog.ZkTrieLogFactory;
import net.consensys.shomei.util.HashSerializer;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.plugin.services.trielogs.TrieLogProvider;

public class TrieLogJson {

  @JsonProperty("blockHash")
  @JsonSerialize(using = HashSerializer.class)
  private Hash blockHash;

  @JsonProperty("blockNumber")
  private long blockNumber;

  @JsonProperty("trieLog")
  private String trieLog;

  public TrieLogJson(final TrieLogProvider.TrieLogRangeTuple tuple) {
    this.blockHash = tuple.blockHash();
    this.blockNumber = tuple.blockNumber();
    this.trieLog = Bytes.wrap(ZkTrieLogFactory.INSTANCE.serialize(tuple.trieLog())).toHexString();
  }

  public TrieLogJson(Hash blockHash, long blockNumber, String trieLog) {
    this.blockHash = blockHash;
    this.blockNumber = blockNumber;
    this.trieLog = trieLog;
  }

  public Hash getBlockHash() {
    return blockHash;
  }

  public long getBlockNumber() {
    return blockNumber;
  }

  public void setBlockNumber(long blockNumber) {
    this.blockNumber = blockNumber;
  }

  public String getTrieLog() {
    return trieLog;
  }

  public void setTrieLog(String trieLog) {
    this.trieLog = trieLog;
  }
}
