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
import {Observable} from 'rxjs/Rx';
import {Headers, RequestOptions} from '@angular/http';

import {HttpUtil} from '../utils/httpUtil';
import {AlertsSearchResponse} from '../model/alerts-search-response';
import {SearchRequest} from '../model/search-request';
import {ElasticSearchLocalstorageImpl} from './elasticsearch-localstorage-impl';
import {AlertSource} from '../model/alert-source';

export class RestApiImpl extends ElasticSearchLocalstorageImpl {

  getAlerts(searchRequest: SearchRequest): Observable<AlertsSearchResponse> {
    let url = '/api/v1/search/search';
    return this.http.post(url, searchRequest, new RequestOptions({headers: new Headers(this.defaultHeaders)}))
      .map(HttpUtil.extractData)
      .catch(HttpUtil.handleError)
      .onErrorResumeNext();
  }

  getAlert(sourceType: string, alertId: string): Observable<AlertSource> {
    let url = '/api/v1/search/findOne';
    let requestSchema = { guid: alertId, sensorType: sourceType};

    return this.http.post(url, requestSchema, new RequestOptions({headers: new Headers(this.defaultHeaders)}))
    .map(HttpUtil.extractData)
    .catch(HttpUtil.handleError)
    .onErrorResumeNext();
  }
}
