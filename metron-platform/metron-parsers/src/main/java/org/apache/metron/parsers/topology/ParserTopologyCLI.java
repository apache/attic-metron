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
package org.apache.metron.parsers.topology;

import org.apache.metron.common.Constants;
import org.apache.metron.parsers.topology.config.ValueSupplier;
import org.apache.metron.storm.kafka.flux.SpoutConfiguration;
import org.apache.storm.Config;
import org.apache.storm.LocalCluster;
import org.apache.storm.StormSubmitter;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.utils.Utils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Joiner;
import org.apache.commons.cli.*;
import org.apache.commons.io.FileUtils;
import org.apache.metron.common.utils.JSONUtils;
import org.apache.metron.parsers.topology.config.Arg;
import org.apache.metron.parsers.topology.config.ConfigHandlers;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;

public class ParserTopologyCLI {

  public enum ParserOptions {
    HELP("h", code -> {
      Option o = new Option(code, "help", false, "This screen");
      o.setRequired(false);
      return o;
    }),
    ZK_QUORUM("z", code -> {
      Option o = new Option(code, "zk", true, "Zookeeper Quroum URL (zk1:2181,zk2:2181,...");
      o.setArgName("ZK_QUORUM");
      o.setRequired(true);
      return o;
    }),
    BROKER_URL("k", code -> {
      Option o = new Option(code, "kafka", true, "Kafka Broker URL");
      o.setArgName("BROKER_URL");
      o.setRequired(false);
      return o;
    }),
    SENSOR_TYPE("s", code -> {
      Option o = new Option(code, "sensor", true, "Sensor Type");
      o.setArgName("SENSOR_TYPE");
      o.setRequired(true);
      return o;
    }),
    SPOUT_PARALLELISM("sp", code -> {
      Option o = new Option(code, "spout_p", true, "Spout Parallelism Hint");
      o.setArgName("SPOUT_PARALLELISM_HINT");
      o.setRequired(false);
      o.setType(Number.class);
      return o;
    }),
    PARSER_PARALLELISM("pp", code -> {
      Option o = new Option(code, "parser_p", true, "Parser Parallelism Hint");
      o.setArgName("PARALLELISM_HINT");
      o.setRequired(false);
      o.setType(Number.class);
      return o;
    }),
    INVALID_WRITER_PARALLELISM("iwp", code -> {
      Option o = new Option(code, "invalid_writer_p", true, "Invalid Message Writer Parallelism Hint");
      o.setArgName("PARALLELISM_HINT");
      o.setRequired(false);
      o.setType(Number.class);
      return o;
    }),
    ERROR_WRITER_PARALLELISM("ewp", code -> {
      Option o = new Option(code, "error_writer_p", true, "Error Writer Parallelism Hint");
      o.setArgName("PARALLELISM_HINT");
      o.setRequired(false);
      o.setType(Number.class);
      return o;
    }),
    SPOUT_NUM_TASKS("snt", code -> {
      Option o = new Option(code, "spout_num_tasks", true, "Spout Num Tasks");
      o.setArgName("NUM_TASKS");
      o.setRequired(false);
      o.setType(Number.class);
      return o;
    }),
    PARSER_NUM_TASKS("pnt", code -> {
      Option o = new Option(code, "parser_num_tasks", true, "Parser Num Tasks");
      o.setArgName("NUM_TASKS");
      o.setRequired(false);
      o.setType(Number.class);
      return o;
    }),
    INVALID_WRITER_NUM_TASKS("iwnt", code -> {
      Option o = new Option(code, "invalid_writer_num_tasks", true, "Invalid Writer Num Tasks");
      o.setArgName("NUM_TASKS");
      o.setRequired(false);
      o.setType(Number.class);
      return o;
    }),
    ERROR_WRITER_NUM_TASKS("ewnt", code -> {
      Option o = new Option(code, "error_writer_num_tasks", true, "Error Writer Num Tasks");
      o.setArgName("NUM_TASKS");
      o.setRequired(false);
      o.setType(Number.class);
      return o;
    }),
    NUM_WORKERS("nw", code -> {
      Option o = new Option(code, "num_workers", true, "Number of Workers");
      o.setArgName("NUM_WORKERS");
      o.setRequired(false);
      o.setType(Number.class);
      return o;
      }, new ConfigHandlers.SetNumWorkersHandler()
    )
    ,NUM_ACKERS("na", code -> {
      Option o = new Option(code, "num_ackers", true, "Number of Ackers");
      o.setArgName("NUM_ACKERS");
      o.setRequired(false);
      o.setType(Number.class);
      return o;
    }, new ConfigHandlers.SetNumAckersHandler()
    )
    ,NUM_MAX_TASK_PARALLELISM("mtp", code -> {
      Option o = new Option(code, "max_task_parallelism", true, "Max task parallelism");
      o.setArgName("MAX_TASK");
      o.setRequired(false);
      o.setType(Number.class);
      return o;
    }, new ConfigHandlers.SetMaxTaskParallelismHandler()
    )
    ,MESSAGE_TIMEOUT("mt", code -> {
      Option o = new Option(code, "message_timeout", true, "Message Timeout in Seconds");
      o.setArgName("TIMEOUT_IN_SECS");
      o.setRequired(false);
      o.setType(Number.class);
      return o;
    }, new ConfigHandlers.SetMessageTimeoutHandler()
    )
    ,EXTRA_OPTIONS("e", code -> {
      Option o = new Option(code, "extra_topology_options", true
                           , "Extra options in the form of a JSON file with a map for content." +
                             "  Available options are those in the Kafka Consumer Configs at http://kafka.apache.org/0100/documentation.html#newconsumerconfigs" +
                             " and " + Joiner.on(",").join(SpoutConfiguration.allOptions())
                           );
      o.setArgName("JSON_FILE");
      o.setRequired(false);
      o.setType(String.class);
      return o;
    }, new ConfigHandlers.LoadJSONHandler()
    )
    ,SPOUT_CONFIG("esc", code -> {
      Option o = new Option(code
                           , "extra_kafka_spout_config"
                           , true
                           , "Extra spout config options in the form of a JSON file with a map for content."
                           );
      o.setArgName("JSON_FILE");
      o.setRequired(false);
      o.setType(String.class);
      return o;
    }
    )
    ,SECURITY_PROTOCOL("ksp", code -> {
      Option o = new Option(code
                           , "kafka_security_protocol"
                           , true
                           , "The kafka security protocol to use (if running with a kerberized cluster).  E.g. PLAINTEXTSASL"
                           );
      o.setArgName("SECURITY_PROTOCOL");
      o.setRequired(false);
      o.setType(String.class);
      return o;
    }
    )
    ,OUTPUT_TOPIC("ot", code -> {
      Option o = new Option(code
                           , "output_topic"
                           , true
                           , "The output kafka topic for the parser.  If unset, the default is " + Constants.ENRICHMENT_TOPIC
                           );
      o.setArgName("KAFKA_TOPIC");
      o.setRequired(false);
      o.setType(String.class);
      return o;
    }
    )
    ,TEST("t", code ->
    {
      Option o = new Option("t", "test", true, "Run in Test Mode");
      o.setArgName("TEST");
      o.setRequired(false);
      return o;
    })
    ;
    Option option;
    String shortCode;
    Function<Arg, Config> configHandler;
    ParserOptions(String shortCode
                 , Function<String, Option> optionHandler
                 ) {
      this(shortCode, optionHandler, arg -> arg.getConfig());
                 }
    ParserOptions(String shortCode
                 , Function<String, Option> optionHandler
                 , Function<Arg, Config> configHandler
                 ) {
      this.shortCode = shortCode;
      this.option = optionHandler.apply(shortCode);
      this.configHandler = configHandler;
    }

