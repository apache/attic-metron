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

import org.apache.metron.common.configuration.writer.WriterConfiguration;
import org.apache.metron.common.field.FieldNameConverter;

/**
 * A factory that creates {@link FieldNameConverter} objects.
 *
 * <p>The {@link WriterConfiguration} allows a user to define the {@link FieldNameConverter}
 * that should be used.
 *
 * <p>Each sensor type can use a different {@link FieldNameConverter} implementation.
 *
 * <p>The user can change the {@link FieldNameConverter} in use at runtime.
 */
public interface FieldNameConverterFactory {

  /**
   * Create a {@link FieldNameConverter} object.
   *
   * @param sensorType The type of sensor.
   * @param config The writer configuration.
   * @return A {@link FieldNameConverter} object.
   */
  FieldNameConverter create(String sensorType, WriterConfiguration config);
}
