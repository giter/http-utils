package giter.http.utils;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

public abstract class QueryStringUtil {

  /**
   * Parse URL QueryString by UTF-8 encoding to Map
   * 
   * @param queryString
   *          Query String
   * @return Map
   */
  public static LinkedHashMap<String, String> parse(String queryString) {
    return parse(queryString, "UTF-8");
  }

  /**
   * Parse URL QueryString to Map<String,String>
   * 
   * @param queryString
   *          QueryString
   * @param encoding
   *          URL Encoding
   * @return If the queryString contains multiple parameters with a same name, first returned
   */
  public static LinkedHashMap<String, String> parse(String queryString, String encoding) {

    LinkedHashMap<String, String> params = new LinkedHashMap<>();

    if (queryString == null) { return params; }

    queryString = queryString.trim();

    if (queryString.startsWith("?")) queryString = queryString.substring(1);

    if (queryString.length() == 0) return params;

    for (String s : queryString.split("&")) {

      String k, v;

      if (s.contains("=")) {

        String[] pr = s.split("=", 2);

        try {
          k = URLDecoder.decode(pr[0].trim(), encoding);
          v = URLDecoder.decode(pr[1].trim(), encoding);
        } catch (UnsupportedEncodingException e) {
          throw new RuntimeException(e);
        }

      } else {

        try {
          k = URLDecoder.decode(s.trim(), encoding);
        } catch (UnsupportedEncodingException e) {
          throw new RuntimeException(e);
        }

        v = "";
      }

      if (k.length() > 0 && !params.containsKey(k)) {
        params.put(k, v);
      }
    }

    return params;
  }

  public static String query(Map<String, String> params) {
    return query(params, "UTF-8");
  }

  public static String query(Map<String, String> params, String encoding) {

    String query = "";

    if (params != null && params.size() > 0) {

      StringBuilder sb = new StringBuilder();

      boolean first = true;

      for (Entry<String, String> entry : params.entrySet()) {

        if (!first) {
          sb.append('&');
        } else {
          first = false;
        }

        try {
          sb.append(URLEncoder.encode(entry.getKey(), encoding));
          sb.append('=');
          sb.append(URLEncoder.encode(entry.getValue(), encoding));
        } catch (UnsupportedEncodingException e) {
          throw new RuntimeException(e);
        }
      }

      if (sb.length() > 0) {
        query = sb.toString();
      }

    }

    return query;
  }
}
