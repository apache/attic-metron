/*
 *
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.apache.metron.profiler.hbase;

import org.apache.storm.tuple.Tuple;
import org.apache.metron.profiler.ProfileMeasurement;
import org.apache.metron.profiler.ProfilePeriod;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Formatter;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests the DecodableRowKeyBuilder.
 */
public class DecodableRowKeyBuilderTest {

  private static final int saltDivisor = 1000;
  private static final long periodDuration = 15;
  private static final TimeUnit periodUnits = TimeUnit.MINUTES;

  private DecodableRowKeyBuilder rowKeyBuilder;
  private ProfileMeasurement measurement;
  private Tuple tuple;

  /**
   * Thu, Aug 25 2016 13:27:10 GMT
   */
  private long AUG2016 = 1472131630748L;

  @Before
  public void setup() throws Exception {

    // a profile measurement
    measurement = new ProfileMeasurement()
            .withProfileName("profile")
            .withEntity("entity")
            .withPeriod(AUG2016, periodDuration, periodUnits);

    // the tuple will contain the original message
    tuple = mock(Tuple.class);
    when(tuple.getValueByField(eq("measurement"))).thenReturn(measurement);

    rowKeyBuilder = new DecodableRowKeyBuilder(saltDivisor, periodDuration, periodUnits);
  }

  /**
   * Test building a row key with no groups.  This is likely the most common scenario.
   */
  @Test
  public void testEncode() throws Exception {
    // setup
    measurement.withGroups(Collections.emptyList());

    // the expected row key
    ByteBuffer buffer = ByteBuffer
            .allocate(100)
            .putShort(DecodableRowKeyBuilder.MAGIC_NUMBER)
            .put(DecodableRowKeyBuilder.VERSION)
            .putInt(DecodableRowKeyBuilder.encodeSalt(measurement.getPeriod(), saltDivisor).length)
            .put(DecodableRowKeyBuilder.encodeSalt(measurement.getPeriod(), saltDivisor))
            .putInt(measurement.getProfileName().getBytes().length)
            .put(measurement.getProfileName().getBytes())
            .putInt(measurement.getEntity().getBytes().length)
            .put(measurement.getEntity().getBytes())
            .putInt(measurement.getGroups().size())
            .putLong(measurement.getPeriod().getPeriod())
            .putLong(measurement.getPeriod().getDurationMillis());

    buffer.flip();
    final byte[] expected = new byte[buffer.limit()];
    buffer.get(expected, 0, buffer.limit());

    // validate encoding
    byte[] actual = rowKeyBuilder.encode(measurement);
    Assert.assertTrue(Arrays.equals(expected, actual));

    // validate decoding
    ProfileMeasurement decoded = rowKeyBuilder.decode(actual);
    Assert.assertEquals(measurement.getProfileName(), decoded.getProfileName());
    Assert.assertEquals(measurement.getEntity(), decoded.getEntity());
    Assert.assertEquals(measurement.getPeriod(), decoded.getPeriod());
    Assert.assertEquals(measurement.getGroups(), decoded.getGroups());
  }

