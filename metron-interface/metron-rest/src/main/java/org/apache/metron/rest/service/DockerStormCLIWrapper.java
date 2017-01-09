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
package org.apache.metron.rest.service;

import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;

public class DockerStormCLIWrapper extends StormCLIWrapper {

  private Logger LOG = LoggerFactory.getLogger(DockerStormCLIWrapper.class);

  @Autowired
  private Environment environment;

  @Override
  protected ProcessBuilder getProcessBuilder(String... command) {
    String[] dockerCommand = {"docker-compose", "-f", environment.getProperty("docker.compose.path"), "-p", "metron", "exec", "storm"};
    ProcessBuilder pb = new ProcessBuilder(ArrayUtils.addAll(dockerCommand, command));
    Map<String, String> pbEnvironment = pb.environment();
    pbEnvironment.put("METRON_VERSION", environment.getProperty("metron.version"));
    setDockerEnvironment(pbEnvironment);
    return pb;
  }

  protected void setDockerEnvironment(Map<String, String> environmentVariables) {
    ProcessBuilder pb = getDockerEnvironmentProcessBuilder();
    try {
      Process process = pb.start();
      BufferedReader inputStream = new BufferedReader(new InputStreamReader(process.getInputStream()));
      String line;
      while((line = inputStream.readLine()) != null) {
        if (line.startsWith("export")) {
          String[] parts = line.replaceFirst("export ", "").split("=");
          environmentVariables.put(parts[0], parts[1].replaceAll("\"", ""));
        }
      }
      process.waitFor();
    } catch (IOException | InterruptedException e) {
      LOG.error(e.getMessage(), e);
    }
  }

  protected ProcessBuilder getDockerEnvironmentProcessBuilder() {
    String[] command = {"docker-machine", "env", "metron-machine"};
    return new ProcessBuilder(command);
  }
}
