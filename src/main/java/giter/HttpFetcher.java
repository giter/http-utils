package giter;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import javax.xml.bind.DataBindingException;
import javax.xml.bind.JAXB;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.sax.SAXSource;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLFilterImpl;

/**
 * Simple HTTP Fetcher implemented by jdk URL
 * 
 * @author giter
 */
public abstract class HttpFetcher {

	public interface HttpCallback {
		public void connection(final HttpURLConnection conn);
	}

	private static class XMLDefaultNamespaceFilter extends XMLFilterImpl {

		private final String[] defaultNamespaces;

		public XMLDefaultNamespaceFilter(XMLReader arg,
				String[] defaultNamespaces) {
			super(arg);
			this.defaultNamespaces = defaultNamespaces;
		}

		@Override
		public void startElement(String uri, String localName, String qName,
				Attributes attributes) throws SAXException {

			String ns = uri;

			if (uri == null || "".equals(uri) && defaultNamespaces != null
					&& defaultNamespaces.length > 0) {
				ns = defaultNamespaces[0];
			} else if (localName == null || "".equals(localName)) {
				for (String _uri : defaultNamespaces) {
					if (_uri.equals(uri)) {
						ns = defaultNamespaces[0];
						break;
					}
				}
			}

			super.startElement(ns, localName, qName, attributes);
		}
	}

	private static final Map<String, Map<String, String>> COOKIES = new ConcurrentHashMap<>();

	private static boolean useRandomAgent = true;
	private static boolean useFakeReferer = true;

	static {
		setMaxConnections(30);
		setFollowRedirect(false);
	}

	public static final String[] AGENTS = new String[] {
			"Mozilla/5.0 (compatible; MSIE 10.0; Windows NT 6.1; WOW64; Trident/6.0)",
			"Mozilla/5.0 (compatible; MSIE 9.0; Windows NT 6.1; Trident/5.0)",
			"Mozilla/4.0 (compatible; MSIE 8.0; Windows NT 6.1; Trident/4.0)",
			"Mozilla/5.0 (Windows NT 6.1; rv:15.0) Gecko/20120716 Firefox/15.0a2",
			"Mozilla/5.0 (Windows NT 6.1; Win64; x64; rv:14.0) Gecko/20120405 Firefox/14.0a1",
			"Mozilla/5.0 (Windows NT 6.1; rv:12.0) Gecko/20120403211507 Firefox/12.0",
			"Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.1 (KHTML, like Gecko) Chrome/22.0.1207.1 Safari/537.1",
			"Mozilla/5.0 (X11; CrOS i686 2268.111.0) AppleWebKit/536.11 (KHTML, like Gecko) Chrome/20.0.1132.57 Safari/536.11",
			"Mozilla/5.0 (Windows NT 6.2; WOW64) AppleWebKit/537.1 (KHTML, like Gecko) Chrome/19.77.34.5 Safari/537.1",
			"Opera/9.80 (Windows NT 6.1; U; es-ES) Presto/2.9.181 Version/12.00",
			"Opera/9.80 (Macintosh; Intel Mac OS X 10.6.8; U; fr) Presto/2.9.168 Version/11.52",
			"Opera/9.80 (X11; Linux x86_64; U; bg) Presto/2.8.131 Version/11.10",
			"Mozilla/5.0 (iPad; CPU OS 6_0 like Mac OS X) AppleWebKit/536.26 (KHTML, like Gecko) Version/6.0 Mobile/10A5355d Safari/8536.25",
			"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_7_3) AppleWebKit/534.55.3 (KHTML, like Gecko) Version/5.1.3 Safari/534.53.10",
			"Mozilla/5.0 (Macintosh; U; Intel Mac OS X 10_6_8; de-at) AppleWebKit/533.21.1 (KHTML, like Gecko) Version/5.0.5 Safari/533.21.1" };

