package giter.http.utils;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.util.AbstractMap.SimpleEntry;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * Low level HttpClient
 * 
 * @author giter
 */
public abstract class LLHttpClient {

	static {
		setMaxConnections(30);
	}

	public static final Charset	DEFAULT_CHARSET	= Charset.forName("UTF-8");
	public final static Pattern	CHARSET_PATTERN	= Pattern
																									.compile(
																											"['\" ;]charset\\s*=([^'\" ]+)[ '\"]|charset\\s*=\\s*\"?([^'\\\" ]+)|['\" ;]encoding\\s*=([^'\" ]+)[ '\"]|encoding\\s*=\\s*\"?([^'\\\" ]+)",
																											Pattern.CASE_INSENSITIVE);

	protected static String charsetFromContent(String content) {

		String s = null;
		Matcher matcher = CHARSET_PATTERN.matcher(content);

		if (matcher.find()) {

			for (int i = 0; i < matcher.groupCount(); i++) {
				s = matcher.group(i + 1);
				if (s != null) break;
			}

			// 处理未在IANA列表中的字符集，如x-gbk
			if (s.startsWith("x-") || s.startsWith("X-")) {
				s = s.substring(2);
			}

			s = s.toLowerCase();
		}

		return s;
	}

	protected static URLConnection connect(Proxy proxy, String url, int connect_timeout, int read_timeout,
			Map<String, String> headers) throws IOException {

		URLConnection conn = new URL(url).openConnection(proxy == null ? Proxy.NO_PROXY : proxy);

		if (conn instanceof HttpURLConnection) {
			((HttpURLConnection) conn).setInstanceFollowRedirects(false);
		}

		conn.setConnectTimeout(connect_timeout);
		conn.setReadTimeout(read_timeout);

		conn.setRequestProperty("Accept-Charset", "GBK,utf-8;q=0.7,*;q=0.3");
		conn.setRequestProperty("Accept-Encoding", "gzip,deflate");
		conn.setRequestProperty("Accept-Language", "zh-cn,zh;q=0.5");

		for (Entry<String, String> h : headers.entrySet()) {
			conn.setRequestProperty(h.getKey(), h.getValue());
		}

		return conn;
	}

	protected static SimpleEntry<URLConnection, String> content(URLConnection conn) throws IOException {

		try (InputStream in = getInputStream(conn)) {

			byte[] buf = new byte[4096];
			ByteArrayOutputStream bos = new ByteArrayOutputStream(4096);

			int n = 0;
			while ((n = in.read(buf)) >= 0) {
				bos.write(buf, 0, n);
			}

			byte[] bytes = bos.toByteArray();

			String cs = encoding(conn);

			// 处理未在IANA列表中的字符集，如x-gbk,认为这些字符集都应该被支持
			// 所有写x-gbk的都是2B青年
			if (cs != null && (cs.startsWith("x-"))) {
				cs = cs.substring(2);
			}

			if (cs == null || !supported(cs)) {
				// 假设charset必须出现在页面的最前的4KB
				int length = bytes.length > 4096 ? 4096 : bytes.length;
				System.arraycopy(bytes, 0, buf, 0, length);
				String head = new String(buf, "ASCII");
				cs = charsetFromContent(head);
			}

			// 如果是GB2312编码，则转换成对应的更高级编码
			if ("gb2312".equals(cs)) {
				if (supported("GB18030")) {
					cs = "GB18030";
				} else if (supported("GBK")) {
					cs = "GBK";
				}
			}

			return new SimpleEntry<>(conn, new String(bytes, cs == null || !supported(cs) ? DEFAULT_CHARSET
					: Charset.forName(cs)));
		}
	}

	public static SimpleEntry<URLConnection, String> DELETE(Proxy proxy, String url, int connect_timeout,
			int read_timeout, Map<String, String> headers) throws IOException {
		return content(method(connect(proxy, url, connect_timeout, read_timeout, headers), "DELETE"));
	}

	/**
	 * 根据Connection判断当前网页编码，默认使用UTF-8
	 * 
	 * @param conn
	 *          URLConnection
	 * @return 当前网页编码
	 */
	protected static String encoding(URLConnection conn) {

		String contentType = conn.getHeaderField("Content-Type");

		if (contentType == null) return null;

		contentType = contentType.toLowerCase();

		for (String cs : new String[] { "charset=", "charset =" }) {
			if (contentType.contains(cs)) { return contentType.substring(contentType.indexOf(cs) + cs.length()).trim()
					.toLowerCase(); }
		}

		return null;
	}

