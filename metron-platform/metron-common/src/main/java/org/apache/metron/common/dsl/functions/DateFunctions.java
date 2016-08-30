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

package org.apache.metron.common.dsl.functions;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.apache.metron.common.dsl.BaseStellarFunction;
import org.apache.metron.common.utils.ConversionUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;
import java.util.concurrent.ExecutionException;

/**
 * Stellar data functions.
 */
public class DateFunctions {

  private static class TimezonedFormat {

    private String format;
    private Optional<String> timezone;

    public TimezonedFormat(String format, String timezone) {
      this.format = format;
      this.timezone = Optional.of(timezone);
    }

    public TimezonedFormat(String format) {
      this.format = format;
      this.timezone = Optional.empty();
    }

    public SimpleDateFormat toDateFormat() {
      return createFormat(format, timezone);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      TimezonedFormat that = (TimezonedFormat) o;

      if (format != null ? !format.equals(that.format) : that.format != null) return false;
      return timezone != null ? timezone.equals(that.timezone) : that.timezone == null;
    }

    @Override
    public int hashCode() {
      int result = format != null ? format.hashCode() : 0;
      result = 31 * result + (timezone != null ? timezone.hashCode() : 0);
      return result;
    }
  }

  private static LoadingCache<TimezonedFormat, ThreadLocal<SimpleDateFormat>> formatCache =
          CacheBuilder.newBuilder().build(
                  new CacheLoader<TimezonedFormat, ThreadLocal<SimpleDateFormat>>() {
                    @Override
                    public ThreadLocal<SimpleDateFormat> load(final TimezonedFormat format) throws Exception {
                      return new ThreadLocal<SimpleDateFormat>() {
                        @Override
                        public SimpleDateFormat initialValue() {
                        return format.toDateFormat();
                        }
                      };
                    }
                  });

  public static SimpleDateFormat createFormat(String format, Optional<String> timezone) {
    SimpleDateFormat sdf = new SimpleDateFormat(format);
    if(timezone.isPresent()) {
      sdf.setTimeZone(TimeZone.getTimeZone(timezone.get()));
    }
    return sdf;
  }

  public static long getEpochTime(String date, String format, Optional<String> timezone) throws ExecutionException, ParseException {
    TimezonedFormat fmt;
    if(timezone.isPresent()) {
      fmt = new TimezonedFormat(format, timezone.get());
    } else {
      fmt = new TimezonedFormat(format);
    }
    SimpleDateFormat sdf = formatCache.get(fmt).get();
    return sdf.parse(date).getTime();
  }

  /**
   * Stellar Function: TO_EPOCH_TIMESTAMP
   */
  public static class ToTimestamp extends BaseStellarFunction {
    @Override
    public Object apply(List<Object> objects) {
      Object dateObj = objects.get(0);
      Object formatObj = objects.get(1);
      Object tzObj = null;
      if(objects.size() >= 3) {
        tzObj = objects.get(2);
      }
      if(dateObj != null && formatObj != null) {
        try {
          Optional<String> tz = (tzObj == null) ? Optional.empty() : Optional.of(tzObj.toString());
          return getEpochTime(dateObj.toString(), formatObj.toString(), tz);

        } catch (ExecutionException | ParseException e) {
          return null;
        }
      }
      return null;
    }
  }

  /**
   * Stellar Function: DAY_OF_WEEK
   *
   * The numbered day within the week.  The first day of the week, Sunday, has a value of 1.
   */
  public static class DayOfWeek extends BaseStellarFunction {
    @Override
    public Object apply(List<Object> args) {

      // expect epoch milliseconds
      Long epochMillis = ConversionUtils.convert(args.get(0), Long.class);
      if(epochMillis == null) {
        return null;
      }

      // create a calendar
      Calendar calendar = Calendar.getInstance();
      calendar.setTimeInMillis(epochMillis);

      return calendar.get(Calendar.DAY_OF_WEEK);
    }
  }

  /**
   * Stellar Function: DAY_OF_MONTH
   *
   * The day within the month.  The first day within the month has a value of 1.
   */
  public static class DayOfMonth extends BaseStellarFunction {
    @Override
    public Object apply(List<Object> args) {

      // expect epoch milliseconds
      Long epochMillis = ConversionUtils.convert(args.get(0), Long.class);
      if(epochMillis == null) {
        return null;
      }

      // create a calendar
      Calendar calendar = Calendar.getInstance();
      calendar.setTimeInMillis(epochMillis);

      return calendar.get(Calendar.DAY_OF_MONTH);
    }
  }

  /**
   * Stellar Function: WEEK_OF_MONTH
   *
   * The numbered week within the month.  The first week has a value of 1.
   */
  public static class WeekOfMonth extends BaseStellarFunction {
    @Override
    public Object apply(List<Object> args) {

      // expect epoch milliseconds
      Long epochMillis = ConversionUtils.convert(args.get(0), Long.class);
      if(epochMillis == null) {
        return null;
      }

      // create a calendar
      Calendar calendar = Calendar.getInstance();
      calendar.setTimeInMillis(epochMillis);

      return calendar.get(Calendar.WEEK_OF_MONTH);
    }
  }

  /**
   * Stellar Function: WEEK_OF_YEAR
   *
   * The numbered week within the year.  The first week in the year has a value of 1.
   */
  public static class WeekOfYear extends BaseStellarFunction {
    @Override
    public Object apply(List<Object> args) {

      // expect epoch milliseconds
      Long epochMillis = ConversionUtils.convert(args.get(0), Long.class);
      if(epochMillis == null) {
        return null;
      }

      // create a calendar
      Calendar calendar = Calendar.getInstance();
      calendar.setTimeInMillis(epochMillis);

      return calendar.get(Calendar.WEEK_OF_YEAR);
    }
  }

  /**
   * Stellar Function: MONTH
   *
   * A number representing the month.  The first month, January, has a value of 0.
   */
  public static class MonthOfYear extends BaseStellarFunction {
    @Override
    public Object apply(List<Object> args) {

      // expect epoch milliseconds
      Long epochMillis = ConversionUtils.convert(args.get(0), Long.class);
      if(epochMillis == null) {
        return null;
      }

      // create a calendar
      Calendar calendar = Calendar.getInstance();
      calendar.setTimeInMillis(epochMillis);

      return calendar.get(Calendar.MONTH);
    }
  }

  /**
   * Stellar Function: YEAR
   *
   * The calendar year.
   */
  public static class Year extends BaseStellarFunction {
    @Override
    public Object apply(List<Object> args) {

      // expect epoch milliseconds
      Long epochMillis = ConversionUtils.convert(args.get(0), Long.class);
      if(epochMillis == null) {
        return null;
      }

      // create a calendar
      Calendar calendar = Calendar.getInstance();
      calendar.setTimeInMillis(epochMillis);

      return calendar.get(Calendar.YEAR);
    }
  }

  /**
   * Stellar Function: DAY_OF_YEAR
   *
   * The day number within the year.  The first day of the year has value of 1.
   */
  public static class DayOfYear extends BaseStellarFunction {
    @Override
    public Object apply(List<Object> args) {

      // expect epoch milliseconds
      Long epochMillis = ConversionUtils.convert(args.get(0), Long.class);
      if(epochMillis == null) {
        return null;
      }

      // create a calendar
      Calendar calendar = Calendar.getInstance();
      calendar.setTimeInMillis(epochMillis);

      return calendar.get(Calendar.DAY_OF_YEAR);
    }
  }
}

