package org.lendingclub.trident.swarm;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.assertj.core.api.Assertions;
import org.assertj.core.util.Lists;
import org.assertj.core.util.Strings;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.TridentIntegrationTest;
import org.lendingclub.trident.envoy.swarm.SwarmIntegrationTestUtils;
import org.lendingclub.trident.swarm.SwarmDiscoverySearch;
import org.lendingclub.trident.swarm.SwarmDiscoverySearch.Service;
import org.lendingclub.trident.swarm.SwarmDiscoverySearch.ServiceImpl;
import org.lendingclub.trident.swarm.SwarmDiscoverySearch.MatchResult;
import org.lendingclub.trident.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;

public class SwarmDiscoverySearchIntegrationTest extends TridentIntegrationTest {
	ObjectMapper mapper = new ObjectMapper();

	Logger logger = LoggerFactory.getLogger(SwarmDiscoverySearchIntegrationTest.class);

	@Autowired
	NeoRxClient neo4j;

	@Autowired
	SwarmClusterManager swarmClusterManager;
	
	public TestServiceBuilder addService(String id) {
		return new TestServiceBuilder(neo4j).addService(id);
	}

	@After
	public void cleanup() {
		if (isIntegrationTestEnabled()) {
			neo4j.execCypher("match (s:DockerService) where s.junitData=true detach delete s");
			neo4j.execCypher("match (s:DockerSwarm) where s.junitData=true detach delete s");
		}
	}

	public SwarmDiscoverySearch newSearch() {
		SwarmDiscoverySearch ss = new SwarmDiscoverySearch(neo4j);

		return ss;

	}

	@Before
	public void setupServices() {
		cleanupUnitTestData();

	}

	@Test
	public void testXX() {

		cleanupUnitTestData();
		addService("x1").withAppId("x1").withEnvironment("qa").withSubEnvironment("default").withServiceGroup("junit");
		addService("x1a").withAppId("x1").withEnvironment("qa").withSubEnvironment("f1").withServiceGroup("junit");
		addService("x2").withAppId("x2").withEnvironment("qa").withSubEnvironment("").withServiceGroup("junit");

		SwarmIntegrationTestUtils.assertSearchResults("uw2", "junit", "qa", "default", ImmutableList.of("x1", "x2"), neo4j);
		SwarmIntegrationTestUtils.assertSearchResults("uw2", "junit", "qa", "default", ImmutableList.of("x1", "x2"), neo4j);
		SwarmIntegrationTestUtils.assertSearchResults("uw2", "junit", "qa", "default", ImmutableList.of("x1", "x2"), neo4j);

		System.out.println(new SwarmDiscoverySearch(neo4j).withEnvironment("qa").withRegion("uw2").withServiceGroup("junit").search());

		 SwarmIntegrationTestUtils.assertSearchResults("uw2","junit","qa","default",ImmutableList.of("x1","x2"), neo4j);
		 
		new SwarmDiscoverySearch(neo4j).withRegion("uw2").withServiceGroup("junit").withEnvironment("qa").search().forEach(it->{
			System.out.println(it);
			
		});
		
	
		
	}

	@Test
	public void testEnv() {

	}

	@Test
	public void testServiceGroup() {

		cleanupUnitTestData();
		addService("s1").withEnvironment("prod").withSubEnvironment("default");
		addService("s2").withEnvironment("prod").withSubEnvironment("default").withServiceGroup("g1");
		addService("s3").withEnvironment("prod").withSubEnvironment("default").withServiceGroup("g1,g2");
		addService("s4").withEnvironment("prod").withSubEnvironment("default").withServiceGroup("!g3");
		SwarmIntegrationTestUtils.assertSearchResults("uw2", "a", "prod", "default", ImmutableList.of("s1", "s4"), neo4j);

		// All will be selected because s1 is a wildcard match, and g1 is in
		// both s2 and s3, and g1 is not g3
		SwarmIntegrationTestUtils.assertSearchResults("uw2", "g1", "prod", "default", ImmutableList.of("s1", "s2", "s3", "s4"), neo4j);

		SwarmIntegrationTestUtils.assertSearchResults("uw2", "g3", "prod", "default", ImmutableList.of("s1"), neo4j);
	}

