package org.lendingclub.trident.event;

import javax.annotation.PostConstruct;

import org.lendingclub.reflex.operator.ExceptionHandlers;
import org.lendingclub.trident.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import io.reactivex.schedulers.Schedulers;



public class Slf4jEventWriter {

	Logger logger = LoggerFactory.getLogger(Slf4jEventWriter.class);
	@Autowired
	EventSystem eventSystem;

	@PostConstruct
	public void subscribe() {
	
		eventSystem.createConcurrentSubscriber(TridentEvent.class).withScheduler(Schedulers.single()).subscribe(x->{
			
	
			logger.info("logging event:\n {}",JsonUtil.prettyFormat(x.getEnvelope()));
		});
		
		
	}
}