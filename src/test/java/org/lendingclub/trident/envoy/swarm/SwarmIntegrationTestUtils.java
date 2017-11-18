package org.lendingclub.trident.envoy.swarm;

import com.google.common.collect.ImmutableList;
import org.assertj.core.api.Assertions;
import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.swarm.SwarmDiscoverySearch;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by hasingh on 9/12/17.
 */
public class SwarmIntegrationTestUtils {

	public static void assertSearchResults(String region, String serviceGroup, String env, String subenv, List<String> vals, NeoRxClient neo4j) {
		assertSearchResults(region, serviceGroup, env, subenv, vals, ImmutableList.of(), neo4j);
	}

	public static void assertSearchResults(String region, String serviceGroup, String env, String subenv, List<String> vals,
			List<String> doesNotContain, NeoRxClient neo4j) {
		List<String> x = new SwarmDiscoverySearch(neo4j).withEnvironment(env).withServiceGroup(serviceGroup)
				.withRegion(region).withSubEnvironment(subenv).search().stream().map(f -> {
					return f.getServiceId();
				}).collect(Collectors.toList());
		Assertions.assertThat(x).contains(vals.toArray(new String[0]));

		if (doesNotContain == null || doesNotContain.isEmpty()) {
			// do nothing
		} else {
			Assertions.assertThat(x).doesNotContain(doesNotContain.toArray(new String[0]));
		}
	}
}
