package org.lendingclub.trident.swarm.platform;

import java.util.function.BiConsumer;

import org.lendingclub.trident.extension.InterceptorGroup;
import org.lendingclub.trident.swarm.platform.AppClusterManager.AppClusterCommand;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * This class allows platform interceptors, which intercept high-level requests to register per-operation interceptors that will customize the actual request sent to swarm.
 * @author rschoening
 *
 */
public interface SwarmRequestInterceptor extends BiConsumer<AppClusterCommand, JsonNode> {


}
