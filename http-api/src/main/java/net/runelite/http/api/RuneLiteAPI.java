/*
 * Copyright (c) 2017, Adam <Adam@sigterm.info>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.http.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import net.runelite.http.api.gson.ColorTypeAdapter;
import net.runelite.http.api.gson.InstantTypeAdapter;
import net.runelite.http.api.gson.IllegalReflectionExclusion;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

public class RuneLiteAPI
{
	private static final Logger logger = LoggerFactory.getLogger(RuneLiteAPI.class);

	public static final String RUNELITE_AUTH = "RUNELITE-AUTH";
	public static final String RUNELITE_MACHINEID = "RUNELITE-MACHINEID";

	public static OkHttpClient CLIENT;
	public static final Gson GSON;
	public static final MediaType JSON = MediaType.parse("application/json");
	public static String userAgent;

	private static final String BASE = "https://api.runelite.net";
	private static final String WSBASE = "https://api.runelite.net/ws";
	private static final String STATICBASE = "https://static.runelite.net";

	private static final String OPENOSRS_SESSION = "https://session.openosrs.dev";
	private static final String OPENOSRS_XTEA = "https://xtea.openosrs.dev";

	private static final Properties properties = new Properties();
	private static String version;

	private static final HttpUrl RL_URL = HttpUrl.parse("https://raw.githubusercontent.com/runelite/runelite/master/runelite-client/pom.xml");

	static
	{
		try
		{
			InputStream in = RuneLiteAPI.class.getResourceAsStream("/runelite.properties");
			properties.load(in);

			version = properties.getProperty("runelite.version");
			String commit = properties.getProperty("runelite.commit");
			boolean dirty = Boolean.parseBoolean(properties.getProperty("runelite.dirty"));

			userAgent = "RuneLite/" + version + "-" + commit + (dirty ? "+" : "");
		}
		catch (NumberFormatException e)
		{
			throw new RuntimeException("Version string has not been substituted; Re-run maven");
		}
		catch (IOException ex)
		{
			logger.error(null, ex);
		}

		CLIENT = new OkHttpClient.Builder()
			.pingInterval(30, TimeUnit.SECONDS)
			.addNetworkInterceptor(new Interceptor()
			{

				@Override
				public Response intercept(Chain chain) throws IOException
				{
					Request userAgentRequest = chain.request()
						.newBuilder()
						.header("User-Agent", userAgent)
						.build();
					return chain.proceed(userAgentRequest);
				}
			})
			.build();

		GsonBuilder gsonBuilder = new GsonBuilder();

		gsonBuilder
			.registerTypeAdapter(Instant.class, new InstantTypeAdapter())
			.registerTypeAdapter(Color.class, new ColorTypeAdapter());

		boolean assertionsEnabled = false;
		assert assertionsEnabled = true;
		if (assertionsEnabled)
		{
			IllegalReflectionExclusion jbe = new IllegalReflectionExclusion();
			gsonBuilder.addSerializationExclusionStrategy(jbe);
			gsonBuilder.addDeserializationExclusionStrategy(jbe);
		}

		GSON = gsonBuilder.create();
	}

	public static HttpUrl getSessionBase()
	{
		return HttpUrl.parse(OPENOSRS_SESSION);
	}

	public static HttpUrl getApiBase()
	{
		final String prop = System.getProperty("runelite.http-service.url");

		if (prop != null && !prop.isEmpty())
		{
			return HttpUrl.parse(prop);
		}

		return HttpUrl.parse(BASE + "/runelite-" + getVersion());
	}

	public static HttpUrl getApiBase2()
	{
		final String prop = System.getProperty("runelite.http-service.url");

		if (prop != null && !prop.isEmpty())
		{
			return HttpUrl.parse(prop);
		}

		return HttpUrl.parse(BASE + "/runelite-" + getVersion2());
	}

	public static HttpUrl getStaticBase()
	{
		final String prop = System.getProperty("runelite.static.url");

		if (prop != null && !prop.isEmpty())
		{
			return HttpUrl.parse(prop);
		}

		return HttpUrl.parse(STATICBASE);
	}

	public static HttpUrl getWsEndpoint()
	{
		final String prop = System.getProperty("runelite.ws.url");

		if (prop != null && !prop.isEmpty())
		{
			return HttpUrl.parse(prop);
		}

		return HttpUrl.parse(WSBASE);
	}

	public static HttpUrl getXteaBase()
	{
		return HttpUrl.parse(OPENOSRS_XTEA);
	}

	public static String getVersion()
	{
		if(version.equals(properties.getProperty("runelite.version"))){
			Request request = new Request.Builder()
					.url(RL_URL)
					.build();
			try {
				Response response = RuneLiteAPI.CLIENT.newCall(request).execute();
				if (response.isSuccessful()) {
					InputStream in = response.body().byteStream();
					Document document = DocumentBuilderFactory.newInstance()
							.newDocumentBuilder()
							.parse(in);

					XPathFactory xpf = XPathFactory.newInstance();
					XPath xp = xpf.newXPath();
					version = xp.evaluate("/project/parent/version/text()",
							document.getDocumentElement());
				}
			} catch (SAXException | XPathExpressionException | ParserConfigurationException | IOException e) {
				logger.error(null, e);
			}
		}
		return version;
	}

	public static String getVersion2()
	{
		version = properties.getProperty("runelite.version");
		return version;
	}

	public static void setVersion(String version)
	{
		RuneLiteAPI.version = version;
	}

}
