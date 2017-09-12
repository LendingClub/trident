package org.lendingclub.trident.swarm;

import org.lendingclub.trident.Trident;
import org.lendingclub.trident.scheduler.DistributedTask;

public class SwarmScanTask extends DistributedTask {

	@Override
	public void run() {
		Trident.getApplicationContext().getBean(SwarmClusterManager.class).scanAllSwarms();
	}

}
