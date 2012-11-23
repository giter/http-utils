package giter;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.xml.bind.DataBindingException;
import javax.xml.bind.JAXB;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.sax.SAXSource;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLFilterImpl;

public class Unmarshaller {

	public static byte[] UTF32BE = { 0x00, 0x00, (byte) 0xFE, (byte) 0xFF };
	public static byte[] UTF32LE = { (byte) 0xFF, (byte) 0xFE, 0x00, 0x00 };
	public static byte[] UTF16BE = { (byte) 0xFE, (byte) 0xFF };
	public static byte[] UTF16LE = { (byte) 0xFF, (byte) 0xFE };
	public static byte[] UTF8 = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };

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
			javax.xml.bind.Unmarshaller unmarshaller = jc.createUnmarshaller();

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
		return unmarshal(HttpFetcher.stream(url), clazz, uris);
	}

	public static <T> T unmarshal(String url, int connect_timeout,
			Class<T> clazz) throws IOException {
		return JAXB.unmarshal(HttpFetcher.stream(url, connect_timeout), clazz);
	}

	public static <T> T unmarshal(String url, int connect_timeout,
			int read_timeout, Class<T> clazz) throws IOException {
		return JAXB.unmarshal(
				HttpFetcher.stream(url, connect_timeout, read_timeout), clazz);
	}

}
