package org.lendingclub.trident.cli.command;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.lendingclub.okrest3.OkRestClient;
import org.lendingclub.okrest3.OkRestTarget;
import org.lendingclub.trident.cli.CLIException;
import org.lendingclub.trident.cli.CommandLineLogger;
import org.lendingclub.trident.cli.ConfigManager;
import org.lendingclub.trident.cli.Main;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.CaseFormat;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;

public abstract class Command {

	ObjectMapper mapper = new ObjectMapper();
	String commandName = null;
	JCommander jcommander;
	ConfigManager configManager;
	OkRestClient restClient;
	
	List<String> argList;
	
	@Parameter(names = { "--debug" }, description = "enable debug-level logging")
	boolean debugLoggingEnabled;

	@Parameter(names = { "--info" }, description = "enable info-level logging")
	boolean infoLoggingEnabled;


	public Command() {

	}
	public void init(List<String> args, JCommander jcommander, ConfigManager configManager) {
	
		this.argList = args;
		this.jcommander = jcommander;
		this.configManager = configManager;
	}
	public void rewriteArgList(List<String> argList) {

	}


	public String getCommandName() {
		if (commandName == null) {
			List<String> list = Splitter.on(".").splitToList(getClass().getName());
			String val = list.get(list.size() - 1);
			val = val.replace("Command", "");
			
			return CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN, val);
			
		} else {
			return commandName;
		}
	}

	public void doParse() {
		getJCommander().parse(getArgList().toArray(new String[0]));
	}
	public abstract void doRun() ;

	public String getCommandDescription() {
		return "description";
	}

	public boolean isHiddenCommand() {
		return false;
	}

	public boolean isQuiet() {
		return true;
	}

	public ConfigManager getConfigManager() {
		return configManager;
	}
	public List<String> getArgList() {
		return argList;
	}
	public JCommander getJCommander() {
		return jcommander;
	}
	public OkRestTarget getTridentRestTarget() {
		if (restClient==null) {
			OkRestClient.Builder builder = new OkRestClient.Builder();
			
			 restClient = new OkRestClient.Builder().withOkHttpClientConfig(cfg -> {
		            cfg.readTimeout(20, TimeUnit.SECONDS);
/*
		            if (isLc2DebugEnabled()) {
		                HttpLoggingInterceptor hli = new HttpLoggingInterceptor(new RedactingLogger());
		                hli.setLevel(Level.BODY);
		                cfg.addInterceptor(hli);
		            }*/
		        }).build();
		}
		String baseUrl = getConfigManager().getConfig().path("url").asText();
		if (Strings.isNullOrEmpty(baseUrl)) {
			throw new CLIException("url not set");
		}
		return restClient.url(baseUrl);
		
	}
	public final void run() {
		doRun();
	}
	
	public static void progress(String fmt, Object... args) {
		Main.progress(fmt, args);
	}

	public static void stdout(String fmt, Object... args) {
		Main.stdout(fmt, args);
	}

	public void stderr(String fmt, Object... args) {
		Main.stderr(fmt, args);
	}

}
