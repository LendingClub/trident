package org.lendingclub.trident.swarm.aws;

import org.junit.Ignore;
import org.junit.Test;
import org.lendingclub.trident.TridentIntegrationTest;
import org.lendingclub.trident.swarm.aws.task.ManagerELBCreationTask;

public class ManagerELBCreationTaskIntegrationTest extends TridentIntegrationTest{

	
	@Test
	@Ignore
	public void testIt() {
		ManagerELBCreationTask task = new ManagerELBCreationTask();
		
	task.run(); 
	}
}