  /**
   * Build a row key that includes only one group.
   */
  @Test
  public void testEncodeWithOneGroup() throws Exception {
    // setup
    measurement.withGroups(Arrays.asList("group1"));

    // the expected row key
    ByteBuffer buffer = ByteBuffer
            .allocate(100)
            .putShort(DecodableRowKeyBuilder.MAGIC_NUMBER)
            .put(DecodableRowKeyBuilder.VERSION)
            .putInt(DecodableRowKeyBuilder.encodeSalt(measurement.getPeriod(), saltDivisor).length)
            .put(DecodableRowKeyBuilder.encodeSalt(measurement.getPeriod(), saltDivisor))
            .putInt(measurement.getProfileName().getBytes().length)
            .put(measurement.getProfileName().getBytes())
            .putInt(measurement.getEntity().getBytes().length)
            .put(measurement.getEntity().getBytes())
            .putInt(measurement.getGroups().size())
            .putInt("group1".getBytes().length)
            .put("group1".getBytes())
            .putLong(measurement.getPeriod().getPeriod())
            .putLong(measurement.getPeriod().getDurationMillis());

    buffer.flip();
    final byte[] expected = new byte[buffer.limit()];
    buffer.get(expected, 0, buffer.limit());

    // validate encoding
    byte[] actual = rowKeyBuilder.encode(measurement);
    Assert.assertTrue(Arrays.equals(expected, actual));

    // validate decoding
    ProfileMeasurement decoded = rowKeyBuilder.decode(actual);
    Assert.assertEquals(measurement.getProfileName(), decoded.getProfileName());
    Assert.assertEquals(measurement.getEntity(), decoded.getEntity());
    Assert.assertEquals(measurement.getPeriod(), decoded.getPeriod());
    Assert.assertEquals(measurement.getGroups(), decoded.getGroups());
  }

  /**
   * Build a row key that includes two groups.
   */
  @Test
  public void testEncodeWithTwoGroups() throws Exception {
    // setup
    measurement.withGroups(Arrays.asList("group1","group2"));

    // the expected row key
    ByteBuffer buffer = ByteBuffer
            .allocate(100)
            .putShort(DecodableRowKeyBuilder.MAGIC_NUMBER)
            .put(DecodableRowKeyBuilder.VERSION)
            .putInt(DecodableRowKeyBuilder.encodeSalt(measurement.getPeriod(), saltDivisor).length)
            .put(DecodableRowKeyBuilder.encodeSalt(measurement.getPeriod(), saltDivisor))
            .putInt(measurement.getProfileName().getBytes().length)
            .put(measurement.getProfileName().getBytes())
            .putInt(measurement.getEntity().getBytes().length)
            .put(measurement.getEntity().getBytes())
            .putInt(measurement.getGroups().size())
            .putInt("group1".getBytes().length)
            .put("group1".getBytes())
            .putInt("group2".getBytes().length)
            .put("group2".getBytes())
            .putLong(measurement.getPeriod().getPeriod())
            .putLong(measurement.getPeriod().getDurationMillis());

    buffer.flip();
    final byte[] expected = new byte[buffer.limit()];
    buffer.get(expected, 0, buffer.limit());

    // validate encoding
    byte[] actual = rowKeyBuilder.encode(measurement);
    Assert.assertTrue(Arrays.equals(expected, actual));

    // validate decoding
    ProfileMeasurement decoded = rowKeyBuilder.decode(actual);
    Assert.assertEquals(measurement.getProfileName(), decoded.getProfileName());
    Assert.assertEquals(measurement.getEntity(), decoded.getEntity());
    Assert.assertEquals(measurement.getPeriod(), decoded.getPeriod());
    Assert.assertEquals(measurement.getGroups(), decoded.getGroups());
  }

  /**
   * Build a row key that includes a single group that is an integer.
   */
  @Test
  public void testEncodeWithOneIntegerGroup() throws Exception {
    // setup
    // when decoding have to treat all groups as strings, thus we expect 200 to be decoded as "200"
    List actualGroups = Arrays.asList(200);
    List expectedGroups = Arrays.asList("200");
    measurement.withGroups(actualGroups);

    // the expected row key
    ByteBuffer buffer = ByteBuffer
            .allocate(100)
            .putShort(DecodableRowKeyBuilder.MAGIC_NUMBER)
            .put(DecodableRowKeyBuilder.VERSION)
            .putInt(DecodableRowKeyBuilder.encodeSalt(measurement.getPeriod(), saltDivisor).length)
            .put(DecodableRowKeyBuilder.encodeSalt(measurement.getPeriod(), saltDivisor))
            .putInt(measurement.getProfileName().getBytes().length)
            .put(measurement.getProfileName().getBytes())
            .putInt(measurement.getEntity().getBytes().length)
            .put(measurement.getEntity().getBytes())
            .putInt(measurement.getGroups().size())
            .putInt("200".getBytes().length)
            .put("200".getBytes())
            .putLong(measurement.getPeriod().getPeriod())
            .putLong(measurement.getPeriod().getDurationMillis());

    buffer.flip();
    final byte[] expected = new byte[buffer.limit()];
    buffer.get(expected, 0, buffer.limit());

    // validate encoding
    byte[] actual = rowKeyBuilder.encode(measurement);
    Assert.assertTrue(Arrays.equals(expected, actual));

    // validate decoding
    ProfileMeasurement decoded = rowKeyBuilder.decode(actual);
    Assert.assertEquals(measurement.getProfileName(), decoded.getProfileName());
    Assert.assertEquals(measurement.getEntity(), decoded.getEntity());
    Assert.assertEquals(measurement.getPeriod(), decoded.getPeriod());
    Assert.assertEquals(expectedGroups, decoded.getGroups());
  }

