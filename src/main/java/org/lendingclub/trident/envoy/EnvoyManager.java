package org.lendingclub.trident.envoy;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import javax.annotation.PostConstruct;

import org.lendingclub.trident.envoy.swarm.SwarmClusterDiscoveryDecorator;
import org.lendingclub.trident.envoy.swarm.SwarmListenerDiscoveryDecorator;
import org.lendingclub.trident.envoy.swarm.SwarmRouteDiscoveryDecorator;
import org.lendingclub.trident.envoy.swarm.SwarmServiceDiscoveryDecorator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.base.Splitter;

@Component
public class EnvoyManager {

	private List<EnvoyBootstrapConfigDecorator> bootstrapDecorators = new CopyOnWriteArrayList<>();
	private List<EnvoyServiceDiscoveryDecorator> serviceDiscoveryDecorators = new CopyOnWriteArrayList<>();
	private List<EnvoyClusterDiscoveryDecorator> clusterDiscoveryDecorators = new CopyOnWriteArrayList<>();
	private List<EnvoyRouteDiscoveryDecorator> routeDiscoveryDecorators = new CopyOnWriteArrayList<>();
	private List<EnvoyListenerDiscoveryDecorator> listenerDiscoveryDecorators = new CopyOnWriteArrayList<>();
	
	@Autowired
	SwarmClusterDiscoveryDecorator swarmClusterDiscoveryDecorator;
	
	@Autowired
	SwarmServiceDiscoveryDecorator swarmServiceDiscoveryDecorator;
	
	@Autowired
	SwarmListenerDiscoveryDecorator swarmListenerDiscoveryDecorator;
	
	@Autowired
	SwarmRouteDiscoveryDecorator swarmRouteDiscoveryDecorator;
	
	
	public List<EnvoyServiceDiscoveryDecorator> getServiceDiscoveryDecorators() {
		return serviceDiscoveryDecorators;
	}

	public List<EnvoyClusterDiscoveryDecorator> getClusterDiscoveryDecorators() {
		return clusterDiscoveryDecorators;
	}
	public List<EnvoyRouteDiscoveryDecorator> getRouteDiscoveryDecorators() {
		return routeDiscoveryDecorators;
	}
	public List<EnvoyListenerDiscoveryDecorator> getListenerDiscoveryDecorators() {
		return listenerDiscoveryDecorators;
	}
	public List<EnvoyBootstrapConfigDecorator> getBootstrapDecorators() {
		return bootstrapDecorators;
	}
	
	@PostConstruct
	public void setupStandardDecorators() {
		serviceDiscoveryDecorators.add(swarmServiceDiscoveryDecorator);
		clusterDiscoveryDecorators.add(swarmClusterDiscoveryDecorator);
		listenerDiscoveryDecorators.add(swarmListenerDiscoveryDecorator);
		routeDiscoveryDecorators.add(swarmRouteDiscoveryDecorator);
	}
	
	public void decorate(EnvoyServiceDiscoveryContext ctx) {
		getServiceDiscoveryDecorators().forEach(it -> {
			it.decorate(ctx);
		});
	}

	public void decorate(EnvoyBootstrapConfigContext ctx) {
		getBootstrapDecorators().forEach(it -> {
			it.decorate(ctx);
		});
	}

	public void decorate(EnvoyClusterDiscoveryContext ctx) {
		getClusterDiscoveryDecorators().forEach(it -> {
			it.decorate(ctx);
		});
	}

	public void decorate(EnvoyRouteDiscoveryContext ctx) {
		getRouteDiscoveryDecorators().forEach(it -> {
			it.decorate(ctx);
		});
	}

	public void decorate(EnvoyListenerDiscoveryContext ctx) {
		getListenerDiscoveryDecorators().forEach(it -> {
			it.decorate(ctx);
		});
	}

	public static String toEnvoyFormatUrl(String url) {

		String out = url;
		if (url == null) {
			// do nothing
		}
		else if (url.startsWith("tcp")) {
			out = url;
		} else if (url.startsWith("http")) {
			List<String> parts = Splitter.on("://").splitToList(url);
			String protocol = parts.get(0);
			String remainder = parts.get(1);

			String hostAndPort = Splitter.on("/").splitToList(remainder).get(0);

			out = "tcp://" + hostAndPort;
			if (!hostAndPort.contains(":")) {
				if (protocol.startsWith("https")) {
					out = out + ":443";
				} else if (protocol.startsWith("http")) {
					out = out + ":80";
				} else {
					throw new IllegalArgumentException("unknown protocol: " + protocol);
				}
			}
		}
		return out;

	}
}
