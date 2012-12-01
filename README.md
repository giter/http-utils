http-util
===================

Pure http crawler URL implemented by `java.net.URL`

Instance
===================

  HttpUtil util = HttpUtil.instance()
  
Connect timeout(ms)
===================

  util.connect(3000)
  
Read timeout(ms)
===================

  util.connect(30000)

Do not follow 301/302 Redirection
===================

	util.follow(false)
	
Callback to get HttpURLConnection
===================

	util.callback(new HttpCallback(){...});

Set HTTP HEADs(like cookie,user-agent,etc)
===================

	util.headers("Cookie: foo=bar;")
	
Do NOT use cookies
===================

	util.persist(false)
	
Proxy interface
===================

	util.proxier(new Proxier(){...});

GET/POST/DELETE
===================

	util.GET("http://foo/bar/");
	util.POST("http://foo/bar/",params);
	util.DELETE("http://foo/bar/");