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
package org.apache.metron.elasticsearch.writer;

import com.google.common.collect.Lists;
import org.apache.metron.common.Constants;
import org.apache.metron.common.configuration.writer.WriterConfiguration;
import org.apache.metron.common.field.FieldNameConverter;
import org.apache.metron.common.field.FieldNameConverters;
import org.apache.metron.common.writer.BulkMessageWriter;
import org.apache.metron.common.writer.BulkWriterResponse;
import org.apache.metron.elasticsearch.bulk.BulkDocumentWriter;
import org.apache.metron.elasticsearch.client.ElasticsearchClient;
import org.apache.metron.elasticsearch.utils.ElasticsearchUtils;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.tuple.Tuple;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.lang.String.format;

/**
 * A {@link BulkMessageWriter} that writes messages to Elasticsearch.
 */
public class ElasticsearchWriter implements BulkMessageWriter<JSONObject>, Serializable {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  /**
   * The Elasticsearch client.
   */
  private transient ElasticsearchClient client;

  private BulkDocumentWriter<TupleBasedDocument> documentWriter;

  /**
   * A simple data formatter used to build the appropriate Elasticsearch index name.
   */
  private SimpleDateFormat dateFormat;

  @Override
  public void init(Map stormConf, TopologyContext topologyContext, WriterConfiguration configurations) {

    // TODO define the documentWriter?  Or initialize if none?  Need to pass in the client?

    Map<String, Object> globalConfiguration = configurations.getGlobalConfig();
    client = ElasticsearchUtils.getClient(globalConfiguration);
    dateFormat = ElasticsearchUtils.getIndexFormat(globalConfiguration);
  }

  @Override
  public BulkWriterResponse write(String sensorType,
                                  WriterConfiguration configurations,
                                  Iterable<Tuple> tuplesIter,
                                  List<JSONObject> messages) {

    // fetch the field name converter for this sensor type
    FieldNameConverter fieldNameConverter = FieldNameConverters.create(sensorType, configurations);
    String indexPostfix = dateFormat.format(new Date());
    String indexName = ElasticsearchUtils.getIndexName(sensorType, indexPostfix, configurations);

    // the number of tuples must match the number of messages
    List<Tuple> tuples = Lists.newArrayList(tuplesIter);
    int batchSize = tuples.size();
    if(messages.size() != batchSize) {
      throw new IllegalStateException(format("Expect same number of tuples and messages; |tuples|=%d, |messages|=%d",
              tuples.size(), messages.size()));
    }
    LOG.debug("About to write {} document(s) to Elasticsearch", batchSize);

    // create a document from each message
    List<TupleBasedDocument> documents = new ArrayList<>();
    for(int i=0; i<tuples.size(); i++) {
      JSONObject message = messages.get(i);
      Tuple tuple = tuples.get(i);

      // transform the message fields to the source fields of the indexed document
      JSONObject source = new JSONObject();
      for(Object k : message.keySet()){
        copyField(k.toString(), message, source, fieldNameConverter);
      }

      // define the unique document id
      String guid = String.class.cast(source.get(Constants.GUID));
      if(guid == null) {
        throw new IllegalStateException("What to do if no GUID?");
      }

      // define the document timestamp
      // TODO what if the timestamp is a string?
      Long timestamp = Long.class.cast(source.get("timestamp"));
      if(timestamp == null) {
        throw new IllegalStateException("What to do if no timestamp?");
      }

      documents.add(new TupleBasedDocument(source, guid, sensorType, timestamp, tuple, Optional.of(indexName)));
    }

    // add successful tuples to the response
    BulkWriterResponse response = new BulkWriterResponse();
    documentWriter.onSuccess(docs -> {
      List<Tuple> successfulTuples = docs.stream().map(doc -> doc.getTuple()).collect(Collectors.toList());
      response.addAllSuccesses(successfulTuples);
    });

    // add any failed tuples to the response
    documentWriter.onFailure((failedDocument, cause, message) -> {
      Tuple failedTuple = failedDocument.getTuple();
      response.addError(cause, failedTuple);
    });

    // write the documents
    documentWriter.write(documents);
    return response;
  }

  @Override
  public String getName() {
    return "elasticsearch";
  }

  @Override
  public void close() throws Exception {
    client.close();
  }

  /**
   * Copies the value of a field from the source message to the destination message.
   *
   * <p>A field name may also be transformed in the destination message by the {@link FieldNameConverter}.
   *
   * @param sourceFieldName The name of the field to copy from the source message
   * @param source The source message.
   * @param destination The destination message.
   * @param fieldNameConverter The field name converter that transforms the field name
   *                           between the source and destination.
   */
  //JSONObject doesn't expose map generics
  @SuppressWarnings("unchecked")
  private void copyField(
          String sourceFieldName,
          JSONObject source,
          JSONObject destination,
          FieldNameConverter fieldNameConverter) {

    // allow the field name to be transformed
    String destinationFieldName = fieldNameConverter.convert(sourceFieldName);

    // copy the field
    destination.put(destinationFieldName, source.get(sourceFieldName));
  }
}

