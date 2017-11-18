package org.lendingclub.trident.swarm.aws;

import java.util.NoSuchElementException;

import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.scheduler.DistributedTask;
import org.lendingclub.trident.scheduler.DistributedTaskScheduler;
import org.lendingclub.trident.swarm.aws.task.AWSEventSetupTask;
import org.lendingclub.trident.util.TridentStartupListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

public class AWSEventSetup implements TridentStartupListener {
	
	@Autowired
	NeoRxClient neo4j;
	
	@Autowired
	DistributedTaskScheduler taskScheduler;
	
	Class<? extends DistributedTask> AWS_SETUP_TASK_CLASS = AWSEventSetupTask.class;
	
	Logger logger = LoggerFactory.getLogger(AWSEventSetup.class);
	
	public void init() { 
			if (isEnabled()) { 
				logger.info("{} is enabled, submitting task", AWS_SETUP_TASK_CLASS);
				taskScheduler.submitTask(AWS_SETUP_TASK_CLASS); 
			} else {
				logger.info("{} is not enabled, will skip SNS/SQS setup", AWS_SETUP_TASK_CLASS);
			}
	}
	
	protected boolean isEnabled() { 
		try { 
			String cypher = "match (x:TridentTaskSchedule {taskClass:{className}}) return x.enabled;";
			return neo4j.execCypher(cypher, "className", AWS_SETUP_TASK_CLASS.getName()).blockingFirst().asBoolean();
		} catch (NoSuchElementException e) { 
			return false;
		}
	}

	@Override
	public void onStart(ApplicationContext context) {
		init();
	}

}