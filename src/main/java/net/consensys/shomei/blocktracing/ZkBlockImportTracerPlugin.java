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
package net.consensys.shomei.blocktracing;

import net.consensys.shomei.cli.ShomeiCliOptions;

import java.util.Optional;

import org.hyperledger.besu.plugin.BesuPlugin;
import org.hyperledger.besu.plugin.ServiceManager;
import org.hyperledger.besu.plugin.services.BlockImportTracerProvider;
import org.hyperledger.besu.plugin.services.PicoCLIOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZkBlockImportTracerPlugin implements BesuPlugin {
  private static final Logger LOG = LoggerFactory.getLogger(ZkBlockImportTracerPlugin.class);
  private static final String NAME = "shomei";

  ShomeiCliOptions options = ShomeiCliOptions.create();

  @Override
  public void register(final ServiceManager serviceManager) {
    // before we register ourselves, check whether we are enabled by CLI options
    if (options.enableZkTracer) {
      LOG.info("Registering ZkBlockImportTracer plugin");

      LOG.debug("Adding command line params");
      final Optional<PicoCLIOptions> cmdlineOptions =
          serviceManager.getService(PicoCLIOptions.class);

      if (cmdlineOptions.isEmpty()) {
        throw new IllegalStateException(
            "Expecting a PicoCLI options to register CLI options with, but none found.");
      }

      cmdlineOptions.get().addPicoCLIOptions(NAME, options);
      serviceManager.addService(
          BlockImportTracerProvider.class, ZkBlockImportTracerProvider.INSTANCE);
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
