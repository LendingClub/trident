package org.lendingclub.trident.swarm.aws;

import org.junit.Ignore;
import org.junit.Test;
import org.lendingclub.trident.TridentIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;

public class AWSEventManagerTest extends TridentIntegrationTest {

	@Autowired
	AWSEventManager eventManager;
	
	@Test
	@Ignore
	public void testIt() throws InterruptedException {
		
		eventManager.startAllQueueConsumers();
		
		Thread.sleep(40000);
	//	eventManager.attachNotificationToAllTridentAutoScalingGroups();
	//	eventManager.createAllSnsTopics();
	//	eventManager.subscribeAsg("swarm-worker-foo-15d7f60dbaa", "lab", Regions.US_WEST_2);
	}
}
