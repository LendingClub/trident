package org.lendingclub.trident;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.github.dockerjava.api.DockerClient;

public class DockerClientManagerTest extends TridentIntegrationTest {

	@Autowired
	DockerClientManager clientManager;

	@Autowired
	ApplicationContext applicationContext;

	@Test
	public void testIt() {
		Assertions.assertThat(clientManager).isNotNull();

	}

	@Test
	public void testX() {
		Assertions.assertThat(clientManager.newClient("local")).isNotNull();
	}

	@Test
	public void testY() {
		Assertions.assertThat(clientManager.getClient("local")).isSameAs(clientManager.getClient("local"));

		System.out.println(clientManager.getClient("local").infoCmd().exec());

	}

	@Test(expected = ClientNotFoundException.class)
	public void testNotFound() {
		clientManager.getClient("notfound");
	}

	@Test
	public void testStage() {

	

		DockerClient c = clientManager.getClient("stage");

		c.listContainersCmd().exec().forEach(it -> {
			System.out.println(it);
		});

	}

	@Test
	public void testSuppliers() {
		System.out.println(clientManager.getDockerClientSuppliers());
	}

	@Test
	public void testLocal() {
		System.out.println(clientManager.getDockerClientSuppliers());
		
			
		
	}


	@Test
	public void testNeo4j() {
		// should be set regardless of whether neo4j is available
		Assertions.assertThat(applicationContext.getBean("neo4j")).isNotNull();

	}
}
