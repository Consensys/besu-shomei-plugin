package net.consensys.shomei.blocktracing;

import net.consensys.shomei.cli.ShomeiCliOptions;
import org.hyperledger.besu.plugin.BesuPlugin;
import org.hyperledger.besu.plugin.ServiceManager;
import org.hyperledger.besu.plugin.services.BlockImportTracerProvider;
import org.hyperledger.besu.plugin.services.PicoCLIOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class ZkBlockImportTracerPlugin implements BesuPlugin {
  private static final Logger LOG = LoggerFactory.getLogger(ZkBlockImportTracerPlugin.class);
  private static final String NAME = "shomei";

  ShomeiCliOptions options = ShomeiCliOptions.create();

  @Override
  public void register(final ServiceManager serviceManager) {
    // before we register ourselves, check whether we are enabled by CLI options
    if (options.enableTraceFiltering) {
      LOG.info("Registering ZkBlockImportTracer plugin");

      LOG.debug("Adding command line params");
      final Optional<PicoCLIOptions> cmdlineOptions = serviceManager.getService(PicoCLIOptions.class);

      if (cmdlineOptions.isEmpty()) {
        throw new IllegalStateException(
            "Expecting a PicoCLI options to register CLI options with, but none found.");
      }

      cmdlineOptions.get().addPicoCLIOptions(NAME, options);
      serviceManager.addService(BlockImportTracerProvider.class, ZkBlockImportTracerProvider.INSTANCE);

    }
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
