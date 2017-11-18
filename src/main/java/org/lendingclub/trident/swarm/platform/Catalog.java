package org.lendingclub.trident.swarm.platform;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import io.reactivex.Observable;

/**
 * Catalog provides access to metadata with some short-term caching to minimize
 * pressure on the underlying provider.
 * 
 * @author rschoening
 *
 */
public class Catalog {

	@Autowired
	NeoRxClient neo4j;

	Logger logger = LoggerFactory.getLogger(Catalog.class);

	private Supplier<List<String>> regionSupplier = new RegionSupplier();
	private Supplier<List<String>> envSupplier = new EnvSupplier();
	private Supplier<List<String>> serviceGroupSupplier = new ServiceGroupSupplier();
	private Supplier<Map<String, JsonNode>> appCatalogSupplier = new ServiceCatalogSupplier();
	private Supplier<List<String>> subEnvSupplier = new SubEnvSupplier();

	LoadingCache<String, List<String>> regionCache = CacheBuilder.newBuilder().expireAfterWrite(30, TimeUnit.SECONDS)
			.build(regionCacheLoader());
	LoadingCache<String, List<String>> envCache = CacheBuilder.newBuilder().expireAfterWrite(30, TimeUnit.SECONDS)
			.build(envCacheLoader());
	LoadingCache<String, List<String>> subEnvCache = CacheBuilder.newBuilder().expireAfterWrite(30, TimeUnit.SECONDS)
			.build(subEnvCacheLoader());
	LoadingCache<String, List<String>> serviceGroupCache = CacheBuilder.newBuilder()
			.expireAfterWrite(30, TimeUnit.SECONDS).build(serviceGroupLoader());
	LoadingCache<String, Map<String, JsonNode>> catalogCache = CacheBuilder.newBuilder()
			.expireAfterWrite(30, TimeUnit.SECONDS).build(catalogCacheLoader());

	class ServiceGroupSupplier implements Supplier<List<String>> {

		@Override
		public List<String> get() {
			return selectUniqueSortedList("match (a:Config) where a.type='serviceGroup' return a.name","match (a:DockerService) return distinct a.label_tsdServiceGroup"
			);
			
				
		}

	}

	class RegionSupplier implements Supplier<List<String>> {

		@Override
		public List<String> get() {
			return selectUniqueSortedList("match (a:Config) where a.type='region' return a.name","match (a:DockerService) return distinct a.label_tsdRegion",
					"match (a:AppCluster) return distinct a.region");
		}

	}

	private List<String> selectUniqueSortedList(String... cyphers) {

		Observable<JsonNode> source = Observable.empty();
		for (String cypher : cyphers) {
			source = source.concatWith(neo4j.execCypher(cypher));
		}
		return source.filter(p -> p.isTextual()).map(f -> f.asText())
				.flatMapIterable(it -> Splitter.on(",").trimResults().omitEmptyStrings().splitToList(it))
				.filter(it -> !Strings.isNullOrEmpty(it)).toList().blockingGet().stream().sorted().distinct()
				.collect(Collectors.toList());

	}

	class EnvSupplier implements Supplier<List<String>> {

		@Override
		public List<String> get() {
			return selectUniqueSortedList("match (a:Config) where a.type='environment' return a.name",
					"match (a:DockerService) where exists(a.label_tsdEnv) return distinct a.label_tsdEnv",
					"match (a:AppCluster) return distinct a.environment");
		}

	}

	class SubEnvSupplier implements Supplier<List<String>> {

		@Override
		public List<String> get() {
			return selectUniqueSortedList(
					"match (a:Config) where a.type='subEnvironment' return a.name",
					"match (a:DockerService) where exists (a.label_tsdSubEnv) return distinct a.label_tsdSubEnv",
					"match (a:AppCluster) return distinct a.subEnvironment"
					);
		}

	}

	class ServiceCatalogSupplier implements Supplier<Map<String, JsonNode>> {

		@Override
		public Map<String, JsonNode> get() {

			Map<String, JsonNode> data = Maps.newHashMap();

			selectUniqueSortedList("match (a:DockerService) return distinct a.label_tsdAppId").forEach(it -> {
				if (!Strings.isNullOrEmpty(it)) {
					data.put(it, JsonUtil.createObjectNode().put("id", it));
				}
			});

			neo4j.execCypher("match (a:ServiceCatalogItem) return a").forEach(it -> {
				String id = it.path("id").asText(null);
				if (!Strings.isNullOrEmpty(id)) {
					data.put(id, it);
				}
			});
			return ImmutableMap.copyOf(data);
		}

	}

