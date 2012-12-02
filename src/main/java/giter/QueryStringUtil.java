package giter;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public abstract class QueryStringUtil {

	public static Map<String, String> parse(String queryString) {

		Map<String, String> params = new HashMap<>();

		for (String s : queryString.split("&|\\?")) {
			if (s != null && s.contains("=")) {
				String[] pr = s.split("=");
				params.put(pr[0], pr[1]);
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
					sb.append(String.format(pattern,
							URLEncoder.encode(entry.getKey(), "UTF-8"),
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
