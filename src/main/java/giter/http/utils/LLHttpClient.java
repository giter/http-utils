package giter.http.utils;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
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
import java.util.zip.Inflater;
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

  public static final Charset DEFAULT_CHARSET = Charset.forName("utf-8");
  public final static Pattern CHARSET_PATTERN = Pattern
      .compile(
          "['\" ;]charset\\s*=([^'\" ]+)[ '\"]|charset\\s*=\\s*\"?([^'\\\" ]+)|['\" ;]encoding\\s*=([^'\" ]+)[ '\"]|encoding\\s*=\\s*\"?([^'\\\" ]+)",
          Pattern.CASE_INSENSITIVE);

  private static final int BUF_SIZE = 4096;
  private static final int EXP_SIZE = 20 * 1024;

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

  /**
   * 根据Connection判断当前网页编码，默认使用UTF-8
   * 
   * @param conn
   *          URLConnection
   * @return 当前网页编码
   */
  protected static String charsetFromHead(URLConnection conn) {

    String contentType = conn.getHeaderField("Content-Type");

    if (contentType == null) return null;

    contentType = contentType.toLowerCase();

    String encoding = null;

    for (String cs : new String[] { "charset=", "charset =" }) {
      if (contentType.contains(cs)) {
        encoding = contentType.substring(contentType.indexOf(cs) + cs.length()).trim().toLowerCase();
        break;
      }
    }

    // 处理未在IANA列表中的字符集，如x-gbk,认为这些字符集都应该被支持
    // 所有写x-gbk的都是2B青年
    if (encoding != null && (encoding.startsWith("x-"))) {
      encoding = encoding.substring(2);
    }

    return encoding;
  }

  /**
   * Connect to url using specified method and proxy
   * 
   * @param proxy
   *          using Proxy.NO_PROXY if no proxy specified
   * @param method
   *          http method
   * @param url
   *          http url
   * @param connect_timeout
   *          http connect timeout
   * @param read_timeout
   *          http read timeout
   * @param headers
   *          http headers
   * @return url connection
   * @throws IOException
   *           any ioexception
   */
  public static URLConnection connect(Proxy proxy, String method, String url, int connect_timeout, int read_timeout,
      Map<String, String> headers) throws IOException {

    URLConnection conn = new URL(url).openConnection(proxy == null ? Proxy.NO_PROXY : proxy);

    conn.setConnectTimeout(connect_timeout);
    conn.setReadTimeout(read_timeout);

    if (conn instanceof HttpURLConnection) {

      // we will manage redirection by ourself,so disable here
      ((HttpURLConnection) conn).setInstanceFollowRedirects(false);

      if (method != null) {
        ((HttpURLConnection) conn).setRequestMethod(method);
      }
    }

    conn.setRequestProperty("Accept-Charset", "GBK,utf-8;q=0.7,*;q=0.3");
    conn.setRequestProperty("Accept-Encoding", "gzip,deflate");
    conn.setRequestProperty("Accept-Language", "zh-cn,zh;q=0.5");

    for (Entry<String, String> h : headers.entrySet()) {
      conn.setRequestProperty(h.getKey(), h.getValue());
    }

    return conn;
  }

  public static SimpleEntry<URLConnection, String> content(URLConnection conn) throws IOException {

    byte[] raw = null;

    try (InputStream in = getInputStream(conn); ByteArrayOutputStream bos = new ByteArrayOutputStream(EXP_SIZE)) {
      copy(in, bos);
      raw = bos.toByteArray();
    }

    String cs = charsetFromHead(conn);

    if (!supportedCharset(cs)) {
      byte[] buf = new byte[BUF_SIZE];
      // 假设charset必须出现在页面的最前的4KB
      System.arraycopy(raw, 0, buf, 0, raw.length > BUF_SIZE ? BUF_SIZE : raw.length);
      cs = charsetFromContent(new String(buf, "ASCII"));
    }

    // 如果是低级编码(GB2312)，则转换成对应的更高级编码(GB18030)
    if ("gb2312".equals(cs)) {
      cs = "gb18030";
    }

    Charset charset = null;

    if (!supportedCharset(cs)) {
      charset = DEFAULT_CHARSET;
    } else {
      charset = Charset.forName(cs);
    }

    return new SimpleEntry<>(conn, new String(raw, charset));
  }

  public static SimpleEntry<URLConnection, String> DELETE(Proxy proxy, String url, int connect_timeout,
      int read_timeout, Map<String, String> headers) throws IOException {
    return content(connect(proxy, "DELETE", url, connect_timeout, read_timeout, headers));
  }

  public static SimpleEntry<URLConnection, String> GET(Proxy proxy, String url, int connect_timeout, int read_timeout,
      Map<String, String> headers) throws IOException {
    return content(connect(proxy, "GET", url, connect_timeout, read_timeout, headers));
  }

  public static InputStream getInputStream(URLConnection conn) throws IOException {

    InputStream cin = conn.getInputStream();

    // check content-encoding see if this stream is a gzipped stream
    String encoding = conn.getHeaderField("Content-Encoding");

    if (encoding != null) {
      switch (encoding.toLowerCase()) {
      case "gzip":
        return new GZIPInputStream(cin);
      case "deflate":
        return new InflaterInputStream(cin, new Inflater(true));
      default:
        throw new IOException("Unsupported encoding format: " + encoding);
      }
    }

    // check gzip magic to see the same thing
    // sometimes a stream may gzipped stream without content-encoding
    BufferedInputStream bi = new BufferedInputStream(cin, 4096);
    bi.mark(2);

    if ((bi.read() == (GZIPInputStream.GZIP_MAGIC & 0xff)) && (bi.read() == ((GZIPInputStream.GZIP_MAGIC >> 8) & 0xff))) {
      bi.reset();
      return new GZIPInputStream(bi);
    }

    bi.reset();
    return bi;
  }

  protected static void multipart(NMultiPartOutputStream mos, String key, InputStream in) throws IOException {

    mos.startPart("application/octet-stream", new String[] { "Content-Disposition: form-data; name=\"" + key
        + "\"; filename=\"" + key + "\"" });

    copy(in, mos);
  }

  public static void copy(InputStream in, OutputStream out) throws IOException {

    byte[] buff = new byte[BUF_SIZE];
    int n = -1;

    try {
      while ((n = in.read(buff)) >= 0) {
        out.write(buff, 0, n);
      }
    } catch (EOFException eof) {
      // reach EOF , return
    }
  }

  protected static void multipart(NMultiPartOutputStream mos, String key, String val) throws IOException {
    mos.startPart("text/plain", new String[] { "Content-Disposition: form-data; name=\"" + key + "\"" }).write(
        val.getBytes());
  }

  public static SimpleEntry<URLConnection, String> POST(Proxy proxy, String url, Map<String, String> params,
      int connect_timeout, int read_timeout, Map<String, String> headers) throws IOException {
    return content(postQuery(connect(proxy, "POST", url, connect_timeout, read_timeout, headers), params));
  }

  public static Map.Entry<URLConnection, String> POST(Proxy proxy, String url, Map<String, String> params,
      List<Entry<String, InputStream>> files, int connect_timeout, int read_timeout, Map<String, String> headers)
      throws IOException {
    return content(postMultipart(connect(proxy, "POST", url, connect_timeout, read_timeout, headers), params, files));
  }

  public static URLConnection postMultipart(URLConnection conn, Map<String, String> params,
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

  public static URLConnection postQuery(URLConnection conn, Map<String, String> params) throws IOException {

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

  protected static boolean supportedCharset(String charset) {
    return charset == null ? false : Charset.isSupported(charset);
  }

}
