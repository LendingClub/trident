package org.lendingclub.trident.chatops;

import java.io.IOException;
import java.util.Optional;

import org.lendingclub.trident.Trident;
import org.lendingclub.trident.util.JsonUtil;
import org.lendingclub.trident.util.ProxyManager;
import org.lendingclub.trident.util.TridentProxySelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Strings;

import io.macgyver.okrest3.OkRestClient;
import io.macgyver.okrest3.OkRestResponse;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * A Config entry is required to enable the provider the attributes are as
 * follows: type=chatops name=default
 * provider=org.lendingclub.trident.chatops.HipChatProvider
 * url=https://api.hipchat.com (optional) token=<your api token>
 * 
 * @author rschoening
 *
 */
public class HipChatProvider extends ChatProvider {

	Logger logger = LoggerFactory.getLogger(HipChatProvider.class);
	JsonNode config;
	OkRestClient client;

	@Override
	public void init(JsonNode config) {
		Interceptor auth = new Interceptor() {

			@Override
			public Response intercept(Chain chain) throws IOException {
				String token = config.path("token").asText();
				if (Strings.isNullOrEmpty(token)) {
					logger.warn("token not provided");
				}
				Request request = chain.request().newBuilder().addHeader("Authorization", "Bearer " + token).build();
				return chain.proceed(request);

			}

		};

		Interceptor l = new Interceptor() {

			@Override
			public Response intercept(Chain chain) throws IOException {
				Response response = null;
				try {

					response = chain.proceed(chain.request());

					return response;
				} finally {
					logger.info("POST {} - {}", chain.request().url().toString(),
							response == null ? -1 : response.code());
				}
			}

		};
		OkRestClient.Builder builder = new OkRestClient.Builder();

		builder = builder.withInterceptor(auth);
		String proxyName = config.path("proxy").asText();
		if (!Strings.isNullOrEmpty(proxyName)) {
			builder.withOkHttpClientConfig(cc -> {
				cc.proxySelector(new TridentProxySelector());
			});

		}

		client = builder.build();
		this.config = config;
	}

	@Override
	public void send(ChatMessage m) {
		OkRestResponse response = null;
		try {
			String url = config.path("url").asText("https://api.hipchat.com");

			JsonNode message = JsonUtil.createObjectNode().put("message", m.getMessage()).put("message_format", "text");

			String room = m.getRoom().orElse(getDefaultRoom().orElse(null));
			if (Strings.isNullOrEmpty(room)) {
				logger.warn("no room specified in message and no default room is available");
				return;
			}
			response = client.uri(url).path("/v2/room").path(room).path("notification")
					.post(RequestBody.create(okhttp3.MediaType.parse("application/json"), message.toString()))
					.execute();

			if (response.response().isSuccessful()) {
				logger.info("send message: {}", m);
			} else {
				logger.warn("failed to send message: {}", m);
			}

		} finally {
			if (response != null) {
				// This is ugly.  We'll add a close() method to OkRestResponse that closes OkHttp Response
				// when we don't read the body. 
				Response okHttpResponse = response.response();
				if (okHttpResponse != null) {
					okHttpResponse.close();
				}
			}
		}

	}

	public Optional<String> getDefaultRoom() {
		return Optional.ofNullable(config.path("defaultRoom").asText(null));
	}
}
