package org.lendingclub.trident.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HeartbeatTask extends DistributedTask {

	Logger logger = LoggerFactory.getLogger(HeartbeatTask.class);

	@Override
	public void run() {
		logger.info("heartbeat");
	}

}
