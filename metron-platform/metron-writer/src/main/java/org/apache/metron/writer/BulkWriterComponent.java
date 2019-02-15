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

package org.apache.metron.writer;

import org.apache.metron.common.configuration.writer.WriterConfiguration;
import org.apache.metron.common.system.Clock;
import org.apache.metron.common.writer.BulkMessageWriter;
import org.apache.metron.common.writer.BulkWriterMessage;
import org.apache.metron.common.writer.BulkWriterResponse;
import org.apache.metron.common.writer.MessageId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * This component manages an internal cache of messages to be written in batch.  A separate cache is used for each sensor.
 * Each time a message is written to this component, the {@link org.apache.metron.writer.FlushPolicy#shouldFlush(String, WriterConfiguration, int)}
 * method is called for each flush policy to determine if a batch of messages should be flushed.  When a flush does happen,
 * the {@link org.apache.metron.writer.FlushPolicy#onFlush(String, BulkWriterResponse)} method is called for each flush policy
 * so that any post-processing (message acknowledgement for example) can be done.  This component also ensures all messages
 * in a batch are included in the response as either a success or failure.
 *
 * @param <MESSAGE_T>
 */
public class BulkWriterComponent<MESSAGE_T> {
  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private Map<String, List<BulkWriterMessage<MESSAGE_T>>> sensorMessageCache = new HashMap<>();
  private List<FlushPolicy> flushPolicies;

  public BulkWriterComponent(int maxBatchTimeout) {
    flushPolicies = new ArrayList<>();
    flushPolicies.add(new BatchSizePolicy());
    flushPolicies.add(new BatchTimeoutPolicy(maxBatchTimeout));
  }

  public BulkWriterComponent(int maxBatchTimeout, Clock clock) {
    flushPolicies = new ArrayList<>();
    flushPolicies.add(new BatchSizePolicy());
    flushPolicies.add(new BatchTimeoutPolicy(maxBatchTimeout, clock));
  }

  protected BulkWriterComponent(List<FlushPolicy> flushPolicies) {
    this.flushPolicies = flushPolicies;
  }

  /**
   * Accepts a message to be written and stores it in an internal cache of messages.  Iterates through {@link org.apache.metron.writer.FlushPolicy}
   * implementations to determine if a batch should be flushed.
   * @param sensorType sensor type
   * @param bulkWriterMessage message to be written
   * @param bulkMessageWriter writer that will do the actual writing
   * @param configurations writer configurations
   */
  public void write(String sensorType
          , BulkWriterMessage<MESSAGE_T> bulkWriterMessage
          , BulkMessageWriter<MESSAGE_T> bulkMessageWriter
          , WriterConfiguration configurations
  )
  {
    List<BulkWriterMessage<MESSAGE_T>> messages = sensorMessageCache.getOrDefault(sensorType, new ArrayList<>());
    sensorMessageCache.put(sensorType, messages);

    // if a sensor type is disabled flush all pending messages and discard the new message
    if (!configurations.isEnabled(sensorType)) {
      // flush pending messages
      flush(sensorType, bulkMessageWriter, configurations, messages);

      // Include the new message for any post-processing but don't write it
      BulkWriterResponse response = new BulkWriterResponse();
      response.addSuccess(bulkWriterMessage.getId());
      onFlush(sensorType, response);
    } else {
      messages.add(bulkWriterMessage);
      applyShouldFlush(sensorType, bulkMessageWriter, configurations, sensorMessageCache.get(sensorType));
    }
  }

  /**
   * Flushes a batch for a sensor type by writing messages with the supplied {@link org.apache.metron.common.writer.BulkMessageWriter}.
   * Ensures all message ids in a batch are included in the response. After messages are written the cache is cleared and
   * flush policies are reset for that sensor type.
   * @param sensorType sensor type
   * @param bulkMessageWriter writer that will do the actual writing
   * @param configurations writer configurations
   * @param messages messages to be written
   */
  protected void flush( String sensorType
                    , BulkMessageWriter<MESSAGE_T> bulkMessageWriter
                    , WriterConfiguration configurations
                    , List<BulkWriterMessage<MESSAGE_T>> messages
                    )
  {
    long startTime = System.currentTimeMillis(); //no need to mock, so use real time
    BulkWriterResponse response = new BulkWriterResponse();

    Collection<MessageId> ids = messages.stream().map(BulkWriterMessage::getId).collect(Collectors.toList());
    try {
      response = bulkMessageWriter.write(sensorType, configurations, messages);

      // Make sure all ids are included in the BulkWriterResponse
      ids.removeAll(response.getSuccesses());
      response.getErrors().values().forEach(ids::removeAll);
      response.addAllSuccesses(ids);
    } catch (Throwable e) {
      response.addAllErrors(e, ids);
    } finally {
      onFlush(sensorType, response);
    }
    long endTime = System.currentTimeMillis();
    long elapsed = endTime - startTime;
    LOG.debug("Bulk batch for sensor {} wrote {} messages in ~{} ms", sensorType, messages.size(), elapsed);
  }

  /**
   * Flushes all queues older than their batchTimeouts.
   * @param bulkMessageWriter writer that will do the actual writing
   * @param configurations writer configurations
   */
  public void flushTimeouts(
            BulkMessageWriter<MESSAGE_T> bulkMessageWriter
          , WriterConfiguration configurations
          )
  {
    // No need to do "all" sensorTypes here, just the ones that have data batched up.
    for (String sensorType : new HashSet<>(sensorMessageCache.keySet())) {
      applyShouldFlush(sensorType, bulkMessageWriter, configurations, sensorMessageCache.get(sensorType));
    }
  }

  /**
   * Add a custom flush policy in addition to the default policies.
   * @param flushPolicy flush policy
   */
  public void addFlushPolicy(FlushPolicy flushPolicy) {
    this.flushPolicies.add(flushPolicy);
  }

  /**
   * Checks each flush policy to determine if a batch should be flushed.  A batch is flushed and the remaining policies
   * are skipped when a policy returns true.
   * @param sensorType sensor type
   * @param bulkMessageWriter writer that will do the actual writing
   * @param configurations writer configurations
   * @param messages messages to be written
   */
  private void applyShouldFlush(String sensorType
          , BulkMessageWriter<MESSAGE_T> bulkMessageWriter
          , WriterConfiguration configurations
          , List<BulkWriterMessage<MESSAGE_T>> messages) {
    if (messages.size() > 0) { // no need to flush empty batches
      for(FlushPolicy flushPolicy: flushPolicies) {
        if (flushPolicy.shouldFlush(sensorType, configurations, messages.size())) {
          flush(sensorType, bulkMessageWriter, configurations, messages);
          break;
        }
      }
    }
  }

  /**
   * Called after a batch is flushed.  The message cache is cleared and the {@link org.apache.metron.writer.FlushPolicy#onFlush(String, BulkWriterResponse)}
   * method is called for each flush policy.
   * @param sensorType sensor type
   * @param response response from a bulk write call
   */
  private void onFlush(String sensorType, BulkWriterResponse response) {
    sensorMessageCache.remove(sensorType);
    for(FlushPolicy flushPolicy: flushPolicies) {
      flushPolicy.onFlush(sensorType, response);
    }
  }
}
