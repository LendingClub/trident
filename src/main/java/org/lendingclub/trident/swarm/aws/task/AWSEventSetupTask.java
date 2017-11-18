package org.lendingclub.trident.swarm.aws.task;

import org.lendingclub.trident.scheduler.DistributedTask;
import org.lendingclub.trident.swarm.aws.AWSEventManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AWSEventSetupTask extends DistributedTask {
	Logger logger = LoggerFactory.getLogger(AWSEventSetupTask.class);

	@Override
	public void run() {
		logger.info("setting up AWS SNS and SQS");
		getApplicationContext().getBean(AWSEventManager.class).createAllSnsTopics();
		getApplicationContext().getBean(AWSEventManager.class).startAllQueueConsumers();
	}
}
