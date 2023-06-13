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

import java.util.Map;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.plugin.services.trielogs.TrieLog;
import org.hyperledger.besu.plugin.services.trielogs.TrieLogFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ZkTrieLogFactoryTests {

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
    TrieLogFactory factory = new ZkTrieLogFactory();
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
    TrieLogFactory factory = new ZkTrieLogFactory();
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
    final TrieLogFactory factory = new ZkTrieLogFactory();
    final Address readAccount = Address.fromHexString("0xfeedf00d");
    final ZkAccountValue read = new ZkAccountValue(0, Wei.fromEth(1), Hash.EMPTY, Hash.EMPTY);

    trieLogFixture.getAccountChanges().put(readAccount, new TrieLogValue<>(read, read, false));

    byte[] rlp = factory.serialize(trieLogFixture);

    TrieLog layer = factory.deserialize(rlp);
    assertThat(layer).isEqualTo(trieLogFixture);
    assertThat(layer.getAccountChanges().get(readAccount).getUpdated()).isEqualTo(read);
    assertThat(layer.getAccountChanges().get(readAccount).getPrior()).isEqualTo(read);
  }
}
