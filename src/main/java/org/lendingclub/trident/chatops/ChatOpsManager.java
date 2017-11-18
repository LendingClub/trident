package org.lendingclub.trident.chatops;

import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.lendingclub.trident.config.ConfigManager;
import org.lendingclub.trident.event.EventSystem;
import org.lendingclub.trident.swarm.aws.event.AutoScalingGroupCreatedEvent;
import org.lendingclub.trident.util.TridentStartupListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import io.reactivex.schedulers.Schedulers;

public class ChatOpsManager implements TridentStartupListener {

	Logger logger = LoggerFactory.getLogger(ChatOpsManager.class);

	LinkedBlockingDeque<Runnable> queue = new LinkedBlockingDeque<>();
	ThreadPoolExecutor tpe = null;

	LoggingChatProvider loggingProvider = new LoggingChatProvider();

	AtomicReference<ChatProvider> provider = new AtomicReference<ChatProvider>(null);

	@Autowired
	ConfigManager configManager;

	@Autowired
	EventSystem eventSystem;

	public ChatOpsManager() {
		ThreadFactory tf = new ThreadFactoryBuilder().setDaemon(true).setNameFormat("chatops-%d").build();
		tpe = new ThreadPoolExecutor(1, 3, 10, TimeUnit.SECONDS, queue, tf);
		tpe.prestartAllCoreThreads();
	}

	public ChatMessage newMessage() {
		return new ChatMessage(this);
	}

	ChatProvider getProvider() {
		ChatProvider p = provider.get();

		if (p == null) {
			try {
				java.util.Optional<JsonNode> cfg = configManager.getConfig("chatops", "default");
				if (!cfg.isPresent()) {
					return loggingProvider;
				}
				String className = cfg.get().path("provider").asText();
				if (Strings.isNullOrEmpty(className)) {
					return loggingProvider;
				}
				Class<ChatProvider> providerClass = (Class<ChatProvider>) Class.forName(className);
				ChatProvider cp = providerClass.newInstance();
				cp.init(cfg.get());
				this.provider.set(cp);
			} catch (ClassNotFoundException | IllegalAccessException | InstantiationException | RuntimeException e) {
				logger.warn("could not configure provider", e.toString());
				return loggingProvider;
			}

		}
		return provider.get();
	}

	void send(ChatMessage m) {
		Runnable r = new Runnable() {

			@Override
			public void run() {
				getProvider().send(m);

			}

		};

		queue.offer(r);
	}

	


	@Override
	public void onStart(ApplicationContext context) {
	
		// WJFNfNViWzfDd7kkyBGHbSuSHBY9WinPvks1DKkr
		configManager.setValueIfNotSet("chatops", "default", "defaultRoom", "ROOM_NAME", false);
		configManager.setValueIfNotSet("chatops", "default", "provider", HipChatProvider.class.getName(), false);
		configManager.setValueIfNotSet("chatops", "default", "token", "TOKEN", false);
	}
}
