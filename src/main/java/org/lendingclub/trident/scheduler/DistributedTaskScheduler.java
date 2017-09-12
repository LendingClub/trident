package org.lendingclub.trident.scheduler;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import org.lendingclub.mercator.core.SchemaManager;
import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.cluster.TridentClusterManager;
import org.lendingclub.trident.swarm.SwarmScanTask;
import org.lendingclub.trident.swarm.aws.AWSRegistrationTask;
import org.lendingclub.trident.swarm.aws.AWSScannerTask;
import org.lendingclub.trident.swarm.aws.AWSTerminatedNodeCleanupTask;
import org.lendingclub.trident.swarm.aws.ManagerDnsRegistrationTask;
import org.lendingclub.trident.swarm.aws.ManagerELBCreationTask;
import org.lendingclub.trident.util.JsonUtil;
import org.lendingclub.trident.util.TridentStartupListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.hash.Hashing;

import it.sauronsoftware.cron4j.Scheduler;
import it.sauronsoftware.cron4j.SchedulingPattern;
import it.sauronsoftware.cron4j.Task;
import it.sauronsoftware.cron4j.TaskCollector;
import it.sauronsoftware.cron4j.TaskExecutionContext;
import it.sauronsoftware.cron4j.TaskTable;

public class DistributedTaskScheduler implements TridentStartupListener {

	Scheduler cron4j = new Scheduler();

	String taskExecutorId = UUID.randomUUID().toString();

	Logger logger = org.slf4j.LoggerFactory.getLogger(DistributedTaskScheduler.class);
	ExecutorService executor;

	ScheduledExecutorService scheduledExecutor;

	@Autowired
	NeoRxClient neo4j;

	@Autowired
	TridentClusterManager tridentClusterManager;
	
	class TridentTaskCollector implements TaskCollector {

		@Override
		public TaskTable getTasks() {
		
			TaskTable tt = new TaskTable();
			if (!tridentClusterManager.isLeader()) {
				logger.info("we are not the leader so we do not run cron4j");
				return tt;
			}
			neo4j.execCypher("match (t:TridentTaskSchedule) where not exists (t.enabled) set t.enabled=true");

			neo4j.execCypher("match (t:TridentTaskSchedule) where t.enabled=true return t").forEach(it -> {
				try {
					String id = it.path("id").asText();
					String crontab = it.path("pattern").asText();
					String task = it.path("task").asText();
					SchedulingPattern sp = new SchedulingPattern(crontab);

					Preconditions.checkArgument(!Strings.isNullOrEmpty(id), "invalid id: " + it);
					Preconditions.checkArgument(!Strings.isNullOrEmpty(task), "invalid task: " + it);
					tt.add(sp, toCron4jTask(id, task, it));
				} catch (RuntimeException e) {
					logger.info("could not schedule: " + it, e);
				}
			});
			return tt;
		}

	}

	public final void submitTask(Class<? extends DistributedTask> task) {
		submitTask(task, null);
	}

	public final void submitTask(Class<? extends DistributedTask> task, JsonNode n) {
		submitTask(task, n);
	}

	public final void submitTask(String task) {
		submitTask(task, null);
	}

	public void submitTask(String task, JsonNode n) {
		if (n == null || n.isMissingNode() || n.isNull()) {
			n = JsonUtil.createObjectNode();
		}
		String uuid = UUID.randomUUID().toString();
		neo4j.execCypher(
				"create (t:TridentTask {tridentTaskId:{tridentTaskId}}) set t.submitTs=timestamp(), t.task={task}, t+={props}",
				"tridentTaskId", uuid, "task", task, "props", n);
	}

	private Task toCron4jTask(String id, String name, JsonNode n) {
		Task task = new Task() {

			@Override
			public void execute(TaskExecutionContext context) throws RuntimeException {
				Preconditions.checkArgument(!Strings.isNullOrEmpty(id));
				Preconditions.checkArgument(!Strings.isNullOrEmpty(name));
				String uuid = UUID.randomUUID().toString();
				neo4j.execCypher(
						"create (t:TridentTask {tridentTaskId:{tridentTaskId}}) set t.submitTs=timestamp(), t.task={task}, t+={props}",
						"tridentTaskId", uuid, "task", name, "props", n);
				neo4j.execCypher(
						"match (q:TridentTask {tridentTaskId:{tridentTaskId}}), (s:TridentTaskSchedule {id:{scheduleId}}) merge (q)-[x:SCHEDULED_BY]->(s)",
						"tridentTaskId", uuid, "scheduleId", id);
			}

		};
		return task;
	}