    public boolean has(CommandLine cli) {
      return cli.hasOption(shortCode);
    }

    public String get(CommandLine cli) {
      return cli.getOptionValue(shortCode);
    }
    public String get(CommandLine cli, String def) {
      return has(cli)?cli.getOptionValue(shortCode):def;
    }

    public static Optional<Config> getConfig(CommandLine cli) {
      Config config = new Config();
      for(ParserOptions option : ParserOptions.values()) {
        config = option.configHandler.apply(new Arg(config, option.get(cli)));
      }
      return config.isEmpty()?Optional.empty():Optional.of(config);
    }

    public static CommandLine parse(CommandLineParser parser, String[] args) throws ParseException {
      try {
        CommandLine cli = parser.parse(getOptions(), args);
        if(HELP.has(cli)) {
          printHelp();
          System.exit(0);
        }
        return cli;
      } catch (ParseException e) {
        System.err.println("Unable to parse args: " + Joiner.on(' ').join(args));
        e.printStackTrace(System.err);
        printHelp();
        throw e;
      }
    }

    public static void printHelp() {
      HelpFormatter formatter = new HelpFormatter();
      formatter.printHelp( "ParserTopologyCLI", getOptions());
    }

    public static Options getOptions() {
      Options ret = new Options();
      for(ParserOptions o : ParserOptions.values()) {
        ret.addOption(o.option);
      }
      return ret;
    }
  }

