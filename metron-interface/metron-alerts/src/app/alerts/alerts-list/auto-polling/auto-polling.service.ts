import { Injectable } from '@angular/core';
import { Subscription, Subject, Observable, interval } from 'rxjs';
import { SearchService } from 'app/service/search.service';
import { QueryBuilder } from '../query-builder';
import { SearchResponse } from 'app/model/search-response';
import { switchMap, filter, takeWhile, tap } from 'rxjs/operators';
import { POLLING_DEFAULT_STATE } from 'app/utils/constants';

interface AutoPollingStateModel {
  isActive: boolean,
  refreshInterval: number,
}

@Injectable()
export class AutoPollingService {
  data = new Subject<SearchResponse>();

  private isCongestion = false;
  private refreshInterval = 10;
  private isPollingActive = POLLING_DEFAULT_STATE;
  private isPending = false;
  private isPollingSuppressed = false;
  private pollingIntervalSubs: Subscription;

  public readonly AUTO_POLLING_STORAGE_KEY = 'autoPolling';

  constructor(private searchService: SearchService,
              private queryBuilder: QueryBuilder) {
                this.restoreState();
              }

  start() {
    if (!this.isPollingActive) {
      this.sendInitial();
      this.activate();
    }
    this.isPollingActive = true;
    this.persistState();
  }

  stop(persist = true) {
    this.isPollingActive = false;
    this.pollingIntervalSubs.unsubscribe();
    this.pollingIntervalSubs = null;

    if (persist) {
      this.persistState();
    }
  }

  setSuppression(value: boolean) {
    this.isPollingSuppressed = value;
  }

  dropNextAndContinue() {
    this.reset();
  }

  setInterval(seconds: number) {
    this.refreshInterval = seconds;
    if (this.isPollingActive) {
      this.reset();
    }
    this.persistState();
  }

  getInterval(): number {
    return this.refreshInterval;
  }

  getIsPollingActive() {
    return this.isPollingActive;
  }

  getIsCongestion() {
    return this.isCongestion
  }

  private sendInitial() {
    this.isPending = true;
    this.searchService.search(this.queryBuilder.searchRequest).subscribe(this.onResult.bind(this));
  }

  private persistState(key = this.AUTO_POLLING_STORAGE_KEY): void {
    localStorage.setItem(key, JSON.stringify(this.getStateModel()));
  }

  private restoreState(key = this.AUTO_POLLING_STORAGE_KEY): void {
    const persistedState = JSON.parse(localStorage.getItem(key)) as AutoPollingStateModel;

    if (persistedState) {
      this.refreshInterval = persistedState.refreshInterval;

      if (persistedState.isActive) {
        this.start();
      }
    }
  }

  private getStateModel(): AutoPollingStateModel {
    return {
      isActive: this.isPollingActive,
      refreshInterval: this.refreshInterval,
    }
  }

  private reset() {
    if (this.pollingIntervalSubs) {
      this.pollingIntervalSubs.unsubscribe();
      this.isPending = false;
    }
    this.activate();
  }

  private activate() {
    this.pollingIntervalSubs = this.startPolling().subscribe(this.onResult.bind(this));
  }

  private onResult(result: SearchResponse) {
    this.data.next(result);
    this.isPending = false;
  }

  private startPolling(): Observable<SearchResponse> {
    return interval(this.refreshInterval * 1000).pipe(
      tap(() => this.checkCongestionOnTick()),
      filter(() => !this.isPollingSuppressed && !this.isCongestion),
      takeWhile(() => this.isPollingActive),
      switchMap(() => {
        this.isPending = true;
        return this.searchService.search(this.queryBuilder.searchRequest);
      }));
  }

  private checkCongestionOnTick() {
    if (this.isPending) {
      this.isCongestion = true;
    } else {
      this.isCongestion = false;
    }
  }

  onDestroy() {
    if (this.getIsPollingActive()) {
      this.stop(false);
    }
  }
}
