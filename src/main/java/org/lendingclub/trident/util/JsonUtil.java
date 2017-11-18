package org.lendingclub.trident.util;

import org.lendingclub.trident.TridentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class JsonUtil {

	static final ObjectMapper mapper = new ObjectMapper();
	static Logger logger = LoggerFactory.getLogger(JsonUtil.class);

	public static ObjectMapper getObjectMapper() {
		return mapper;
	}

	public static ObjectNode createObjectNode() {
		return getObjectMapper().createObjectNode();
	}

	public static ArrayNode createArrayNode() {
		return getObjectMapper().createArrayNode();
	}

	public static String prettyFormat(Object n) {
		try {
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(n);
		} catch (JsonProcessingException e) {
			throw new TridentException(e);
		}
	}

	public static void logInfo(String message, Object n) {
		logInfo(JsonUtil.class,message,n);
	}
	public static void logInfo(Logger logger, String message, Object n) {
		try {
			if (logger!=null && logger.isInfoEnabled()) {

				logger.info("{} - \n{}", message, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(n));
			}
		} catch (JsonProcessingException e) {
			logger.warn("problem logging: {}", e.toString());
		}
	}

	public static void logInfo(Class z, String message, Object n) {

		if (z != null) {
			logInfo(LoggerFactory.getLogger(z), message, n);
		}

	}

	public static void logDebug(Class z, String message, Object n) {

		if (z != null) {
			logDebug(LoggerFactory.getLogger(z), message, n);
		}
	}

	public static void logDebug(Logger log, String message, Object n) {
		try {

			if (log != null && log.isDebugEnabled()) {
				log.debug("{} - \n{}", message, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(n));
			}
		} catch (

		JsonProcessingException e) {
			logger.warn("problem logging: {}", e.toString());
		}

	}
}
