package org.lendingclub.trident.haproxy;

import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.extension.BasicInterceptorGroup;
import org.lendingclub.trident.extension.InterceptorGroup;
import org.lendingclub.trident.haproxy.swarm.SwarmBootstrapConfigInterceptor;
import org.lendingclub.trident.haproxy.swarm.SwarmHostDiscoveryInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.base.Strings;
import com.google.common.hash.Hashing;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class HAProxyManager implements HAProxyInterceptors {

	Logger logger = LoggerFactory.getLogger(HAProxyManager.class);
	private InterceptorGroup<HAProxyBootstrapConfigInterceptor> haProxyBootstrapConfigDecorators = new BasicInterceptorGroup<>();
	private InterceptorGroup<HAProxyHostDiscoveryInterceptor> haProxyHostDiscoveryDecorators = new BasicInterceptorGroup<>();
	private InterceptorGroup<HAProxyConfigDiscoveryInterceptor> haProxyConfigBundleDiscoveryDecorators = new BasicInterceptorGroup<>();
	private InterceptorGroup<HAProxyCertDiscoveryInterceptor> haProxyCertBundleDiscoveryDecorators = new BasicInterceptorGroup<>();

	@Autowired
	HAProxyCertBundleDiscoveryInterceptor haProxyCertDiscoveryDecorator;

	@Autowired
	SwarmBootstrapConfigInterceptor swarmBootstrapConfigDecorator;

	@Autowired
	SwarmHostDiscoveryInterceptor swarmHostDiscoveryDecorator;

	@Autowired
	HAProxyConfigBundleDiscoveryInterceptor haProxyConfigBundleDiscoveryDecorator;

	@Autowired
	NeoRxClient neo4j;

	public InterceptorGroup<HAProxyBootstrapConfigInterceptor> getHAProxyBootstrapConfigInterceptors() {
		return haProxyBootstrapConfigDecorators;
	}

	public InterceptorGroup<HAProxyHostDiscoveryInterceptor> getHAProxyHostDiscoveryInterceptors() {
		return haProxyHostDiscoveryDecorators;
	}

	public InterceptorGroup<HAProxyConfigDiscoveryInterceptor> getHAProxyConfigBundleDiscoveryInterceptors() {
		return haProxyConfigBundleDiscoveryDecorators;
	}

	public InterceptorGroup<HAProxyCertDiscoveryInterceptor> getHAProxyCertBundleDiscoveryInterceptors() {
		return haProxyCertBundleDiscoveryDecorators;
	}

	@PostConstruct
	public void setupStandardDecorators() {
		haProxyBootstrapConfigDecorators.addInterceptor(swarmBootstrapConfigDecorator);
		haProxyHostDiscoveryDecorators.addInterceptor(swarmHostDiscoveryDecorator);
		haProxyConfigBundleDiscoveryDecorators.addInterceptor(haProxyConfigBundleDiscoveryDecorator);
		haProxyCertBundleDiscoveryDecorators.addInterceptor(haProxyCertDiscoveryDecorator);
	}

	public void decorate(HAProxyBootstrapConfigDiscoveryContext ctx) {
		getHAProxyBootstrapConfigInterceptors().getInterceptors().forEach(it -> {
			logger.info("invoking decorator: {}", it);
			it.accept(ctx);
		});
	}

	public void decorate(HAProxyHostDiscoveryContext ctx) {
		getHAProxyHostDiscoveryInterceptors().getInterceptors().forEach(it -> {
			logger.info("invoking decorator: {}", it);
			it.accept(ctx);
		});
	}

	public void decorate(HAProxyConfigBundleDiscoveryContext ctx) {
		getHAProxyConfigBundleDiscoveryInterceptors().getInterceptors().forEach(it -> {
			logger.info("invoking decorator: {}", it);
			it.interceptor(ctx);
		});
	}

	public void decorate(HAProxyCertBundleDiscoveryContext ctx) {
		getHAProxyCertBundleDiscoveryInterceptors().getInterceptors().forEach(it -> {
			logger.info("invoking decorator {}", it);
			it.accept(ctx);
		});
	}

	protected void recordCheckIn(String node, String env, String subEnv, String serviceGroup, String region) {
		subEnv = Strings.isNullOrEmpty(subEnv) ? "default" : subEnv;
		if (Strings.isNullOrEmpty(node) || Strings.isNullOrEmpty(env) || Strings.isNullOrEmpty(subEnv)
				|| Strings.isNullOrEmpty(region)) {
			logger.info("missing metadata on HAProxy check-in: node={} env={} subEnv={} serviceGroup={} region={}",
					node, env, subEnv, serviceGroup, region);

		}
		try {
			String id = String.format("%s__%s__%s__%s__%s", node, env, subEnv, serviceGroup, region);
			id = Hashing.sha1().hashBytes(id.getBytes()).toString();

			neo4j.execCypher(
					"merge (a:HAProxyInstance {id:{id}}) set a.region={region}, a.serviceGroup={serviceGroup}, a.environment={env}, a.subEnvironment={subEnv}, a.node={node}, a.lastContactTs=timestamp(), a.updateTs=timestamp()",
					"id", id, "serviceGroup", serviceGroup, "region", region, "env", env, "subEnv", subEnv, "node",
					node);
		} catch (RuntimeException e) {
			logger.warn("problem recording HAProxy check-in", e);
		}

	}

}
