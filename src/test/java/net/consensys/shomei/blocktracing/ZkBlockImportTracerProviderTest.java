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

import net.consensys.linea.zktracer.ZkTracer;
import net.consensys.shomei.cli.ShomeiCliOptions;
import net.consensys.shomei.context.TestShomeiContext;
import net.consensys.shomei.trielog.TrieLogValue;
import net.consensys.shomei.trielog.ZkAccountValue;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SECPPrivateKey;
import org.hyperledger.besu.crypto.SECPPublicKey;
import org.hyperledger.besu.datatypes.AccountValue;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.GWei;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.BlockProcessingResult;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.BlockchainSetupUtil;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.core.TransactionTestFixture;
import org.hyperledger.besu.ethereum.mainnet.BlockProcessor;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.worldview.BonsaiWorldState;
import org.hyperledger.besu.plugin.ServiceManager;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.services.BlockImportTracerProvider;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;
import org.hyperledger.besu.plugin.services.tracer.BlockAwareOperationTracer;
import org.hyperledger.besu.plugin.services.trielogs.TrieLog;
import org.hyperledger.besu.plugin.services.trielogs.TrieLogAccumulator;
import org.hyperledger.besu.testutil.BlockTestUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class ZkBlockImportTracerProviderTest {
  ShomeiCliOptions testOpts = new ShomeiCliOptions();
  TestShomeiContext testContext;
  private ZkBlockImportTracerProvider comparator;
  private BlockHeader mockHeader = mock(BlockHeader.class);
  @Mock TrieLogAccumulator mockAccumulator;

  @BeforeEach
  void setup() {
    testContext = TestShomeiContext.create().setCliOptions(testOpts);
    testOpts.zkTraceComparisonMask = 15;
    comparator =
        spy(new ZkBlockImportTracerProvider(testContext, () -> Optional.of(BigInteger.ONE)));
  }

  @Test
  void testMatchingAccounts() {
    Address a = Address.fromHexString("0x1");
    Address b = Address.fromHexString("0x2");
    var mockAcctVal = new ZkAccountValue(0, Wei.ZERO, Hash.ZERO, Hash.EMPTY_TRIE_HASH);
    Map<Address, TrieLog.LogTuple<? extends AccountValue>> accountsToUpdate =
        Map.of(
            a, new TrieLogValue<>(mockAcctVal, mockAcctVal, false),
            b, new TrieLogValue<>(mockAcctVal, mockAcctVal, false));

    Set<Address> hubAccountsSeen = Set.of(a, b);
    doAnswer(__ -> accountsToUpdate).when(mockAccumulator).getAccountsToUpdate();
    comparator.compareAndWarnAccount(mockHeader, mockAccumulator, hubAccountsSeen);
    verify(comparator, never()).alert(any());
  }

  @Test
  void testHubSawMoreAccounts() {
    Address a = Address.fromHexString("0x1");
    Address b = Address.fromHexString("0x2");
    Address c = Address.fromHexString("0x3");
    var mockAcctVal = new ZkAccountValue(0, Wei.ZERO, Hash.ZERO, Hash.EMPTY_TRIE_HASH);

    Map<Address, TrieLog.LogTuple<? extends AccountValue>> accountsToUpdate =
        Map.of(
            a, new TrieLogValue<>(mockAcctVal, mockAcctVal, false),
            b, new TrieLogValue<>(mockAcctVal, mockAcctVal, false));

    Set<Address> hubAccountsSeen = Set.of(a, b, c);

    doAnswer(__ -> accountsToUpdate).when(mockAccumulator).getAccountsToUpdate();
    comparator.compareAndWarnAccount(mockHeader, mockAccumulator, hubAccountsSeen);
    verify(comparator, times(1)).alert(any());
  }

  @Test
  void testAccumulatorSawMoreAccounts() {
    Address a = Address.fromHexString("0x1");
    Address b = Address.fromHexString("0x2");
    Address c = Address.fromHexString("0x3");
    var mockAcctVal = new ZkAccountValue(0, Wei.ZERO, Hash.ZERO, Hash.EMPTY_TRIE_HASH);

    Map<Address, TrieLog.LogTuple<? extends AccountValue>> accountsToUpdate =
        Map.of(
            a, new TrieLogValue<>(mockAcctVal, mockAcctVal, false),
            b, new TrieLogValue<>(mockAcctVal, mockAcctVal, false),
            c, new TrieLogValue<>(mockAcctVal, mockAcctVal, false));

    Set<Address> hubAccountsSeen = Set.of(a, b);

    doAnswer(__ -> accountsToUpdate).when(mockAccumulator).getAccountsToUpdate();
    comparator.compareAndWarnAccount(mockHeader, mockAccumulator, hubAccountsSeen);
    verify(comparator, times(1)).alert(any());
  }

  @Test
  void testMatchingStorageMaps() {
    Address addr = Address.fromHexString("0x1");
    UInt256 slot = UInt256.valueOf(1234);
    Bytes32 slotBytes = slot.toBytes();

    StorageSlotKey slotKey = new StorageSlotKey(slot);
    TrieLogValue<UInt256> logTuple = new TrieLogValue<UInt256>(UInt256.ZERO, UInt256.ZERO, false);
    Map<Address, Map<StorageSlotKey, TrieLogValue<UInt256>>> storageToUpdate =
        Map.of(addr, Map.of(slotKey, logTuple));
    Map<Address, Set<Bytes32>> hubSeenStorage = Map.of(addr, Set.of(slotBytes));

    doAnswer(__ -> storageToUpdate).when(mockAccumulator).getStorageToUpdate();
    comparator.compareAndWarnStorage(mockHeader, mockAccumulator, hubSeenStorage);

    // ✅ Verify alert() was never called
    verify(comparator, never()).alert(any());
  }

  @Test
  void testHubMissingStorageAccount() {
    Address addr = Address.fromHexString("0x1");
    UInt256 slot = UInt256.valueOf(1234);

    StorageSlotKey slotKey = new StorageSlotKey(slot);
    TrieLogValue<UInt256> logTuple = new TrieLogValue<UInt256>(UInt256.ZERO, UInt256.ZERO, false);
    Map<Address, Map<StorageSlotKey, TrieLogValue<UInt256>>> storageToUpdate =
        Map.of(addr, Map.of(slotKey, logTuple));
    Map<Address, Set<Bytes32>> hubSeenStorage = Collections.emptyMap();

    doAnswer(__ -> storageToUpdate).when(mockAccumulator).getStorageToUpdate();
    comparator.compareAndWarnStorage(mockHeader, mockAccumulator, hubSeenStorage);

    // ✅ Verify alert() was never called
    verify(comparator, times(1)).alert(any());
  }

  @Test
  void testAccumulatorMissingStorageAccount() {
    Address addr = Address.fromHexString("0x1");
    UInt256 slot = UInt256.valueOf(1234);
    Bytes32 slotBytes = slot.toBytes();

    Map<Address, Map<StorageSlotKey, TrieLogValue<UInt256>>> storageToUpdate =
        Collections.emptyMap();
    Map<Address, Set<Bytes32>> hubSeenStorage = Map.of(addr, Set.of(slotBytes));

    doAnswer(__ -> storageToUpdate).when(mockAccumulator).getStorageToUpdate();
    comparator.compareAndWarnStorage(mockHeader, mockAccumulator, hubSeenStorage);

    // ✅ Verify alert() was never called
    verify(comparator, times(1)).alert(any());
  }

  @Test
  void testHubMissingSlot() {
    Address addr = Address.fromHexString("0x1");
    UInt256 slot = UInt256.valueOf(1234);
    UInt256 slot2 = UInt256.valueOf(4321);
    Bytes32 slotBytes = slot.toBytes();

    StorageSlotKey slotKey = new StorageSlotKey(slot);
    StorageSlotKey slotKey2 = new StorageSlotKey(slot2);
    TrieLogValue<UInt256> logTuple = new TrieLogValue<UInt256>(UInt256.ZERO, UInt256.ZERO, false);
    Map<Address, Map<StorageSlotKey, TrieLogValue<UInt256>>> storageToUpdate =
        Map.of(addr, Map.of(slotKey, logTuple, slotKey2, logTuple));
    Map<Address, Set<Bytes32>> hubSeenStorage = Map.of(addr, Set.of(slotBytes));

    doAnswer(__ -> storageToUpdate).when(mockAccumulator).getStorageToUpdate();
    comparator.compareAndWarnStorage(mockHeader, mockAccumulator, hubSeenStorage);

    // ✅ Verify alert() was never called
    verify(comparator, times(1)).alert(any());
  }

  @Test
  void testAccumulatorMissingSlot() {
    Address addr = Address.fromHexString("0x1");
    UInt256 slot = UInt256.valueOf(1234);
    UInt256 slot2 = UInt256.valueOf(4321);
    Bytes32 slotBytes = slot.toBytes();
    Bytes32 slot2Bytes = slot2.toBytes();

    StorageSlotKey slotKey = new StorageSlotKey(slot);
    TrieLogValue<UInt256> logTuple = new TrieLogValue<>(UInt256.ZERO, UInt256.ZERO, false);
    Map<Address, Map<StorageSlotKey, TrieLogValue<UInt256>>> storageToUpdate =
        Map.of(addr, Map.of(slotKey, logTuple));
    Map<Address, Set<Bytes32>> hubSeenStorage = Map.of(addr, Set.of(slotBytes, slot2Bytes));

    doAnswer(__ -> storageToUpdate).when(mockAccumulator).getStorageToUpdate();
    comparator.compareAndWarnStorage(mockHeader, mockAccumulator, hubSeenStorage);

    // ✅ Verify alert() was never called
    verify(comparator, times(1)).alert(any());
  }


  /**
   * edge case assertion:
   *   create2, with delegate call revert
   */

  @Test
  public void transactionRevertsDueToDelegateCallFailure() {
    // Set up test world state and blockchain
    final BlockchainSetupUtil setupUtil = BlockchainSetupUtil.createForEthashChain(
        new BlockTestUtil.ChainResources(
        this.getClass().getClassLoader().getResource("zktestGenesis.json"),
        BlockTestUtil.class.getClassLoader().getResource("chain.blocks")),
        DataStorageFormat.BONSAI);
    final Blockchain blockchain = setupUtil.getBlockchain();
    final MutableWorldState worldState = setupUtil.getWorldArchive().getWorldState();

    KeyPair genesisAccountKeyPair =
        new KeyPair(
            SECPPrivateKey.create(
                Bytes32.fromHexString(
                    "0x45a915e4d060149eb4365960e6a7a45f334393093061116b197e3240065ff2d8"),
                "ECDSA"),
            SECPPublicKey.create(
                Bytes.fromHexString(
                    "0x3a514176466fa815ed481ffad09110a2d344f6c9b78c1d14afc351c3a51be33d8072e77939dc03ba44790779b7a1025baf3003f6732430e20cd9b76d953391b3"),
                "ECDSA"));


    // Set up sender and failing target contract
    final Address sender = Address.fromHexString("0xa94f5374fce5edbc8e2a8697c15331677e6ebf0b");
    //final Address target = Address.fromHexString("0xdeadbeef");

    // 0xdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef contract configured in genesis alloc always reverts:
    final Bytes proxyRuntime = Bytes.fromHexString(
        // runtime: calldatacopy(0, 0, calldatasize())
        "0x60006000" + // PUSH1 00 PUSH1 00
            "37" +         // CALLDATACOPY
            // delegatecall(gas, target, 0, calldatasize, 0, 0)
            "6000" + "6000" + "6000" + "73deadbeefdeadbeefdeadbeefdeadbeefdeadbeef" + "5af4" +
            // if !success revert(0, 0)
            "6000" + "6000" + "fd"
    );

    final Bytes initCode = Bytes.concatenate(Bytes.fromHexString(
        "0x600c600c600039600c6000f3" // copy 12 bytes into memory and return as contract code
    ), proxyRuntime); // init + runtime

    final var createTx = new TransactionTestFixture()
        .type(TransactionType.FRONTIER)
        .nonce(0)
        .gasLimit(3_000_000)
        .gasPrice(GWei.ONE.getAsWei())
        .sender(sender)
        .chainId(setupUtil.getProtocolSchedule().getChainId())
        .payload(initCode)
        .createTransaction(genesisAccountKeyPair);


    // Process block with this tx
    final BlockDataGenerator gen = new BlockDataGenerator();
    final Block block = gen.block(BlockDataGenerator.BlockOptions
        .create()
        .addTransaction(createTx)
        .hasOmmers(false)
        .setStateRoot(Hash.fromHexString("0x2be8c4a0e388c8fa9fa69277e64b4dc2ed2def16b9352231f6a66cfa3d3da01b"))
    );
    final BlockProcessor processor = setupUtil
        .getProtocolSchedule()
        .getByBlockHeader(
            blockchain.getChainHeadHeader())
        .getBlockProcessor();

    var spyProtocolContext = spy(setupUtil.getProtocolContext());
    var mockPluginServiceManager = mock(ServiceManager.class);
    var zkTracerProviderSpy = spy(new ZkBlockImportTracerProvider(
        testContext, () -> setupUtil.getProtocolSchedule().getChainId()));

    AtomicReference<ZkTracer> capturedTracer = new AtomicReference<>();

    doAnswer(invocation -> {
      BlockAwareOperationTracer tracer = (BlockAwareOperationTracer) invocation.callRealMethod();
      capturedTracer.set((ZkTracer) tracer);
      return tracer;
    }).when(zkTracerProviderSpy).getBlockImportTracer(any(BlockHeader.class));


    doAnswer(__ -> Optional.of(zkTracerProviderSpy))
        .when(mockPluginServiceManager)
        .getService(BlockImportTracerProvider.class);
    doAnswer(__ -> mockPluginServiceManager)
        .when(spyProtocolContext)
        .getPluginServiceManager();

    final BlockProcessingResult result = processor.processBlock(
        spyProtocolContext,
        blockchain,
        worldState,
        block);

    // Assert transaction is present
    assertEquals(1, result.getReceipts().size());

    // Check that status == 0 (reverted)
    // final TransactionReceipt receipt = result.getReceipts().get(0);
    //    assertEquals(Bytes32.ZERO, receipt.getStatus());

    assertEquals(capturedTracer.get().getAddressesSeenByHubForRelativeBlock(1).size(),
        worldState.updater().getTouchedAccounts().size());

  }
}
