package org.lendingclub.trident.chatops;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

public class LoggingChatProvider extends ChatProvider {

	Logger logger = LoggerFactory.getLogger(LoggingChatProvider.class);
	
	public void init(JsonNode n) {
		
	}
	@Override
	public void send(ChatMessage m) {
		
		logger.info("sending: {}",m);

	}

}
