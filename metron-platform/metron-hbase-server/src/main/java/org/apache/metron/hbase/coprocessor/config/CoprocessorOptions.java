package org.apache.metron.hbase.coprocessor.config;

import org.apache.metron.common.configuration.ConfigOption;

public enum CoprocessorOptions implements ConfigOption {
  TABLE_NAME("tableName"),
  COLUMN_FAMILY("columnFamily"),
  COLUMN_QUALIFIER("columnQualifier");

  private String key;

  CoprocessorOptions(String key) {
    this.key = key;
  }

  @Override
  public String getKey() {
    return key;
  }

}
