package org.lendingclub.trident.swarm;

import org.lendingclub.trident.event.TridentEvent;

public class DockerEvent extends TridentEvent {

	public String getSwarmClusterId() {
		return getEnvelope().path("swarmClusterId").asText(null);
	}
}
