package org.lendingclub.trident.swarm;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.lendingclub.neorx.NeoRxClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class SwarmDiscoverySearch {

	public static final String TSD_APP_ID = "tsdAppId";
	public static final String TSD_PORT = "tsdPort";
	public static final String TSD_ENV = "tsdEnv";
	public static final String TSD_SUB_ENV = "tsdSubEnv";
	public static final String TSD_SERVICE_GROUP = "tsdServiceGroup";
	public static final String TSD_REGION = "tsdRegion";
	public static final String TSD_PATH = "tsdPath";

	public static final String TSD_APP_ID_LABEL = "label_tsdAppId";
	public static final String TSD_PORT_LABEL = "label_tsdPort";
	public static final String TSD_ENV_LABEL = "label_tsdEnv";
	public static final String TSD_SUB_ENV_LABEL = "label_tsdSubEnv";
	public static final String TSD_SERVICE_GROUP_LABEL = "label_tsdServiceGroup";
	public static final String TSD_REGION_LABEL = "label_tsdRegion";
	public static final String TSD_PATH_LABEL = "label_tsdPath";
	NeoRxClient neo4j;

	public SwarmDiscoverySearch(NeoRxClient neo4j) {
		this.neo4j = neo4j;
	}

	public static interface Service {
		String getSwarmName();

		Optional<String> getAppId();

		String getSwarmId();

		String getServiceId();

		String getServiceName();

		JsonNode getData();

		public String getServiceGroupSelector();

		public String getEnvironmentSelector();

		public String getSubEnvironmentSelector();

		public String getRegionSelector();

		public List<String> getPaths();

		public Optional<Integer> getPort();

	}

	public class ServiceImpl implements Service {
		JsonNode data;

		String selectedAppId = null;
		String selectedRegion = null;
		String selectedEnvironment = null;
		String selectedSubEnvironment = null;

		@Override
		public String getSwarmName() {
			return data.path("swarmName").asText();
		}

		@Override
		public JsonNode getData() {
			return data;
		}

		private Optional<String> getOptionalServiceAttribute(String n) {
			return Optional.ofNullable(Strings.emptyToNull(data.path("s").path(n).asText()));
		}

		@Override
		public Optional<String> getAppId() {
			return getOptionalServiceAttribute(TSD_APP_ID_LABEL);
		}

		@Override
		public String getSwarmId() {
			return data.path("swarmClusterId").asText();
		}

		@Override
		public String getServiceId() {
			return data.path("s").path("serviceId").asText();
		}

		@Override
		public String getServiceName() {
			return data.path("s").path("name").asText();
		}

		@Override
		public String getServiceGroupSelector() {
			return data.path("s").path(TSD_SERVICE_GROUP_LABEL).asText();
		}

		@Override
		public String getEnvironmentSelector() {
			return data.path("s").path(TSD_ENV_LABEL).asText();
		}

		@Override
		public String getSubEnvironmentSelector() {
			return data.path("s").path(TSD_SUB_ENV_LABEL).asText();
		}

		@Override
		public List<String> getPaths() {
			return toList(data.path("s").path(TSD_PATH_LABEL).asText());
		}

		@Override
		public String getRegionSelector() {
			return data.path("s").path(TSD_REGION_LABEL).asText();
		}

		@Override
		public Optional<Integer> getPort() {
			int val = data.path("s").path(TSD_PORT_LABEL).asInt();
			if (val > 0) {
				return Optional.of(new Integer(val));
			}
			return Optional.empty();

		}

		@Override
		public String toString() {
			return MoreObjects.toStringHelper(this).add("serviceId", getServiceId())
			        .add("appId", getAppId().orElse(null)).add("port", getPort().orElse(0)).toString();
		}
	}

	static CharMatcher delimiter = CharMatcher.anyOf(",:;");
	static Logger logger = LoggerFactory.getLogger(SwarmDiscoverySearch.class);

	List<String> serviceGroupIncludes = Lists.newCopyOnWriteArrayList();

	List<String> appIdIncludes = Lists.newCopyOnWriteArrayList();

	List<String> pathIncludes = Lists.newArrayList();

	String environment;
	String subEnvironment;
	String region;
	String serviceGroup;
	String appId;

	Thread violationThread = Thread.currentThread();

	private void checkThreadViolation() {
		Preconditions.checkState(Thread.currentThread() == violationThread);
	}

	public SwarmDiscoverySearch withEnvironment(String val) {
		this.environment = val;
		return this;
	}

	public SwarmDiscoverySearch withAppId(String id) {
		this.appId = id;
		return this;
	}

	public SwarmDiscoverySearch withSubEnvironment(String val) {
		this.subEnvironment = val;
		return this;
	}

	public SwarmDiscoverySearch withRegion(String val) {
		this.region = val;
		return this;
	}

	public SwarmDiscoverySearch withServiceGroup(String val) {
		this.serviceGroup = val;
		return this;

	}

	protected boolean match(Service service) {

		checkThreadViolation();
		logger.info("----");
		logger.info("matching {}: env={} subEnv={} region={} serviceGroup={}", service.getServiceId(),
		        service.getEnvironmentSelector(), service.getSubEnvironmentSelector(), service.getRegionSelector(),
		        service.getServiceGroupSelector());
		boolean b = false;
		try {
			b = match(environment, service.getEnvironmentSelector(), "Env") != MatchResult.NONE
			        && match(serviceGroup, service.getServiceGroupSelector(), "ServiceGroup") != MatchResult.NONE
			        && match(region, service.getRegionSelector(), "Region") != MatchResult.NONE;

			MatchResult m = matchSubEnv(subEnvironment, service.getSubEnvironmentSelector(), "SubEnv");
			b = b && (!m.equals(MatchResult.NONE));

			if (b && service.getAppId().isPresent() && (!Strings.isNullOrEmpty(appId))) {
				b = service.getAppId().get().equals(appId);

			}
			if (b == true) {
				ServiceImpl si = ServiceImpl.class.cast(service);

				Preconditions.checkState(si.selectedAppId == null);
				Preconditions.checkState(si.selectedEnvironment == null);
				Preconditions.checkState(si.selectedSubEnvironment == null);
				Preconditions.checkState(si.selectedRegion == null);
				si.selectedRegion = region;
				si.selectedEnvironment = environment;
				if (m == MatchResult.DEFAULT) {
					si.selectedSubEnvironment = "default";
				} else {
					si.selectedSubEnvironment = subEnvironment;
				}
				if (service.getAppId().isPresent()) {
					si.selectedAppId = service.getAppId().get();
				}

			}
			return b;
		} finally {
			logger.info("result: {}", b);
		}

	}

	public List<Service> search() {

		Preconditions.checkArgument(!Strings.isNullOrEmpty(this.environment),
		        "SwarmDiscoverySearch.withEnvironment() not set");
		Preconditions.checkArgument(!Strings.isNullOrEmpty(this.serviceGroup),
		        "SwarmDiscoverySearch.withServiceGroup() not set");
		Preconditions.checkArgument(!Strings.isNullOrEmpty(this.region), "SwarmDiscoverySearch.withRegion() not set");

		checkThreadViolation();
		List<Service> list = neo4j
		        .execCypher(
		                "match (x:DockerSwarm)--(s:DockerService) return x.name as swarmName, x.swarmClusterId as swarmClusterId,s")
		        .map(it -> {
			        ServiceImpl si = new ServiceImpl();
			        si.data = it;

			        return (Service) si;
		        }).filter(it -> {

			        return match(it);
		        }).toList().blockingGet();

		removeShadowedServices(list);
		return list;
	}

	/**
	 * This method takes a list of services and removes services in the default
	 * sub-environment for which there is a non-default sub-env service that
	 * shadows it.
	 *
	 * @param list
	 */
	protected void removeShadowedServices(List<Service> list) {

		// Keep a map of name->value mappings
		Map<String, Service> map = Maps.newHashMap();
		List<String> keysToRemove = Lists.newArrayList();
		list.forEach(it -> {
			ServiceImpl si = ServiceImpl.class.cast(it);

			// create a key to uniquely identify the node...use slashes to make
		    // it obvious that this is slightly different than the double-dash
		    // keys
			String key = si.selectedRegion + "/" + si.selectedEnvironment + "/" + si.selectedSubEnvironment + "/"
		            + si.selectedAppId;
			map.put(key, it);
			if (!si.selectedSubEnvironment.equals("default")) {
				// since this is a subenvironment service, we need to get *rid*
		        // of the corresponding default
		        // So note this key to be removed. We have to go through this
		        // key/map charade because we might not have seen the object
		        // that we're going to remove.
				String keyToRemove = si.selectedRegion + "/" + si.selectedEnvironment + "/default/" + si.selectedAppId;
				keysToRemove.add(keyToRemove);
			}

		});

		// Now simply remove all the entries
		keysToRemove.forEach(it -> {
			Service svcToRemove = map.get(it);
			if (svcToRemove != null) {
				list.remove(svcToRemove);
			}
		});
	}

	public static class MatcherSet {
		List<String> inclusions = Lists.newArrayList();
		List<String> exclusions = Lists.newArrayList();

		public List<String> getExcludes() {
			return exclusions;
		}

		public List<String> getIncludes() {
			return inclusions;
		}

		public MatchResult match(String val) {
			boolean include = false;

			if (getIncludes().isEmpty()) {
				// empty includes is treated like a wildcard
				include = true;
			} else {

				if (!Strings.isNullOrEmpty(val)) {
					Iterator<String> t = getIncludes().iterator();
					while (t.hasNext() && !include) {
						String inc = t.next();

						if (val.equals(inc)) {
							include = true;
						}
					}
				}

			}
			if (!include) {

				return MatchResult.NONE;
			}

			if (!getExcludes().isEmpty()) {
				Iterator<String> t = getExcludes().iterator();
				while (t.hasNext()) {
					String ex = t.next();
					if (!Strings.isNullOrEmpty(val)) {
						if (val.equals(ex)) {
							return MatchResult.NONE;
						}
					}
				}

			}

			return MatchResult.DEFAULT;
		}
	}

	/**
	 * The rules are slightly different for sub-environment matching.
	 *
	 * @param input
	 * @param pattern
	 * @param context
	 * @return
	 */
	public MatchResult matchSubEnv(String input, String pattern, String context) {
		checkThreadViolation();
		MatcherSet set = parse(pattern);

		if (set.getIncludes().isEmpty()) {
			set.getIncludes().add("default");
		}
		boolean b = false;
		try {

			if (Strings.isNullOrEmpty(input)) {
				input = "default";
			}
			// If we are in a sub-environment, we pull in all the default
			// services, and
			// then filter them back out if they are shadowed

			b = set.match(input) != MatchResult.NONE;

			if (b) {
				if (input.equals("default")) {
					return MatchResult.DEFAULT;
				}
				return MatchResult.SUBENV;
			}
			if (set.match("default") != MatchResult.NONE) {
				return MatchResult.DEFAULT;
			}
			return MatchResult.NONE;
		} finally {

			logger.info("match(input='{}' pattern='{}' context='{}') => {}", input,
			        Joiner.on(",").join(set.getIncludes()), context, b);
		}
	}

	public static enum MatchResult {
		NONE, DEFAULT, SUBENV;

		public boolean isMatch() {
			return !this.equals(NONE);

		}
	}

	public MatchResult match(String input, String pattern, String context) {

		MatchResult b = MatchResult.NONE;
		try {
			if (Strings.isNullOrEmpty(Strings.nullToEmpty(input).trim())) {
				// any empty input (i.e. from envoy) will NOT match
				// This is really not a valid condition and might be considered
				// an exception
				logger.warn("invalid matching input='{}' context={}", input, context);
				b = MatchResult.NONE;
			} else {
				b = parse(pattern).match(input);
			}
			return b;
		} finally {
			logger.info("match input={} pattern={} context={} => {}", input, pattern, context, b);
		}

	}

	public MatcherSet parse(String... vals) {
		MatcherSet result = new MatcherSet();

		if (vals != null) {
			for (String val : vals) {
				if (!Strings.isNullOrEmpty(val)) {
					Splitter.on(SwarmDiscoverySearch.delimiter).omitEmptyStrings().trimResults().split(val)
					        .forEach(it -> {
						        if (it.startsWith("!")) {
							        String x = it.substring(1);
							        if (!Strings.isNullOrEmpty(x)) {
								        result.exclusions.add(x.trim());
							        }
						        } else {
							        if (!Strings.isNullOrEmpty(it)) {
								        result.inclusions.add(it.trim());
							        }
						        }
					        });
				}
			}
		}
		return result;
	}

	static List<String> toList(String s) {
		return Splitter.on(delimiter).omitEmptyStrings().trimResults().splitToList(s);
	}
}
