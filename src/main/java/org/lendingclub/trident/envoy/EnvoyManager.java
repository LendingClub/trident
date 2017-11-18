package org.lendingclub.trident.envoy;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import javax.annotation.PostConstruct;

import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.envoy.swarm.SwarmClusterDiscoveryInterceptor;
import org.lendingclub.trident.envoy.swarm.SwarmListenerDiscoveryInterceptor;
import org.lendingclub.trident.envoy.swarm.SwarmRouteDiscoveryInterceptor;
import org.lendingclub.trident.envoy.swarm.SwarmServiceDiscoveryInterceptor;
import org.lendingclub.trident.extension.BasicInterceptorGroup;
import org.lendingclub.trident.extension.InterceptorGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.base.Splitter;
import com.google.common.hash.Hashing;

@Component
public class EnvoyManager implements EnvoyInterceptors {

	Logger logger = LoggerFactory.getLogger(EnvoyManager.class);
	
	
	private InterceptorGroup<EnvoyBootstrapConfigInterceptor> bootstrapInterceptors = new BasicInterceptorGroup<>();
	private InterceptorGroup<EnvoyServiceDiscoveryInterceptor> serviceDiscoveryInterceptors = new BasicInterceptorGroup<>();
	private InterceptorGroup<EnvoyClusterDiscoveryInterceptor> clusterDiscoveryInterceptors = new BasicInterceptorGroup<>();
	private InterceptorGroup<EnvoyRouteDiscoveryInterceptor> routeDiscoveryInterceptors = new BasicInterceptorGroup<>();
	private InterceptorGroup<EnvoyListenerDiscoveryInterceptor> listenerDiscoveryInterceptors = new BasicInterceptorGroup<>();
	
	@Autowired
	SwarmClusterDiscoveryInterceptor swarmClusterDiscoveryInterceptors;
	
	@Autowired
	SwarmServiceDiscoveryInterceptor swarmServiceDiscoveryInterceptors;
	
	@Autowired
	SwarmListenerDiscoveryInterceptor swarmListenerDiscoveryDecorator;
	
	@Autowired
	SwarmRouteDiscoveryInterceptor swarmRouteDiscoveryDecorator;
	
	@Autowired NeoRxClient neo4j;
	
	public InterceptorGroup<EnvoyServiceDiscoveryInterceptor> getServiceDiscoveryInterceptors() {
		return serviceDiscoveryInterceptors;
	}

	public InterceptorGroup<EnvoyClusterDiscoveryInterceptor> getClusterDiscoveryInterceptors() {
		return clusterDiscoveryInterceptors;
	}
	public InterceptorGroup<EnvoyRouteDiscoveryInterceptor> getRouteDiscoveryInterceptors() {
		return routeDiscoveryInterceptors;
	}
	public InterceptorGroup<EnvoyListenerDiscoveryInterceptor> getListenerDiscoveryInterceptors() {
		return listenerDiscoveryInterceptors;
	}
	public InterceptorGroup<EnvoyBootstrapConfigInterceptor> getBootstrapInterceptors() {
		return bootstrapInterceptors;
	}
	
	@PostConstruct
	public void setupStandardInterceptors() {
		serviceDiscoveryInterceptors.getInterceptors().add(swarmServiceDiscoveryInterceptors);
		clusterDiscoveryInterceptors.getInterceptors().add(swarmClusterDiscoveryInterceptors);
		listenerDiscoveryInterceptors.getInterceptors().add(swarmListenerDiscoveryDecorator);
		routeDiscoveryInterceptors.getInterceptors().add(swarmRouteDiscoveryDecorator);
	}
	
	public void invokeInterceptors(EnvoyServiceDiscoveryContext ctx) {
		getServiceDiscoveryInterceptors().getInterceptors().forEach(it -> {
			logger.info("invoking interceptor: {}",it);
			it.accept(ctx);
		});
	}

	public void invokeInterceptors(EnvoyBootstrapConfigContext ctx) {
		getBootstrapInterceptors().getInterceptors().forEach(it -> {
			logger.info("invoking interceptor: {}",it);
			it.accept(ctx);
		});
	}

	public void invokeInterceptors(EnvoyClusterDiscoveryContext ctx) {
		getClusterDiscoveryInterceptors().getInterceptors().forEach(it -> {
			logger.info("invoking interceptor: {}",it);
			it.accept(ctx);
		});
	}

	public void invokeInterceptors(EnvoyRouteDiscoveryContext ctx) {
		getRouteDiscoveryInterceptors().getInterceptors().forEach(it -> {
			logger.info("invoking interceptor: {}",it);
			it.accept(ctx);
		});
	}

	public void decorate(EnvoyListenerDiscoveryContext ctx) {
		getListenerDiscoveryInterceptors().getInterceptors().forEach(it -> {
			logger.info("invoking decorator: {}",it);
			it.accept(ctx);
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
	
	public void record(EnvoyDiscoveryContext ctx) {
		
		if (ctx instanceof EnvoyListenerDiscoveryContext || ctx instanceof EnvoyClusterDiscoveryContext) {
			try {
			String id = String.format("%s__%s__%s__%s__%s",ctx.getServiceZone().get(),ctx.getEnvironment().get(),ctx.getSubEnvironment().orElse("default"),
					ctx.getServiceCluster().get(),ctx.getServiceNode().get());
			id = Hashing.sha1().hashBytes(id.getBytes()).toString();
			
			neo4j.execCypher("merge (a:EnvoyInstance {id:{id}}) set a.region={region}, a.serviceGroup={serviceGroup}, a.environment={env}, a.subEnvironment={subEnv}, a.node={node}, a.lastContactTs=timestamp(), a.updateTs=timestamp()"
					,"id",id
					,"serviceGroup",ctx.getServiceCluster().get()
					,"region",ctx.getServiceZone().get()
					,"env",ctx.getEnvironment().get()
					,"subEnv",ctx.getSubEnvironment().orElse("default")
					,"node",ctx.getServiceNode().get());
			}
			catch (NoSuchElementException e) {
				
			}
			
		}
	}
}
