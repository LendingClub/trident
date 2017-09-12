package org.lendingclub.trident.swarm.aws;

import java.util.function.BiConsumer;

import com.amazonaws.services.elasticloadbalancing.model.CreateLoadBalancerRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public interface ManagerLoadBalancerDecorator extends BiConsumer<ObjectNode, CreateLoadBalancerRequest> {

}
