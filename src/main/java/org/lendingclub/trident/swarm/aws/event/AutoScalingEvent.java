package org.lendingclub.trident.swarm.aws.event;

import org.lendingclub.trident.event.TridentEvent;

public class AutoScalingEvent extends TridentEvent {

	public AutoScalingEvent() { 
		super();
	}
	
	public String getAutoScalingGroupName() { 
		return getData().path("AutoScalingGroupName").asText();
	}
	
	public String getAutoScalingGroupArn() { 
		return getData().path("AutoScalingGroupARN").asText();
	}
	
	public String getAutoScalingEvent() { 
		return getData().path("Event").asText();
	}
	
	public String getEC2InstanceId() { 
		return getData().path("EC2InstanceId").asText();
	}
	
	public String getAWSAccount() { 
		return getData().path("AccountId").asText();
	}
	
	public String getDescription() { 
		return getData().path("Description").asText();
	}
	
	public String getCause() { 
		return getData().path("Cause").asText();
	}
}
