package org.lendingclub.trident.loadbalancer;

import java.util.function.Consumer;

import org.lendingclub.trident.loadbalancer.LoadBalancerManager.LoadBalancerCommand;

public interface LoadBalancerCommandInterceptor extends Consumer<LoadBalancerCommand> {

}
