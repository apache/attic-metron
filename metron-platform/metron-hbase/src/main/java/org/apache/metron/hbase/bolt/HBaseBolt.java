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

package org.apache.metron.hbase.bolt;

import backtype.storm.Config;
import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseRichBolt;
import backtype.storm.tuple.Tuple;
import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.client.Durability;
import org.apache.hadoop.hbase.client.Mutation;
import org.apache.metron.hbase.HTableProvider;
import org.apache.metron.hbase.TableProvider;
import org.apache.metron.hbase.client.HBaseClient;
import org.apache.storm.hbase.bolt.mapper.HBaseMapper;
import org.apache.storm.hbase.common.ColumnList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * A bolt that writes to HBase.
 *
 * Each bolt defined within a topology can interact with only a single HBase table.
 */
public class HBaseBolt extends BaseRichBolt {

  private static final Logger LOG = LoggerFactory.getLogger(HBaseBolt.class);

  /**
   * Should the write-ahead-log be used.
   */
  private boolean writeToWAL = true;

  /**
   * The interval in seconds at which the writes are flushed to Hbase.
   */
  private int flushIntervalSecs = 1;

  /**
   * The batch size.
   */
  private int batchSize = 1000;

  /**
   * The name of the HBase table.  Each bolt communicates with a single HBase table.
   */
  protected String tableName;

  /**
   * The mapper which defines how tuple fields are mapped to HBase.
   */
  protected HBaseMapper mapper;

  /**
   * The name of the class that should be used as a table provider.
   */
  protected String tableProvider = "org.apache.metron.hbase.HTableProvider";

  private BatchHelper batchHelper;
  protected OutputCollector collector;
  protected transient HBaseClient hbaseClient;

  public HBaseBolt(String tableName, HBaseMapper mapper) {
    this.tableName = tableName;
    this.mapper = mapper;
  }

  public HBaseBolt writeToWAL(boolean writeToWAL) {
    this.writeToWAL = writeToWAL;
    return this;
  }

  public HBaseBolt withTableProvider(String tableProvider) {
    this.tableProvider = tableProvider;
    return this;
  }

  public HBaseBolt withBatchSize(int batchSize) {
    this.batchSize = batchSize;
    return this;
  }

  public HBaseBolt withFlushIntervalSecs(int flushIntervalSecs) {
    this.flushIntervalSecs = flushIntervalSecs;
    return this;
  }

  public void setClient(HBaseClient hbaseClient) {
    this.hbaseClient = hbaseClient;
  }

  @Override
  public Map<String, Object> getComponentConfiguration() {
    Config conf = new Config();
    conf.put(Config.TOPOLOGY_TICK_TUPLE_FREQ_SECS, flushIntervalSecs);
    return conf;
  }

  @Override
  public void prepare(Map map, TopologyContext topologyContext, OutputCollector collector) {
    this.collector = collector;
    this.batchHelper = new BatchHelper(batchSize, collector);

    TableProvider provider = getTableProvider(tableProvider);
    hbaseClient = new HBaseClient(provider, HBaseConfiguration.create(), tableName);
  }

  @Override
  public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
    // nothing emitted
  }

  @Override
  public void execute(Tuple tuple) {
    try {
      if (batchHelper.shouldHandle(tuple)) {
        save(tuple);
      }

      if (batchHelper.shouldFlush()) {
        flush();
      }

    } catch (Exception e) {
      batchHelper.fail(e);
      hbaseClient.clearMutations();
    }
  }

  /**
   * Saves an operation for later.
   * @param tuple Contains the data elements that need written to HBase.
   */
  private void save(Tuple tuple) {
    byte[] rowKey = this.mapper.rowKey(tuple);
    ColumnList cols = this.mapper.columns(tuple);
    Durability durability = writeToWAL ? Durability.SYNC_WAL : Durability.SKIP_WAL;
    hbaseClient.addMutation(rowKey, cols, durability);
    batchHelper.addBatch(tuple);
  }

  /**
   * Flush all saved operations.
   */
  private void flush() {
    this.hbaseClient.mutate();
    batchHelper.ack();
  }

  /**
   *
   * @param connectorImpl
   * @return
   */
  private static TableProvider getTableProvider(String connectorImpl) {

    // if class name not defined, use a reasonable default
    if(StringUtils.isEmpty(connectorImpl) || connectorImpl.charAt(0) == '$') {
      return new HTableProvider();
    }

    // instantiate the table provider
    try {
      Class<? extends TableProvider> clazz = (Class<? extends TableProvider>) Class.forName(connectorImpl);
      return clazz.getConstructor().newInstance();

    } catch (InstantiationException | IllegalAccessException | IllegalStateException |
              InvocationTargetException | NoSuchMethodException | ClassNotFoundException e) {
      throw new IllegalStateException("Unable to instantiate connector", e);
    }
  }
}
