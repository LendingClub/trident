package org.lendingclub.trident.swarm.auth;

import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

public abstract class DockerPluginAuthorizationContext {

	private Boolean decision = null;
	private String reason = null;

	private JsonNode request;

	public static class DockerPluginRequestAuthorizationContext extends DockerPluginAuthorizationContext {

		public DockerPluginRequestAuthorizationContext(JsonNode n) {
			super(n);

		}

	}

	public static class DockerPluginResponseAuthorizationContext extends DockerPluginAuthorizationContext {
		public DockerPluginResponseAuthorizationContext(JsonNode n) {
			super(n);

		}
	}

	public DockerPluginAuthorizationContext(JsonNode n) {
		Preconditions.checkNotNull(n);
		this.request = n;
	}

	public JsonNode getPluginAuthorizationRequest() {
		return request;
	}

	public void permit(String reason) {
		decision = true;
		this.reason = reason;
	}

	public void deny(String reason) {
		decision = false;
		this.reason = reason;
	}

	public Optional<String> getReason() {
		return Optional.ofNullable(Strings.emptyToNull(reason));
	}

	public Optional<Boolean> isAuthorized() {
		return Optional.ofNullable(decision);
	}

}
