package org.lendingclub.trident.scheduler;

import java.util.concurrent.Callable;

import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.Trident;
import org.springframework.context.ApplicationContext;

import com.fasterxml.jackson.databind.JsonNode;

public abstract class DistributedTask implements Runnable {
	DistributedTaskScheduler taskExecutor = null;
	JsonNode data;
	protected void init(DistributedTaskScheduler taskExecutor, JsonNode data) {
		this.taskExecutor = taskExecutor;
		this.data = data;
	}

	public Trident getTrident() {
		return Trident.getInstance();
	}
	
	public ApplicationContext getApplicationContext() {
		return Trident.getInstance().getApplicationContext();
	}
	public JsonNode getData() {
		return data;
	}

	public NeoRxClient getNeoRxClient() {
		return getApplicationContext().getBean(NeoRxClient.class);
	}
	
}
