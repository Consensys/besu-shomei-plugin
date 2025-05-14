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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import net.consensys.shomei.blocktracing.ZkBlockImportTracerProvider;
import net.consensys.shomei.blocktracing.ZkBlockImportTracerProvider.HubSeenDiff;
import net.consensys.shomei.cli.ShomeiCliOptions;
import net.consensys.shomei.context.TestShomeiContext;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.AccountValue;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.BonsaiAccount;
import org.hyperledger.besu.ethereum.trie.pathbased.common.PathBasedValue;
import org.hyperledger.besu.ethereum.trie.pathbased.common.worldview.accumulator.PathBasedWorldStateUpdateAccumulator;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.services.trielogs.TrieLog;
import org.hyperledger.besu.plugin.services.trielogs.TrieLogFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ZkTrieLogFactoryTests {

  final TestShomeiContext testCtx = TestShomeiContext.create();
  final Address accountFixture = Address.fromHexString("0xdeadbeef");
  final PluginTrieLogLayer trieLogFixture = new PluginTrieLogLayer(Hash.ZERO);

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
    doAnswer(__ -> mockDiff).when(mockTraceProvider).compareWithTrace(any(), any());

    // mock test options to enable tracing
    ShomeiCliOptions testOpts = new ShomeiCliOptions();
    testOpts.zkTraceComparisonMask = 15;

    // configure our test context
    testCtx.setCliOptions(testOpts).setBlockImportTraceProvider(mockTraceProvider);

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
            false);
    AccountValue account2 =
        new BonsaiAccount(
            null,
            mockAddress2,
            Hash.hash(mockAddress2),
            0,
            Wei.ZERO,
            Hash.EMPTY_TRIE_HASH,
            Hash.ZERO,
            false);
    ;
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
  }
}