	public static SimpleEntry<URLConnection, String> GET(Proxy proxy, String url, int connect_timeout, int read_timeout,
			Map<String, String> headers) throws IOException {
		return content(connect(proxy, url, connect_timeout, read_timeout, headers));
	}

	protected static InputStream getInputStream(URLConnection conn) throws IOException {

		InputStream cin = conn.getInputStream();

		String encoding = conn.getHeaderField("Content-Encoding");

		if (encoding != null) {
			switch (encoding.toLowerCase()) {
			case "gzip":
				return new GZIPInputStream(cin);
			case "deflate":
				return new InflaterInputStream(cin);
			default:
				throw new IOException("Unsupported encoding format: " + encoding);
			}
		}

		BufferedInputStream bi = new BufferedInputStream(cin, 4096);
		bi.mark(2);

		if ((bi.read() == (GZIPInputStream.GZIP_MAGIC & 0xff)) && (bi.read() == ((GZIPInputStream.GZIP_MAGIC >> 8) & 0xff))) {
			bi.reset();
			return new GZIPInputStream(bi);
		}

		bi.reset();
		return bi;
	}

	/**
	 * 设置访问方法（此时必须是HttpURLConnection）
	 * 
	 * @param conn
	 *          连接
	 * @param method
	 *          方法(GET,POST,DELETE,etc)
	 * @return conn
	 * @throws ProtocolException
	 */
	protected static URLConnection method(URLConnection conn, String method) throws ProtocolException {
		if (method != null) {
			((HttpURLConnection) conn).setRequestMethod(method);
		}
		return conn;
	}

	protected static void multipart(NMultiPartOutputStream mos, String key, InputStream in) throws IOException {

		byte[] buff = new byte[512];

		mos.startPart("application/octet-stream", new String[] { "Content-Disposition: form-data; name=\"" + key
				+ "\"; filename=\"" + key + "\"" });

		int n = -1;
		while ((n = in.read(buff)) > 0) {
			mos.write(buff, 0, n);
		}
	}

	protected static void multipart(NMultiPartOutputStream mos, String key, String val) throws IOException {
		mos.startPart("text/plain", new String[] { "Content-Disposition: form-data; name=\"" + key + "\"" });
		mos.write(val.getBytes());
	}

	public static SimpleEntry<URLConnection, String> POST(Proxy proxy, String url, Map<String, String> params,
			int connect_timeout, int read_timeout, Map<String, String> headers) throws IOException {
		return content(postQuery(method(connect(proxy, url, connect_timeout, read_timeout, headers), "POST"), params));
	}

	public static Map.Entry<URLConnection, String> POST(Proxy proxy, String url, Map<String, String> params,
			List<Entry<String, InputStream>> files, int connect_timeout, int read_timeout, Map<String, String> headers)
			throws IOException {
		return content(postMultipart(method(connect(proxy, url, connect_timeout, read_timeout, headers), "POST"), params,
				files));
	}

	protected static URLConnection postMultipart(URLConnection conn, Map<String, String> params,
			List<Entry<String, InputStream>> files) throws IOException {

		if ((params == null || params.size() == 0) && (files == null || files.size() == 0)) { return conn; }

		conn.setDoOutput(true);

		String boundary = Long.toHexString(System.currentTimeMillis());
		conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

		try (NMultiPartOutputStream mos = new NMultiPartOutputStream(conn.getOutputStream(), boundary)) {

			if (params != null) {
				for (Entry<String, String> param : params.entrySet()) {
					multipart(mos, param.getKey(), param.getValue());
				}
			}

			if (files != null) {
				for (Entry<String, InputStream> file : files) {
					multipart(mos, file.getKey(), file.getValue());
				}
			}
		}

		return conn;
	}

	protected static URLConnection postQuery(URLConnection conn, Map<String, String> params) throws IOException {

		if (params == null) { return conn; }
		conn.setDoOutput(true);

		try (OutputStream out = conn.getOutputStream()) {
			out.write(QueryStringUtil.query(params).getBytes("UTF-8"));
		}

		return conn;
	}

	public static void setMaxConnections(int conns) {

		System.setProperty("http.keepAlive", "true");

		if (conns > 5) {
			System.setProperty("http.maxConnections", "" + conns);
		} else {
			throw new IllegalArgumentException("max connections must greater than 5");
		}
	}

	protected static boolean supported(String charset) {
		return charset == null ? false : Charset.isSupported(charset);
	}

}
