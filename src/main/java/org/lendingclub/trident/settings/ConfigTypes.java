package org.lendingclub.trident.settings;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum ConfigTypes {

	TRIDENT("trident"),
	HIPCHAT("chatops"),
	PAGERDUTY("pagerduty"),
	EMAIL("email"),
	GIT("git"),
	AWS("aws"),
	ENVIRONMENT("environment");
	
	private String configType;
	
	private ConfigTypes(String configType) {
		this.configType = configType;
	}

	public String getConfigType() {
		return configType;
	}
	
	public static ConfigTypes getByConfigType(String configType) {
		for (ConfigTypes a : ConfigTypes.values()) {
			if (a.getConfigType().equalsIgnoreCase(configType)) {
				return a;
			}
		}
		return null;
	}
	
	public static boolean isValidConfigType(String configType) {
		for(String config: getConfigTypeValues()) {
			if ( config.equalsIgnoreCase(configType))
				return true;
		}
		return false;
	}

	public static List<String> getConfigTypeValues() {
		return Arrays.stream(ConfigTypes.values()).map(f -> f.getConfigType().toString()).collect(Collectors.toList());
	}
}