	public List<String> getServiceGroupNames() {
		try {
			return serviceGroupCache.get("ignore");
		} catch (ExecutionException e) {
			logger.warn("", e);
		}
		return ImmutableList.of();
	}

	public List<String> getRegionNames() {
		try {
			return regionCache.get("ignore");
		} catch (ExecutionException e) {
			logger.warn("", e);
		}
		return ImmutableList.of();
	}

	public List<String> getServiceIds() {
		return getAppIds();
	}
	public List<String> getAppIds() {
		List<String> list = Lists.newArrayList(appCatalogSupplier.get().keySet()).stream().sorted().collect(Collectors.toList());
		return ImmutableList.copyOf(list);
	}

	public List<String> getEnvironmentNames() {
		try {
			return ImmutableList.copyOf(envCache.get("ignore"));
		} catch (ExecutionException e) {
			logger.warn("", e);
		}
		return ImmutableList.of();
	}

	public List<String> getSubEnvironmentNames() {
		try {
			List<String> subenvCache = subEnvCache.get("ignore");
			return subenvCache;
		} catch (ExecutionException e) {
			logger.warn("", e);
		}
		return ImmutableList.of("default");
	}

	private CacheLoader<String, List<String>> regionCacheLoader() {
		return new CacheLoader<String, List<String>>() {

			@Override
			public List<String> load(String key) throws Exception {
				return regionSupplier.get();
			}

		};
	}

	private CacheLoader<String, List<String>> serviceGroupLoader() {
		return new CacheLoader<String, List<String>>() {

			@Override
			public List<String> load(String key) throws Exception {
				return serviceGroupSupplier.get();
			}

		};
	}

	private CacheLoader<String, List<String>> envCacheLoader() {
		return new CacheLoader<String, List<String>>() {

			@Override
			public List<String> load(String key) throws Exception {
				return envSupplier.get();
			}

		};
	}

	public void clearCache() {
		regionCache.invalidateAll();
		envCache.invalidateAll();
		subEnvCache.invalidateAll();
		catalogCache.invalidateAll();
		serviceGroupCache.invalidateAll();
	}

	private CacheLoader<String, List<String>> subEnvCacheLoader() {
		return new CacheLoader<String, List<String>>() {

			@Override
			public List<String> load(String key) throws Exception {
				return subEnvSupplier.get();
			}

		};
	}

	private CacheLoader<String, Map<String, JsonNode>> catalogCacheLoader() {
		return new CacheLoader<String, Map<String, JsonNode>>() {

			@Override
			public Map<String, JsonNode> load(String disregard) throws Exception {
				Map<String, JsonNode> m = appCatalogSupplier.get();

				if (m == null) {
					return ImmutableMap.of();
				}
				return ImmutableMap.copyOf(m);
			}

		};
	}

	public Optional<JsonNode> getServiceCatalogEntry(String appId) {
		try {
			Map<String, JsonNode> map = catalogCache.get("DISREGARD");
			if (map == null) {
				return Optional.empty();
			}

			return Optional.ofNullable(map.get(appId));
		} catch (RuntimeException | ExecutionException e) {
			logger.warn("could not load appId: " + appId, e);
		}
		return Optional.empty();
	}

	public void setEnvironmentSupplier(Supplier<List<String>> s) {
		Preconditions.checkNotNull(s);
		this.envSupplier = s;
	}

	public void setServiceGroupSupplier(Supplier<List<String>> s) {
		Preconditions.checkNotNull(s);
		this.serviceGroupSupplier = s;
	}

	public void setSubEnvironmentSupplier(Supplier<List<String>> s) {
		Preconditions.checkNotNull(s);
		this.subEnvSupplier = s;
	}

	public void setRegionSupplier(Supplier<List<String>> s) {
		Preconditions.checkNotNull(s);
		this.regionSupplier = s;
	}

	public void getServiceCatalogSupplier(Supplier<Map<String, JsonNode>> s) {
		Preconditions.checkNotNull(s);
		this.appCatalogSupplier = s;
	}

}
