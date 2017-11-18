package org.lendingclub.trident.swarm.platform;

public enum BlueGreenState {

	LIVE("live"),
	DARK("dark"),
	DRAIN("drain");
	private final String value;
	
	BlueGreenState(String v) {
		this.value = v;
	}
	
	public String toString() {
		return value;
	}
	
}
