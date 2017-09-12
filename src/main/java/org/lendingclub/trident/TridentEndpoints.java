package org.lendingclub.trident;

import org.lendingclub.trident.config.ConfigManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import com.fasterxml.jackson.databind.node.MissingNode;

public class TridentEndpoints {

	@Autowired
	Environment environment;

	@Autowired
	ConfigManager config;

	public String getUIEndpoint() {

		String uiEndpoint = System.getProperty("trident.ui.url");
		;
		if (uiEndpoint == null) {
			uiEndpoint = config.getConfig("trident", "core").orElse(MissingNode.getInstance()).path("uiEndpoint")
					.asText(null);
			if (uiEndpoint == null) {
				uiEndpoint = "http://localhost:" + environment.getProperty("local.server.port");
			}
		}
		while (uiEndpoint.endsWith("/")) {
			uiEndpoint = uiEndpoint.substring(0, uiEndpoint.length() - 1);
		}
		return uiEndpoint;
	}

	public String getAPIEndpoint() {
		String apiEndpoint = System.getProperty("trident.api.url");

		if (apiEndpoint == null) {
			apiEndpoint = config.getConfig("trident", "core").orElse(MissingNode.getInstance()).path("apiEndpoint")
					.asText(null);
			if (apiEndpoint == null) {
				apiEndpoint = "http://localhost:" + environment.getProperty("local.server.port") + "/api/trident";
			}
		}
		while (apiEndpoint.endsWith("/")) {
			apiEndpoint = apiEndpoint.substring(0, apiEndpoint.length() - 1);
		}
		return apiEndpoint;
	}
}
