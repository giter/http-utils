package giter.http.utils.test;

import giter.http.utils.QueryStringUtil;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

public class TestQueryStringUtil {

  @Test
  public void parseEmptyString() {

    // null check
    Assert.assertEquals(0, QueryStringUtil.parse(null).size());

    // empty check
    Assert.assertEquals(0, QueryStringUtil.parse("").size());

    // trim check
    Assert.assertEquals(0, QueryStringUtil.parse("\n").size());
    Assert.assertEquals(0, QueryStringUtil.parse(" \n").size());
    Assert.assertEquals(0, QueryStringUtil.parse("\t\u0003 \n\u0000\u0001\u0002").size());
    Assert.assertEquals(1, QueryStringUtil.parse("\t\u0003 p\n\u0000p\u0001\u0002").size());
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
    parsed = QueryStringUtil.parse("a=1&c&d&&&&e&f&f=1&a");
    Assert.assertEquals(5, parsed.size());
    Assert.assertEquals("1", parsed.get("a"));
    Assert.assertEquals("", parsed.get("f"));

    // ? leading should be ignored
    parsed = QueryStringUtil.parse("??=i&a&a=1");
    Assert.assertTrue(parsed.size() == 2);
    Assert.assertEquals("i", parsed.get("?"));
    Assert.assertEquals("", parsed.get("a"));

  }

  @Test
  public void query() {

    // null check
    Assert.assertEquals("", QueryStringUtil.query(null));

    Map<String, String> params = new LinkedHashMap<>();

    // empty check
    Assert.assertEquals("", QueryStringUtil.query(params));

    // normal item
    params.put("1", "2");
    Assert.assertEquals("1=2", QueryStringUtil.query(params));

    // whitespace convension
    params.put("s", "  ");
    Assert.assertEquals("1=2&s=++", QueryStringUtil.query(params));

    // default encoding
    params.put("中", "文");
    Assert.assertEquals("1=2&s=++&%E4%B8%AD=%E6%96%87", QueryStringUtil.query(params));

    // another encoding
    params.put("中", "文");
    Assert.assertEquals("1=2&s=++&%D6%D0=%CE%C4", QueryStringUtil.query(params, "GBK"));

    // modification
    params.put("s", "");
    Assert.assertEquals("1=2&s=&%D6%D0=%CE%C4", QueryStringUtil.query(params, "GBK"));

    // make sure no cache
    params.clear();
    Assert.assertEquals("", QueryStringUtil.query(params));
  }

}
