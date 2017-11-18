package org.lendingclub.trident.platform;

import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Test;
import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.TridentIntegrationTest;
import org.lendingclub.trident.swarm.platform.Catalog;
import org.springframework.beans.factory.annotation.Autowired;

public class CatalogIntegrationTest extends TridentIntegrationTest {

	@Autowired
	Catalog catalog;
	
	@Autowired
	NeoRxClient neo4j;
	
	@Test
	public void testIt() {
		Assertions.assertThat(catalog).isNotNull();
		
	}
	
	@Test
	public void testRegions() {
		neo4j.execCypher("merge (a:Config {name:'junit-3',type:'region'}) set a.junitData=true");
		neo4j.execCypher("create (a:DockerService) set a.junitData=true, a.label_tsdRegion='junit-1'");
		neo4j.execCypher("create (a:DockerService) set a.junitData=true, a.label_tsdRegion='junit-4'");
		neo4j.execCypher("create (a:DockerService) set a.junitData=true, a.label_tsdRegion='junit-1'");
		catalog.clearCache();
		Assertions.assertThat(catalog.getRegionNames()).contains("junit-1").contains("junit-4").contains("junit-3");
		Assertions.assertThat(catalog.getRegionNames().stream().filter(p->p.equals("junit-1")).count()).isEqualTo(1);
		
	}
	@Test
	public void testAppId() {
		
		neo4j.execCypher("create (a:DockerService) set a.junitData=true, a.label_tsdAppId='app-a'");
		neo4j.execCypher("create (a:DockerService) set a.junitData=true, a.label_tsdAppId='app-b'");
		neo4j.execCypher("create (a:DockerService) set a.junitData=true, a.label_tsdRegion='junit-1'");
		catalog.clearCache();
		Assertions.assertThat(catalog.getAppIds()).doesNotContain("null").contains("app-a").contains("app-b");
		
		System.out.println(catalog.getAppIds());
	}
	@Test
	public void testEnv() {
		neo4j.execCypher("merge (a:Config {name:'junit-3',type:'environment'}) set a.junitData=true");
		neo4j.execCypher("create (a:DockerService) set a.junitData=true, a.label_tsdEnv='env-a,env-c'");
		neo4j.execCypher("create (a:DockerService) set a.junitData=true, a.label_tsdEnv='env-b'");
		neo4j.execCypher("create (a:DockerService) set a.junitData=true, a.label_tsdRegion='junit-1'");
		catalog.clearCache();
		Assertions.assertThat(catalog.getEnvironmentNames()).doesNotContain("null").contains("env-a").contains("env-b").contains("junit-3");

	}
	@Test
	public void testSubEnv() {
		neo4j.execCypher("merge (a:Config {name:'junit-x',type:'subEnvironment'}) set a.junitData=true");
		neo4j.execCypher("create (a:DockerService) set a.junitData=true, a.label_tsdSubEnv='subenv-b,subenv-x'");
		neo4j.execCypher("create (a:DockerService) set a.junitData=true, a.label_tsdSubEnv='subenv-a'");
		neo4j.execCypher("create (a:DockerService) set a.junitData=true, a.label_tsdRegion='junit-1'");
		catalog.clearCache();
		Assertions.assertThat(catalog.getSubEnvironmentNames()).doesNotContain("null").contains("subenv-a").contains("subenv-b","junit-x");

	}
}
