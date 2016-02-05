package org.apache.metron.dataloads.hbase.mr;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.metron.dataloads.extractor.Extractor;
import org.apache.metron.dataloads.extractor.ExtractorHandler;
import org.apache.metron.threatintel.ThreatIntelResults;
import org.apache.metron.threatintel.hbase.Converter;

import java.io.IOException;

/**
 * Created by cstella on 2/3/16.
 */
public class BulkLoadMapper extends Mapper<Object, Text, ImmutableBytesWritable, Put>
{
    public static final String CONFIG_KEY="bl_extractor_config";
    public static final String COLUMN_FAMILY_KEY = "bl_column_family";
    public static final String LAST_SEEN_KEY = "bl_last_seen";
    Extractor extractor = null;
    String columnFamily = null;
    Long lastSeen = null;
    @Override
    public void setup(Context context) throws IOException,
            InterruptedException {
        initialize(context.getConfiguration());
    }

    @Override
    public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
        ThreatIntelResults results = extractor.extract(value.toString());
        if(results != null) {
            Put put = Converter.INSTANCE.toPut(columnFamily, results.getKey(), results.getValue(), lastSeen);
            write(new ImmutableBytesWritable(results.getKey().toBytes()), put, context);
        }
    }

    protected void initialize(Configuration configuration) throws IOException {
        String configStr = configuration.get(CONFIG_KEY);
        extractor = ExtractorHandler.load(configStr).getExtractor();
        columnFamily = configuration.get(COLUMN_FAMILY_KEY);
        lastSeen = Long.parseLong(configuration.get(LAST_SEEN_KEY));
    }

    protected void write(ImmutableBytesWritable key, Put value, Context context) throws IOException, InterruptedException {
        context.write(key, value);
    }
}
