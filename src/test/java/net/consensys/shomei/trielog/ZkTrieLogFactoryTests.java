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
package net.consensys.shomei.trielog;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import net.consensys.shomei.blocktracing.ZkBlockImportTracerProvider;
import net.consensys.shomei.blocktracing.ZkBlockImportTracerProvider.HubSeenDiff;
import net.consensys.shomei.cli.ShomeiCliOptions;
import net.consensys.shomei.context.TestShomeiContext;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.AccountValue;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.BonsaiAccount;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.cache.CodeCache;
import org.hyperledger.besu.ethereum.trie.pathbased.common.PathBasedValue;
import org.hyperledger.besu.ethereum.trie.pathbased.common.worldview.accumulator.PathBasedWorldStateUpdateAccumulator;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.services.trielogs.TrieLog;
import org.hyperledger.besu.plugin.services.trielogs.TrieLog.LogTuple;
import org.hyperledger.besu.plugin.services.trielogs.TrieLogFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ZkTrieLogFactoryTests {

  final TestShomeiContext testCtx = TestShomeiContext.create();
  final Address accountFixture = Address.fromHexString("0xdeadbeef");
  final PluginTrieLogLayer trieLogFixture =
      new PluginTrieLogLayer(
          Hash.ZERO,
          Optional.of(1L),
          new HashMap<>(),
          new HashMap<>(),
          new HashMap<>(),
          true,
          Optional.empty());

  @BeforeEach
  public void setup() {
    // mock account addition
    trieLogFixture
        .accounts()
        .put(
            accountFixture,
            new TrieLogValue<>(
                null, new ZkAccountValue(0, Wei.fromEth(1), Hash.EMPTY, Hash.EMPTY), false));

    // mock code addition
    trieLogFixture
        .getCodeChanges()
        .put(Address.ZERO, new TrieLogValue<>(null, Bytes.fromHexString("0xfeeddeadbeef"), false));

    // mock storage addition
    trieLogFixture
        .getStorageChanges()
        .put(
            Address.ZERO,
            Map.of(new StorageSlotKey(UInt256.ZERO), new TrieLogValue<>(null, UInt256.ONE, false)));
  }

  @Test
  public void assertPluginTrieLogLayerApiBackwardsCompatibility() {
    assertDoesNotThrow(
        () -> {
          new PluginTrieLogLayer(
              Hash.ZERO, Optional.of(1L), new HashMap<>(), new HashMap<>(), new HashMap<>(), true);
        });
  }

  @Test
  public void testZkSlotKeyIsPresent() {
    TrieLogFactory factory = new ZkTrieLogFactory(testCtx);
    byte[] rlp = factory.serialize(trieLogFixture);

    TrieLog layer = factory.deserialize(rlp);
    assertThat(layer).isEqualTo(trieLogFixture);

    assertThat(
            layer.getStorageChanges().get(Address.ZERO).keySet().stream()
                .map(k -> k.getSlotKey())
                .filter(Optional::isPresent)
                .map(Optional::get)
                .anyMatch(key -> key.equals(UInt256.ZERO)))
        .isTrue();
  }

  @Test
  public void testZkSlotKeyZeroReadIsPresent() {
    TrieLogFactory factory = new ZkTrieLogFactory(testCtx);
    // add a read of an empty slot:
    trieLogFixture
        .getStorageChanges()
        .put(
            Address.ZERO,
            Map.of(new StorageSlotKey(UInt256.ONE), new TrieLogValue<>(null, null, false)));
    byte[] rlp = factory.serialize(trieLogFixture);

    TrieLog layer = factory.deserialize(rlp);
    assertThat(layer).isEqualTo(trieLogFixture);

    assertThat(
            layer.getStorageChanges().get(Address.ZERO).values().stream()
                .anyMatch(v -> v.getPrior() == null && v.getUpdated() == null))
        .isTrue();
  }

  @Test
  public void testZkAccountReadIsPresent() {
    // zkbesu test
    final TrieLogFactory factory = new ZkTrieLogFactory(testCtx);
    final Address readAccount = Address.fromHexString("0xfeedf00d");
    final ZkAccountValue read = new ZkAccountValue(0, Wei.fromEth(1), Hash.EMPTY, Hash.EMPTY);
    trieLogFixture.getAccountChanges().put(readAccount, new TrieLogValue<>(read, read, false));

    byte[] rlp = factory.serialize(trieLogFixture);

    TrieLog layer = factory.deserialize(rlp);
    assertThat(layer).isEqualTo(trieLogFixture);
    assertThat(layer.getAccountChanges().get(readAccount).getUpdated()).isEqualTo(read);
    assertThat(layer.getAccountChanges().get(readAccount).getPrior()).isEqualTo(read);
  }

  @Test
  public void assertHubSeenIsPresentInTrieLog() {
    // mock trace provider
    var mockTraceProvider = mock(ZkBlockImportTracerProvider.class);
    var mockAddress = Address.fromHexString("0xdeadbeef");
    var mockAddress2 = Address.fromHexString("0xc0ffee");
    var mockDiff =
        new HubSeenDiff(
            Set.of(mockAddress, mockAddress2),
            Map.of(mockAddress, Set.of(UInt256.ZERO, UInt256.ONE)));

    // mock test options to enable tracing
    ShomeiCliOptions testOpts = new ShomeiCliOptions();

    // everything except filtering
    testOpts.zkTraceComparisonMask = 15;

    // use same diff for found and missing to assert we are not filtering
    var mockDiffTuple = new ZkBlockImportTracerProvider.HubDiffTuple(mockDiff, mockDiff);
    doAnswer(__ -> mockDiffTuple).when(mockTraceProvider).compareWithTrace(any(), any());

    // configure our test context
    testCtx.setCliOptions(testOpts).setBlockImportTraceProvider(mockTraceProvider);

    final CodeCache codeCache = new CodeCache();

    // mock an accumulator
    AccountValue account =
        new BonsaiAccount(
            null,
            mockAddress,
            Hash.hash(mockAddress),
            0,
            Wei.ONE,
            Hash.EMPTY_TRIE_HASH,
            Hash.EMPTY,
            false,
            codeCache);
    AccountValue account2 =
        new BonsaiAccount(
            null,
            mockAddress2,
            Hash.hash(mockAddress2),
            0,
            Wei.ZERO,
            Hash.EMPTY_TRIE_HASH,
            Hash.ZERO,
            false,
            codeCache);

    var mockAccountMap = new HashMap<Address, PathBasedValue<AccountValue>>();
    mockAccountMap.put(mockAddress2, new PathBasedValue<>(account2, account2, false));

    var mockStorageMap = new HashMap<Address, Map<StorageSlotKey, PathBasedValue<UInt256>>>();
    var mockAccumulator = mock(PathBasedWorldStateUpdateAccumulator.class, RETURNS_DEEP_STUBS);
    doAnswer(__ -> account).when(mockAccumulator).getAccount(eq(mockAddress));
    doAnswer(__ -> mockAccountMap).when(mockAccumulator).getAccountsToUpdate();
    doAnswer(__ -> mockStorageMap).when(mockAccumulator).getStorageToUpdate();

    // mock block header
    var mockHeader = mock(BlockHeader.class, RETURNS_DEEP_STUBS);

    // create factor class under test:
    var factory = new ZkTrieLogFactory(testCtx);

    // generate the trielog
    var trielog = factory.create(mockAccumulator, mockHeader);

    // assert hub added address is present (with no values)
    assertThat(trielog.getAccountChanges().containsKey(mockAddress)).isTrue();
    assertThat(trielog.getAccountChanges().get(mockAddress).getPrior()).isEqualTo(account);
    assertThat(trielog.getAccountChanges().get(mockAddress).getUpdated()).isEqualTo(account);

    // assert accumulator address is still present
    assertThat(trielog.getAccountChanges().containsKey(mockAddress2)).isTrue();
    assertThat(trielog.getAccountChanges().get(mockAddress2).getPrior()).isEqualTo(account2);
    assertThat(trielog.getAccountChanges().get(mockAddress2).getUpdated()).isEqualTo(account2);
    var hubStorageChanges = trielog.getStorageChanges();
    assertThat(hubStorageChanges).isNotNull();
    assertThat(hubStorageChanges.isEmpty()).isFalse();
    var hubAddressStorageChanges = hubStorageChanges.get(mockAddress);
    assertThat(hubAddressStorageChanges).isNotNull();
    assertThat(hubAddressStorageChanges.isEmpty()).isFalse();
    var zeroKey = new StorageSlotKey(UInt256.ZERO);
    assertThat(hubAddressStorageChanges.containsKey(zeroKey)).isTrue();
    var oneKey = new StorageSlotKey(UInt256.ONE);
    assertThat(hubAddressStorageChanges.containsKey(oneKey)).isTrue();

    // assert zkTraceComparisonMask used in the factory is reflected in the trielog:
    assertThat(trielog).isInstanceOf(PluginTrieLogLayer.class);
    PluginTrieLogLayer pluginTrieLogLayer = (PluginTrieLogLayer) trielog;
    assertThat(pluginTrieLogLayer.zkTraceComparisonFeature()).isPresent();
    assertThat(pluginTrieLogLayer.zkTraceComparisonFeature().orElse(0)).isEqualTo(15);
  }

  @Test
  public void assertHubSeenFiltersTrieLog() {
    // mock trace provider
    var mockTraceProvider = mock(ZkBlockImportTracerProvider.class);
    var mockAddress = Address.fromHexString("0xdeadbeef");
    var mockAddress2 = Address.fromHexString("0xc0ffee");
    var mockAddress3 = Address.fromHexString("0x1337");
    var mockDiff =
        new HubSeenDiff(
            Set.of(mockAddress),
            Map.of(
                mockAddress, Set.of(UInt256.ZERO, UInt256.ONE),
                mockAddress2, Set.of(UInt256.ZERO, UInt256.MAX_VALUE)));
    var mockDiffTuple =
        new ZkBlockImportTracerProvider.HubDiffTuple(
            new HubSeenDiff(Collections.emptySet(), Collections.emptyMap()), mockDiff);
    doAnswer(__ -> mockDiffTuple).when(mockTraceProvider).compareWithTrace(any(), any());

    // mock test options to enable tracing
    ShomeiCliOptions testOpts = new ShomeiCliOptions();
    // only enable filtering
    testOpts.zkTraceComparisonMask = 16;

    // configure our test context
    testCtx.setCliOptions(testOpts).setBlockImportTraceProvider(mockTraceProvider);

    var mockAccumulator = mock(PathBasedWorldStateUpdateAccumulator.class, RETURNS_DEEP_STUBS);
    var mockAccountMap = new HashMap<Address, PathBasedValue<AccountValue>>();
    ZkAccountValue mockAccountValue = new ZkAccountValue(0, Wei.ZERO, Hash.EMPTY, Hash.EMPTY);
    mockAccountMap.put(mockAddress, new PathBasedValue<>(mockAccountValue, mockAccountValue));
    mockAccountMap.put(mockAddress2, new PathBasedValue<>(mockAccountValue, mockAccountValue));
    var mockStorageMap = new HashMap<Address, Map<StorageSlotKey, PathBasedValue<UInt256>>>();
    mockStorageMap.put(
        mockAddress,
        Map.of(new StorageSlotKey(UInt256.ZERO), new PathBasedValue<>(UInt256.ONE, UInt256.ONE)));
    mockStorageMap.put(
        mockAddress2,
        Map.of(
            new StorageSlotKey(UInt256.ZERO), new PathBasedValue<>(UInt256.ZERO, UInt256.ZERO),
            new StorageSlotKey(UInt256.ONE), new PathBasedValue<>(UInt256.ZERO, UInt256.ONE),
            new StorageSlotKey(UInt256.MAX_VALUE),
                new PathBasedValue<>(UInt256.ZERO, UInt256.ONE)));
    mockStorageMap.put(
        mockAddress3,
        Map.of(new StorageSlotKey(UInt256.ZERO), new PathBasedValue<>(UInt256.ZERO, UInt256.ZERO)));
    doAnswer(__ -> mockAccountMap).when(mockAccumulator).getAccountsToUpdate();
    doAnswer(__ -> mockStorageMap).when(mockAccumulator).getStorageToUpdate();

    // mock block header
    var mockHeader = mock(BlockHeader.class, RETURNS_DEEP_STUBS);

    // create factor class under test:
    var factory = new ZkTrieLogFactory(testCtx);

    // generate the trielog
    var trielog = factory.create(mockAccumulator, mockHeader);

    // assert we filtered out mockAddress, and not mockAddress2
    assertThat(trielog.getAccountChanges().containsKey(mockAddress)).isFalse();
    assertThat(trielog.getAccountChanges().containsKey(mockAddress2)).isTrue();

    // assert we filtered out all mockAddress storage:
    assertThat(trielog.getStorageChanges().containsKey(mockAddress)).isFalse();

    // assert we partially filtered mockAddress2 storage
    var address2StorageChanges = trielog.getStorageChanges().get(mockAddress2);
    assertNotNull(address2StorageChanges);
    assertThat(address2StorageChanges.size()).isEqualTo(2);
    assertTrue(address2StorageChanges.containsKey(new StorageSlotKey(UInt256.ONE)));
    // assert we refused to filter a changed slot value:
    assertTrue(address2StorageChanges.containsKey(new StorageSlotKey(UInt256.MAX_VALUE)));

    // assert we did not filter mockAddress3 storage
    var address3StorageChanges = trielog.getStorageChanges().get(mockAddress3);
    assertNotNull(address3StorageChanges);
    assertThat(address3StorageChanges.size()).isEqualTo(1);
    assertTrue(address3StorageChanges.containsKey(new StorageSlotKey(UInt256.ZERO)));

    // assert zkTraceComparisonMask used in the factory is reflected in the trielog:
    assertThat(trielog).isInstanceOf(PluginTrieLogLayer.class);
    PluginTrieLogLayer pluginTrieLogLayer = (PluginTrieLogLayer) trielog;
    assertThat(pluginTrieLogLayer.zkTraceComparisonFeature()).isPresent();
    assertThat(pluginTrieLogLayer.zkTraceComparisonFeature().orElse(0)).isEqualTo(16);
  }

  @Test
  void testFilterAccounts() {
    Address address1 = Address.fromHexString("0xdead");
    Address address2 = Address.fromHexString("0xbeef");
    Address address3 = Address.fromHexString("0xc0ffee");

    ZkAccountValue mockAccountVal = new ZkAccountValue(0, Wei.ZERO, Hash.EMPTY, Hash.EMPTY);
    ZkAccountValue mockUpdatedVal = new ZkAccountValue(1, Wei.ZERO, Hash.EMPTY, Hash.EMPTY);
    var tuple1 = new TrieLogValue<ZkAccountValue>(mockAccountVal, mockAccountVal, false);
    var tuple2 = new TrieLogValue<ZkAccountValue>(mockAccountVal, mockAccountVal, false);
    var tuple3 = new TrieLogValue<ZkAccountValue>(mockAccountVal, mockUpdatedVal, false);

    Map<Address, TrieLogValue<? extends AccountValue>> accountsToUpdate =
        Map.of(
            address1, tuple1,
            address2, tuple2,
            address3, tuple3);

    Set<Address> hubUnSeenAccounts = Set.of(address1, address3);

    Map<Address, ? extends LogTuple<? extends AccountValue>> result =
        ZkTrieLogFactory.filterAccounts(accountsToUpdate, hubUnSeenAccounts);

    // Verify results.
    // acct1 unseen & filtered, 2 seen & unfiltered, acct3 unseen refused to filter due to change
    assertEquals(2, result.size());
    assertFalse(result.containsKey(address1));
    assertTrue(result.containsKey(address2));
    assertTrue(result.containsKey(address3));

    assertEquals(tuple2, result.get(address2));
    assertEquals(tuple3, result.get(address3));
  }

  @Test
  void testFilterStorage() {
    Address address1 = Address.fromHexString("0x1234");
    Address address2 = Address.fromHexString("0x5678");
    Address address3 = Address.fromHexString("0x9abc");

    StorageSlotKey slot1 = new StorageSlotKey(UInt256.valueOf(1L));
    StorageSlotKey slot2 = new StorageSlotKey(UInt256.valueOf(2L));
    StorageSlotKey slot3 = new StorageSlotKey(UInt256.valueOf(3L));

    LogTuple<UInt256> value1 =
        new TrieLogValue<>(UInt256.valueOf(100), UInt256.valueOf(100), false);
    LogTuple<UInt256> value2 =
        new TrieLogValue<>(UInt256.valueOf(200), UInt256.valueOf(200), false);
    LogTuple<UInt256> value3 =
        new TrieLogValue<>(UInt256.valueOf(300), UInt256.valueOf(300), false);

    // storageToUpdate: address1 -> {slot1, slot2}, address2 -> {slot3}
    Map<Address, Map<StorageSlotKey, LogTuple<UInt256>>> storageToUpdate = new HashMap<>();
    storageToUpdate.put(address1, Map.of(slot1, value1, slot2, value2, slot3, value3));
    storageToUpdate.put(address2, Map.of(slot2, value2));
    storageToUpdate.put(address3, Map.of(slot3, value3));

    // hubSeenStorage: address1 -> [slot1 only]
    Map<Address, Set<Bytes32>> hubSeenStorage =
        Map.of(
            address1,
                Set.of(slot1.getSlotKey().get().toBytes(), slot2.getSlotKey().get().toBytes()),
            address3, Set.of(slot3.getSlotKey().get().toBytes()));

    // Call method under test
    Map<Address, Map<StorageSlotKey, ? extends LogTuple<UInt256>>> result =
        ZkTrieLogFactory.filterStorage(storageToUpdate, hubSeenStorage);

    // Assert address1 is partially filtered, with only slot3 remaining:
    assertTrue(result.containsKey(address1));
    Map<StorageSlotKey, ? extends LogTuple<UInt256>> filteredSlots = result.get(address1);
    assertTrue(filteredSlots.size() == 1);
    assertTrue(filteredSlots.containsKey(slot3));
    assertEquals(value3, filteredSlots.get(slot3));

    // Assert address2 storage is not filtered at all
    assertTrue(result.containsKey(address2));
    Map<StorageSlotKey, ? extends LogTuple<UInt256>> filteredSlots2 = result.get(address2);
    assertTrue(filteredSlots2.size() == 1);
    assertTrue(filteredSlots2.containsKey(slot2));
    assertEquals(value2, filteredSlots2.get(slot2));

    // Assert address3 is filtered out entirely
    assertFalse(result.containsKey(address3));
  }
}
