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

package org.apache.metron.stellar.common.utils;

/**
 * Utility functions for working with numbers that represent different
 * formats of EPOCH time.
 */
public class EpochUtils {

  /**
   * Ensures returns the passed value as milliseconds from EPOCH if the value is in seconds.
   * This is done by looking at the number of digits.
   * If there are 10, then the value is concidered to be in seconds and will by
   * muliplited by 1000.
   * If there not 10, then the original value will be returned.
   *
   *
   * </p>
   * @param canidate The Long value to concider
   * @return A Long value
   */
  public static Long ensureEpochMillis(Long canidate) {
    int length = (int)Math.floor(Math.log10(canidate) + 1);
    return length == 10 ? canidate * 1000 : canidate;
  }

}