	public static byte[] UTF32BE = { 0x00, 0x00, (byte) 0xFE, (byte) 0xFF };
	public static byte[] UTF32LE = { (byte) 0xFF, (byte) 0xFE, 0x00, 0x00 };
	public static byte[] UTF16BE = { (byte) 0xFE, (byte) 0xFF };
	public static byte[] UTF16LE = { (byte) 0xFF, (byte) 0xFE };
	public static byte[] UTF8 = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };

	public static final int DEFAULT_READ_TIMEOUT = 0;
	public static final int DEFAULT_CONNECT_TIMEOUT = 5000;

	public static final String DEFAULT_CHARSET = "UTF-8";

	public final static Pattern CHARSET_PATTERN = Pattern
			.compile(
					"['\" ;]charset\\s*=([^'\" ]+)[ '\"]|charset\\s*=\\s*\"?([^'\\\" ]+)",
					Pattern.CASE_INSENSITIVE);

	private static final Random rander = new Random();

	public static void addCookie(String domain, String key, String value) {

		domain = domain.replaceAll("^.", "");

		Map<String, String> cookies = COOKIES.get(domain);

		if (cookies == null) {
			cookies = new ConcurrentHashMap<String, String>();
			COOKIES.put(domain, cookies);
		}

		cookies.put(key, value);
	}

	protected static String charsetFromContent(String content) {

		Matcher matcher = CHARSET_PATTERN.matcher(content);

		if (matcher.find()) {

			String charset = matcher.group(1);

			if (charset == null) {
				charset = matcher.group(2);
			} else {
				// 处理未在IANA列表中的字符集，如x-gbk
				if (charset.startsWith("x-") || charset.startsWith("X-")) {
					charset = charset.substring(2);
				}
			}

			if (Charset.isSupported(charset)) {
				return charset;
			}
		}
		return null;
	}

	protected static HttpURLConnection connect(String method, String url,
			int connect_timeout, int read_timeout, String... headers)
			throws IOException {

		HttpURLConnection conn = (HttpURLConnection) new URL(url)
				.openConnection();

		conn.setUseCaches(false);
		conn.setRequestMethod(method);
		conn.setConnectTimeout(connect_timeout);
		conn.setReadTimeout(read_timeout);

		conn.setRequestProperty("Accept-Charset", "GBK,utf-8;q=0.7,*;q=0.3");
		conn.setRequestProperty("Accept-Encoding", "gzip,deflate");
		conn.setRequestProperty("Accept-Language", "zh-cn,zh;q=0.5");

		boolean referer = false;
		boolean agent = false;

		Map<String, String> cookies = cookies(conn);

		for (String header : headers) {
			String[] pair = header.split(":", 2);
			if (pair.length == 2) {

				switch (pair[0].trim().toLowerCase()) {
				case "cookie":
					for (String cookie : pair[1].split(":")) {
						if (cookie.contains("=")) {
							String[] kv = cookie.split("=", 2);
							cookies.put(kv[0], kv[1]);
						}
					}
					break;
				case "referer":
					referer = true;
					break;
				case "user-agent":
					agent = true;
					break;
				default:
					conn.setRequestProperty(pair[0], pair[1]);
				}
			}
		}

		if (cookies.size() > 0) {

			StringBuilder sb = new StringBuilder(100);
			boolean first = true;

			for (Entry<String, String> cookie : cookies.entrySet()) {

				if (!first) {
					sb.append("; ");
				} else {
					first = false;
				}

				sb.append(cookie.getKey());
				sb.append('=');
				sb.append(cookie.getValue());
			}

			conn.setRequestProperty("Cookie", sb.toString());
		}

		if (useRandomAgent && !agent) {
			conn.setRequestProperty("User-Agent", randomAgent());
		}

		if (useFakeReferer && !referer) {
			conn.setRequestProperty("Referer", url);
		}

		return conn;
	}

	protected static HttpURLConnection connect(String method, String url,
			int connect_timeout, String... options) throws IOException {
		return connect(method, url, connect_timeout, DEFAULT_READ_TIMEOUT,
				options);
	}

	protected static HttpURLConnection connect(String method, String url,
			String... options) throws IOException {
		return connect(method, url, DEFAULT_CONNECT_TIMEOUT, options);
	}

	protected static String content(HttpURLConnection conn) throws IOException {

		try (InputStream in = getInputStream(conn)) {

			byte[] bs = new byte[4096];
			ByteArrayOutputStream bos = new ByteArrayOutputStream(4096);

			int n = 0;
			while ((n = in.read(bs)) > 0) {
				bos.write(bs, 0, n);
			}

			String charset = encodingFromHttpHead(conn);
			byte[] bytes = bos.toByteArray();

			// 处理未在IANA列表中的字符集，如x-gbk
			if (charset != null) {
				if (charset.startsWith("x-") || charset.startsWith("X-")) {
					charset = charset.substring(2);
				}
			}

			if (charset == null || isSupported(charset) == false) {
				// 假设charset必须出现在页面的最前的4KB
				int length = bytes.length > 4096 ? 4096 : bytes.length;
				System.arraycopy(bytes, 0, bs, 0, length);
				String head = new String(bs, "ASCII");
				charset = charsetFromContent(head);
			}

			// 如果是GB2312编码，则转换成对应的更高级编码
			if (charset != null) {
				if (Charset.forName(charset).equals(Charset.forName("GB2312"))) {
					if (isSupported("GB18030")) {
						charset = "GB18030";
					} else if (isSupported("GBK")) {
						charset = "GBK";
					}
				}
			}

			if (charset == null || isSupported(charset) == false) {
				charset = DEFAULT_CHARSET;
			}

			return new String(bytes, charset);
		}
	}

	protected static Map<String, String> cookies(HttpURLConnection conn) {

		Map<String, String> m = new HashMap<>();

		String host = conn.getURL().getHost();

		for (Entry<String, Map<String, String>> cookies : COOKIES.entrySet()) {
			if (host.endsWith(cookies.getKey())) {
				for (Entry<String, String> cookie : cookies.getValue()
						.entrySet()) {
					m.put(cookie.getKey(), cookie.getValue());
				}
			}
		}

		return m;
	}

	public static String DELETE(String url, int connect_timeout,
			int read_timeout, HttpCallback callback, String... headers)
			throws IOException {
		return text("DELETE", url, connect_timeout, read_timeout, callback,
				headers);
	}

	public static String DELETE(String url, int connect_timeout,
			int read_timeout, String... headers) throws IOException {
		return DELETE(url, connect_timeout, read_timeout, (HttpCallback) null,
				headers);
	}

	public static String DELETE(String url, int connect_timeout,
			String... headers) throws IOException {
		return DELETE(url, connect_timeout, DEFAULT_READ_TIMEOUT,
				(HttpCallback) null, headers);
	}

	public static String DELETE(String path, Map<String, String> params,
			int connect_timeout, String... headers) throws IOException {

		StringBuilder sb = new StringBuilder(path.replace("\\?.+$", "?"))
				.append(queryString(params));

		return DELETE(sb.toString(), connect_timeout, headers);

	}

	public static String DELETE(String path, Map<String, String> params,
			String... headers) throws IOException {
		return DELETE(path, params, DEFAULT_CONNECT_TIMEOUT, headers);
	}

	public static String DELETE(String url, String... headers)
			throws IOException {
		return DELETE(url, DEFAULT_CONNECT_TIMEOUT, DEFAULT_READ_TIMEOUT,
				(HttpCallback) null, headers);
	}

	/**
	 * 根据Connection判断当前网页编码，默认使用UTF-8
	 * 
	 * @param conn
	 *            URLConnection
	 * @return 当前网页编码
	 */
	protected static String encodingFromHttpHead(HttpURLConnection conn) {

		String contentType = conn.getHeaderField("Content-Type");
		if (contentType != null) {

			contentType = contentType.toLowerCase();

			for (String cs : new String[] { "charset=", "charset =" }) {
				if (contentType.contains(cs)) {
					return contentType.substring(
							contentType.indexOf(cs) + cs.length()).trim();
				}
			}
		}

		return null;
	}

	public static String GET(String url, int connect_timeout, int read_timeout,
			HttpCallback callback, String... headers) throws IOException {
		return text("GET", url, connect_timeout, read_timeout, callback,
				headers);
	}

	public static String GET(String url, int connect_timeout, int read_timeout,
			String... headers) throws IOException {
		return GET(url, connect_timeout, read_timeout, (HttpCallback) null,
				headers);
	}

	/**
	 * 获取URL内容 - GET
	 * 
	 * @param url
	 * @param connect_timeout
	 *            连接超时时间
	 * @return
	 * @throws IOException
	 */
	public static String GET(String url, int connect_timeout, String... headers)
			throws IOException {
		return GET(url, connect_timeout, DEFAULT_READ_TIMEOUT,
				(HttpCallback) null, headers);
	}

	/**
	 * 获取URL内容 - GET
	 * 
	 * @param path
	 *            此时path后跟?a=b&amp;c=d这样的参数都会被忽略
	 * @param params
	 *            参数列表
	 * @param connect_timeout
	 *            连接超时时间
	 * 
	 * @param headers
	 *            HTTP头信息
	 * @return
	 * @throws IOException
	 */
	public static String GET(String path, Map<String, String> params,
			int connect_timeout, String... headers) throws IOException {

		StringBuilder sb = new StringBuilder(path.replace("\\?.+$", ""));

		String qs = queryString(params);

		if (qs != null && qs.length() > 0) {
			sb.append('?').append(qs);
		}

		return GET(sb.toString(), connect_timeout, headers);
	}

	public static String GET(String path, Map<String, String> params,
			String... headers) throws IOException {
		return GET(path, params, DEFAULT_CONNECT_TIMEOUT, headers);
	}

	/**
	 * 获取URL内容 - GET
	 * 
	 * @param url
	 * @return
	 * @throws IOException
	 */
	public static String GET(String url, String... headers) throws IOException {
		return GET(url, DEFAULT_CONNECT_TIMEOUT, DEFAULT_READ_TIMEOUT,
				(HttpCallback) null, headers);
	}

	protected static InputStream getInputStream(HttpURLConnection conn)
			throws IOException {

		InputStream cin = conn.getInputStream();

		persistence(conn);

		String encoding = conn.getHeaderField("Content-Encoding");

		if (encoding != null) {
			switch (encoding.toLowerCase()) {
			case "gzip":
				return new GZIPInputStream(cin);
			case "deflate":
				return new InflaterInputStream(cin);
			}
		}

		BufferedInputStream bi = new BufferedInputStream(cin, 4096);
		bi.mark(2);

		if ((bi.read() == (GZIPInputStream.GZIP_MAGIC & 0xff))
				&& (bi.read() == ((GZIPInputStream.GZIP_MAGIC >> 8) & 0xff))) {
			bi.reset();
			return new GZIPInputStream(bi);
		}

		bi.reset();
		return bi;
	}

	private static boolean isSupported(String charset) {
		try {
			return Charset.isSupported(charset);
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	protected static void multipart(NMultiPartOutputStream mos, String key,
			InputStream in) throws IOException {

		byte[] buff = new byte[512];

		mos.startPart("application/octet-stream",
				new String[] { "Content-Disposition: form-data; name=\"" + key
						+ "\"; filename=\"" + key + "\"" });

		int n = -1;
		while ((n = in.read(buff)) > 0) {
			mos.write(buff, 0, n);
		}
	}

	protected static void multipart(NMultiPartOutputStream mos, String key,
			String val) throws IOException {
		mos.startPart("text/plain",
				new String[] { "Content-Disposition: form-data; name=\"" + key
						+ "\"" });
		mos.write(val.getBytes());
	}

	public static Map<String, String> parseQuery(String queryString) {

		Map<String, String> params = new HashMap<>();

		for (String s : queryString.split("&|\\?")) {
			if (s != null && s.contains("=")) {
				String[] pr = s.split("=");
				params.put(pr[0], pr[1]);
			}
		}

		return params;
	}

	protected static void persistence(HttpURLConnection conn) {

		for (Entry<String, List<String>> header : conn.getHeaderFields()
				.entrySet()) {

			if (header.getKey() != null) {

				if (header.getKey().equalsIgnoreCase("Set-Cookie")) {

					for (String cookie : header.getValue()) {

						String[] pieces = cookie.split(";");
						if (pieces[0].indexOf('=') > 0) {

							String[] kv = pieces[0].split("=", 2);

							String key = kv[0];
							String val = kv[1];
							String domain = conn.getURL().getHost();

							for (int i = 1; i < pieces.length; i++) {
								String[] p = pieces[i].split("=");
								if (p.length == 2) {
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
							}

							addCookie(domain, key, val);
						}
					}
				}
			}
		}
	}

	public static String POST(String url, Map<String, String> params,
			int connect_timeout, int read_timeout, HttpCallback callback,
			String... headers) throws IOException {

		HttpURLConnection conn = connect("POST", url, connect_timeout,
				read_timeout, headers);

		postQuery(conn, params);

		if (callback != null) {
			callback.connection(conn);
		}

		return content(conn);
	}

	public static String POST(String url, Map<String, String> params,
			int connect_timeout, int read_timeout, String... headers)
			throws IOException {
		return POST(url, params, connect_timeout, read_timeout,
				(HttpCallback) null, headers);
	}

	public static String POST(String url, Map<String, String> params,
			List<Entry<String, InputStream>> files, int connect_timeout,
			int read_timeout, String... headers) throws IOException {

		HttpURLConnection conn = postMultipart(
				connect("POST", url, connect_timeout, read_timeout, headers),
				params, files);

		return content(conn);
	}

	public static String POST(String url, Map<String, String> params,
			List<Entry<String, InputStream>> files, String... headers)
			throws IOException {
		return POST(url, params, files, DEFAULT_CONNECT_TIMEOUT,
				DEFAULT_READ_TIMEOUT, headers);
	}

	/**
	 * 获取URL内容 - POST
	 * 
	 * @param url
	 * @param params
	 * @return
	 * @throws IOException
	 */
	public static String POST(String url, Map<String, String> params,
			String... headers) throws IOException {
		return POST(url, params, DEFAULT_CONNECT_TIMEOUT, DEFAULT_READ_TIMEOUT,
				(HttpCallback) null, headers);
	}

	/**
	 * 获取URL内容 - POST
	 * 
	 * @param url
	 * @return
	 * @throws IOException
	 */
	public static String POST(String url, String... headers) throws IOException {
		return POST(url, null, DEFAULT_CONNECT_TIMEOUT, DEFAULT_READ_TIMEOUT,
				(HttpCallback) null, headers);
	}

	protected static HttpURLConnection postMultipart(HttpURLConnection conn,
			Map<String, String> params, List<Entry<String, InputStream>> files)
			throws IOException {

		if ((params != null && params.size() > 0)
				|| (files != null && files.size() > 0)) {

			conn.setDoOutput(true);
			String boundary = Long.toHexString(System.currentTimeMillis());
			conn.setRequestProperty("Content-Type",
					"multipart/form-data; boundary=" + boundary);

			try (OutputStream out = conn.getOutputStream();
					NMultiPartOutputStream mos = new NMultiPartOutputStream(
							out, boundary)) {

				if (params != null && params.size() > 0) {
					for (Entry<String, String> param : params.entrySet()) {
						multipart(mos, param.getKey(), param.getValue());
					}
				}

				if (files != null && files.size() > 0) {
					for (Entry<String, InputStream> file : files) {
						multipart(mos, file.getKey(), file.getValue());
					}
				}
			}

		}

		return conn;
	}

	protected static HttpURLConnection postQuery(HttpURLConnection conn,
			Map<String, String> params) throws IOException {

		if (params != null) {
			conn.setDoOutput(true);
			try (OutputStream out = conn.getOutputStream()) {
				out.write(queryString(params).getBytes("UTF-8"));
			}
		}

		return conn;
	}

	public static String queryString(Map<String, String> params)
			throws UnsupportedEncodingException {

		String query = "";

		if (params != null && params.size() > 0) {

			StringBuilder sb = new StringBuilder();

			String FIRST = "%s=%s";
			String SECONDARY = "&%s=%s";

			boolean first = true;
			for (Entry<String, String> entry : params.entrySet()) {

				String pattern = SECONDARY;

				if (first) {
					pattern = FIRST;
					first = false;
				}

				sb.append(String.format(pattern,
						URLEncoder.encode(entry.getKey(), "UTF-8"),
						URLEncoder.encode(entry.getValue(), "UTF-8")));
			}

			if (sb.length() > 0) {
				query = sb.toString();
			}

		}

		return query;
	}

	public static String randomAgent() {
		return AGENTS[rander.nextInt(AGENTS.length)];
	}

	private static void removeBOM(InputStream stream) throws IOException {

		if (removeBOM(stream, UTF32BE)) {
			return;
		}

		if (removeBOM(stream, UTF32LE)) {
			return;
		}

		if (removeBOM(stream, UTF16BE)) {
			return;
		}

		if (removeBOM(stream, UTF16LE)) {
			return;
		}

		if (removeBOM(stream, UTF8)) {
			return;
		}
	}

	private static boolean removeBOM(InputStream reader, byte[] bom)
			throws IOException {

		reader.mark(bom.length);

		byte[] possibleBOM = new byte[bom.length];
		reader.read(possibleBOM);

		for (int x = 0; x < bom.length; x++) {
			if (bom[x] != possibleBOM[x]) {
				reader.reset();
				return false;
			}
		}

		return true;
	}

	public static void setFollowRedirect(boolean b) {
		HttpURLConnection.setFollowRedirects(b);
	}

	public static void setMaxConnections(int conns) {

		System.setProperty("http.keepAlive", "true");

		if (conns > 5) {
			System.setProperty("http.maxConnections", "" + conns);
		} else {
			throw new IllegalArgumentException(
					"max connections must greater than 5");
		}
	}

	/**
	 * 使HTTP请求的Referer与请求地址相同防止一些硬屏蔽
	 * 
	 * @param useFakeReferer
	 */
	public static void setUseFakeReferer(boolean useFakeReferer) {
		HttpFetcher.useFakeReferer = useFakeReferer;
	}

	/**
	 * 使HTTP请求使用随机的UserAgent
	 * 
	 * @param b
	 */
	public static void setUseRandomAgent(boolean b) {
		HttpFetcher.useRandomAgent = b;
	}

	public static InputStream stream(String url, int connect_timeout,
			int read_timeout, String... headers) throws IOException {
		return getInputStream(connect("GET", url, connect_timeout,
				read_timeout, headers));
	}

	public static InputStream stream(String url, int connect_timeout,
			String... headers) throws IOException {
		return stream(url, connect_timeout, DEFAULT_READ_TIMEOUT, headers);
	}

	/**
	 * 取URL流 - GET
	 * 
	 * @param url
	 * @return
	 * @throws IOException
	 */
	public static InputStream stream(String url, String... headers)
			throws IOException {
		return stream(url, DEFAULT_CONNECT_TIMEOUT, DEFAULT_READ_TIMEOUT,
				headers);
	}

	public static String text(String method, String url, int connect_timeout,
			int read_timeout, HttpCallback callback, String... headers)
			throws IOException {

		HttpURLConnection conn = connect(method, url, connect_timeout,
				read_timeout, headers);

		if (callback != null) {
			callback.connection(conn);
		}

		return content(conn);
	}

	public static <T> T unmarshal(InputStream stream, Class<T> clazz,
			String... uris) throws IOException {

		BufferedInputStream bis;

		if (!(stream instanceof BufferedInputStream)) {
			bis = new BufferedInputStream(stream, 4096);
		} else {
			bis = (BufferedInputStream) stream;
		}

		removeBOM(bis);

		try {

			JAXBContext jc = JAXBContext.newInstance(clazz);
			Unmarshaller unmarshaller = jc.createUnmarshaller();

			SAXParserFactory factory = SAXParserFactory.newInstance();
			XMLReader reader = factory.newSAXParser().getXMLReader();

			XMLFilterImpl filter = new XMLDefaultNamespaceFilter(reader, uris);
			reader.setContentHandler(unmarshaller.getUnmarshallerHandler());
			SAXSource source = new SAXSource(filter, new InputSource(bis));

			return unmarshaller.unmarshal(source, clazz).getValue();

		} catch (JAXBException | SAXException | ParserConfigurationException e) {
			throw new DataBindingException(e);
		}
	}

	public static <T> T unmarshal(String url, Class<T> clazz, String... uris)
			throws IOException {
		return unmarshal(stream(url), clazz, uris);
	}

	public static <T> T unmarshal(String url, int connect_timeout,
			Class<T> clazz) throws IOException {
		return JAXB.unmarshal(stream(url, connect_timeout), clazz);
	}

	public static <T> T unmarshal(String url, int connect_timeout,
			int read_timeout, Class<T> clazz) throws IOException {
		return JAXB
				.unmarshal(stream(url, connect_timeout, read_timeout), clazz);
	}

}