  /**
   * Build a row key that includes a single group that is an integer.
   */
  @Test
  public void testEncodeWithMixedGroups() throws Exception {
    // setup
    // when decoding have to treat all groups as strings, thus we expect 200 to be decoded as "200"
    List actualGroups = Arrays.asList(200, "group1");
    List expectedGroups = Arrays.asList("200", "group1");
    measurement.withGroups(actualGroups);

    // the expected row key
    ByteBuffer buffer = ByteBuffer
            .allocate(100)
            .putShort(DecodableRowKeyBuilder.MAGIC_NUMBER)
            .put(DecodableRowKeyBuilder.VERSION)
            .putInt(DecodableRowKeyBuilder.encodeSalt(measurement.getPeriod(), saltDivisor).length)
            .put(DecodableRowKeyBuilder.encodeSalt(measurement.getPeriod(), saltDivisor))
            .putInt(measurement.getProfileName().getBytes().length)
            .put(measurement.getProfileName().getBytes())
            .putInt(measurement.getEntity().getBytes().length)
            .put(measurement.getEntity().getBytes())
            .putInt(measurement.getGroups().size())
            .putInt("200".getBytes().length)
            .put("200".getBytes())
            .putInt("group1".getBytes().length)
            .put("group1".getBytes())
            .putLong(measurement.getPeriod().getPeriod())
            .putLong(measurement.getPeriod().getDurationMillis());

    buffer.flip();
    final byte[] expected = new byte[buffer.limit()];
    buffer.get(expected, 0, buffer.limit());

    // validate encoding
    byte[] actual = rowKeyBuilder.encode(measurement);
    Assert.assertTrue(Arrays.equals(expected, actual));

    // validate decoding
    ProfileMeasurement decoded = rowKeyBuilder.decode(actual);
    Assert.assertEquals(measurement.getProfileName(), decoded.getProfileName());
    Assert.assertEquals(measurement.getEntity(), decoded.getEntity());
    Assert.assertEquals(measurement.getPeriod(), decoded.getPeriod());
    Assert.assertEquals(expectedGroups, decoded.getGroups());
  }

  /**
   * Tests encoding multiple row keys at once.
   */
  @Test
  public void testEncodeMultipleRowKeys() throws Exception {
    int hoursAgo = 1;

    // setup
    List<Object> groups = Collections.emptyList();
    rowKeyBuilder = new DecodableRowKeyBuilder(saltDivisor, periodDuration, periodUnits);

    // a dummy profile measurement
    long now = System.currentTimeMillis();
    long oldest = now - TimeUnit.HOURS.toMillis(hoursAgo);
    ProfileMeasurement m = new ProfileMeasurement()
            .withProfileName("profile")
            .withEntity("entity")
            .withPeriod(oldest, periodDuration, periodUnits)
            .withProfileValue(22);

    // generate a list of expected keys
    List<byte[]> expectedKeys = new ArrayList<>();
    for  (int i=0; i<(hoursAgo * 4)+1; i++) {

      // generate the expected key
      byte[] rk = rowKeyBuilder.encode(m);
      expectedKeys.add(rk);

      // advance to the next period
      ProfilePeriod next = m.getPeriod().next();
      m = new ProfileMeasurement()
              .withProfileName("profile")
              .withEntity("entity")
              .withPeriod(next.getStartTimeMillis(), periodDuration, periodUnits);
    }

    // execute
    List<byte[]> actualKeys = rowKeyBuilder.encode(measurement.getProfileName(), measurement.getEntity(), groups, oldest, now);

    // validate - expectedKeys == actualKeys
    for(int i=0; i<actualKeys.size(); i++) {
      byte[] actual = actualKeys.get(i);
      byte[] expected = expectedKeys.get(i);
      assertThat(actual, equalTo(expected));
    }
  }

