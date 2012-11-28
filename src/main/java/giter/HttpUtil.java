package giter;

import giter.HttpFetcher.HttpCallback;

import java.io.IOException;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HttpUtil {

	private Proxy proxy = null;
	private int connectTimeOut = HttpFetcher.DEFAULT_CONNECT_TIMEOUT;
	private int readTimeOut = HttpFetcher.DEFAULT_READ_TIMEOUT;
	private List<String> headers = null;
	private HttpCallback callback = null;
	private boolean persist = true;
	private Proxier proxier = null;

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

	private HttpUtil() {
	}

	public static HttpUtil instance() {
		return new HttpUtil();
	}

	public HttpUtil proxy(Proxy proxy) {
		this.proxy = proxy;
		return this;
	}

	public HttpUtil proxier(Proxier proxier) {
		this.proxier = proxier;
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

		for (String header : headers) {
			headers().add(header);
		}

		return this;
	}

	public HttpUtil callback(HttpCallback callback) {
		this.callback = callback;
		return this;
	}

	public String GET(String url) throws IOException {

		return HttpFetcher.GET(proxy(), url, connectTimeOut, readTimeOut,
				callback, persist,
				headers().toArray(new String[headers().size()]));
	}

	public String POST(String url, Map<String, String> params)
			throws IOException {

		return HttpFetcher.POST(proxy(), url, params, connectTimeOut,
				readTimeOut, callback, persist,
				headers().toArray(new String[headers().size()]));
	}

	public String DELETE(String url) throws IOException {

		return HttpFetcher.DELETE(proxy(), url, connectTimeOut, readTimeOut,
				callback, persist,
				headers().toArray(new String[headers().size()]));
	}

	private Proxy proxy() {

		Proxy _proxy = proxy;

		if (proxier != null) {
			_proxy = proxier.get();
		}

		return _proxy;
	}
}
