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
package net.consensys.shomei.context;

import net.consensys.shomei.blocktracing.ZkBlockImportTracerProvider;
import net.consensys.shomei.cli.ShomeiCliOptions;
import net.consensys.shomei.trielog.ZkTrieLogFactory;
import net.consensys.shomei.trielog.ZkTrieLogService;

import com.google.common.annotations.VisibleForTesting;
import org.hyperledger.besu.plugin.services.TrieLogService;

/** Context class which allows for lazy instantiation of these services via plugin registry. */
public interface ShomeiContext {

  TrieLogService getTrieLogService();

  ShomeiContext setZkTrieLogService(ZkTrieLogService service);

  ZkTrieLogFactory getZkTrieLogFactory();

  ShomeiContext setZkTrieLogFactory(ZkTrieLogFactory factory);

  ZkBlockImportTracerProvider getBlockImportTraceProvider();

  ShomeiContext setBlockImportTraceProvider(ZkBlockImportTracerProvider provider);

  ShomeiCliOptions getCliOptions();

  class ShomeiContextImpl implements ShomeiContext {
    private final ShomeiCliOptions cliOptions = new ShomeiCliOptions();
    private ZkBlockImportTracerProvider blockImportTraceProvider = null;
    private ZkTrieLogFactory trieLogFactory = null;
    private ZkTrieLogService trieLogService = null;

    @VisibleForTesting
    ShomeiContextImpl() {}

    public static ShomeiContext getOrCreate() {
      return Holder.INSTANCE;
    }

    private static class Holder {
      private static final ShomeiContext INSTANCE = new ShomeiContextImpl();
    }

    @Override
    public TrieLogService getTrieLogService() {
      return trieLogService;
    }

    @Override
    public ShomeiContext setBlockImportTraceProvider(ZkBlockImportTracerProvider provider) {
      this.blockImportTraceProvider = provider;
      return this;
    }

    @Override
    public ShomeiContext setZkTrieLogFactory(ZkTrieLogFactory factory) {
      this.trieLogFactory = factory;
      return this;
    }

    @Override
    public ShomeiContext setZkTrieLogService(final ZkTrieLogService trieLogService) {
      this.trieLogService = trieLogService;
      return this;
    }

    @Override
    public ZkTrieLogFactory getZkTrieLogFactory() {
      return trieLogFactory;
    }

    @Override
    public ZkBlockImportTracerProvider getBlockImportTraceProvider() {
      return blockImportTraceProvider;
    }

    @Override
    public ShomeiCliOptions getCliOptions() {
      return cliOptions;
    }
  }
}
