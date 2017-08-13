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

package org.apache.metron.profiler;

import org.apache.metron.common.configuration.profiler.ProfilerConfig;
import org.apache.metron.stellar.dsl.Context;
import org.json.simple.JSONObject;

import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * A stand alone version of the Profiler that does not require a
 * distributed execution environment like Apache Storm.
 */
public class StandAloneProfiler {

  /**
   * The Stellar execution context.
   */
  private Context context;

  /**
   * The configuration for the Profiler.
   */
  private ProfilerConfig config;

  /**
   * The message router.
   */
  private MessageRouter router;

  /**
   * The message distributor.
   */
  private MessageDistributor distributor;

  public StandAloneProfiler(ProfilerConfig config, long periodDurationMillis, Context context) {
    // TODO does this even need to be configurable?
    long profileTimeToLiveMillis = periodDurationMillis * 3;

    this.context = context;
    this.config = config;
    this.router = new DefaultMessageRouter(context);
    this.distributor = new DefaultMessageDistributor(periodDurationMillis, profileTimeToLiveMillis);
  }

  /**
   * Apply a message to a set of profiles.
   * @param message The message to apply.
   * @throws ExecutionException
   */
  public void apply(JSONObject message) throws ExecutionException {

    List<MessageRoute> routes = router.route(message, config, context);
    for(MessageRoute route : routes) {
      distributor.distribute(message, route, context);
    }
  }

  /**
   * Flush the set of profiles.
   * @return A ProfileMeasurement for each (Profile, Entity) pair.
   */
  public List<ProfileMeasurement> flush() {
    return distributor.flush();
  }

  public ProfilerConfig getConfig() {
    return config;
  }
}
