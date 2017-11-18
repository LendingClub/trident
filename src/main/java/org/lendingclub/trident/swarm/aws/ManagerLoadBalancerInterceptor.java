package org.lendingclub.trident.swarm.aws;

import java.util.function.BiConsumer;

import com.amazonaws.services.elasticloadbalancing.model.CreateLoadBalancerRequest;
import com.fasterxml.jackson.databind.JsonNode;

public interface ManagerLoadBalancerInterceptor extends BiConsumer<JsonNode, CreateLoadBalancerRequest> {

}