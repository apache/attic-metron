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

package org.apache.metron.hbase;

import backtype.storm.tuple.Tuple;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.storm.hbase.bolt.mapper.HBaseMapper;
import org.apache.storm.hbase.common.ColumnList;

import java.util.Calendar;

/**
 * Maps a Widget to HBase.  Used only for testing.
 */
public class WidgetMapper implements HBaseMapper {

  @Override
  public byte[] rowKey(Tuple tuple) {
    Widget w = (Widget) tuple.getValueByField("widget");
    return Bytes.toBytes(w.getName());
  }

  @Override
  public ColumnList columns(Tuple tuple) {
    Widget w = (Widget) tuple.getValueByField("widget");

    ColumnList cols = new ColumnList();
    cols.addColumn(CF, QNAME, Bytes.toBytes(w.getName()));
    cols.addColumn(CF, QCOST, Bytes.toBytes(w.getCost()));
    return cols;
  }

  public static final String CF_STRING = "cfWidget";
  public static final byte[] CF = Bytes.toBytes(CF_STRING);
  public static final byte[] QNAME = Bytes.toBytes("qName");
  public static final byte[] QCOST = Bytes.toBytes("qCost");
}
