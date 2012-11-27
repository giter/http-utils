package giter;

import giter.HttpFetcher.HttpCallback;

import java.io.IOException;
import java.net.Proxy;
import java.util.Map;

public class HttpUtil {

	private Proxy proxy = null;
	private int connectTimeOut = HttpFetcher.DEFAULT_CONNECT_TIMEOUT;
	private int readTimeOut = HttpFetcher.DEFAULT_READ_TIMEOUT;
	private String[] headers = null;
	private HttpCallback callback = null;
	private boolean persist = true;

	private HttpUtil() {
	}

	public static HttpUtil instance() {
		return new HttpUtil();
	}

	public HttpUtil proxy(Proxy proxy) {
		this.proxy = proxy;
		return this;
	}

	public HttpUtil connect(int timeout) {
		this.connectTimeOut = timeout;
		return this;
	}

	public HttpUtil read(int timeout) {
		this.readTimeOut = timeout;
		return this;
	}

	public HttpUtil persist(boolean persist) {
		this.persist = persist;
		return this;
	}

	public HttpUtil headers(String... headers) {
		this.headers = headers;
		return this;
	}

	public HttpUtil callback(HttpCallback callback) {
		this.callback = callback;
		return this;
	}

	public String GET(String url) throws IOException {
		return HttpFetcher.GET(proxy, url, connectTimeOut, readTimeOut,
				callback, persist, headers == null ? new String[0] : headers);
	}

	public String POST(String url, Map<String, String> params)
			throws IOException {
		return HttpFetcher.POST(proxy, url, params, connectTimeOut,
				readTimeOut, callback, persist, headers == null ? new String[0]
						: headers);
	}

	public String html(String method, String url) throws IOException {
		return HttpFetcher.text(proxy, method, url, connectTimeOut,
				readTimeOut, callback, persist, headers == null ? new String[0]
						: headers);
	}
}
