package org.lendingclub.trident.swarm;

import com.google.common.base.MoreObjects;

public class SwarmToken {

	private SwarmNodeType nodeType;
	private String token;
	private String address;

	public SwarmToken(SwarmNodeType nodeType, String token, String address) {
		this.nodeType = nodeType;
		this.token = token;
		this.address = address;
	}

	public String getToken() {
		return token;
	}

	public String getAddress() {
		return address;
	}

	public SwarmNodeType getNodeType() {
		return nodeType;
	}

	public String toString() {
		String masked = token.length() > 20 ? token.substring(0, token.length() - 15) + "***************" : token;
		return MoreObjects.toStringHelper(this).add("swarmNodeType", nodeType.toString()).add("token", masked)
				.add("address", address).toString();
	}
}
