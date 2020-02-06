package org.apache.helix.rest.metadatastore.constant;

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

public class MetadataStoreRoutingConstants {
  public static final String ROUTING_DATA_PATH = "/METADATA_STORE_ROUTING_DATA";

  // For ZK only
  public static final String ZNRECORD_LIST_FIELD_KEY = "ZK_PATH_SHARDING_KEYS";

  // This is the name of the ZNode that will be used to implement a mutex for ZkRoutingDataWriter
  public static final String ZK_LOCK_BASE_PATH = ROUTING_DATA_PATH + "/LOCK_ZK_ROUTING_DATA_WRITER";
}
