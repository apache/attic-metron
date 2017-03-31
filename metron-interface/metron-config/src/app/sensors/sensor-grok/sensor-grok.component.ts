import { Component, OnInit, Input, OnChanges, SimpleChanges, ViewChild, EventEmitter, Output} from '@angular/core';
import {SensorParserConfig} from '../../model/sensor-parser-config';
import {ParseMessageRequest} from '../../model/parse-message-request';
import {SensorParserConfigService} from '../../service/sensor-parser-config.service';
import {AutocompleteOption} from '../../model/autocomplete-option';
import {GrokValidationService} from '../../service/grok-validation.service';
import {SampleDataComponent} from '../../shared/sample-data/sample-data.component';
import {MetronAlerts} from '../../shared/metron-alerts';

@Component({
  selector: 'metron-config-sensor-grok',
  templateUrl: './sensor-grok.component.html',
  styleUrls: ['./sensor-grok.component.scss']
})
export class SensorGrokComponent implements OnInit, OnChanges {

  @Input() showGrok; boolean;
  @Input() sensorParserConfig: SensorParserConfig;
  @Input() grokStatement: string;

  @Output() hideGrok = new EventEmitter<void>();
  @Output() onSaveGrokStatement = new EventEmitter<string>();

  @ViewChild(SampleDataComponent) sampleData: SampleDataComponent;

  newGrokStatement = '';
  parsedMessage: any = {};
  parsedMessageKeys: string[] = [];
  grokFunctionList: AutocompleteOption[] = [];
  parseMessageRequest: ParseMessageRequest = new ParseMessageRequest();

  constructor(private sensorParserConfigService: SensorParserConfigService, private grokValidationService: GrokValidationService,
              private metronAlerts: MetronAlerts) {
    this.parseMessageRequest.sampleData = '';
  }

  ngOnInit() {
    this.getGrokFunctions();
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes['showGrok'] && changes['showGrok'].currentValue) {
      this.newGrokStatement = this.grokStatement;
      this.sampleData.getNextSample();
    }
  }

  onSampleDataChanged(sampleData: string) {
    this.parseMessageRequest.sampleData = sampleData;
    this.onTestGrokStatement();
  }

  onTestGrokStatement() {
    this.parsedMessage = {};

    if (this.newGrokStatement.length === 0) {
      return;
    }

    let topicUpperCase = this.sensorParserConfig.sensorTopic.toUpperCase();

    this.parseMessageRequest.sensorParserConfig = JSON.parse(JSON.stringify(this.sensorParserConfig));
    this.parseMessageRequest.grokStatement = topicUpperCase + ' ' + this.newGrokStatement;
    if (this.parseMessageRequest.sensorParserConfig.parserConfig['patternLabel'] == null) {
        this.parseMessageRequest.sensorParserConfig.parserConfig['patternLabel'] = topicUpperCase;
    }
    this.parseMessageRequest.sensorParserConfig.parserConfig['grokPath'] = './' + this.parseMessageRequest.sensorParserConfig.sensorTopic;

    this.sensorParserConfigService.parseMessage(this.parseMessageRequest).subscribe(
        result => {
          this.parsedMessage = result;
          this.setParsedMessageKeys();
        }, error => {
          this.metronAlerts.showErrorMessage(error.message);
          this.setParsedMessageKeys();
        });
  }

  private getGrokFunctions() {
    this.grokValidationService.list().subscribe(result => {
      Object.keys(result).forEach(name => {
        let autocompleteOption: AutocompleteOption = new AutocompleteOption();
        autocompleteOption.name = name;
        this.grokFunctionList.push(autocompleteOption);
      });
    });
  }

  private setParsedMessageKeys() {
    try {
      this.parsedMessageKeys = Object.keys(this.parsedMessage).sort();
    } catch (e) {
      this.parsedMessageKeys = [];
    }
  }

  onSaveGrok(): void {
    this.onSaveGrokStatement.emit(this.newGrokStatement);
    this.hideGrok.emit();
  }

  onCancelGrok(): void {
    this.hideGrok.emit();
  }


}
