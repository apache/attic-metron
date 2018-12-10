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
package org.apache.metron.enrichment.adapters.maxmind.geo;

import ch.hsr.geohash.WGS84Point;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.AddressNotFoundException;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CityResponse;
import com.maxmind.geoip2.record.City;
import com.maxmind.geoip2.record.Country;
import com.maxmind.geoip2.record.Location;
import com.maxmind.geoip2.record.Postal;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import org.apache.metron.enrichment.adapters.maxmind.GeoLiteDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum GeoLiteCityDatabase implements GeoLiteDatabase {
  INSTANCE;

  protected static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  public static final String GEO_HDFS_FILE = "geo.hdfs.file";
  public static final String GEO_HDFS_FILE_DEFAULT = "/apps/metron/geo/default/GeoLite2-City.mmdb.gz";

  private static ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
  private static final Lock readLock = lock.readLock();
  private static final Lock writeLock = lock.writeLock();
  private static volatile String hdfsLoc = GEO_HDFS_FILE_DEFAULT;
  private static DatabaseReader reader = null;

  public enum GeoProps {
    LOC_ID("locID"),
    COUNTRY("country"),
    CITY("city"),
    POSTAL_CODE("postalCode"),
    DMA_CODE("dmaCode"),
    LATITUDE("latitude"),
    LONGITUDE("longitude"),
    LOCATION_POINT("location_point"),
    ;
    Function<Map<String, String>, String> getter;
    String simpleName;

    GeoProps(String simpleName) {
      this(simpleName, m -> m.get(simpleName));
    }

    GeoProps(String simpleName,
             Function<Map<String, String>, String> getter
    ) {
      this.simpleName = simpleName;
      this.getter = getter;
    }
    public String getSimpleName() {
      return simpleName;
    }

    public String get(Map<String, String> map) {
      return getter.apply(map);
    }

    public void set(Map<String, String> map, String val) {
      map.put(simpleName, val);
    }
  }

  @Override
  public String getHdfsFileConfig() {
    return GEO_HDFS_FILE;
  }

  @Override
  public String getHdfsFileDefault() {
    return GEO_HDFS_FILE_DEFAULT;
  }

  @Override
  public void lockIfNecessary() {
    writeLock.lock();
  }

  @Override
  public void unlockIfNecessary() {
    writeLock.unlock();
  }

  @Override
  public DatabaseReader getReader() {
    return reader;
  }

  @Override
  public void setReader(DatabaseReader reader) {
    GeoLiteCityDatabase.reader = reader;
  }

  public synchronized void updateIfNecessary(Map<String, Object> globalConfig) {
    // Reload database if necessary (file changes on HDFS)
    LOG.trace("[Metron] Determining if GeoIpDatabase update required");
    String hdfsFile = GEO_HDFS_FILE_DEFAULT;
    if (globalConfig != null) {
      hdfsFile = (String) globalConfig.getOrDefault(GEO_HDFS_FILE, GEO_HDFS_FILE_DEFAULT);
    }

    // Always update if we don't have a DatabaseReader
    if (reader == null || !hdfsLoc.equals(hdfsFile)) {
      // Update
      hdfsLoc = hdfsFile;
      update(hdfsFile);
    } else {
      LOG.trace("[Metron] Update to GeoIpDatabase unnecessary");
    }
  }

  // Optional.empty means that we don't have any geo location in database.
  // Optional exists, but empty means local IP (valid, but no info will be in the DB)
  public Optional<Map<String, String>> get(String ip) {
    if (invalidIp(ip)) {
      return Optional.empty();
    }

    try {
      readLock.lock();
      InetAddress addr = InetAddress.getByName(ip);
      CityResponse cityResponse = reader.city(addr);
      HashMap<String, String> geoInfo = new HashMap<>();

      Country country = cityResponse.getCountry();
      City city = cityResponse.getCity();
      Postal postal = cityResponse.getPostal();
      Location location = cityResponse.getLocation();

      GeoProps.LOC_ID.set(geoInfo, convertNullToEmptyString(city.getGeoNameId()));
      GeoProps.COUNTRY.set(geoInfo, convertNullToEmptyString(country.getIsoCode()));
      GeoProps.CITY.set(geoInfo, convertNullToEmptyString(city.getName()));
      GeoProps.POSTAL_CODE.set(geoInfo, convertNullToEmptyString(postal.getCode()));
      GeoProps.DMA_CODE.set(geoInfo, convertNullToEmptyString(location.getMetroCode()));

      Double latitudeRaw = location.getLatitude();
      String latitude = convertNullToEmptyString(latitudeRaw);
      GeoProps.LATITUDE.set(geoInfo, latitude);

      Double longitudeRaw = location.getLongitude();
      String longitude = convertNullToEmptyString(longitudeRaw);
      GeoProps.LONGITUDE.set(geoInfo, longitude);

      if (latitudeRaw == null || longitudeRaw == null) {
        GeoProps.LOCATION_POINT.set(geoInfo, "");
      } else {
        GeoProps.LOCATION_POINT.set(geoInfo, latitude + "," + longitude);
      }

      return Optional.of(geoInfo);
    } catch (UnknownHostException | AddressNotFoundException e) {
      LOG.debug("[Metron] No result found for IP {}", ip);
    } catch (GeoIp2Exception | IOException e) {
      LOG.warn("[Metron] GeoLite2 DB encountered an error", e);
    } finally {
      readLock.unlock();
    }
    return Optional.empty();
  }

  public Optional<WGS84Point> toPoint(Map<String, String> geoInfo) {
    String latitude = GeoProps.LATITUDE.get(geoInfo);
    String longitude = GeoProps.LONGITUDE.get(geoInfo);
    if(latitude == null || longitude == null) {
      return Optional.empty();
    }

    try {
      double latD = Double.parseDouble(latitude);
      double longD = Double.parseDouble(longitude);
      return Optional.of(new WGS84Point(latD, longD));
    } catch (NumberFormatException nfe) {
      LOG.warn(String.format("Invalid lat/long: %s/%s: %s", latitude, longitude, nfe.getMessage()), nfe);
      return Optional.empty();
    }
  }
}