	private void applyConstraints() {

		if (neo4j.checkConnection()) {
			SchemaManager sm = new SchemaManager(neo4j);
			sm.applyUniqueConstraint("TridentTaskSchedule", "id");
			sm.applyUniqueConstraint("TridentTask", "tridentTaskId");

		}
	}

	@PostConstruct
	public void startup() {

		applyConstraints();

		cron4j.addTaskCollector(new TridentTaskCollector());
		cron4j.start();
		this.executor = Executors.newFixedThreadPool(10);
		Runnable r = new Runnable() {

			@Override
			public void run() {
				if (tridentClusterManager.isEligibleLeader());
				logger.debug("looking for tasks...");
				neo4j.execCypher("match (t:TridentTask) where not exists(t.taskExecutor) return t").forEach(it -> {
					String task = it.path("task").asText();
					String id = it.path("tridentTaskId").asText();

					if (!Strings.isNullOrEmpty(id)) {
						neo4j.execCypher(
								"match (t:TridentTask{tridentTaskId:{id}}) where not exists(t.taskExecutor) set t.startTs=timestamp(), t.taskExecutor={te} return t",
								"id", id, "te", taskExecutorId).forEach(x -> {
									logger.info("submitting task " + x);
									submitLocal(task, x);

								});

					}

				});

				neo4j.execCypher(
						"match (t:TridentTask) where  t.submitTs<timestamp()-{retentionMillis} detach delete t",
						"retentionMillis", TimeUnit.HOURS.toMillis(1));

			}

		};
		scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
		scheduledExecutor.scheduleWithFixedDelay(r, 0, 5, TimeUnit.SECONDS);

		
	}

	public final void submitLocal(DistributedTask task) {
		Runnable exceptionSafeWrapper = new Runnable() {
			public void run() {
				try {
					task.run();
				} catch (Exception e) {
					LoggerFactory.getLogger(task.getClass()).error("problem running task: " + task, e);
				}
			}
		};
		this.executor.submit(exceptionSafeWrapper);
	}

	public final void submitLocal(Class<? extends DistributedTask> c, JsonNode data) {
		submitLocal(c.getName(), data);
	}

	public final void submitLocal(Class<? extends DistributedTask> c) {
		submitLocal(c.getName());
	}

	public final void submitLocal(String name) {
		submitLocal(name, null);
	}

	public final void submitLocal(String name, JsonNode data) {
		try {

			Class<? extends DistributedTask> taskClass = (Class<? extends DistributedTask>) Class.forName(name);
			DistributedTask task = taskClass.newInstance();
			if (data == null || data.isNull() || data.isMissingNode()) {
				data = JsonUtil.createObjectNode();
			}
			task.init(this, data);
			submitLocal(task);
		}
		catch (ClassNotFoundException e) {
			logger.warn("task class not found: {}",name);
		} catch (IllegalAccessException | InstantiationException e) {
			logger.warn("problem submitting task for execution", e);
		}

	}

	public void enableTask(String id, boolean b) {
		neo4j.execCypher("match (c:TridentTaskSchedule {id:{id}}) set c.enabled={enabled}, c.updateTs=timestamp()",
				"id", id, "enabled", b);
	}

	public void scheduleTask(String crontab, Class<? extends DistributedTask> name) {
		scheduleTask(crontab, name, null);
	}

	public void scheduleTask(String crontab, Class<? extends DistributedTask> name, JsonNode n) {
		scheduleTask(crontab, name.getName(), n);
	}

	public void scheduleTask(String crontab, String name, JsonNode n) {

			if (n == null || n.isNull() || n.isMissingNode()) {
				n = JsonUtil.createObjectNode();
			}
			String id = "id-" + Hashing.sha1().hashBytes(name.getBytes()).toString();

			neo4j.execCypher(
					"merge (c:TridentTaskSchedule {id:{id}}) ON CREATE set c.pattern={pattern}, c.task={name}, c+={props}",
					"id", id, "pattern", crontab, "props", n, "name", name);
	
	}

	private void seedTasks() {
		scheduleTask("* * * * *", HeartbeatTask.class);
		scheduleTask("* * * * *", SwarmScanTask.class);
		scheduleTask("*/12 * * * *", AWSScannerTask.class);
		scheduleTask("* * * * *", AWSRegistrationTask.class);
		scheduleTask("*/5 * * * *", AWSTerminatedNodeCleanupTask.class);
		scheduleTask("* * * * *", ManagerELBCreationTask.class);
		scheduleTask("* * * * *", ManagerDnsRegistrationTask.class);
	}

	@Override
	public void onStart(ApplicationContext context) {
		seedTasks();
		
	}
}
