package giter;

import giter.HttpFetcher.HttpCallback;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.List;
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
	private List<String> headers = null;
	private HttpCallback hc = null;
	private boolean persist = true;
	private boolean follow = true;

	private Proxier proxier = null;

	private HttpUtil() {
	}

	public HttpUtil agent(String agent) {
		headers().add("User-Agent: " + agent);
		return this;
	}

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

	public HttpUtil connect(int timeout) {
		this.connectTimeOut = timeout;
		return this;
	}

	public String DELETE(String url) throws IOException {

		if (follow) {
			final AtomicReference<String> rtext = new AtomicReference<String>();
			String text = HttpFetcher
					.DELETE(proxy(), url, connectTimeOut, readTimeOut,
							new HttpCallback() {
								@Override
								public void connection(HttpURLConnection conn)
										throws IOException {
									String loc = check(conn);
									if (loc != null) {
										rtext.set(GET(loc));
									}
								}
							}, persist,
							headers().toArray(new String[headers().size()]));

			return rtext.get() != null ? rtext.get() : text;

		} else {
			return HttpFetcher.DELETE(proxy(), url, connectTimeOut,
					readTimeOut, hc, persist,
					headers().toArray(new String[headers().size()]));
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
					getConn, persist,
					headers().toArray(new String[headers().size()]));
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

	private List<String> headers() {

		if (headers == null) {
			synchronized (HttpUtil.class) {
				if (headers == null) {
					headers = new ArrayList<String>();
				}
			}
		}

		return headers;
	}

	public HttpUtil headers(String... headers) {

		for (String header : headers) {
			headers().add(header);
		}

		return this;
	}

	public HttpUtil persist(boolean persist) {
		this.persist = persist;
		return this;
	}

	public String POST(String url, Map<String, String> params)
			throws IOException {

		if (follow) {

			final AtomicReference<String> rtext = new AtomicReference<String>();

			String text = HttpFetcher
					.POST(proxy(), url, params, connectTimeOut, readTimeOut,
							new HttpCallback() {
								@Override
								public void connection(HttpURLConnection conn)
										throws IOException {
									String loc = check(conn);
									if (loc != null) {
										rtext.set(GET(loc));
									}
								}
							}, persist,
							headers().toArray(new String[headers().size()]));

			return rtext.get() != null ? rtext.get() : text;

		} else {
			return HttpFetcher.POST(proxy(), url, params, connectTimeOut,
					readTimeOut, hc, persist,
					headers().toArray(new String[headers().size()]));
		}
	}

	public HttpUtil proxier(Proxier proxier) {
		this.proxier = proxier;
		return this;
	}

	public Proxy proxy() {
		return proxier != null ? proxier.get() : proxy;
	}

	public HttpUtil proxy(Proxy proxy) {
		this.proxy = proxy;
		return this;
	}

	public HttpUtil read(int timeout) {
		this.readTimeOut = timeout;
		return this;
	}
}
