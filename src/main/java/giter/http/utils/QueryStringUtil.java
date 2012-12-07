package giter.http.utils;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public abstract class QueryStringUtil {

	public static Map<String, String> parse(String queryString) {
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
	public static Map<String, String> parse(String queryString, String encoding) {

		Map<String, String> params = new HashMap<>();

		if (queryString == null) { return params; }

		queryString = queryString.trim();

		if (queryString.startsWith("?")) queryString = queryString.substring(1);

		if (queryString.length() == 0) return params;

		for (String s : queryString.split("&")) {

			if (s.length() == 0) continue;

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

			if (!params.containsKey(k)) {
				params.put(k, v);
			}
		}

		return params;
	}

	public static String query(Map<String, String> params) {

		String query = "";

		if (params != null && params.size() > 0) {

			StringBuilder sb = new StringBuilder();

			String pattern = "%s=%s";

			boolean first = true;

			for (Entry<String, String> entry : params.entrySet()) {

				if (!first) {
					sb.append("&");
				} else {
					first = false;
				}

				try {
					sb.append(String.format(pattern, URLEncoder.encode(entry.getKey(), "UTF-8"),
							URLEncoder.encode(entry.getValue(), "UTF-8")));
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
