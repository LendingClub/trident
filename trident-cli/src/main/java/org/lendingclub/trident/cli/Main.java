package org.lendingclub.trident.cli;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import org.lendingclub.trident.cli.command.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.bridge.SLF4JBridgeHandler;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.MissingCommandException;
import com.beust.jcommander.Parameters;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.reflect.ClassPath;
import com.google.common.reflect.ClassPath.ClassInfo;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import ch.qos.logback.core.util.StatusPrinter;

public class Main {
	static Logger logger = LoggerFactory.getLogger(Main.class);

	static boolean quiet = true;
	List<String> argList = Lists.newArrayList();
	ConfigManager configManager;
	JCommander jcommander = null;
	Map<String, Command> commandMap = Maps.newHashMap();
	Command activeCommand;

	public static void main(String[] args) {
		configureLogging(Lists.newArrayList(args)); // logging needs to be configured first
		try {
		Main main = new Main();
		main.exec(args);
		
		}
		catch (GracefulExitException e) {
			// do nothing
		}
		catch (IOException | TimeoutException | RuntimeException e) {
			logger.error("error", e);
			stderr("error: %s", e.getMessage()); // exception type is not useful
													// here
			System.exit(1);
		}
	}

	public JCommander getJCommander() {
		return jcommander;
	}

	public List<String> getArgList() {
		return argList;
	}
	@SuppressWarnings("unchecked")
	protected <T> T narrow(Object x) {
		return (T) x;
	}


	private String rpad(String input, int len) {
		StringBuffer sb = new StringBuffer();
		sb.append(input);
		int padLen = len - input.length();
		for (int i = 0; i < padLen; i++) {
			sb.append(" ");
		}
		return sb.toString();
	}
	static class CLILogger extends CommandLineLogger {

		@Override
		public void doProgress(String fmt, Object... args) {

			String out = CommandLineLogger.format(fmt, args);
			logger.info("{}", out);
			if (!quiet) {
				System.err.println(out); // intentional
			}

		}

	}

	public void globalRewrite() {

		logger.info("args before globalRewrite: {}", argList);
		if (argList.contains("-q") || argList.contains("--quiet") || argList.contains("--silent")) {
			argList.remove("-q");
			argList.remove("--quiet");
			argList.remove("--silent");
			quiet = true;
		}

		if (!argList.isEmpty()) {
			String firstArg = argList.get(0);
			if (firstArg.equals("--version")) {
				argList.set(0, "version");
				argList.remove("--version");
			}
		}
		logger.info("args after gloablRewrite: {}", argList);
	}

	protected void initializeJCommander() {
		Preconditions.checkNotNull(argList);

		jcommander = new JCommander(argList.toArray(new String[0]));
		jcommander.setProgramName("trident");
		jcommander.setExpandAtSign(false);

		registerCommands();

	}

	private List<Command> loadCommands() {
		try {
			List<Command> list = Lists.newArrayList();
			ImmutableSet<ClassInfo> classes = ClassPath.from(getClass().getClassLoader())
					.getTopLevelClasses(Command.class.getPackage().getName());
			classes.forEach(it -> {
				try {
					if (it.getName().endsWith("Command")) {

						Command c = (Command) Class.forName(it.getName()).newInstance();
						Parameters params = c.getClass().getAnnotation(Parameters.class);

						if (params == null) {
							logger.warn("{} is missing @Parameters annotation", c.getClass());
						} else {
							list.add(c);
						}

					}
				} catch (InstantiationException e) {
					logger.debug("could not register: {}", it.getName() + " : " + e.toString());
				} catch (ClassNotFoundException | IllegalAccessException | RuntimeException e) {
					logger.warn("could not register: {}", it.getName() + " : " + e.toString());
				}

			});
			return list;
		} catch (IOException e) {
			throw new CLIException(e);
		}
	}

	private void registerCommands() {

		for (Command command : loadCommands()) {

			add(command);

		}

	}

	public void add(Command cmd) {
		add(cmd.getCommandName(), cmd);
	}

	public void add(String name, Command cmd) {

		jcommander.addCommand(name, cmd);
	    commandMap.put(name, cmd);

		try {
			cmd.rewriteArgList(argList);
		} catch (RuntimeException e) {
			logger.warn("problem rewriting arg list", e);
		}
	}

	public Optional<Command> getActiveCommand() {
		return Optional.ofNullable(null);
	}

	public <T extends Command> T exec(String... args) throws IOException, TimeoutException {

		this.argList = Lists.newArrayList(args);

		globalRewrite();

		if (configManager == null) {
			configManager = new ConfigManager();
			configManager.initConfig();
				configManager.getConfig();
			
		}

		initializeJCommander();

		if (argList.size() == 0) {
			throw new MissingCommandException("command not specified");
		}
		String commandName = argList.get(0);
		MDC.put("cmd", commandName);

		logger.info("command={}", commandName);
		Command cmd = getCommand(commandName);

	
		if (cmd == null) {
			usage();
			throw new GracefulExitException();
		}
		cmd.init(argList,jcommander,configManager);
		activeCommand = cmd;
		cmd.doParse();
		if (cmd.isQuiet()) {
			quiet = true;
		}
		cmd.run();

		return narrow(cmd);
	}