  private static CommandLine parse(Options options, String[] args) {
    CommandLineParser parser = new PosixParser();
    try {
      return ParserOptions.parse(parser, args);
    } catch (ParseException pe) {
      pe.printStackTrace();
      final HelpFormatter usageFormatter = new HelpFormatter();
      usageFormatter.printHelp("ParserTopologyCLI", null, options, null, true);
      System.exit(-1);
      return null;
    }
  }

  public static void main(String[] args) {

    try {
      Options options = new Options();
      final CommandLine cmd = parse(options, args);
      if (cmd.hasOption("h")) {
        final HelpFormatter usageFormatter = new HelpFormatter();
        usageFormatter.printHelp("ParserTopologyCLI", null, options, null, true);
        System.exit(0);
      }
      String zookeeperUrl = ParserOptions.ZK_QUORUM.get(cmd);;
      Optional<String> brokerUrl = ParserOptions.BROKER_URL.has(cmd)?Optional.of(ParserOptions.BROKER_URL.get(cmd)):Optional.empty();
      String sensorType= ParserOptions.SENSOR_TYPE.get(cmd);
      ValueSupplier<Integer> spoutParallelism = (parserConfig, clazz) -> {
        if(ParserOptions.SPOUT_PARALLELISM.has(cmd)) {
          return Integer.parseInt(ParserOptions.SPOUT_PARALLELISM.get(cmd, "1"));
        }
        return Optional.ofNullable(parserConfig.getSpoutParallelism()).orElse(1);
      };
      ValueSupplier<Integer> spoutNumTasks = (parserConfig, clazz) -> {
        if(ParserOptions.SPOUT_NUM_TASKS.has(cmd)) {
          return Integer.parseInt(ParserOptions.SPOUT_NUM_TASKS.get(cmd, "1"));
        }
        return Optional.ofNullable(parserConfig.getSpoutNumTasks()).orElse(1);
      };
      ValueSupplier<Integer> parserParallelism = (parserConfig, clazz) -> {
        if(ParserOptions.PARSER_PARALLELISM.has(cmd)) {
          return Integer.parseInt(ParserOptions.PARSER_PARALLELISM.get(cmd, "1"));
        }
        return Optional.ofNullable(parserConfig.getParserParallelism()).orElse(1);
      };

      ValueSupplier<Integer> parserNumTasks = (parserConfig, clazz) -> {
        if(ParserOptions.PARSER_NUM_TASKS.has(cmd)) {
          return Integer.parseInt(ParserOptions.PARSER_NUM_TASKS.get(cmd, "1"));
        }
        return Optional.ofNullable(parserConfig.getParserNumTasks()).orElse(1);
      };

      ValueSupplier<Integer> errorParallelism = (parserConfig, clazz) -> {
        if(ParserOptions.ERROR_WRITER_PARALLELISM.has(cmd)) {
          return Integer.parseInt(ParserOptions.ERROR_WRITER_PARALLELISM.get(cmd, "1"));
        }
        return Optional.ofNullable(parserConfig.getErrorWriterParallelism()).orElse(1);
      };

      ValueSupplier<Integer> errorNumTasks = (parserConfig, clazz) -> {
        if(ParserOptions.ERROR_WRITER_NUM_TASKS.has(cmd)) {
          return Integer.parseInt(ParserOptions.ERROR_WRITER_NUM_TASKS.get(cmd, "1"));
        }
        return Optional.ofNullable(parserConfig.getErrorWriterNumTasks()).orElse(1);
      };

      ValueSupplier<Map> spoutConfig = (parserConfig, clazz) -> {
        if(ParserOptions.SPOUT_CONFIG.has(cmd)) {
          return readSpoutConfig(new File(ParserOptions.SPOUT_CONFIG.get(cmd)));
        }
        return Optional.ofNullable(parserConfig.getSpoutConfig()).orElse(new HashMap<>());
      };

      ValueSupplier<String> securityProtocol = (parserConfig, clazz) -> {
        Optional<String> sp = Optional.empty();
        if (ParserOptions.SECURITY_PROTOCOL.has(cmd)) {
          sp = Optional.of(ParserOptions.SECURITY_PROTOCOL.get(cmd));
        }
        if (!sp.isPresent()) {
          sp = getSecurityProtocol(sp, spoutConfig.get(parserConfig, Map.class));
        }
        return sp.orElse(Optional.ofNullable(parserConfig.getSecurityProtocol()).orElse(null));
      };

      ValueSupplier<Config> stormConf = (parserConfig, clazz) -> {
        Optional<Config> ret = ParserOptions.getConfig(cmd);
        if(ret.isPresent()) {
          return ret.get();
        }
        Map<String, Object> c = parserConfig.getStormConfig();
        if(c.isEmpty()) {
          return new Config();
        }
        else {
          Config finalConfig = new Config();
          finalConfig.putAll(c);
          return finalConfig;
        }
      };

      Optional<String> outputTopic = ParserOptions.OUTPUT_TOPIC.has(cmd)?Optional.of(ParserOptions.OUTPUT_TOPIC.get(cmd)):Optional.empty();
      ParserTopologyBuilder.ParserTopology topology = ParserTopologyBuilder.build(zookeeperUrl,
              brokerUrl,
              sensorType,
              spoutParallelism,
              spoutNumTasks,
              parserParallelism,
              parserNumTasks,
              errorParallelism,
              errorNumTasks,
              spoutConfig,
              securityProtocol,
              outputTopic,
              stormConf
      );
      if (ParserOptions.TEST.has(cmd)) {
        topology.getTopologyConfig().put(Config.TOPOLOGY_DEBUG, true);
        LocalCluster cluster = new LocalCluster();
        cluster.submitTopology(sensorType, topology.getTopologyConfig(), topology.getBuilder().createTopology());
        Utils.sleep(300000);
        cluster.shutdown();
      } else {
        StormSubmitter.submitTopology(sensorType, topology.getTopologyConfig(), topology.getBuilder().createTopology());
      }
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(-1);
    }
  }

  private static Optional<String> getSecurityProtocol(Optional<String> protocol, Map<String, Object> spoutConfig) {
    Optional<String> ret = protocol;
    if(ret.isPresent() && protocol.get().equalsIgnoreCase("PLAINTEXT")) {
      ret = Optional.empty();
    }
    if(!ret.isPresent()) {
      ret = Optional.ofNullable((String) spoutConfig.get("security.protocol"));
    }
    if(ret.isPresent() && protocol.get().equalsIgnoreCase("PLAINTEXT")) {
      ret = Optional.empty();
    }
    return ret;
  }

  private static Map<String, Object> readSpoutConfig(File inputFile) {
    String json = null;
    if (inputFile.exists()) {
      try {
        json = FileUtils.readFileToString(inputFile);
      } catch (IOException e) {
        throw new IllegalStateException("Unable to process JSON file " + inputFile, e);
      }
    }
    else {
      throw new IllegalArgumentException("Unable to load JSON file at " + inputFile.getAbsolutePath());
    }
    try {
      return JSONUtils.INSTANCE.load(json, new TypeReference<Map<String, Object>>() {
      });
    } catch (IOException e) {
      throw new IllegalStateException("Unable to process JSON.", e);
    }
  }
}
