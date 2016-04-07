package org.apache.metron.bolt;

import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.tuple.Tuple;
import org.apache.metron.Constants;
import org.apache.metron.domain.Configurations;
import org.apache.metron.domain.SensorEnrichmentConfig;
import org.apache.metron.utils.ConfigurationsUtils;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.Mockito.mock;

public class ConfiguredBoltTest extends BaseBoltTest {

  private static Set<String> configsUpdated = new HashSet<>();

  public static class StandAloneConfiguredBolt extends ConfiguredBolt {

    public StandAloneConfiguredBolt(String zookeeperUrl) {
      super(zookeeperUrl);
    }

    @Override
    public void execute(Tuple input) {
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
    }

    @Override
    protected void reloadCallback(String name, Configurations.Type type) {
      configsUpdated.add(name);
    }
  }

  @Test
  public void test() throws Exception {
    Configurations sampleConfigurations = new Configurations();
    TopologyContext topologyContext = mock(TopologyContext.class);
    OutputCollector outputCollector = mock(OutputCollector.class);
    try {
      StandAloneConfiguredBolt configuredBolt = new StandAloneConfiguredBolt(null);
      configuredBolt.prepare(new HashMap(), topologyContext, outputCollector);
      Assert.fail("A valid zookeeper url must be supplied");
    } catch (RuntimeException e){}

    configsUpdated = new HashSet<>();
    sampleConfigurations.updateGlobalConfig(ConfigurationsUtils.readGlobalConfigFromFile(sampleConfigRoot));
    Map<String, byte[]> sensorEnrichmentConfigs = ConfigurationsUtils.readSensorEnrichmentConfigsFromFile(sampleConfigRoot);
    for (String sensorType : sensorEnrichmentConfigs.keySet()) {
      sampleConfigurations.updateSensorEnrichmentConfig(sensorType, sensorEnrichmentConfigs.get(sensorType));
    }

    StandAloneConfiguredBolt configuredBolt = new StandAloneConfiguredBolt(zookeeperUrl);
    configuredBolt.prepare(new HashMap(), topologyContext, outputCollector);
    waitForConfigUpdate(allConfigurationTypes);
    Assert.assertEquals(sampleConfigurations, configuredBolt.configurations);

    configsUpdated = new HashSet<>();
    Map<String, Object> sampleGlobalConfig = sampleConfigurations.getGlobalConfig();
    sampleGlobalConfig.put("newGlobalField", "newGlobalValue");
    ConfigurationsUtils.writeGlobalConfigToZookeeper(sampleGlobalConfig, zookeeperUrl);
    waitForConfigUpdate(Constants.GLOBAL_CONFIG_NAME);
    Assert.assertEquals("Add global config field", sampleConfigurations.getGlobalConfig(), configuredBolt.configurations.getGlobalConfig());

    configsUpdated = new HashSet<>();
    sampleGlobalConfig.remove("newGlobalField");
    ConfigurationsUtils.writeGlobalConfigToZookeeper(sampleGlobalConfig, zookeeperUrl);
    waitForConfigUpdate(Constants.GLOBAL_CONFIG_NAME);
    Assert.assertEquals("Remove global config field", sampleConfigurations, configuredBolt.configurations);

    configsUpdated = new HashSet<>();
    String sensorType = "testSensorConfig";
    SensorEnrichmentConfig testSensorConfig = new SensorEnrichmentConfig();
    testSensorConfig.setBatchSize(50);
    testSensorConfig.setIndex("test");
    Map<String, List<String>> enrichmentFieldMap = new HashMap<>();
    enrichmentFieldMap.put("enrichmentTest", new ArrayList<String>() {{
      add("enrichmentField");
    }});
    testSensorConfig.setEnrichmentFieldMap(enrichmentFieldMap);
    Map<String, List<String>> threatIntelFieldMap = new HashMap<>();
    threatIntelFieldMap.put("threatIntelTest", new ArrayList<String>() {{
      add("threatIntelField");
    }});
    testSensorConfig.setThreatIntelFieldMap(threatIntelFieldMap);
    sampleConfigurations.updateSensorEnrichmentConfig(sensorType, testSensorConfig);
    ConfigurationsUtils.writeSensorEnrichmentConfigToZookeeper(sensorType, testSensorConfig, zookeeperUrl);
    waitForConfigUpdate(sensorType);
    Assert.assertEquals("Add new sensor config", sampleConfigurations, configuredBolt.configurations);

    configsUpdated = new HashSet<>();
    String someConfigType = "someConfig";
    Map<String, Object> someConfig = new HashMap<>();
    someConfig.put("someField", "someValue");
    sampleConfigurations.updateConfig(someConfigType, someConfig);
    ConfigurationsUtils.writeConfigToZookeeper(someConfigType, someConfig, zookeeperUrl);
    waitForConfigUpdate(someConfigType);
    Assert.assertEquals("Add new misc config", sampleConfigurations, configuredBolt.configurations);
    configuredBolt.cleanup();
  }

  private void waitForConfigUpdate(final String expectedConfigUpdate) {
    waitForConfigUpdate(new HashSet<String>() {{ add(expectedConfigUpdate); }});
  }

  private void waitForConfigUpdate(Set<String> expectedConfigUpdates) {
    int count = 0;
    while (!configsUpdated.equals(expectedConfigUpdates)) {
      if (count++ > 5) {
        Assert.fail("ConfiguredBolt was not updated in time");
        return;
      }
      try {
        Thread.sleep(500);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }
}