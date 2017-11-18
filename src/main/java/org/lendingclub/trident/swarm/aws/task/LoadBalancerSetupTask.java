package org.lendingclub.trident.swarm.aws.task;

import org.lendingclub.trident.loadbalancer.LoadBalancerSetupManager;
import org.lendingclub.trident.scheduler.DistributedTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoadBalancerSetupTask extends DistributedTask {
	Logger logger = LoggerFactory.getLogger(LoadBalancerSetupTask.class);

	@Override
	public void run() {
		getApplicationContext().getBean(LoadBalancerSetupManager.class).setupLoadBalancers();
		getApplicationContext().getBean(LoadBalancerSetupManager.class).cleanUpLoadBalancers();
	}

}