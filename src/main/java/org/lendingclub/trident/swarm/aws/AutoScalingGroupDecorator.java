package org.lendingclub.trident.swarm.aws;

import java.util.function.BiConsumer;

import com.amazonaws.services.autoscaling.model.CreateAutoScalingGroupRequest;
import com.fasterxml.jackson.databind.JsonNode;

public interface AutoScalingGroupDecorator extends BiConsumer<JsonNode, CreateAutoScalingGroupRequest>{

}
