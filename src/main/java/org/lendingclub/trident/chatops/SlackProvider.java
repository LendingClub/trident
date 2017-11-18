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
import com.fasterxml.jackson.databind.node.MissingNode;
import com.google.common.base.Strings;

import io.macgyver.okrest3.OkRestClient;
import io.macgyver.okrest3.OkRestResponse;
import okhttp3.FormBody;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 
 * @author rschoening
 *
 */
public class SlackProvider extends ChatProvider {

	Logger logger = LoggerFactory.getLogger(SlackProvider.class);
	JsonNode config;
	OkRestClient client;

	@Override
	public void init(JsonNode config) {
		

	
		OkRestClient.Builder builder = new OkRestClient.Builder();

		
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
			String url = config.path("url").asText("https://slack.com/api");

			if (!m.getChannel().isPresent()) {
				m.withChannel(getDefaultChannel().orElse("#general"));
			}
			String room = m.getRoom().orElse(getDefaultChannel().orElse("#general"));
			
			String token = config.path("token").asText(null);
			if (Strings.isNullOrEmpty(token)) {
				throw new IllegalStateException("token not specified");
			}
			RequestBody formBody = new FormBody.Builder().add("channel", room).add("text", m.getMessage()).add("token", token).build(); 
			response = client.uri(url).path("chat.postMessage")
					.post(formBody)
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

	
	public Optional<String> getDefaultChannel() {
		return Optional.ofNullable(config.path("defaultChannel").asText("#general"));
	}
	
	
	public static void main(String [] args) {
		SlackProvider sp = new SlackProvider();
		
		sp.init(JsonUtil.createObjectNode().put("token", "xoxp-262987616752-264524392646-264736394487-8ba03315800ac3e5e2ccd779aa58e223"));
		sp.send(new ChatMessage(null).withMessage("message "+System.currentTimeMillis()));
	}
}
