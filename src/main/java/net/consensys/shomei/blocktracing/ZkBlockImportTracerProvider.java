package net.consensys.shomei.blocktracing;

import net.consensys.linea.plugins.config.LineaL1L2BridgeSharedConfiguration;
import net.consensys.linea.zktracer.ZkTracer;
import net.consensys.shomei.trielog.ZkTrieLogFactory;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.services.BlockImportTracerProvider;
import org.hyperledger.besu.plugin.services.tracer.BlockAwareOperationTracer;
import org.hyperledger.besu.plugin.services.trielogs.TrieLogAccumulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 *   naive tracking implementation of BlockImportTracerProvider.
 *   Presumes currentTracer will be consumed during trielog writing,
 *   prior to fetching another block import tracer.
 */
public class ZkBlockImportTracerProvider implements BlockImportTracerProvider {
  public static final ZkBlockImportTracerProvider INSTANCE = new ZkBlockImportTracerProvider();
  private static final Logger LOG = LoggerFactory.getLogger(ZkBlockImportTracerProvider.class);

  private final AtomicReference<HeaderTracerTuple> currentTracer = new AtomicReference<>();

  /**
   * package private for testing.
   */
  ZkBlockImportTracerProvider() {}

  @Override
  public BlockAwareOperationTracer getBlockImportTracer(
      final BlockHeader processableBlockHeader) {
    // TODO: if we are just tracking storage, do we need an explicit L1L2Bridge config or chain id?
    //       empty bridge and linea mainnet chain id for now
    ZkTracer zkTracer = new ZkTracer(
        LineaL1L2BridgeSharedConfiguration.EMPTY, BigInteger.valueOf(59144L));

    zkTracer.traceStartConflation(1L);
    zkTracer.traceStartBlock(processableBlockHeader, processableBlockHeader.getCoinbase());
    currentTracer.set(new HeaderTracerTuple(processableBlockHeader, zkTracer));

    return zkTracer;
  }

  public Optional<HeaderTracerTuple> getCurrentTracerTuple() {
    return Optional.ofNullable(currentTracer.get());
  }

  public void filterWithTrace(final BlockHeader blockHeader, final TrieLogAccumulator accumulator) {
      var current = getCurrentTracerTuple();
      if (!current
          .filter(t -> t.header().getBlockHash().equals(blockHeader.getBlockHash()))
          .isPresent()) {
        LOG.warn("Trace not found while attempting to filter block {}.  current trace block {}",
            headerLogString(blockHeader),
            current.map(t-> t.header).map(this::headerLogString).orElse("empty")
        );


        // use tracer state to 'filter' besu accumulator:
        var state = current.get().zkTracer.getHub().state;
        var accountsToUpdate = accumulator.getAccountsToUpdate();
        var storageToUpdate = accumulator.getStorageToUpdate();
        var codeToUpdate = accumulator.getCodeToUpdate();

        //todo : figure out how state stores pre and post images

      }


  }
  public String headerLogString(BlockHeader header) {
    return header.getNumber() + " (" + header.getBlockHash() + ")";
  }

  public record HeaderTracerTuple(BlockHeader header, ZkTracer zkTracer){}
}
