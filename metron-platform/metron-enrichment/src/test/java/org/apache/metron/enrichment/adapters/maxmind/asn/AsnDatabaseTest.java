/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.metron.enrichment.adapters.maxmind.asn;

import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.metron.stellar.dsl.Context;
import org.apache.metron.test.utils.UnitTestHelper;
import org.json.simple.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class AsnDatabaseTest {

  private static Context context;
  private static File asnHdfsFile;
  private static File asnHdfsFile_update;
  private static final String IP_ADDR = "8.8.4.0";

  private static JSONObject expectedAsnMessage = new JSONObject();

  @Rule
  public TemporaryFolder testFolder = new TemporaryFolder();

  @BeforeClass
  @SuppressWarnings("unchecked")
  public static void setupOnce() {
    // Construct this explicitly here, otherwise it'll be a Long instead of Integer.
    expectedAsnMessage.put("autonomous_system_organization", "Google LLC");
    expectedAsnMessage.put("autonomous_system_number", 15169);
    expectedAsnMessage.put("network", "8.8.4.0");

    String baseDir = UnitTestHelper.findDir("GeoLite");
    asnHdfsFile = new File(new File(baseDir), "GeoLite2-ASN.tar.gz");
    asnHdfsFile_update = new File(new File(baseDir), "GeoLite2-ASN-2.tar.gz");
  }

  @Before
  public void setup() throws Exception {
    testFolder.create();
    context = new Context.Builder().with(Context.Capabilities.GLOBAL_CONFIG,
        () -> ImmutableMap.of(AsnDatabase.ASN_HDFS_FILE, asnHdfsFile.getAbsolutePath())
    ).build();
  }

  @Test
  public void testGetLocal() {
    AsnDatabase.INSTANCE.update(asnHdfsFile.getAbsolutePath());

    Optional<Map<String, Object>> result = AsnDatabase.INSTANCE.get("192.168.0.1");
    Assert.assertFalse("Local address result should be empty", result.isPresent());
  }

  @Test
  public void testExternalAddressNotFound() {
    AsnDatabase.INSTANCE.update(asnHdfsFile.getAbsolutePath());

    // the range 203.0.113.0/24 is assigned as "TEST-NET-3" and should never be locatable
    Optional<Map<String, Object>> result = AsnDatabase.INSTANCE.get("203.0.113.1");
    Assert.assertFalse("External address not found", result.isPresent());
  }

  @Test
  public void testGetRemote() {
    AsnDatabase.INSTANCE.update(asnHdfsFile.getAbsolutePath());

    Optional<Map<String, Object>> result = AsnDatabase.INSTANCE.get(IP_ADDR);
    Assert.assertEquals("Remote Local IP should return result based on DB", expectedAsnMessage,
        result.get());
  }

  @Test
  public void testMultipleUpdates() {
    AsnDatabase.INSTANCE.update(asnHdfsFile.getAbsolutePath());
    AsnDatabase.INSTANCE.update(asnHdfsFile.getAbsolutePath());

    Optional<Map<String, Object>> result = AsnDatabase.INSTANCE.get(IP_ADDR);
    Assert.assertEquals("Remote Local IP should return result based on DB", expectedAsnMessage,
        result.get());
  }

  @Test
  public void testUpdateIfNecessary() {
    HashMap<String, Object> globalConfig = new HashMap<>();
    globalConfig.put(AsnDatabase.ASN_HDFS_FILE, asnHdfsFile.getAbsolutePath());
    AsnDatabase.INSTANCE.updateIfNecessary(globalConfig);

    Optional<Map<String, Object>> result = AsnDatabase.INSTANCE.get(IP_ADDR);
    Assert.assertEquals("Remote Local IP should return result based on DB", expectedAsnMessage,
        result.get());
  }

  @Test
  public void testMultipleUpdateIfNecessary() {
    HashMap<String, Object> globalConfig = new HashMap<>();
    globalConfig.put(AsnDatabase.ASN_HDFS_FILE, asnHdfsFile.getAbsolutePath());
    AsnDatabase.INSTANCE.updateIfNecessary(globalConfig);
    AsnDatabase.INSTANCE.updateIfNecessary(globalConfig);

    Optional<Map<String, Object>> result = AsnDatabase.INSTANCE.get(IP_ADDR);
    Assert.assertEquals("Remote Local IP should return result based on DB", expectedAsnMessage,
        result.get());
  }

  @Test
  public void testDifferingUpdateIfNecessary() {
    HashMap<String, Object> globalConfig = new HashMap<>();
    globalConfig.put(AsnDatabase.ASN_HDFS_FILE, asnHdfsFile.getAbsolutePath());
    AsnDatabase.INSTANCE.updateIfNecessary(globalConfig);
    Optional<Map<String, Object>> result = AsnDatabase.INSTANCE.get(IP_ADDR);
    Assert.assertEquals("Remote Local IP should return result based on DB", expectedAsnMessage,
        result.get());

    globalConfig.put(AsnDatabase.ASN_HDFS_FILE, asnHdfsFile_update.getAbsolutePath());
    AsnDatabase.INSTANCE.updateIfNecessary(globalConfig);
    result = AsnDatabase.INSTANCE.get(IP_ADDR);

    Assert.assertEquals("Remote Local IP should return result based on DB", expectedAsnMessage,
        result.get());
  }
}
