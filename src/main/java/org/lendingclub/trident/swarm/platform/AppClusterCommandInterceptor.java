package org.lendingclub.trident.swarm.platform;

import java.util.function.Consumer;
import java.util.function.Function;

import org.lendingclub.trident.swarm.platform.AppClusterManager.DeployCommand;
import org.lendingclub.trident.swarm.platform.AppClusterManager.AppClusterCommand;

public interface AppClusterCommandInterceptor extends Consumer<AppClusterCommand>	{
	
}
