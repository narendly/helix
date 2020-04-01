package org.apache.helix.rest.metadatastore;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.google.common.annotations.VisibleForTesting;
import org.apache.helix.msdcommon.callback.RoutingDataListener;
import org.apache.helix.msdcommon.datamodel.MetadataStoreRoutingData;
import org.apache.helix.msdcommon.datamodel.TrieRoutingData;
import org.apache.helix.msdcommon.exception.InvalidRoutingDataException;
import org.apache.helix.rest.metadatastore.accessor.MetadataStoreRoutingDataReader;
import org.apache.helix.rest.metadatastore.accessor.MetadataStoreRoutingDataWriter;
import org.apache.helix.rest.metadatastore.accessor.ZkRoutingDataReader;
import org.apache.helix.rest.metadatastore.accessor.ZkRoutingDataWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * NOTE: This is a singleton class. DO NOT EXTEND!
 * ZK-based MetadataStoreDirectory that listens on the routing data in routing ZKs with a update
 * callback.
 */
public class ZkMetadataStoreDirectory implements MetadataStoreDirectory, RoutingDataListener {
  private static final Logger LOG = LoggerFactory.getLogger(ZkMetadataStoreDirectory.class);

  // The following maps' keys represent the namespace
  // NOTE: made protected for testing reasons. DO NOT MODIFY!
  protected final Map<String, MetadataStoreRoutingDataReader> _routingDataReaderMap;
  protected final Map<String, MetadataStoreRoutingDataWriter> _routingDataWriterMap;
  protected final Map<String, MetadataStoreRoutingData> _routingDataMap;
  protected final Map<String, String> _routingZkAddressMap;
  // <namespace, <realm, <list of sharding keys>> mappings
  protected final Map<String, Map<String, List<String>>> _realmToShardingKeysMap;

  private static volatile ZkMetadataStoreDirectory _zkMetadataStoreDirectoryInstance;

  public static ZkMetadataStoreDirectory getInstance() {
    if (_zkMetadataStoreDirectoryInstance == null) {
      synchronized (ZkMetadataStoreDirectory.class) {
        if (_zkMetadataStoreDirectoryInstance == null) {
          _zkMetadataStoreDirectoryInstance = new ZkMetadataStoreDirectory();
        }
      }
    }
    return _zkMetadataStoreDirectoryInstance;
  }

  public static ZkMetadataStoreDirectory getInstance(String namespace, String zkAddress)
      throws InvalidRoutingDataException {
    getInstance().init(namespace, zkAddress);
    return _zkMetadataStoreDirectoryInstance;
  }

  /**
   * Note: this is a singleton class. The constructor is made protected for testing. DO NOT EXTEND!
   */
  @VisibleForTesting
  protected ZkMetadataStoreDirectory() {
    _routingDataReaderMap = new ConcurrentHashMap<>();
    _routingDataWriterMap = new ConcurrentHashMap<>();
    _routingZkAddressMap = new ConcurrentHashMap<>();
    _realmToShardingKeysMap = new ConcurrentHashMap<>();
    _routingDataMap = new ConcurrentHashMap<>();
  }

  private void init(String namespace, String zkAddress) throws InvalidRoutingDataException {
    if (!_routingZkAddressMap.containsKey(namespace)) {
      synchronized (_routingZkAddressMap) {
        if (!_routingZkAddressMap.containsKey(namespace)) {
          _routingZkAddressMap.put(namespace, zkAddress);
          _routingDataReaderMap.put(namespace, new ZkRoutingDataReader(namespace, zkAddress, this));
          _routingDataWriterMap.put(namespace, new ZkRoutingDataWriter(namespace, zkAddress));

          // Populate realmToShardingKeys with ZkRoutingDataReader
          _realmToShardingKeysMap
              .put(namespace, _routingDataReaderMap.get(namespace).getRoutingData());
          _routingDataMap
              .put(namespace, new TrieRoutingData(_realmToShardingKeysMap.get(namespace)));
        }
      }
    }
  }

  @Override
  public Collection<String> getAllNamespaces() {
    return Collections.unmodifiableCollection(_routingZkAddressMap.keySet());
  }

  @Override
  public Collection<String> getAllMetadataStoreRealms(String namespace) {
    if (!_realmToShardingKeysMap.containsKey(namespace)) {
      throw new NoSuchElementException("Namespace " + namespace + " does not exist!");
    }
    return Collections.unmodifiableCollection(_realmToShardingKeysMap.get(namespace).keySet());
  }

  @Override
  public Collection<String> getAllShardingKeys(String namespace) {
    if (!_realmToShardingKeysMap.containsKey(namespace)) {
      throw new NoSuchElementException("Namespace " + namespace + " does not exist!");
    }
    Set<String> allShardingKeys = new HashSet<>();
    _realmToShardingKeysMap.get(namespace).values().forEach(keys -> allShardingKeys.addAll(keys));
    return allShardingKeys;
  }

  @Override
  public Map<String, List<String>> getNamespaceRoutingData(String namespace) {
    Map<String, List<String>> routingData = _realmToShardingKeysMap.get(namespace);
    if (routingData == null) {
      throw new NoSuchElementException("Namespace " + namespace + " does not exist!");
    }

    return routingData;
  }

