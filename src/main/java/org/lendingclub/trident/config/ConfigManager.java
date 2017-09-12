package org.lendingclub.trident.config;

import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

public interface ConfigManager {


	public abstract Map<String,JsonNode> getConfigOfType(String type);
	public abstract Optional<JsonNode> getConfig(String type, String name);	
	public abstract void setValue(String type, String name, String key, String val, boolean b);
	public void reload();
}
