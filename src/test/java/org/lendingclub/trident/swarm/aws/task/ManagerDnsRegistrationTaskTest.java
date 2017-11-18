package org.lendingclub.trident.swarm.aws.task;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.lendingclub.trident.swarm.aws.task.ManagerDnsRegistrationTask;

public class ManagerDnsRegistrationTaskTest {

	

	@Test
	public void testId() {
		Assertions.assertThat(ManagerDnsRegistrationTask.convertDnsNameToRoute53Format("foo")).isEqualTo("foo");
		Assertions.assertThat(ManagerDnsRegistrationTask.convertDnsNameToRoute53Format("foo.example.com")).isEqualTo("foo.example.com.");
		Assertions.assertThat(ManagerDnsRegistrationTask.convertDnsNameToRoute53Format("foo.example.com.")).isEqualTo("foo.example.com.");
	}
}
