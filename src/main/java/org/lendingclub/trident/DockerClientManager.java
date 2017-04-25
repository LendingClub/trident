package org.lendingclub.trident;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;

import org.lendingclub.mercator.core.Projector;
import org.lendingclub.mercator.docker.DockerClientSupplier;
import org.lendingclub.mercator.docker.DockerScanner;
import org.lendingclub.mercator.docker.DockerScannerBuilder;

import org.lendingclub.trident.DockerClientManager.ClientConfig;

import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectExecResponse.Container;
import com.github.dockerjava.api.model.Info;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.UncheckedExecutionException;

import jersey.repackaged.com.google.common.collect.Lists;

@Component
public class DockerClientManager {

	static Logger logger = org.slf4j.LoggerFactory.getLogger(DockerClientManager.class);

	@Autowired
	ApplicationContext applicationContext;

	Map<String, DockerClientSupplier> supplierMap = Maps.newConcurrentMap();

	LoadingCache<String, DockerClient> cache = CacheBuilder.newBuilder().build(new ClientCacheLoader());

	@Autowired
	Projector projector;

	public static interface ClientConfig {
		public File getCertPath();

		public String getDockerHost();

		public boolean isTlsVerifyEnabled();

		public String getName();
	}

	static class ClientConfigImpl implements ClientConfig {

		File certPath;
		String dockerHost;
		boolean tlsVerify;
		String name;

		public String getName() {
			return name;
		}

		@Override
		public File getCertPath() {
			return certPath;
		}

		@Override
		public String getDockerHost() {
			return dockerHost;
		}

		@Override
		public boolean isTlsVerifyEnabled() {
			return tlsVerify;
		}

		public String toString() {
			return MoreObjects.toStringHelper(ClientConfig.class).add("name", name).add("dockerHost", getDockerHost())
					.add("certPath", getCertPath().getAbsolutePath()).add("tlsVerifyEnabled", isTlsVerifyEnabled())
					.toString();
		}
	}

	class ClientCacheLoader extends CacheLoader<String, DockerClient> {

		@Override
		public DockerClient load(String name) throws Exception {
			return newClient(name);
		}

	}

	public DockerClientManager() {
	}

	public DockerClient getClient(String name) {
		try {

			return cache.get(name);

		} catch (UncheckedExecutionException e) {
			Throwable t = e.getCause();
			if (t instanceof ClientNotFoundException) {
				throw ClientNotFoundException.class.cast(t);
			}
			throw new ClientNotFoundException(name, e.getCause());
		} catch (ExecutionException e) {
			throw new ClientNotFoundException(name, e);
		}
	}

	public Map<String, DockerClientSupplier> getDockerClientSuppliers() {
		Map<String, DockerClientSupplier> m = Maps.newConcurrentMap();

		applicationContext.getBeansOfType(DockerClientSupplier.class).forEach((k, v) -> {
			String name = v.getName();
			com.google.common.base.Preconditions.checkNotNull(Strings.emptyToNull(name), "name cannot be null");
			m.put(v.getName(), v);
		});
		m.putAll(supplierMap);
		return m;
	}

	public void registerSupplier(DockerClientSupplier supplier) {
		Preconditions.checkNotNull(supplier);
		Preconditions.checkNotNull(supplier.getName());
		supplierMap.put(supplier.getName(), supplier);
	}

	protected Optional<Supplier<DockerClient>> getSupplier(String name) {
		Supplier<DockerClient> supplier = getDockerClientSuppliers().get(name);

		return Optional.ofNullable(supplier);
	}

	public DockerClient newClient(String name) {

		Optional<Supplier<DockerClient>> s = getSupplier(name);
		if (s.isPresent()) {
			return s.get().get();
		}
		throw new ClientNotFoundException("could not create client: " + name);

	}

	public List<JsonNode> loadConfig() {
		List<JsonNode> list = Lists.newArrayList();

		list.addAll(DockerScannerBuilder
				.loadDockerConfig(new File(System.getProperty("user.home", "NOTFOUND"), ".docker"), true));
		list.addAll(DockerScannerBuilder.loadDockerConfig(new File("./config"), true));
		return list;
	}

	public org.lendingclub.mercator.docker.DockerScanner createDockerScanner(String name) {
		DockerClientSupplier s = getDockerClientSuppliers().get(name);

		return createDockerScanner(s);
	}

	public DockerScanner createDockerScanner(DockerClientSupplier s) {
		return projector.createBuilder(DockerScannerBuilder.class).withClientSupplierBuilder(s.newBuilder()).build();
	}

	private void registerClientConfig(JsonNode n) {
		String name = new File(n.path("DOCKER_CERT_PATH").asText()).getName();
		DockerClientSupplier dcs = new DockerClientSupplier.Builder().withClientConfig(n).withName(name).build();
		logger.info("adding name={} - {}", name, dcs);
		supplierMap.put(name, dcs);

	}

	@PostConstruct
	public void discoverAll() {
		loadConfig().forEach(it -> {

			try {
				registerClientConfig(it);
			} catch (RuntimeException e) {
				logger.error("could not register: " + it, e);
			}
		});
	}

}
