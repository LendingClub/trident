package org.lendingclub.trident.agent;


public class Main {
	public static void main(String[] args) throws Exception {

		DockerEventAgent agent = new DockerEventAgent();
		agent.start();

		AWSSpotTerminationAgent terminationAgent = new AWSSpotTerminationAgent();
	    terminationAgent.start();
			
	
		
		
		AWSDockerInfoAgent awsInfoAgent = new AWSDockerInfoAgent();
		if (awsInfoAgent.isRunningInEC2()) {		
			awsInfoAgent.start();		
		}
		else {
			DockerInfoAgent dockerInfoAgent = new DockerInfoAgent();

			dockerInfoAgent.start();
		}
	}
}
