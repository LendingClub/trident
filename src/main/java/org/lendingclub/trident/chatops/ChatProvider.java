package org.lendingclub.trident.chatops;

import com.fasterxml.jackson.databind.JsonNode;

public abstract class ChatProvider {

	public abstract void init(JsonNode config);
	public abstract void send(ChatMessage m);
}
