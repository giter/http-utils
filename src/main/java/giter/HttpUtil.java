package giter;

import giter.HttpFetcher.HttpCallback;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Better wrapper for HttpFetcher
 * 
 * @author giter
 * 
 */
public final class HttpUtil {

	public static HttpUtil instance() {
		return new HttpUtil();
	}

	private Proxy proxy = null;
	private int connectTimeOut = HttpFetcher.DEFAULT_CONNECT_TIMEOUT;
	private int readTimeOut = HttpFetcher.DEFAULT_READ_TIMEOUT;
	private LinkedHashMap<String, String> headers = null;
	private LinkedHashMap<String, String> cookies = null;
	private HttpCallback hc = null;
	private boolean persist = true;
	private boolean follow = true;

	private Proxier proxier = null;

	private HttpUtil() {
	}

	/**
	 * Set User-Agent
	 * 
	 * @param agent
	 *            User-Agent to set
	 * @return this object
	 */
	public HttpUtil agent(String agent) {
		headers().put("User-Agent", agent);
		return this;
	}

	/**
	 * callback to get HttpURLConnection
	 * 
	 * @param hc
	 * @return this object
	 */
	public HttpUtil callback(HttpCallback hc) {
		this.hc = hc;
		return this;
	}

	/**
	 * Check response code
	 * 
	 * @param conn
	 * @return the HTTP redirection url or nil
	 * @throws IOException
	 *             throw if when code != [ 301, 302, 200 ]
	 */
	private String check(final HttpURLConnection conn) throws IOException {
		switch (conn.getResponseCode()) {
		case HttpURLConnection.HTTP_MOVED_PERM:
		case HttpURLConnection.HTTP_MOVED_TEMP:
			return conn.getHeaderField("Location");
		case HttpURLConnection.HTTP_OK:
			return null;
		default:
			throw new IOException(String.format("Error response code %d",
					conn.getResponseCode()));
		}
	}

	/**
	 * connect timeout
	 * 
	 * @param timeout
	 *            in ms
	 * @return this object
	 */
	public HttpUtil connect(int timeout) {
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
	public HttpUtil cookie(String key, String value) {
		cookies().put(key, value);
		headers().put("Cookie", cookiesString());
		return this;
	}

	@SafeVarargs
	final public HttpUtil cookie(Map.Entry<String, String>... cookies) {

		for (Map.Entry<String, String> cookie : cookies) {
			cookies().put(cookie.getKey(), cookie.getValue());
		}

		headers().put("Cookie", cookiesString());

		return this;
	}

	private LinkedHashMap<String, String> cookies() {

		if (cookies == null) {
			synchronized (HttpUtil.class) {
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
	public String DELETE(String url) throws IOException {

		if (follow) {
			final AtomicReference<String> rtext = new AtomicReference<String>();
			String text = HttpFetcher.DELETE(proxy(), url, connectTimeOut,
					readTimeOut, new HttpCallback() {
						@Override
						public void connection(HttpURLConnection conn)
								throws IOException {
							String loc = check(conn);
							if (loc != null) {
								rtext.set(GET(loc));
							}
						}
					}, persist, headersArray());

			return rtext.get() != null ? rtext.get() : text;

		} else {
			return HttpFetcher.DELETE(proxy(), url, connectTimeOut,
					readTimeOut, hc, persist, headersArray());
		}
	}

	/**
	 * set whether util should follow http 301/302 moved command
	 * 
	 * @param follow
	 *            boolean
	 * @return this util it self
	 */
	public HttpUtil follow(boolean follow) {
		this.follow = follow;
		return this;
	}

	/**
	 * GET url
	 * 
	 * @param url
	 *            to GET
	 * @return this object
	 * @throws IOException
	 */
	public String GET(String url) throws IOException {

		final AtomicReference<HttpURLConnection> ref = new AtomicReference<>();

		HttpCallback getConn = new HttpCallback() {
			@Override
			public void connection(HttpURLConnection conn) throws IOException {
				ref.set(conn);
			}
		};

		Proxy proxy = proxy();

		String text;
		int j = follow ? 5 : 2;

		do {
			text = HttpFetcher.GET(proxy, url, connectTimeOut, readTimeOut,
					getConn, persist, headersArray());
			url = check(ref.get());
		} while (follow && (--j) > 0 && url != null);

		if (follow && j <= 0 && url != null) {
			throw new IOException("May redirection loops!");
		}

		if (hc != null) {
			hc.connection(ref.get());
		}

		return text;
	}

	private LinkedHashMap<String, String> headers() {

		if (headers == null) {
			synchronized (HttpUtil.class) {
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
	final public HttpUtil headers(Map.Entry<String, String>... headers) {

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
	 * persist cookies
	 * 
	 * @param persist
	 * @return this object
	 */
	public HttpUtil persist(boolean persist) {
		this.persist = persist;
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
	public String POST(String url, Map<String, String> params)
			throws IOException {

		if (follow) {

			final AtomicReference<String> rtext = new AtomicReference<String>();

			String text = HttpFetcher.POST(proxy(), url, params,
					connectTimeOut, readTimeOut, new HttpCallback() {
						@Override
						public void connection(HttpURLConnection conn)
								throws IOException {
							String loc = check(conn);
							if (loc != null) {
								rtext.set(GET(loc));
							}
						}
					}, persist, headersArray());

			return rtext.get() != null ? rtext.get() : text;

		} else {
			return HttpFetcher.POST(proxy(), url, params, connectTimeOut,
					readTimeOut, hc, persist, headersArray());
		}
	}

	/**
	 * Proxier to get proxy for each single request
	 * 
	 * @param proxier
	 * @return this object
	 * @see Proxier
	 */
	public HttpUtil proxier(Proxier proxier) {
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
	public HttpUtil proxy(Proxy proxy) {
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
	public HttpUtil read(int timeout) {
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
	public HttpUtil referer(String url) {
		headers().put("Referer", url);
		return this;
	}
}
