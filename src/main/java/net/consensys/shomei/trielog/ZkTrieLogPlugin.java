package net.consensys.shomei.trielog;

import org.hyperledger.besu.plugin.BesuContext;
import org.hyperledger.besu.plugin.BesuPlugin;
import org.hyperledger.besu.plugin.services.TrieLogService;

import com.google.auto.service.AutoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@AutoService(BesuPlugin.class)
public class ZkTrieLogPlugin implements BesuPlugin {
  private static final Logger LOG = LoggerFactory.getLogger(ZkTrieLogPlugin.class);

  @Override
  public void register(final BesuContext besuContext) {
    LOG.info("Registering ZkTrieLog plugin");
    besuContext.addService(TrieLogService.class, new ZkTrieLogService());
  }

  @Override
  public void start() {
    //no-op
  }

  @Override
  public void stop() {
    //no-op
  }
}
