/**
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
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.metron.common.field.mapping;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import org.adrianwalker.multilinestring.Multiline;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.metron.common.configuration.MappingHandler;
import org.apache.metron.common.configuration.SensorParserConfig;
import org.json.simple.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;

public class FieldMappingTest {
  public static class TestMapping implements FieldMapping {

    @Override
    public Map<String, Object> map(Map<String, Object> input, String outputField, Map<String, Object> fieldMappingConfig, Map<String, Object> sensorConfig) {
      return ImmutableMap.of(outputField, Joiner.on(fieldMappingConfig.get("delim").toString()).join(input.entrySet()));
    }
  }

 /**
   {
    "fieldMappings" : [
          {
            "input" : [ "field1", "field2" ]
          , "output" : "output"
          , "mapping" : "org.apache.metron.common.field.mapping.FieldMappingTest$TestMapping"
          , "config" : {
                "delim" : ","
                      }
          }
                      ]
   }
   */
  @Multiline
  public static String complexConfig;

  /**
   {
    "fieldMappings" : [
          {
            "input" : "protocol"
          , "mapping" : "IP_PROTOCOL"
          }
                      ]
   }
   */
  @Multiline
  public static String config;

  /**
   {
    "fieldMappings" : [
          {
           "mapping" : "IP_PROTOCOL"
          }
                      ]
   }
   */
  @Multiline
  public static String badConfigMissingInput;

  /**
   {
    "fieldMappings" : [
          {
            "input" : "protocol"
          }
                      ]
   }
   */
  @Multiline
  public static String badConfigMissingMapping;

  @Test
  public void testValidSerde_simple() throws IOException {
    SensorParserConfig c = SensorParserConfig.fromBytes(Bytes.toBytes(config));
    Assert.assertEquals(1, c.getFieldMappings().size());
    Assert.assertEquals(IPProtocolMapping.class, c.getFieldMappings().get(0).getMapping().getClass());
    Assert.assertEquals(ImmutableList.of("protocol"), c.getFieldMappings().get(0).getInput());
  }

  @Test(expected = IllegalStateException.class)
  public void testInValidSerde_missingInput() throws IOException {
    SensorParserConfig.fromBytes(Bytes.toBytes(badConfigMissingInput));
  }

  @Test(expected = IllegalStateException.class)
  public void testInValidSerde_missingMapping() throws IOException {
    SensorParserConfig.fromBytes(Bytes.toBytes(badConfigMissingMapping));
  }

  @Test
  public void testComplexMapping() throws IOException {
    SensorParserConfig c = SensorParserConfig.fromBytes(Bytes.toBytes(complexConfig));
    MappingHandler handler = Iterables.getFirst(c.getFieldMappings(), null);

    Assert.assertNotNull(handler);
    Assert.assertEquals(ImmutableMap.of("output", "field1=value1,field2=value2")
                       ,handler.map(new JSONObject(ImmutableMap.of("field1", "value1"
                                                                  ,"field2", "value2"
                                                                  )
                                                  )
                                   , c.getParserConfig()
                                   )
                       );
  }
  @Test
  public void testSimpleMapping() throws IOException {
    SensorParserConfig c = SensorParserConfig.fromBytes(Bytes.toBytes(config));
    MappingHandler handler = Iterables.getFirst(c.getFieldMappings(), null);

    Assert.assertNotNull(handler);
    Assert.assertEquals(ImmutableMap.of("protocol", "TCP")
                       ,handler.map(new JSONObject(ImmutableMap.of("protocol", 6)), c.getParserConfig())
                       );
  }
}
