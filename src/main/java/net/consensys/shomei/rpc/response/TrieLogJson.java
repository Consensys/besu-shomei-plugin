package net.consensys.shomei.rpc.response;

import org.hyperledger.besu.plugin.services.trielogs.TrieLogProvider;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import net.consensys.shomei.trielog.ZkTrieLogFactory;
import org.apache.tuweni.bytes.Bytes;

public class TrieLogJson {

  @JsonProperty("blockNumber")
  private long blockNumber;

  @JsonProperty("trieLog")
  private String trieLog;

  public TrieLogJson(final TrieLogProvider.TrieLogRangePair pair) {
    this.blockNumber = pair.blockNumber();
    this.trieLog = Bytes.wrap(ZkTrieLogFactory.INSTANCE.serialize(pair.trieLog())).toHexString();
  }

  public TrieLogJson(long blockNumber, String trieLog) {
    this.blockNumber = blockNumber;
    this.trieLog = trieLog;
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
