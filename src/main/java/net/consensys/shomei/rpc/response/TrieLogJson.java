package net.consensys.shomei.rpc.response;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.plugin.services.trielogs.TrieLogProvider;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import net.consensys.shomei.trielog.ZkTrieLogFactory;
import net.consensys.shomei.util.HashSerializer;
import org.apache.tuweni.bytes.Bytes;

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