	@Test
	public void testSubEnvironment() {

		cleanupUnitTestData();
		addService("s1").withAppId("s1").withEnvironment("prod").withSubEnvironment("default")
				.withServiceGroup("junit");
		addService("s2").withAppId("s2").withEnvironment("prod").withSubEnvironment("default")
				.withServiceGroup("junit");
		addService("s1a").withAppId("s1").withEnvironment("prod").withSubEnvironment("a").withServiceGroup("junit");

		SwarmIntegrationTestUtils.assertSearchResults("uw2", "junit", "prod", "default", ImmutableList.of("s1", "s2"), neo4j);
		SwarmIntegrationTestUtils.assertSearchResults("uw2", "junit", "prod", "a", ImmutableList.of("s2", "s1a"), ImmutableList.of("s1"), neo4j);
		// assertSearchResults("uw2", "g3", "prod","default",
		// ImmutableList.of("s1"));
	}

	@Test
	public void testIt() {

		Assertions.assertThat(new SwarmDiscoverySearch(null).match("foo", null, "").isMatch()).isTrue();
		Assertions.assertThat(new SwarmDiscoverySearch(null).match("foo", "", "").isMatch()).isTrue();
		Assertions.assertThat(new SwarmDiscoverySearch(null).match("foo", "foo", "").isMatch()).isTrue();

		Assertions.assertThat(new SwarmDiscoverySearch(null).match("foo", "", "").isMatch()).isTrue();
		Assertions.assertThat(new SwarmDiscoverySearch(null).match("foo", "foo", "").isMatch()).isTrue();
		Assertions.assertThat(new SwarmDiscoverySearch(null).match("foo", "bar,foo", "").isMatch()).isTrue();
		Assertions.assertThat(new SwarmDiscoverySearch(null).match("foo", "bar, foo", "").isMatch()).isTrue();

		Assertions.assertThat(new SwarmDiscoverySearch(null).match("app-a", "app-a", "").isMatch()).isTrue();
		Assertions.assertThat(new SwarmDiscoverySearch(null).match("fizz", "fizz,!buzz", "").isMatch()).isTrue();

		Assertions.assertThat(new SwarmDiscoverySearch(null).match("fizz", "!fizz,buzz", "").isMatch()).isFalse();
		
		Assertions.assertThat(new SwarmDiscoverySearch(null).match("", "prod", "").isMatch()).isFalse();

	}

	@Test
	public void testMatch() {
		Assertions.assertThat(MatchResult.DEFAULT.isMatch()).isTrue();
		Assertions.assertThat(MatchResult.SUBENV.isMatch()).isTrue();
		Assertions.assertThat(MatchResult.NONE.isMatch()).isFalse();
	}
	@Test
	public void testSubEnvMatching() {
		Assertions.assertThat(new SwarmDiscoverySearch(null).matchSubEnv("", "default", "")).isEqualTo(MatchResult.DEFAULT);
		Assertions.assertThat(new SwarmDiscoverySearch(null).matchSubEnv(null, "default", "")).isEqualTo(MatchResult.DEFAULT);
		Assertions.assertThat(new SwarmDiscoverySearch(null).matchSubEnv("default", "default", "")).isEqualTo(MatchResult.DEFAULT);
		Assertions.assertThat(new SwarmDiscoverySearch(null).matchSubEnv("default", "", "")).isEqualTo(MatchResult.DEFAULT);
		Assertions.assertThat(new SwarmDiscoverySearch(null).matchSubEnv("default", null, "")).isEqualTo(MatchResult.DEFAULT);
		Assertions.assertThat(new SwarmDiscoverySearch(null).matchSubEnv("default", "default", "")).isEqualTo(MatchResult.DEFAULT);
		Assertions.assertThat(new SwarmDiscoverySearch(null).matchSubEnv("default", "!default", "")).isEqualTo(MatchResult.NONE);

	}

	@Test
	public void testEmptyInputs() {
		// Empty inputs should never match
		Assertions.assertThat(new SwarmDiscoverySearch(null).match("", "prod", "")).isEqualTo(MatchResult.NONE);
		Assertions.assertThat(new SwarmDiscoverySearch(null).match(null, "prod", "")).isEqualTo(MatchResult.NONE);
		Assertions.assertThat(new SwarmDiscoverySearch(null).match("  ", "prod", "")).isEqualTo(MatchResult.NONE);
		Assertions.assertThat(new SwarmDiscoverySearch(null).match("", "", "")).isEqualTo(MatchResult.NONE);
		Assertions.assertThat(new SwarmDiscoverySearch(null).match(null, "", "")).isEqualTo(MatchResult.NONE);
		Assertions.assertThat(new SwarmDiscoverySearch(null).match("  ", "", "")).isEqualTo(MatchResult.NONE);
		Assertions.assertThat(new SwarmDiscoverySearch(null).match("  ", null, "")).isEqualTo(MatchResult.NONE);
		Assertions.assertThat(new SwarmDiscoverySearch(null).match(null, null, "")).isEqualTo(MatchResult.NONE);
	}

