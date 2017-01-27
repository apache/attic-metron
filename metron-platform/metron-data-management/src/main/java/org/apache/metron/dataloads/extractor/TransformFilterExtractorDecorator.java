package org.apache.metron.dataloads.extractor;

import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.lang.StringUtils;
import org.apache.curator.framework.CuratorFramework;
import org.apache.log4j.Logger;
import org.apache.metron.common.configuration.ConfigurationsUtils;
import org.apache.metron.common.dsl.Context;
import org.apache.metron.common.dsl.MapVariableResolver;
import org.apache.metron.common.dsl.StellarFunctions;
import org.apache.metron.common.stellar.StellarPredicateProcessor;
import org.apache.metron.common.stellar.StellarProcessor;
import org.apache.metron.common.utils.JSONUtils;
import org.apache.metron.enrichment.lookup.LookupKV;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;

public class TransformFilterExtractorDecorator extends ExtractorDecorator {
  private static final Logger LOG = Logger.getLogger(TransformFilterExtractorDecorator.class);
  private static final String TRANSFORMATIONS = "transformations";
  private static final String FILTER = "filter";
  private static final String ZK_QUORUM = "zk_quorum";
  private Map<String, String> transforms;
  private String filterExpression;
  private Context stellarContext;
  private StellarProcessor transformProcessor;
  private StellarPredicateProcessor filterProcessor;
  private Map<String, Object> globalConfig;

  public TransformFilterExtractorDecorator(Extractor decoratedExtractor) {
    super(decoratedExtractor);
  }

  @Override
  public void initialize(Map<String, Object> config) {
    super.initialize(config);
    if (config.containsKey(TRANSFORMATIONS)) {
      this.transforms = getTransforms(config.get(TRANSFORMATIONS));
    } else {
      this.transforms = new HashMap<>();
    }
    if (config.containsKey(FILTER)) {
      this.filterExpression = config.get(FILTER).toString();
    }
    String zkClientUrl = "";
    if (config.containsKey(ZK_QUORUM)) {
      zkClientUrl = config.get(ZK_QUORUM).toString();
    }
    Optional<CuratorFramework> zkClient = createClient(zkClientUrl);
    this.globalConfig = getGlobalConfig(zkClient);
    this.stellarContext = createContext(zkClient);
    StellarFunctions.initialize(stellarContext);
    this.transformProcessor = new StellarProcessor();
    this.filterProcessor = new StellarPredicateProcessor();
  }

  private Map<String, String> getTransforms(Object transformsConfig) {
    Map<String, String> transforms = new HashMap<>();
    if (transformsConfig instanceof Map) {
      Map<Object, Object> map = (Map<Object, Object>) transformsConfig;
      for (Map.Entry<Object, Object> e : map.entrySet()) {
        transforms.put(e.getKey().toString(), e.getValue().toString());
      }
    }
    return transforms;
  }

  /**
   * Creates a Zookeeper client.
   * @param zookeeperUrl The Zookeeper URL.
   */
  private Optional<CuratorFramework> createClient(String zookeeperUrl) {
    // can only create client, if have valid zookeeper URL
    if (StringUtils.isNotBlank(zookeeperUrl)) {
      CuratorFramework client = ConfigurationsUtils.getClient(zookeeperUrl);
      client.start();
      return Optional.of(client);
    } else {
      LOG.warn("Unable to setup zookeeper client - zk_quorum url not provided. **This will limit some Stellar functionality**");
      return Optional.empty();
    }
  }

  private Map<String, Object> getGlobalConfig(Optional<CuratorFramework> zkClient) {
    if (zkClient.isPresent()) {
      try {
        return JSONUtils.INSTANCE.load(
                new ByteArrayInputStream(ConfigurationsUtils.readGlobalConfigBytesFromZookeeper(zkClient.get())),
                new TypeReference<Map<String, Object>>() {
                });
      } catch (Exception e) {
        LOG.warn("Exception thrown while attempting to get global config from Zookeeper.", e);
      }
    }
    return new HashMap<>();
  }

  private Context createContext(Optional<CuratorFramework> zkClient) {
    Context.Builder builder = new Context.Builder();
    if (zkClient.isPresent()) {
      builder.with(Context.Capabilities.ZOOKEEPER_CLIENT, () -> zkClient.get())
              .with(Context.Capabilities.GLOBAL_CONFIG, () -> globalConfig);
    }
    return builder.build();
  }

  @Override
  public Iterable<LookupKV> extract(String line) throws IOException {
    List<LookupKV> lkvs = new ArrayList<>();
    for (LookupKV lkv : super.extract(line)) {
      if (updateLookupKV(lkv)) {
        lkvs.add(lkv);
      }
    }
    return lkvs;
  }

  private boolean updateLookupKV(LookupKV lkv) {
    Map<String, Object> ret = lkv.getValue().getMetadata();
    MapVariableResolver resolver = new MapVariableResolver(ret, globalConfig);
    for (Map.Entry<String, String> entry : transforms.entrySet()) {
      Object o = transformProcessor.parse(entry.getValue(), resolver, StellarFunctions.FUNCTION_RESOLVER(), stellarContext);
      if (o == null) {
        ret.remove(entry.getKey());
      } else {
        ret.put(entry.getKey(), o);
      }
    }
    return filterProcessor.parse(filterExpression, resolver, StellarFunctions.FUNCTION_RESOLVER(), stellarContext);
  }

}
