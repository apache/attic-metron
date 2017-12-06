/// <reference path="../../matchers/custom-matchers.d.ts"/>
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

import {customMatchers} from '../../matchers/custom-matchers';
import {LoginPage} from '../../login/login.po';
import {AlertFacetsPage} from './alert-filters.po';
import {loadTestData, deleteTestData} from '../../utils/e2e_util';
import {MetronAlertsPage} from '../alerts-list.po';

describe('Test spec for facet filters', function() {
  let page: AlertFacetsPage;
  let tablePage: MetronAlertsPage;
  let loginPage: LoginPage;

  beforeAll(async function() : Promise<any> {
    loginPage = new LoginPage();
    loginPage.login();
    tablePage = new MetronAlertsPage();
    await loadTestData();
  });

  afterAll(async function() : Promise<any> {
    loginPage.logout();
    await deleteTestData();
  });

  beforeEach(() => {
    page = new AlertFacetsPage();
    jasmine.addMatchers(customMatchers);
  });

  it('should display facets data', () => {
    let facetValues = [ 'enrichm...:country 3', 'host 9', 'ip_dst_addr 8', 'ip_src_addr 2', 'source:type 1' ];

    page.navgateToAlertList();
    expect(page.getFacetsTitle()).toEqualBcoz(['Filters'], 'for Title as Filters');
    expect(page.getFacetsValues()).toEqual(facetValues, 'for Facet values');
  });

  it('should search when facet is selected', () => {
    let facetValues = [ 'enrichm...:country 1', 'host 1', 'ip_dst_addr 1', 'ip_src_addr 1', 'source:type 1' ];

    expect(page.getFacetState(1)).toEqualBcoz('collapse', 'for second facet');
    page.toggleFacetState(1);
    expect(page.getFacetState(1)).toEqualBcoz('collapse show', 'for second facet');

    page.selectFilter('ubb67.3c147o.u806a4.w07d919.o5f.f1.b80w.r0faf9.e8mfzdgrf7g0.groupprograms.in');
    expect(tablePage.getChangesAlertTableTitle('Alerts (169)')).toEqual('Alerts (1)');

    expect(page.getFacetState(1)).toEqualBcoz('collapse show', 'for second facet');
    page.toggleFacetState(1);
    expect(page.getFacetState(1)).toEqualBcoz('collapse', 'for second facet');

    expect(page.getFacetsValues()).toEqual(facetValues, 'for Facet values');

  });
});