	public static void configureLogging(List<String> args) {

		
		CommandLineLogger.installLogger(new CLILogger());

		File tridentDir = new File(System.getProperty("user.home", "."), ".trident");
		tridentDir.mkdirs();

		File deprecatedLogFile = new File(tridentDir, "trident.log"); // not honored due to use of prudent=true
		if (deprecatedLogFile.exists()) {
			deprecatedLogFile.delete();
		}

		LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
		ch.qos.logback.classic.Logger myLogger = loggerContext
				.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
		myLogger.detachAndStopAllAppenders();

		logger = myLogger;

		RollingFileAppender<ILoggingEvent> fileAppender = new RollingFileAppender<ILoggingEvent>();
		fileAppender.setFile(null); // not honored due to use of prudent=true
		fileAppender.setContext(loggerContext);

		PatternLayoutEncoder encoder = new PatternLayoutEncoder();
		encoder.setContext(loggerContext);
		encoder.setPattern("%d{yyyy-MM-dd HH:mm:ss.SSS Z} %level [%thread] [%X] %logger{36} - %msg %n");
		encoder.start();

		TimeBasedRollingPolicy<ILoggingEvent> rollingPolicy = new TimeBasedRollingPolicy<>();
		rollingPolicy.setContext(loggerContext);
		rollingPolicy.setMaxHistory(5);
		rollingPolicy.setCleanHistoryOnStart(true);
		rollingPolicy.setFileNamePattern(new File(tridentDir, "trident.%d{yyyy-MM-dd}.log").getAbsolutePath());
		rollingPolicy.setParent(fileAppender);
		rollingPolicy.start();

		fileAppender.setRollingPolicy(rollingPolicy);
		fileAppender.setTriggeringPolicy(rollingPolicy);

		fileAppender.setName("TRIDENT_FILE_LOGGER");
		fileAppender.setAppend(true);
		fileAppender.setEncoder(encoder);
		fileAppender.setPrudent(true);

		fileAppender.start();
		myLogger.addAppender(fileAppender);

		if (isConsoleLoggingEnabled(args)) {
			ConsoleAppender<ILoggingEvent> consoleAppender = new ConsoleAppender<>();
			PatternLayoutEncoder consoleEncoder = new PatternLayoutEncoder();
			consoleEncoder.setContext(loggerContext);
			consoleEncoder.setPattern("%r %level [%thread] [%X] %logger{36} - %msg %n");
			consoleEncoder.start();

			consoleAppender.setEncoder(consoleEncoder);
			consoleAppender.setName("CONSOLE");
			consoleAppender.setOutputStream(System.err);
			consoleAppender.setContext(loggerContext);
			consoleAppender.start();

			myLogger.addAppender(consoleAppender);

			StatusPrinter.print(loggerContext);
		}

		myLogger.setLevel(Level.INFO);
		if (args.contains("--debug")) {
			myLogger.setLevel(Level.DEBUG);
		}

		try {

			SLF4JBridgeHandler.removeHandlersForRootLogger();
			SLF4JBridgeHandler.install();
		} catch (Exception e) {
			logger.warn("problem installing bridge", e);
		}
	}

	private static boolean isConsoleLoggingEnabled(List<String> argList) {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		new RuntimeException().printStackTrace(pw);
		pw.close();

		if (sw.toString().contains("org.junit.runners")) {
			return true;
		}

		if (argList.contains("--debug") || argList.contains("--info")) {
			return true;
		}

		return false;
	}

	public void usage() {
		stderr("usage: trident <command> [<options>]  [--debug]");
		stderr("");
		stderr("commands:");
		stderr("");

		List<Command> list = loadCommands();
		int longest = list.stream().mapToInt(f -> f.getCommandName().length()).max().getAsInt();

		list.stream().sorted(new CommandSortComparator()).forEach(command -> {
			if (!command.isHiddenCommand()) {
				stderr("%s %s", rpad(command.getCommandName(), longest + 1), command.getCommandDescription());
			}
		});

		stderr("");
		stderr("For more information on each command, run:");
		stderr("");
		stderr("  trident <command> --help");
		stderr("");
	}

	class CommandSortComparator implements Comparator<Command> {

		@Override
		public int compare(Command o1, Command o2) {
			return o1.getCommandName().compareTo(o2.getCommandName());
		}

	}

	public static void progress(String fmt, Object... args) {
		if (quiet) {
			String out = CommandLineLogger.format(fmt, args);
			logger.info("{}", out);
		} else {
			stderr(fmt, args);
		}
	}

	public static void stdout(String fmt, Object... args) {
		String out = CommandLineLogger.format(fmt, args);
		logger.info("{}", out);
		System.out.println(out); // intentional!!!
	}

	public static void stderr(String fmt, Object... args) {
		String out = CommandLineLogger.format(fmt, args);
		logger.warn("{}", out);
		System.err.println(out); // intentional!!!
	}

	public <T extends Command> T getCommand(String name) {
		@SuppressWarnings("unchecked")
		T cmd = (T) commandMap.get(name);

		return cmd;
	}

}
