package org.lendingclub.trident.settings;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum NotificationChannels {
	
	HIPCHAT("hipChat"),
	EMAIL("email"),
	PAGERDUTY("pagerDuty");
	
	private String channelName;
	
	private NotificationChannels(String channelName) {
		this.channelName = channelName;
	}

	public String getChannelName() {
		return channelName;
	}
	
	public static NotificationChannels getByChannel(String channelName) {
		for (NotificationChannels a : NotificationChannels.values()) {
			if (a.getChannelName().equalsIgnoreCase(channelName)) {
				return a;
			}
		}
		return null;
	}
	
	public static boolean isValidChannel(String channelName) {
		for(String channel: getChannelValues()) {
			if ( channel.equalsIgnoreCase(channelName))
				return true;
		}
		return false;
	}

	public static List<String> getChannelValues() {
		return Arrays.stream(NotificationChannels.values()).map(f -> f.getChannelName().toString()).collect(Collectors.toList());
	}
}
