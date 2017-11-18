package org.lendingclub.trident.event;

import org.lendingclub.trident.chatops.ChatOpsManager;
import org.lendingclub.trident.cluster.TridentClusterManager.LeaderElectedEvent;
import org.lendingclub.trident.swarm.aws.event.AutoScalingGroupCreatedEvent;
import org.lendingclub.trident.util.TridentStartedEvent;
import org.lendingclub.trident.util.TridentStartupListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import io.reactivex.schedulers.Schedulers;

public class EventRegistrations implements TridentStartupListener {

	static Logger logger = LoggerFactory.getLogger(EventRegistrations.class);

	@Autowired
	EventSystem eventSystem;

	@Autowired
	ChatOpsManager chatOpsManager;

	@Override
	public void onStart(ApplicationContext context) {

		eventSystem.createConcurrentSubscriber(LeaderElectedEvent.class).withScheduler(Schedulers.io())
		.subscribe(event -> {
			try {
				
				chatOpsManager.newMessage().withMessage(event.getEventMessage()
						).send();
				;
			} catch (RuntimeException e) {
				logger.info("uncaught execption", e);
			}
		});
		

		eventSystem.createConcurrentSubscriber(AutoScalingGroupCreatedEvent.class).withScheduler(Schedulers.io())
				.subscribe(it -> {
					chatOpsManager.newMessage().withMessage(String.format("Swarm ASG created asg=%s cluster=%s",
							it.getData().path("aws_autoScalingGroupName").asText(),
							it.getEnvelope().path("tridentClusterId").asText())).send();

				});
		
		TridentStartedEvent event = new TridentStartedEvent();
		event.withMessage("Trident started on "+event.getEventSource()).publish();
	}
	

}
