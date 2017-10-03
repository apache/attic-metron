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
package org.apache.metron.elasticsearch.utils;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import org.apache.metron.common.configuration.writer.WriterConfiguration;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ElasticsearchUtils {

  private static ThreadLocal<Map<String, SimpleDateFormat>> DATE_FORMAT_CACHE
          = ThreadLocal.withInitial(() -> new HashMap<>());

  public static SimpleDateFormat getIndexFormat(WriterConfiguration configurations) {
    return getIndexFormat(configurations.getGlobalConfig());
  }

  public static SimpleDateFormat getIndexFormat(Map<String, Object> globalConfig) {
    String format = (String) globalConfig.get("es.date.format");
    return DATE_FORMAT_CACHE.get().computeIfAbsent(format, SimpleDateFormat::new);
  }

  /**
   * Builds the name of an Elasticsearch index.
   * @param sensorType The sensor type; bro, yaf, snort, ...
   * @param indexPostfix The index postfix; most often a formatted date.
   * @param configurations User-defined configuration for the writers.
   */
  public static String getIndexName(String sensorType, String indexPostfix, WriterConfiguration configurations) {
    String indexName = sensorType;
    if (configurations != null) {
      indexName = configurations.getIndex(sensorType);
    }
    indexName = indexName + getIndexDelimiter() + "_" + indexPostfix;
    return indexName;
  }

  /**
   * Returns the delimiter that is appended to the user-defined index name to separate
   * the index's date postfix.  For example, if the user-defined index name is 'bro'
   * and the delimiter is '_index_', then one likely index name is 'bro_index_2017.10.03.19'.
   */
  public static String getIndexDelimiter() {
    return "_index";
  }

  public static TransportClient getClient(Map<String, Object> globalConfiguration, Map<String, String> optionalSettings) {
    Settings.Builder settingsBuilder = Settings.settingsBuilder();
    settingsBuilder.put("cluster.name", globalConfiguration.get("es.clustername"));
    settingsBuilder.put("client.transport.ping_timeout","500s");
    if (optionalSettings != null) {
      settingsBuilder.put(optionalSettings);
    }
    Settings settings = settingsBuilder.build();
    TransportClient client;
    try{
      client = TransportClient.builder().settings(settings).build();
      for(HostnamePort hp : getIps(globalConfiguration)) {
        client.addTransportAddress(
                new InetSocketTransportAddress(InetAddress.getByName(hp.hostname), hp.port)
        );
      }
    } catch (UnknownHostException exception){
      throw new RuntimeException(exception);
    }
    return client;
  }

  public static class HostnamePort {
    String hostname;
    Integer port;
    public HostnamePort(String hostname, Integer port) {
      this.hostname = hostname;
      this.port = port;
    }
  }

  protected static List<HostnamePort> getIps(Map<String, Object> globalConfiguration) {
    Object ipObj = globalConfiguration.get("es.ip");
    Object portObj = globalConfiguration.get("es.port");
    if(ipObj == null) {
      return Collections.emptyList();
    }
    if(ipObj instanceof String
            && ipObj.toString().contains(",") && ipObj.toString().contains(":")){
      List<String> ips = Arrays.asList(((String)ipObj).split(","));
      List<HostnamePort> ret = new ArrayList<>();
      for(String ip : ips) {
        Iterable<String> tokens = Splitter.on(":").split(ip);
        String host = Iterables.getFirst(tokens, null);
        String portStr = Iterables.getLast(tokens, null);
        ret.add(new HostnamePort(host, Integer.parseInt(portStr)));
      }
      return ret;
    }else if(ipObj instanceof String
            && ipObj.toString().contains(",")){
      List<String> ips = Arrays.asList(((String)ipObj).split(","));
      List<HostnamePort> ret = new ArrayList<>();
      for(String ip : ips) {
        ret.add(new HostnamePort(ip, Integer.parseInt(portObj + "")));
      }
      return ret;
    }else if(ipObj instanceof String
            && !ipObj.toString().contains(":")
            ) {
      return ImmutableList.of(new HostnamePort(ipObj.toString(), Integer.parseInt(portObj + "")));
    }
    else if(ipObj instanceof String
            && ipObj.toString().contains(":")
            ) {
      Iterable<String> tokens = Splitter.on(":").split(ipObj.toString());
      String host = Iterables.getFirst(tokens, null);
      String portStr = Iterables.getLast(tokens, null);
      return ImmutableList.of(new HostnamePort(host, Integer.parseInt(portStr)));
    }
    else if(ipObj instanceof List) {
      List<String> ips = (List)ipObj;
      List<HostnamePort> ret = new ArrayList<>();
      for(String ip : ips) {
        Iterable<String> tokens = Splitter.on(":").split(ip);
        String host = Iterables.getFirst(tokens, null);
        String portStr = Iterables.getLast(tokens, null);
        ret.add(new HostnamePort(host, Integer.parseInt(portStr)));
      }
      return ret;
    }
    throw new IllegalStateException("Unable to read the elasticsearch ips, expected es.ip to be either a list of strings, a string hostname or a host:port string");
  }
}
