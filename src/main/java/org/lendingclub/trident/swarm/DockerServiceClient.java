package org.lendingclub.trident.swarm;

import javax.ws.rs.client.WebTarget;

import com.github.dockerjava.api.DockerClient;

/**
 * Provides ability to uniquely name/address a given docker service across all swarms.
 * @author rschoening
 *
 */
public interface DockerServiceClient {

	String getServiceId();
	String getSwarmId();
	DockerClient getDockerClient();
	WebTarget getDockerWebTarget();
}
