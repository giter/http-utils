package giter.http.utils.test;

import giter.http.utils.QueryStringUtil;

import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

public class TestQueryStringUtil {

	@Test
	public void parseEmptyString() {
		Assert.assertTrue(QueryStringUtil.parse(null).size() == 0);
		Assert.assertTrue(QueryStringUtil.parse("").size() == 0);
		Assert.assertTrue(QueryStringUtil.parse("\n").size() == 0);
		Assert.assertTrue(QueryStringUtil.parse(" \n").size() == 0);
		Assert.assertTrue(QueryStringUtil.parse("\t \n").size() == 0);
	}

	@Test
	public void parseMultipleParams() {

		// special string %20 should convert to whitespace
		Map<String, String> parsed = QueryStringUtil.parse("a=1&b= %201%201%20 ");
		Assert.assertEquals(2, parsed.size());
		Assert.assertEquals("1", parsed.get("a"));
		Assert.assertEquals(" 1 1 ", parsed.get("b"));

		// special string + should convert to whitespace
		parsed = QueryStringUtil.parse("a=1=+&a=2");
		Assert.assertTrue(parsed.size() == 1);
		Assert.assertEquals("1= ", parsed.get("a"));

		// first value should be returned
		parsed = QueryStringUtil.parse("a=1&c&d&e&f&f=1&a");
		Assert.assertTrue(parsed.size() == 5);
		Assert.assertEquals("1", parsed.get("a"));
		Assert.assertEquals("", parsed.get("f"));

		// ? leading should be ignored
		parsed = QueryStringUtil.parse("??=i&a&a=1");
		Assert.assertTrue(parsed.size() == 2);
		Assert.assertEquals("i", parsed.get("?"));
		Assert.assertEquals("", parsed.get("a"));

	}

}
