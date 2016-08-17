#!/usr/bin/env python
"""
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at
    http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
"""

from resource_management.core.logger import Logger
from resource_management.core.resources.system import Execute
from resource_management.libraries.functions import format


# Wrap major operations and functionality in this class
class Commands:
    __params = None
    __indexing = None

    def __init__(self, params):
        if params is None:
            raise ValueError("params argument is required for initialization")
        self.__params = params
        self.__indexing = params.metron_indexing_topology

    def setup_repo(self):
        def local_repo():
            Logger.info("Setting up local repo")
            Execute("yum -y install createrepo")
            Execute("createrepo /localrepo")
            Execute("chmod -R o-w+r /localrepo")
            Execute("echo \"[METRON-0.2.0BETA]\n"
                    "name=Metron 0.2.0BETA packages\n"
                    "baseurl=file:///localrepo\n"
                    "gpgcheck=0\n"
                    "enabled=1\" > /etc/yum.repos.d/local.repo")

        def remote_repo():
            print('Using remote repo')

        yum_repo_types = {
            'local': local_repo,
            'remote': remote_repo
        }
        repo_type = self.__params.yum_repo_type
        if repo_type in yum_repo_types:
            yum_repo_types[repo_type]()
        else:
            raise ValueError("Unsupported repo type '{}'".format(repo_type))

    def init_kafka_topics(self):
        Logger.info('Creating Kafka topics')
        command_template = """{}/kafka-topics.sh \
                                --zookeeper {} \
                                --create \
                                --if-not-exists \
                                --topic {} \
                                --partitions {} \
                                --replication-factor {} \
                                --config retention.bytes={}"""
        num_partitions = 1
        replication_factor = 1
        retention_gigabytes = self.__params.metron_indexing_topic_retention
        retention_bytes = retention_gigabytes * 1024 * 1024 * 1024
        Logger.info("Creating topics for indexing")

        Logger.info("STACK HOME DIR: " + self.__params.stack_root)
        Logger.info("KAFKA HOME DIR: " + self.__params.kafka_home)
        Logger.info("KAFKA BIN DIR: " + self.__params.kafka_bin_dir)

        Logger.info("Creating topic'{}'".format(self.__indexing))
        Execute(command_template.format(self.__params.kafka_bin_dir,
                                        self.__params.zookeeper_quorum,
                                        self.__indexing,
                                        num_partitions,
                                        replication_factor,
                                        retention_bytes))
        #TODO Error Handling topologies
        # Logger.info("Creating topics for error handling")
        # Execute(command_template.format(self.__params.hadoop_home_dir,
        #                                 self.__params.zookeeper_quorum,
        #                                 "parser_invalid",
        #                                 num_partitions,
        #                                 replication_factor,
        #                                 retention_bytes))
        # Execute(command_template.format(self.__params.hadoop_home_dir,
        #                                 self.__params.zookeeper_quorum,
        #                                 "parser_error",
        #                                 num_partitions, replication_factor,
        #                                 retention_bytes))
        Logger.info("Done creating Kafka topics")

    def init_indexing_config(self):
        Logger.info('Loading indexing config into ZooKeeper')
        Execute(format(
            "{metron_home}/bin/zk_load_configs.sh --mode PUSH -i {metron_zookeeper_config_path} -z {zookeeper_quorum}"))

    def start_indexing_topology(self):
        Logger.info("Starting Metron indexing topology: {}".format(self.__indexing))
        start_cmd_template = """{}/bin/start_indexing_topology.sh \
                                    -s {} \
                                    -z {}"""
        Logger.info('Starting ' + self.__indexing)
        Execute(start_cmd_template.format(self.__params.metron_home, self.__indexing, self.__params.zookeeper_quorum))

        Logger.info('Finished starting indexing topology')

    def stop_indexing_topology(self):
        Logger.info('Stopping ' + self.__indexing)
        stop_cmd = 'storm kill ' + self.__indexing
        Execute(stop_cmd)
        Logger.info('Done stopping indexing topologies')

    def restart_indexing_topology(self):
        Logger.info('Restarting the indexing topologies')
        self.stop_indexing_topology()
        self.start_indexing_topology()
        Logger.info('Done restarting the indexing topologies')