  @Override
  public Collection<String> getAllShardingKeysInRealm(String namespace, String realm) {
    if (!_realmToShardingKeysMap.containsKey(namespace)) {
      throw new NoSuchElementException("Namespace " + namespace + " does not exist!");
    }
    if (!_realmToShardingKeysMap.get(namespace).containsKey(realm)) {
      throw new NoSuchElementException(
          "Realm " + realm + " does not exist in namespace " + namespace);
    }
    return Collections.unmodifiableCollection(_realmToShardingKeysMap.get(namespace).get(realm));
  }

  @Override
  public Map<String, String> getAllMappingUnderPath(String namespace, String path) {
    if (!_routingDataMap.containsKey(namespace)) {
      throw new NoSuchElementException(
          "Failed to get all mapping under path: Namespace " + namespace + " is not found!");
    }
    return _routingDataMap.get(namespace).getAllMappingUnderPath(path);
  }

  @Override
  public String getMetadataStoreRealm(String namespace, String shardingKey) {
    if (!_routingDataMap.containsKey(namespace)) {
      throw new NoSuchElementException(
          "Failed to get metadata store realm: Namespace " + namespace + " is not found!");
    }
    return _routingDataMap.get(namespace).getMetadataStoreRealm(shardingKey);
  }

  @Override
  public boolean addMetadataStoreRealm(String namespace, String realm) {
    if (!_routingDataWriterMap.containsKey(namespace)) {
      throw new IllegalArgumentException(
          "Failed to add metadata store realm: Namespace " + namespace + " is not found!");
    }
    return _routingDataWriterMap.get(namespace).addMetadataStoreRealm(realm);
  }

  @Override
  public boolean deleteMetadataStoreRealm(String namespace, String realm) {
    if (!_routingDataWriterMap.containsKey(namespace)) {
      throw new IllegalArgumentException(
          "Failed to delete metadata store realm: Namespace " + namespace + " is not found!");
    }
    return _routingDataWriterMap.get(namespace).deleteMetadataStoreRealm(realm);
  }

  @Override
  public boolean addShardingKey(String namespace, String realm, String shardingKey) {
    if (!_routingDataWriterMap.containsKey(namespace) || !_routingDataMap.containsKey(namespace)) {
      throw new IllegalArgumentException(
          "Failed to add sharding key: Namespace " + namespace + " is not found!");
    }
    if (_routingDataMap.get(namespace).containsKeyRealmPair(shardingKey, realm)) {
      return true;
    }
    if (!_routingDataMap.get(namespace).isShardingKeyInsertionValid(shardingKey)) {
      throw new IllegalArgumentException(
          "Failed to add sharding key: Adding sharding key " + shardingKey
              + " makes routing data invalid!");
    }
    return _routingDataWriterMap.get(namespace).addShardingKey(realm, shardingKey);
  }

  @Override
  public boolean deleteShardingKey(String namespace, String realm, String shardingKey) {
    if (!_routingDataWriterMap.containsKey(namespace)) {
      throw new IllegalArgumentException(
          "Failed to delete sharding key: Namespace " + namespace + " is not found!");
    }
    return _routingDataWriterMap.get(namespace).deleteShardingKey(realm, shardingKey);
  }

  /**
   * Callback for updating the cached routing data.
   * Note: this method should not synchronize on the class or the map. We do not want namespaces
   * blocking each other.
   * Threadsafe map is used for _realmToShardingKeysMap.
   * The global consistency of the in-memory routing data is not a requirement (eventual consistency
   * is enough).
   * @param namespace
   */
  @Override
  public void refreshRoutingData(String namespace) {
    // Safe to ignore the callback if any of the maps are null.
    // If routingDataMap is null, then it will be populated by the constructor anyway
    // If routingDataMap is not null, then it's safe for the callback function to update it
    if (_routingZkAddressMap == null || _routingDataMap == null || _realmToShardingKeysMap == null
        || _routingDataReaderMap == null || _routingDataWriterMap == null) {
      LOG.warn(
          "refreshRoutingData callback called before ZKMetadataStoreDirectory was fully initialized. Skipping refresh!");
      return;
    }

    // Check if namespace exists; otherwise, return as a NOP and log it
    if (!_routingZkAddressMap.containsKey(namespace)) {
      LOG.error(
          "Failed to refresh internally-cached routing data! Namespace not found: " + namespace);
    }

    try {
      Map<String, List<String>> rawRoutingData =
          _routingDataReaderMap.get(namespace).getRoutingData();
      _realmToShardingKeysMap.put(namespace, rawRoutingData);

      MetadataStoreRoutingData routingData = new TrieRoutingData(rawRoutingData);
      _routingDataMap.put(namespace, routingData);
    } catch (InvalidRoutingDataException e) {
      LOG.error("Failed to refresh cached routing data for namespace {}", namespace, e);
    }
  }

  @Override
  public synchronized void close() {
    _routingDataReaderMap.values().forEach(MetadataStoreRoutingDataReader::close);
    _routingDataWriterMap.values().forEach(MetadataStoreRoutingDataWriter::close);
    _routingDataReaderMap.clear();
    _routingDataWriterMap.clear();
    _routingZkAddressMap.clear();
    _realmToShardingKeysMap.clear();
    _routingDataMap.clear();
    _zkMetadataStoreDirectoryInstance = null;
  }
}