	@Test
	public void testLabels() {
		Assertions.assertThat(SwarmDiscoverySearch.TSD_APP_ID).isEqualTo("tsdAppId");
		Assertions.assertThat(SwarmDiscoverySearch.TSD_ENV).isEqualTo("tsdEnv");
		Assertions.assertThat(SwarmDiscoverySearch.TSD_SUB_ENV).isEqualTo("tsdSubEnv");
		Assertions.assertThat(SwarmDiscoverySearch.TSD_PORT).isEqualTo("tsdPort");
		Assertions.assertThat(SwarmDiscoverySearch.TSD_REGION).isEqualTo("tsdRegion");
		Assertions.assertThat(SwarmDiscoverySearch.TSD_SERVICE_GROUP).isEqualTo("tsdServiceGroup");

		Assertions.assertThat(SwarmDiscoverySearch.TSD_APP_ID_LABEL).isEqualTo("label_tsdAppId");
		Assertions.assertThat(SwarmDiscoverySearch.TSD_ENV_LABEL).isEqualTo("label_tsdEnv");
		Assertions.assertThat(SwarmDiscoverySearch.TSD_SUB_ENV_LABEL).isEqualTo("label_tsdSubEnv");
		Assertions.assertThat(SwarmDiscoverySearch.TSD_PORT_LABEL).isEqualTo("label_tsdPort");
		Assertions.assertThat(SwarmDiscoverySearch.TSD_REGION_LABEL).isEqualTo("label_tsdRegion");
		Assertions.assertThat(SwarmDiscoverySearch.TSD_SERVICE_GROUP_LABEL).isEqualTo("label_tsdServiceGroup");
	}

	@Test
	public void testParse() {
		SwarmDiscoverySearch sds = new SwarmDiscoverySearch(null);
		
		Assertions.assertThat(sds.parse((String []) null).getExcludes()).isEmpty();
		Assertions.assertThat(sds.parse((String []) null).getIncludes()).isEmpty();
		Assertions.assertThat(sds.parse("").getExcludes()).isEmpty();
		Assertions.assertThat(sds.parse("").getIncludes()).isEmpty();
		Assertions.assertThat(sds.parse(" ").getExcludes()).isEmpty();
		Assertions.assertThat(sds.parse(" ").getIncludes()).isEmpty();
		Assertions.assertThat(sds.parse(",").getExcludes()).isEmpty();
		Assertions.assertThat(sds.parse(",").getIncludes()).isEmpty();

		Assertions.assertThat(sds.parse("foo").getIncludes()).containsExactly("foo");
		Assertions.assertThat(sds.parse("foo").getExcludes()).containsExactly();

		Assertions.assertThat(sds.parse("foo, bar,!baz").getIncludes()).containsExactly("foo", "bar");
		Assertions.assertThat(sds.parse("foo, bar,!baz").getExcludes()).containsExactly("baz");
		Assertions.assertThat(sds.parse("foo, bar,!baz, !fizz").getExcludes()).containsExactly("baz", "fizz");
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testMandatoryEnvironment() {
		new SwarmDiscoverySearch(neo4j).withRegion("region").withServiceGroup("sg").search();
		
	}
	@Test(expected=IllegalArgumentException.class)
	public void testMandatoryServiceGroup() {
		new SwarmDiscoverySearch(neo4j).withEnvironment("dummy").withRegion("region").search();
		
	}
	@Test(expected=IllegalArgumentException.class)
	public void testMandatoryRegion() {
		new SwarmDiscoverySearch(neo4j).withEnvironment("dummy").withServiceGroup("sg").search();
		
	}
	
	@Test
	public void localTest() {
		swarmClusterManager.newServiceDiscoverySearch().withRegion("local").withEnvironment("demo").withServiceGroup("www").search().forEach(it->{
			logger.info(it.toString());
		});
	}
}
