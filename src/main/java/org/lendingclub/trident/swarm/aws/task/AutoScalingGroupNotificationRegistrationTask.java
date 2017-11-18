package org.lendingclub.trident.swarm.aws.task;

import org.lendingclub.trident.scheduler.DistributedTask;
import org.lendingclub.trident.swarm.aws.AWSEventManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutoScalingGroupNotificationRegistrationTask extends DistributedTask {
	
	Logger logger = LoggerFactory.getLogger(AutoScalingGroupNotificationRegistrationTask.class);
	
	@Override
	public void run() {
		logger.info("configuring all ASGs to send notifications to SNS");
		getApplicationContext().getBean(AWSEventManager.class).attachNotificationToAllTridentAutoScalingGroups();
	}
}