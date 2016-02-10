package org.apache.metron.parsing.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.json.simple.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class ParserUtils {

  public static final String PREFIX = "stream2file";
  public static final String SUFFIX = ".tmp";

  public static File stream2file(InputStream in) throws IOException {
    final File tempFile = File.createTempFile(PREFIX, SUFFIX);
    tempFile.deleteOnExit();
    try (FileOutputStream out = new FileOutputStream(tempFile)) {
      IOUtils.copy(in, out);
    }
    return tempFile;
  }

  public static Long convertToEpoch(String m, String d, String ts,
                                    boolean adjust_timezone) throws ParseException {
    d = d.trim();
    if (d.length() <= 2) {
      d = "0" + d;
    }
    Date date = new SimpleDateFormat("MMM", Locale.ENGLISH).parse(m);
    Calendar cal = Calendar.getInstance();
    cal.setTime(date);
    String month = String.valueOf(cal.get(Calendar.MONTH));
    int year = Calendar.getInstance().get(Calendar.YEAR);
    if (month.length() <= 2) {
      month = "0" + month;
    }
    String coglomerated_ts = year + "-" + month + "-" + d + " " + ts;
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    if (adjust_timezone) {
      sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
    }
    date = sdf.parse(coglomerated_ts);
    long timeInMillisSinceEpoch = date.getTime();
    return timeInMillisSinceEpoch;
  }
}
