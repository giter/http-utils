package giter;

import java.io.IOException;
import java.net.Proxy;
import java.net.URLConnection;
import java.util.AbstractMap.SimpleEntry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Better wrapper for LLHTTPClient
 * 
 * @author giter
 * 
 */
public final class HttpClient {

	public static final int DEFAULT_READ_TIMEOUT = 0;
	public static final int DEFAULT_CONNECT_TIMEOUT = 5000;

	private int connectTimeOut = DEFAULT_CONNECT_TIMEOUT;
	private int readTimeOut = DEFAULT_READ_TIMEOUT;

	private Proxier proxier = null;
	private Proxy proxy = null;

	private LinkedHashMap<String, String> headers = null;
	private LinkedHashMap<String, String> cookies = null;

	private boolean persistCookies = true;

	public HttpClient() {
	}

	/**
	 * Set User-Agent
	 * 
	 * @param agent
	 *            User-Agent to set
	 * @return this object
	 */
	public HttpClient agent(String agent) {
		headers().put("User-Agent", agent);
		return this;
	}

	/**
	 * Http Basic Realm
	 * 
	 * @param username
	 *            username
	 * @param password
	 *            password
	 * @return this object
	 */
	public HttpClient auth(String username, String password) {
		headers().put("Authorization",
				"Basic " + B64Code.decode(username + ":" + password));
		return this;
	}

	/**
	 * connect timeout
	 * 
	 * @param timeout
	 *            in ms
	 * @return this object
	 */
	public HttpClient connect(int timeout) {
		this.connectTimeOut = timeout;
		return this;
	}

	/**
	 * Set single cookie to request
	 * 
	 * @param key
	 *            cookie key
	 * @param value
	 *            cookie value
	 * @return this object
	 */
	public HttpClient cookie(String key, String value) {
		cookies().put(key, value);
		headers().put("Cookie", cookiesString());
		return this;
	}

	@SafeVarargs
	final public HttpClient cookie(SimpleEntry<String, String>... cookies) {

		for (Map.Entry<String, String> cookie : cookies) {
			cookies().put(cookie.getKey(), cookie.getValue());
		}

		headers().put("Cookie", cookiesString());

		return this;
	}

	private LinkedHashMap<String, String> cookies() {

		if (cookies == null) {
			synchronized (HttpClient.class) {
				if (cookies == null) {
					cookies = new LinkedHashMap<>();
				}
			}
		}

		return cookies;
	}

	private String cookiesString() {

		LinkedHashMap<String, String> cookies = cookies();

		if (cookies.size() > 0) {

			StringBuilder sb = new StringBuilder();
			boolean first = true;

			for (Map.Entry<String, String> kv : cookies.entrySet()) {

				if (first) {
					first = false;
				} else {
					sb.append(";");
				}

				sb.append(kv.getKey());
				sb.append("=");
				sb.append(kv.getValue());
			}

			return sb.toString();
		}

		return null;

	}

	/**
	 * delete method
	 * 
	 * @param url
	 *            url to delete
	 * @return this object
	 * @throws IOException
	 */
	public Entry<URLConnection, String> DELETE(String url) throws IOException {

		Entry<URLConnection, String> r = store(LLHttpClient.DELETE(proxy(),
				url, connectTimeOut, readTimeOut, headersArray()));
		return r;

	}

	/**
	 * GET url
	 * 
	 * @param url
	 *            to GET
	 * @return this object
	 * @throws IOException
	 */
	public Entry<URLConnection, String> GET(String url) throws IOException {
		return store(LLHttpClient.GET(proxy(), url, connectTimeOut,
				readTimeOut, headersArray()));
	}

	private LinkedHashMap<String, String> headers() {

		if (headers == null) {
			synchronized (HttpClient.class) {
				if (headers == null) {
					headers = new LinkedHashMap<>();
				}
			}
		}

		return headers;
	}

	/**
	 * directly put headers
	 * 
	 * @param headers
	 *            headers to put
	 * @return this object
	 */
	@SafeVarargs
	final public HttpClient headers(Map.Entry<String, String>... headers) {

		for (Map.Entry<String, String> header : headers) {
			headers().put(header.getKey(), header.getValue());
		}

		return this;
	}

	private String[] headersArray() {

		LinkedHashMap<String, String> headers = headers();

		String[] ls = new String[headers.size()];

		int i = 0;

		for (Map.Entry<String, String> header : headers.entrySet()) {
			ls[i++] = (header.getKey() + ": " + header.getValue());
		}

		return ls;
	}

	/**
	 * whether cookies should be persistence
	 * 
	 * @param bool
	 * @return this object
	 */
	public HttpClient persistCookies(boolean bool) {
		this.persistCookies = bool;
		return this;
	}

	/**
	 * POST url
	 * 
	 * @param url
	 *            url to POST
	 * @param params
	 *            params to POST
	 * @return this object
	 * @throws IOException
	 */
	public SimpleEntry<URLConnection, String> POST(String url,
			Map<String, String> params) throws IOException {
		return store(LLHttpClient.POST(proxy(), url, params, connectTimeOut,
				readTimeOut, headersArray()));
	}

	/**
	 * Proxier to get proxy for each single request
	 * 
	 * @param proxier
	 * @return this object
	 * @see Proxier
	 */
	public HttpClient proxier(Proxier proxier) {
		this.proxier = proxier;
		return this;
	}

	/**
	 * get proxy vars
	 * 
	 * @return proxier.get() || proxy
	 */
	public Proxy proxy() {
		return proxier != null ? proxier.get() : proxy;
	}

	/**
	 * Fix proxy for each request
	 * 
	 * @param proxy
	 * @return this object
	 */
	public HttpClient proxy(Proxy proxy) {
		this.proxy = proxy;
		return this;
	}

	/**
	 * read timeout
	 * 
	 * @param timeout
	 *            timeout in ms
	 * @return this object
	 */
	public HttpClient read(int timeout) {
		this.readTimeOut = timeout;
		return this;
	}

	/**
	 * Set URL referer
	 * 
	 * @param url
	 *            URL Referer to set
	 * @return this object
	 */
	public HttpClient referer(String url) {
		headers().put("Referer", url);
		return this;
	}

	protected SimpleEntry<URLConnection, String> store(
			SimpleEntry<URLConnection, String> r) {

		final URLConnection conn = r.getKey();
		final String d = conn.getURL().getHost();

		if (!persistCookies)
			return r;

		for (Entry<String, List<String>> header : conn.getHeaderFields()
				.entrySet()) {

			if (header.getKey() == null
					|| !header.getKey().equalsIgnoreCase("Set-Cookie")) {
				continue;
			}

			for (String cookie : header.getValue()) {

				String[] pieces = cookie.split(";");

				if (pieces[0].indexOf('=') <= 0) {
					continue;
				}

				String[] kv = pieces[0].split("=", 2);

				String key = kv[0];
				String val = kv[1];

				String domain = d;

				for (int i = 1; i < pieces.length; i++) {

					String[] p = pieces[i].split("=");

					if (p.length != 2)
						continue;

					switch (p[0].trim().toLowerCase()) {
					case "domain":
						domain = p[1].trim();
						break;
					case "path":
						break;
					case "expires":
						break;
					}
				}

				if (d.endsWith(domain)) {
					cookie(key, val);
				}
			}
		}

		return r;
	}
}
