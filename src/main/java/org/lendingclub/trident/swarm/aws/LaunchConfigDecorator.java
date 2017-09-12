package org.lendingclub.trident.swarm.aws;

import java.util.function.BiConsumer;

import com.amazonaws.services.autoscaling.model.CreateLaunchConfigurationRequest;
import com.fasterxml.jackson.databind.node.ObjectNode;

public interface LaunchConfigDecorator extends BiConsumer<ObjectNode, CreateLaunchConfigurationRequest>{

}
