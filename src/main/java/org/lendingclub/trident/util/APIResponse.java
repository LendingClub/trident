package org.lendingclub.trident.util;

import com.fasterxml.jackson.databind.JsonNode;

public class APIResponse {

	public static JsonNode ok(JsonNode data) {
		return JsonUtil.createObjectNode().put("status", "ok").set("result", data);
	}
	public static JsonNode ok() {
		return JsonUtil.createObjectNode().put("status", "ok");
	}
}
