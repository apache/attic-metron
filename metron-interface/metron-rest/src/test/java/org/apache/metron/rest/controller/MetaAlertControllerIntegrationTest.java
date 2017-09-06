/*
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

package org.apache.metron.rest.controller;

import static org.apache.metron.rest.MetronRestConstants.TEST_PROFILE;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.google.common.collect.ImmutableMap;
import org.adrianwalker.multilinestring.Multiline;
import org.apache.curator.framework.CuratorFramework;
import org.apache.metron.indexing.dao.MetaAlertDao;
import org.apache.metron.indexing.dao.SearchIntegrationTest;
import org.apache.metron.rest.service.MetaAlertService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles(TEST_PROFILE)
public class MetaAlertControllerIntegrationTest extends DaoControllerTest {

  @Autowired
  private MetaAlertService metaAlertService;
  @Autowired
  public CuratorFramework client;

  @Autowired
  private WebApplicationContext wac;

  private MockMvc mockMvc;

  private String metaalertUrl = "/api/v1/metaalert";
  private String user = "user";
  private String password = "password";

  /**
   {
     "guidToIndices" : {
       "bro_1":"bro_index_2017.01.01.01",
       "snort_2":"snort_index_2017.01.01.01"
     },
     "groups" : ["group_one", "group_two"]
   }
   */
  @Multiline
  public static String create;

  @Before
  public void setup() throws Exception {
    this.mockMvc = MockMvcBuilders.webAppContextSetup(this.wac).apply(springSecurity()).build();
    ImmutableMap<String, String> testData = ImmutableMap.of(
        "bro_index_2017.01.01.01", SearchIntegrationTest.broData,
        "snort_index_2017.01.01.01", SearchIntegrationTest.snortData,
        MetaAlertDao.METAALERTS_INDEX, SearchIntegrationTest.metaAlertData
    );
    loadTestData(testData);
  }

  @Test
  public void test() throws Exception {
    // Testing searching by alert
    // Test no meta alert
    String guid = "missing_1";
    ResultActions result = this.mockMvc.perform(
        post(metaalertUrl + "/searchByAlert")
            .with(httpBasic(user, password)).with(csrf())
            .contentType(MediaType.parseMediaType("text/plain;charset=UTF-8"))
            .content(guid));
    result.andExpect(status().isOk())
        .andExpect(
            content().contentType(MediaType.parseMediaType("application/json;charset=UTF-8")))
        .andExpect(jsonPath("$.total").value(0));

    // Test single meta alert
    guid = "snort_1";
    result = this.mockMvc.perform(
        post(metaalertUrl + "/searchByAlert")
            .with(httpBasic(user, password)).with(csrf())
            .contentType(MediaType.parseMediaType("text/plain;charset=UTF-8"))
            .content(guid));
    result.andExpect(status().isOk())
        .andExpect(
            content().contentType(MediaType.parseMediaType("application/json;charset=UTF-8")))
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.results[0].source.guid").value("meta_2"))
        .andExpect(jsonPath("$.results[0].source.count").value(3.0));

    // Test multiple meta alerts
    guid = "bro_1";
    result = this.mockMvc.perform(
        post(metaalertUrl + "/searchByAlert")
            .with(httpBasic(user, password)).with(csrf())
            .contentType(MediaType.parseMediaType("text/plain;charset=UTF-8"))
            .content(guid));
    result.andExpect(status().isOk())
        .andExpect(
            content().contentType(MediaType.parseMediaType("application/json;charset=UTF-8")))
        .andExpect(jsonPath("$.total").value(2))
        .andExpect(jsonPath("$.results[0].source.guid").value("meta_2"))
        .andExpect(jsonPath("$.results[0].source.count").value(3.0))
        .andExpect(jsonPath("$.results[1].source.guid").value("meta_1"))
        .andExpect(jsonPath("$.results[1].source.count").value(1.0));

    result = this.mockMvc.perform(
        post(metaalertUrl + "/create")
            .with(httpBasic(user, password)).with(csrf())
            .contentType(MediaType.parseMediaType("application/json;charset=UTF-8"))
            .content(create));
    result.andExpect(status().isOk());

    // Test that we can find the newly created meta alert by the sub alerts
    guid = "bro_1";
    result = this.mockMvc.perform(
        post(metaalertUrl + "/searchByAlert")
            .with(httpBasic(user, password)).with(csrf())
            .contentType(MediaType.parseMediaType("text/plain;charset=UTF-8"))
            .content(guid));
    result.andExpect(status().isOk())
        .andExpect(
            content().contentType(MediaType.parseMediaType("application/json;charset=UTF-8")))
        .andExpect(jsonPath("$.total").value(3))
        .andExpect(jsonPath("$.results[0].source.guid").value("meta_3"))
        .andExpect(jsonPath("$.results[0].source.count").value(2.0))
        .andExpect(jsonPath("$.results[1].source.guid").value("meta_2"))
        .andExpect(jsonPath("$.results[1].source.count").value(3.0))
        .andExpect(jsonPath("$.results[2].source.guid").value("meta_1"))
        .andExpect(jsonPath("$.results[2].source.count").value(1.0));

    guid = "snort_2";
    result = this.mockMvc.perform(
        post(metaalertUrl + "/searchByAlert")
            .with(httpBasic(user, password)).with(csrf())
            .contentType(MediaType.parseMediaType("text/plain;charset=UTF-8"))
            .content(guid));
    result.andExpect(status().isOk())
        .andExpect(
            content().contentType(MediaType.parseMediaType("application/json;charset=UTF-8")))
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.results[0].source.guid").value("meta_3"))
        .andExpect(jsonPath("$.results[0].source.count").value(2.0));
  }
}
