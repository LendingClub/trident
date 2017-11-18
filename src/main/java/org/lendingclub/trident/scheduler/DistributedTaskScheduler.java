package org.lendingclub.trident.scheduler;

import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import org.lendingclub.mercator.core.SchemaManager;
import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.cluster.TridentClusterManager;
import org.lendingclub.trident.swarm.SwarmScanTask;
import org.lendingclub.trident.swarm.aws.task.AWSEventSetupTask;
import org.lendingclub.trident.swarm.aws.task.AWSRegistrationTask;
import org.lendingclub.trident.swarm.aws.task.AWSScannerTask;
import org.lendingclub.trident.swarm.aws.task.AWSTerminatedNodeCleanupTask;
import org.lendingclub.trident.swarm.aws.task.AutoScalingGroupNotificationRegistrationTask;
import org.lendingclub.trident.swarm.aws.task.LoadBalancerSetupTask;
import org.lendingclub.trident.swarm.aws.task.ManagerDnsRegistrationTask;
import org.lendingclub.trident.swarm.aws.task.ManagerELBCreationTask;
import org.lendingclub.trident.swarm.aws.task.AWSManagerASGScaleUpTask;
import org.lendingclub.trident.util.JsonUtil;
import org.lendingclub.trident.util.TridentStartupListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import com.google.common.primitives.Longs;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

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
					String id = it.path("scheduleId").asText();
					String crontab = it.path("cronExpression").asText();
					String task = it.path("taskClass").asText();
					SchedulingPattern sp = new SchedulingPattern(crontab);

					Preconditions.checkArgument(!Strings.isNullOrEmpty(id), "invalid id: " + it);
					Preconditions.checkArgument(!Strings.isNullOrEmpty(task), "invalid task: " + it);
					tt.add(sp, toCron4jTask(id, crontab, task, it));
				} catch (RuntimeException e) {
					logger.info("could not schedule: " + it, e);
				}
			});
			return tt;
		}

	}

	class IgniteQueueConsumer implements Runnable {
		@Override
		public void run() {

			while (true == true) {
				try {
					if (tridentClusterManager.isEligibleLeader()) {
						takeItemFromIgniteQueue();
					} else {
						try {
							Thread.sleep(5000);
						} catch (Exception ignore) {
						}
					}
				} catch (Exception e) {
					logger.warn("problem getting item from ignite queue", e);

				}
			}

		}
	}

	public final void submitTask(Class<? extends DistributedTask> task) {
		submitTask(task, null);
	}

	public final void submitTask(Class<? extends DistributedTask> task, JsonNode n) {
		ObjectNode taskNode = null;
		if (n != null && n.isObject()) {
			taskNode = n.deepCopy();
		} else {
			taskNode = JsonUtil.createObjectNode();
		}

		taskNode.put("taskClass", task.getName());
		taskNode.remove("createTs");
		taskNode.remove("updateTs");
		taskNode.remove("enabled");
		submitTask(taskNode);
	}

	private void addTaskQueue(JsonNode n) {
		JsonUtil.logInfo("adding task to queue", n);
		String uuid = UUID.randomUUID().toString();
		neo4j.execCypher(
				"merge (a:TridentTaskQueue {taskId:{taskId}}) set a+={props}, a.claimId={claimId}, a.createTs=timestamp()",
				"taskId", n.path("taskId").asText(), "props", n, "claimId", uuid);

	}

	public void submitTask(ObjectNode n) {
		ObjectNode copy = n.deepCopy();
		if (!copy.has("taskId")) {
			copy.put("taskId", "tid-" + BaseEncoding.base32().encode(Longs.toByteArray(new SecureRandom().nextLong()))
					.replace("=", "").toLowerCase());
		}
		copy.remove("enabled");
		copy.remove("createTs");
		copy.remove("updateTs");

		addTaskQueue(copy);
	}

	private Task toCron4jTask(String id, String crontab, String name, JsonNode n) {
		Task task = new Task() {

			@Override
			public void execute(TaskExecutionContext context) throws RuntimeException {
				Preconditions.checkArgument(!Strings.isNullOrEmpty(id));
				Preconditions.checkArgument(!Strings.isNullOrEmpty(name));

				ObjectNode copy = null;
				if (n != null && n.isObject()) {
					copy = n.deepCopy();
				} else {
					copy = JsonUtil.createObjectNode();
				}
				copy.put("taskId", UUID.randomUUID().toString());
				copy.put("scheduleId", id);
				copy.put("taskClass", name);
				copy.put("cronExpression", crontab);
				submitTask(copy);
			}

		};
		return task;
	}

	private void applyConstraints() {

		if (neo4j.checkConnection()) {
			SchemaManager sm = new SchemaManager(neo4j);

			// remove old constraints
			// sm.dropUniqueConstraint("TridentTaskSchedule","id");
			// sm.dropUniqueConstraint("TridentTask","tridentTaskId");
			sm.applyUniqueConstraint("TridentTaskSchedule", "scheduleId");
			sm.applyUniqueConstraint("TridentTask", "taskId");
			sm.applyUniqueConstraint("TridentTaskQueue","taskId");
		}
	}

	private void takeItemFromIgniteQueue() {

	
		AtomicLong count = new AtomicLong(0);
		neo4j.execCypher("match (a:TridentTaskQueue) return a order by a.createTs desc limit 1").forEach(it -> {
			
			// First delete the task.  
			long c = neo4j.execCypher("match (a:TridentTaskQueue {taskId:{taskId}}) delete a return a", "taskId",
					it.path("taskId").asText()).count().blockingGet();
			JsonUtil.logInfo("process item", it);
			count.addAndGet(c);
			if (c>0) {
				submitLocal(it);
			}	
		});
		
		
		if (count.get() == 0) {
			try {
				Thread.sleep(1000L);
			} catch (Exception e) {
			}
		}
		else {
			logger.info("from queue: {}",count.get());
		}
		

	}

	protected void recordTaskStart(DistributedTask task) {
		neo4j.execCypher(
				"merge (t:TridentTask {taskId:{taskId}}) on create set t.startTs=timestamp(),t.cronExpression={cronExpression},t.status='started',t+={props}",
				"taskClass", task.getClass().getName(), "taskId", task.getData().path("taskId").asText(),
				"cronExpression", task.getData().path("cronExpression").asText(), "props", task.getData());
	}

	protected void recordTaskEnd(DistributedTask task) {
		neo4j.execCypher("merge (t:TridentTask {taskId:{taskId}}) set t.endTs=timestamp(),t.status='complete'",
				"taskId", task.getData().path("taskId").asText());
	}

	protected void recordTaskFailure(DistributedTask task, Exception e) {

		neo4j.execCypher("merge (t:TridentTask {taskId:{taskId}}) set t.endTs=timestamp(),t.status='failed'", "taskId",
				task.getData().path("taskId").asText());
	}

	private final void submitLocal(DistributedTask task) {
		Runnable exceptionSafeWrapper = new Runnable() {
			public void run() {
				try {
					recordTaskStart(task);
					task.run();
					recordTaskEnd(task);
				} catch (Exception e) {
					recordTaskFailure(task, e);
					LoggerFactory.getLogger(task.getClass()).error("problem running task: " + task, e);
				}
			}
		};
		this.executor.submit(exceptionSafeWrapper);
	}

	private final void submitLocal(JsonNode data) {

		String name = null;
		try {

			name = data.path("taskClass").asText(null);
			if (Strings.isNullOrEmpty(name)) {
				throw new IllegalArgumentException("taskClass must be set");
			}
			Class<? extends DistributedTask> taskClass = (Class<? extends DistributedTask>) Class.forName(name);
			DistributedTask task = taskClass.newInstance();
			if (data == null || data.isNull() || data.isMissingNode()) {
				data = JsonUtil.createObjectNode();
			}
			task.init(this, data);
			submitLocal(task);
		} catch (ClassNotFoundException e) {
			logger.warn("task class not found: {}", name);
		} catch (IllegalAccessException | InstantiationException e) {
			logger.warn("problem submitting task for execution", e);
		}

	}

	public void enableTask(String id, boolean b) {
		neo4j.execCypher(
				"match (c:TridentTaskSchedule {scheduleId:{id}}) set c.enabled={enabled}, c.updateTs=timestamp()", "id",
				id, "enabled", b);
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

		String id = "sid-" + BaseEncoding.base32().encode(Hashing.sha1().hashBytes(name.getBytes()).asBytes())
				.replace("=", "").toLowerCase();

		neo4j.execCypher(
				"merge (c:TridentTaskSchedule {scheduleId:{scheduleId}}) ON CREATE set c.cronExpression={cronExpression}, c.taskClass={taskClass}, c+={props},c.createTs=timestamp(),c.updateTs=timestamp()",
				"scheduleId", id, "cronExpression", crontab, "props", n, "taskClass", name);

	}

	private void seedTasks() {
		scheduleTask("* * * * *", HeartbeatTask.class);
		scheduleTask("* * * * *", SwarmScanTask.class);
		scheduleTask("*/12 * * * *", AWSScannerTask.class);
		scheduleTask("* * * * *", AWSRegistrationTask.class);
		scheduleTask("*/5 * * * *", AWSTerminatedNodeCleanupTask.class);
		scheduleTask("* * * * *", ManagerELBCreationTask.class);
		scheduleTask("* * * * *", ManagerDnsRegistrationTask.class);
		scheduleTask("* * * * *", AutoScalingGroupNotificationRegistrationTask.class);
		scheduleTask("* */23 * * *", AWSEventSetupTask.class);
		scheduleTask("*/6 * * * *", LoadBalancerSetupTask.class);
		scheduleTask("*/6 * * * *",AWSManagerASGScaleUpTask.class);
	}

	void oneTimeFixes() {
		// one-time data-model fixes...can be removed soon
		neo4j.execCypher("match (t:TridentTaskSchedule) where not exists (t.taskScheduleId) set t.taskScheduleId=t.id");
		neo4j.execCypher(
				"match (t:TridentTaskSchedule) where not exists (t.taskScheduleExpression) and exists(t.pattern) set t.taskScheduleExpression=t.pattern");
		neo4j.execCypher(
				"match (t:TridentTaskSchedule) where not exists (t.taskScheduleExpression) and exists(t.taskSchedulePattern) set t.taskScheduleExpression=t.taskSchedulePattern");
		neo4j.execCypher("match (t:TridentTaskSchedule) where not exists (t.taskClass) set t.taskClass=t.task");
		neo4j.execCypher(
				"match (t:TridentTaskSchedule) where not exists (t.taskClass) set t.taskClass=t.taskClassName");
		neo4j.execCypher(
				"match (t:TridentTaskSchedule) where not exists (t.cronExpression) and exists(t.taskScheduleExpression) set t.cronExpression=t.taskScheduleExpression");
		neo4j.execCypher(
				"match (t:TridentTaskSchedule) where not exists (t.scheduleId) and exists(t.taskScheduleId) set t.scheduleId=t.taskScheduleId");

		// delete scheduled tasks before a refactoring point
		neo4j.execCypher(
				"match (t:TridentTaskSchedule) where t.createTs is null or t.createTs<1509460091356 detach delete t");
		neo4j.execCypher(
				"match (t:TridentTaskSchedule) remove t.id, t.pattern,t.name,t.task,t.taskScheduleExpression,t.taskSchedulePattern,t.taskClassName,t.taskScheduleId");
	}

	@Override
	public void onStart(ApplicationContext context) {

		oneTimeFixes();

		applyConstraints();

		cron4j.addTaskCollector(new TridentTaskCollector());
		cron4j.start();
		this.executor = Executors.newFixedThreadPool(10);

		seedTasks();

		Thread t = new ThreadFactoryBuilder().setNameFormat("DistributedTaskScheduler-%d").setDaemon(true).build()
				.newThread(new IgniteQueueConsumer());

		t.start();
	}
}
