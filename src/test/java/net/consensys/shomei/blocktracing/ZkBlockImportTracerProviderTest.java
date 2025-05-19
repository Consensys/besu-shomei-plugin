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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import net.consensys.linea.zktracer.ZkTracer;
import net.consensys.shomei.cli.ShomeiCliOptions;
import net.consensys.shomei.context.TestShomeiContext;
import net.consensys.shomei.trielog.TrieLogValue;
import net.consensys.shomei.trielog.ZkAccountValue;
import net.consensys.shomei.trielog.ZkTrieLogFactory;
import net.consensys.shomei.trielog.ZkTrieLogService;

import java.math.BigInteger;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.bouncycastle.math.ec.custom.sec.SecP256K1FieldElement;
import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.datatypes.AccountValue;
import org.hyperledger.besu.datatypes.Address;
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
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.mainnet.BlockProcessor;
import org.hyperledger.besu.plugin.ServiceManager;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.services.BlockImportTracerProvider;
import org.hyperledger.besu.plugin.services.TrieLogService;
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

  @Test
  public void assertTrieLogContainsHubStateOnNewContractSload() {
    testContext.getCliOptions().zkTraceComparisonMask = 15;
    var mockPluginServiceManager = mock(ServiceManager.class);

    // setup mock plugin service manager zktrielogfactory:
    var zkTrieLogService = mock(ZkTrieLogService.class);
    var zkTrieLogFactoryImpl = spy(new ZkTrieLogFactory(testContext));
    doAnswer(__ -> Optional.of(zkTrieLogService))
        .when(mockPluginServiceManager)
        .getService(TrieLogService.class);
    doAnswer(__ -> Optional.of(zkTrieLogFactoryImpl)).when(zkTrieLogService).getTrieLogFactory();
    AtomicReference<TrieLog> capturedTrieLog = new AtomicReference<>();
    doAnswer(
            invocation -> {
              var trielog = (TrieLog) invocation.callRealMethod();
              capturedTrieLog.set(trielog);
              return trielog;
            })
        .when(zkTrieLogFactoryImpl)
        .create(any(TrieLogAccumulator.class), any(BlockHeader.class));

    var zkTracerProviderSpy =
        spy(
            new ZkBlockImportTracerProvider(
                testContext, () -> Optional.of(BigInteger.valueOf(59144L))));
    testContext.setBlockImportTraceProvider(zkTracerProviderSpy);

    // Set up test world state and blockchain
    final BlockchainSetupUtil setupUtil =
        BlockchainSetupUtil.createForEthashChain(
            new BlockTestUtil.ChainResources(
                this.getClass().getClassLoader().getResource("zktestGenesis.json"),
                BlockTestUtil.class.getClassLoader().getResource("chain.blocks")),
            DataStorageFormat.BONSAI,
            mockPluginServiceManager);
    final Blockchain blockchain = setupUtil.getBlockchain();
    final MutableWorldState worldState = setupUtil.getWorldArchive().getWorldState();

    var spyProtocolContext = spy(setupUtil.getProtocolContext());

    // setup zktracerprovider plugin service
    AtomicReference<ZkTracer> capturedTracer = new AtomicReference<>();
    doAnswer(__ -> Optional.of(zkTracerProviderSpy))
        .when(mockPluginServiceManager)
        .getService(BlockImportTracerProvider.class);
    doAnswer(__ -> mockPluginServiceManager).when(spyProtocolContext).getPluginServiceManager();
    doAnswer(
            invocation -> {
              BlockAwareOperationTracer tracer =
                  (BlockAwareOperationTracer) invocation.callRealMethod();
              capturedTracer.set((ZkTracer) tracer);
              return tracer;
            })
        .when(zkTracerProviderSpy)
        .getBlockImportTracer(any(BlockHeader.class));

    var r =
        Bytes.fromHexString("0xde0db8ce81c8092823a2d91b3a3cc421bc6b384b562f567e67b26c5443ff28e9")
            .toUnsignedBigInteger();
    var s =
        Bytes.fromHexString("0x44457e69f445284d1f1ba99107b53c1d4b3d1e8c7283ff504341e5a92699c09d")
            .toUnsignedBigInteger();
    var sig = SECPSignature.create(r, s, (byte) 0, SecP256K1FieldElement.Q);

    var tx =
        new Transaction.Builder()
            .signature(sig)
            .type(TransactionType.EIP1559)
            .sender(Address.fromHexString("0x43370108f30ee5ed54a9565f37af3be8502903f5"))
            .to(Address.fromHexString("0x5ff137d4b0fdcd49dca30c7cf57e578a026d2789"))
            .nonce(14261)
            .value(Wei.ZERO)
            .payload(
                Bytes.fromHexString(
                    "0x1fad948c000000000000000000000000000000000000000000000000000000000000004000000000000000000000000043370108f30ee5ed54a9565f37af3be8502903f50000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000002000000000000000000000000051ce6a8e868fa0c928346ac7489a74432c44bc933db076b8929744925f16e35a14de0c7e122e512efe282e76000000000000000000000000000000000000000000000000000000000000000000000000000001600000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000007a120000000000000000000000000000000000000000000000000000000000006b08a000000000000000000000000000000000000000000000000000000000000b1580000000000000000000000000000000000000000000000000000000065fa53ff000000000000000000000000000000000000000000000000000000005cb5068300000000000000000000000000000000000000000000000000000000000002c000000000000000000000000000000000000000000000000000000000000002e000000000000000000000000000000000000000000000000000000000000000783fbe35a874284e41c955331a363c1ea085301a8dd8fd8f440000000000000000000000001708dc05842c8fbd5e2e60a0adbd1b2efab061640000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000084b61d27f60000000000000000000000000acf75e7c29bd27f1cd6ed47c8769c61bc6a68810000000000000000000000000000000000000000000000000006379da05b600000000000000000000000000000000000000000000000000000000000000000600000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004189f4d1fe2626f881082f93a336e51d6fad4faecfaffb843307c6c658bc4ae3ac737b6ece16b3c206b2d26efa8ca1ee6ac85e6a1e984456b7282404592e3d12e31c00000000000000000000000000000000000000000000000000000000000000"))
            .maxPriorityFeePerGas(Wei.wrap(Bytes.fromHexString("5cb50683")))
            .maxFeePerGas(Wei.wrap(Bytes.fromHexString("0x5cb5068b")))
            .gasLimit(1396683L)
            .chainId(BigInteger.valueOf(59144))
            .build();

    // Process block with this tx
    final BlockDataGenerator gen = new BlockDataGenerator();
    final Block block =
        gen.block(
            BlockDataGenerator.BlockOptions.create()
                .addTransaction(tx)
                .hasOmmers(false)
                // .setStateRoot(Hash.fromHexString("0xc70a52d661bf92297e17d79b0cb04c508f3767acecf6145171884a82ea5e8a9d"))
                .setStateRoot(
                    Hash.fromHexString(
                        "0x543543c7c19059bbc9c384ab7344bc281fe08938732480a1bc48d01e3ba8090b")));
    final BlockProcessor processor =
        setupUtil
            .getProtocolSchedule()
            .getByBlockHeader(blockchain.getChainHeadHeader())
            .getBlockProcessor();

    final BlockProcessingResult result =
        processor.processBlock(spyProtocolContext, blockchain, worldState, block);

    // Assert transaction is present
    assertEquals(1, result.getReceipts().size());

    // Check that status == 0 (reverted)
    final TransactionReceipt receipt = result.getReceipts().get(0);
    assertEquals(0, receipt.getStatus());

    var create2Address = Address.fromHexString("0x51ce6a8e868fa0c928346ac7489a74432c44bc93");
    var create2StorageSeen =
        capturedTracer.get().getStoragesSeenByHubForRelativeBlock(1).get(create2Address);
    assertNotNull(create2StorageSeen);

    var trieLog = capturedTrieLog.get();
    var create2TrielogSeen = trieLog.getStorageChanges().get(create2Address);
    assertNotNull(create2TrielogSeen);

    assertEquals(create2StorageSeen.size(), create2TrielogSeen.size());
    // known reverted storage slot key:
    final Bytes32 revertedSlotKey =
        Bytes32.fromHexString("0x322cf19c484104d3b1a9c2982ebae869ede3fa5f6c4703ca41b9a48c76ee0300");
    assertTrue(create2StorageSeen.contains(revertedSlotKey));
    var trieLogSlotVal =
        create2TrielogSeen.get(new StorageSlotKey(UInt256.fromBytes(revertedSlotKey)));
    assertNotNull(trieLogSlotVal);
  }
}
