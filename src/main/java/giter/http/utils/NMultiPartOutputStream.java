package giter.http.utils;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;

/**
 * Handle a multipart MIME response.
 */
public class NMultiPartOutputStream extends FilterOutputStream {

  private static byte[] __CRLF;
  private static byte[] __DASHDASH;

  public static String MULTIPART_MIXED = "multipart/mixed";
  public static String MULTIPART_X_MIXED_REPLACE = "multipart/x-mixed-replace";

  private static final Charset ISO_8859_1 = Charset.forName("ISO-8859-1");

  static {
    try {
      __CRLF = "\015\012".getBytes(ISO_8859_1);
      __DASHDASH = "--".getBytes(ISO_8859_1);
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
  }

  private final String boundary;
  private final byte[] boundaryBytes;

  private boolean inPart = false;

  public NMultiPartOutputStream(OutputStream out, String boundary) throws IOException {

    super(out);

    this.boundary = boundary;
    boundaryBytes = boundary.getBytes(ISO_8859_1);

    inPart = false;
  }

  /**
   * End the current part.
   * 
   * @exception IOException
   *              IOException
   */
  @Override
  public void close() throws IOException {
    if (inPart) out.write(__CRLF);
    out.write(__DASHDASH);
    out.write(boundaryBytes);
    out.write(__DASHDASH);
    out.write(__CRLF);
    inPart = false;
    super.close();
  }

  public String getBoundary() {
    return boundary;
  }

  public OutputStream getOut() {
    return out;
  }

  /**
   * Start creation of the next Content.
   */
  public void startPart(String contentType) throws IOException {
    if (inPart) out.write(__CRLF);
    inPart = true;
    out.write(__DASHDASH);
    out.write(boundaryBytes);
    out.write(__CRLF);
    out.write(("Content-Type: " + contentType).getBytes(ISO_8859_1));
    out.write(__CRLF);
    out.write(__CRLF);
  }

  /**
   * Start creation of the next Content.
   */
  public void startPart(String contentType, String[] headers) throws IOException {
    if (inPart) out.write(__CRLF);
    inPart = true;
    out.write(__DASHDASH);
    out.write(boundaryBytes);
    out.write(__CRLF);
    out.write(("Content-Type: " + contentType).getBytes(ISO_8859_1));
    out.write(__CRLF);
    for (int i = 0; headers != null && i < headers.length; i++) {
      out.write(headers[i].getBytes(ISO_8859_1));
      out.write(__CRLF);
    }
    out.write(__CRLF);
  }

}
