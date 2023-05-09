package net.consensys.shomei.trielog;

import org.hyperledger.besu.plugin.services.TrieLogService;
import org.hyperledger.besu.plugin.services.trielogs.TrieLogEvent;
import org.hyperledger.besu.plugin.services.trielogs.TrieLogFactory;
import org.hyperledger.besu.plugin.services.trielogs.TrieLogProvider;

import java.util.List;

public class ZkTrieLogService  implements TrieLogService {

  // TODO: configure:
  private final ZkTrieLogObserver observer = new ZkTrieLogObserver("127.0.0.1", 8888);
  private final ZkTrieLogFactory factory = new ZkTrieLogFactory();
  private TrieLogProvider trieLogProvider = null;

  @Override
  public List<TrieLogEvent.TrieLogObserver> getObservers() {
    return List.of(observer);
  }

  @Override
  public TrieLogFactory getTrieLogFactory() {
    return factory;
  }

  @Override
  public void configureTrieLogProvider(final TrieLogProvider trieLogProvider) {
    this.trieLogProvider = trieLogProvider;
  }

  @Override
  public TrieLogProvider getTrieLogProvider() {
    return trieLogProvider;
  }
}