  /**
   * Attempt to encode a row key with an invalid ProfileMeasurement.
   */
  @Test(expected = IllegalArgumentException.class)
  public void testEncodeEmptyProfileMeasurement() throws Exception {
    // setup - create an empty profile measurement
    measurement = new ProfileMeasurement();

    // cannot encode without a valid profile measurement
    rowKeyBuilder.encode(measurement);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testDecodeInvalidMagicNumber() throws Exception {

    // a row key with an invalid magic number
    ByteBuffer buffer = ByteBuffer
            .allocate(100)
            .putShort((short)11)
            .put(DecodableRowKeyBuilder.VERSION)
            .putInt(DecodableRowKeyBuilder.encodeSalt(measurement.getPeriod(), saltDivisor).length)
            .put(DecodableRowKeyBuilder.encodeSalt(measurement.getPeriod(), saltDivisor))
            .putInt(measurement.getProfileName().getBytes().length)
            .put(measurement.getProfileName().getBytes())
            .putInt(measurement.getEntity().getBytes().length)
            .put(measurement.getEntity().getBytes())
            .putInt(measurement.getGroups().size())
            .putLong(measurement.getPeriod().getPeriod())
            .putLong(measurement.getPeriod().getDurationMillis());

    buffer.flip();
    final byte[] invalid = new byte[buffer.limit()];
    buffer.get(invalid, 0, buffer.limit());

    rowKeyBuilder.decode(invalid);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testDecodeInvalidVersion() throws Exception {

    // a row key with an invalid version number
    ByteBuffer buffer = ByteBuffer
            .allocate(100)
            .putShort(DecodableRowKeyBuilder.MAGIC_NUMBER)
            .put((byte) 9)
            .putInt(DecodableRowKeyBuilder.encodeSalt(measurement.getPeriod(), saltDivisor).length)
            .put(DecodableRowKeyBuilder.encodeSalt(measurement.getPeriod(), saltDivisor))
            .putInt(measurement.getProfileName().getBytes().length)
            .put(measurement.getProfileName().getBytes())
            .putInt(measurement.getEntity().getBytes().length)
            .put(measurement.getEntity().getBytes())
            .putInt(measurement.getGroups().size())
            .putLong(measurement.getPeriod().getPeriod())
            .putLong(measurement.getPeriod().getDurationMillis());

    buffer.flip();
    final byte[] invalid = new byte[buffer.limit()];
    buffer.get(invalid, 0, buffer.limit());

    rowKeyBuilder.decode(invalid);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testDecodeWithUndeflow() throws Exception {

    // a row key missing everything except the magic number and version
    ByteBuffer buffer = ByteBuffer
            .allocate(100)
            .putShort(DecodableRowKeyBuilder.MAGIC_NUMBER)
            .put(DecodableRowKeyBuilder.VERSION);

    buffer.flip();
    final byte[] invalid = new byte[buffer.limit()];
    buffer.get(invalid, 0, buffer.limit());

    rowKeyBuilder.decode(invalid);
  }

  private void printBytes(byte[] bytes) {
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    Formatter formatter = new Formatter(sb);
    for (byte b : bytes) {
      formatter.format("%02x ", b);
    }
    System.out.println(sb.toString());
  }
}
