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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import net.consensys.shomei.trielog.TrieLogValue;

import java.math.BigInteger;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.AccountValue;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.services.trielogs.TrieLog;
import org.junit.jupiter.api.Test;

public class ZkBlockImportTracerProviderTest {
  private ZkBlockImportTracerProvider comparator =
      spy(new ZkBlockImportTracerProvider(BigInteger.ONE));
  private BlockHeader mockHeader = mock(BlockHeader.class);

  @Test
  void testMatchingAccounts() {
    Address a = Address.fromHexString("0x1");
    Address b = Address.fromHexString("0x2");

    Map<Address, TrieLog.LogTuple<? extends AccountValue>> accountToUpdate =
        Map.of(
            a, new TrieLogValue<>(null, null, false),
            b, new TrieLogValue<>(null, null, false));

    Set<Address> hubAccountsSeen = Set.of(a, b);

    comparator.compareAndWarnAccount(mockHeader, accountToUpdate, hubAccountsSeen);
    verify(comparator, never()).alert(any());
  }

  @Test
  void testHubSawMoreAccounts() {
    Address a = Address.fromHexString("0x1");
    Address b = Address.fromHexString("0x2");
    Address c = Address.fromHexString("0x3");

    Map<Address, TrieLog.LogTuple<? extends AccountValue>> accountToUpdate =
        Map.of(
            a, new TrieLogValue<>(null, null, false),
            b, new TrieLogValue<>(null, null, false));

    Set<Address> hubAccountsSeen = Set.of(a, b, c);

    comparator.compareAndWarnAccount(mockHeader, accountToUpdate, hubAccountsSeen);
    verify(comparator, times(1)).alert(any());
  }

  @Test
  void testAccumulatorSawMoreAccounts() {
    Address a = Address.fromHexString("0x1");
    Address b = Address.fromHexString("0x2");
    Address c = Address.fromHexString("0x3");

    Map<Address, TrieLog.LogTuple<? extends AccountValue>> accountToUpdate =
        Map.of(
            a, new TrieLogValue<>(null, null, false),
            b, new TrieLogValue<>(null, null, false),
            c, new TrieLogValue<>(null, null, false));

    Set<Address> hubAccountsSeen = Set.of(a, b);

    comparator.compareAndWarnAccount(mockHeader, accountToUpdate, hubAccountsSeen);
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

    comparator.compareAndWarnStorage(mockHeader, storageToUpdate, hubSeenStorage);

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

    comparator.compareAndWarnStorage(mockHeader, storageToUpdate, hubSeenStorage);

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

    comparator.compareAndWarnStorage(mockHeader, storageToUpdate, hubSeenStorage);

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

    comparator.compareAndWarnStorage(mockHeader, storageToUpdate, hubSeenStorage);

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

    comparator.compareAndWarnStorage(mockHeader, storageToUpdate, hubSeenStorage);

    // ✅ Verify alert() was never called
    verify(comparator, times(1)).alert(any());
  }
}
