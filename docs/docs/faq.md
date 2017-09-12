# FAQ

## Why Trident?

The objective of Trident is to provide a delightful experience for developing, testing, deploying and operating modern microservices in a container environment.  

Trident provides/supports:

* 100% agnostic about runtime environment (same experience OnPrem and in Cloud)
* Immutable infrastructure
* Cloud-native design principles
* Hundreds or thousands of deployable services
* Dozens or 100s of distinct swarm clusters
* Lightweight but rock-solid load balancing platform that requires minimal configuration management
* 100% Open Source


## Why Docker Swarm?

Docker Swarm is drop dead simple to use.  We like simple technology because it tends to be easy to understand and operate at scale.  There is nothing
worse than being a slave to your own technology.

If you have Docker for Mac or Docker for Windows running, you can set up a single-node Swarm cluster with a single command:

```bash
$ docker swarm init
Swarm initialized: current node (un7e89zvlnb4c2l11yvmsvlio) is now a manager.

To add a worker to this swarm, run the following command:

    docker swarm join --token SWMTKN-1-5t5h6cyr9r97tmw3nhyrdqo7gomqujd7xdd7mbpu2b9i0o43yt-154dwrq8kbqal3g1j2fjnaa3g 192.168.20.21:2377

To add a manager to this swarm, run 'docker swarm join-token manager' and follow the instructions.
```

It's that simple!

## What about Kubernetes?

Like Docker Swarm, Kubernetes provides only a piece of the puzzle of managing complex sets of enterprise services at scale.

You still need substantial investment in infrastructure automation to operate at scale.

Trident targets Docker Swarm as the underlying container scheduling engine for now because it is significantly simpler to operate than Kubernetes.  We may consider adding Kubernetes to Trident at some point in the future.

## What about Amazon Elastic Container Service (ECS)?

Amazon Elastic Container Service is a great product.  We use Amazon CodeDeploy extensively at Lending Club, which is very similar to ECS, but designed for non-container workloads.  Both ECS and Code Deploy are delightfully simple products that just work.

If you are running a 100% AWS environment and want to run containers easily, ECS probably makes sense.

But the reality is that most enterprises aren't 100% cloud.  Enterprises have heterogeneous environments that have grown over time organically or through acquisition.  In such environments, we still want all the benefits of modern cloud-native infrastructure.  However, we understand that "just move it to AWS" may not be practical.

The challenge of Amazon products is that its load balancing services (ELB/ALB) do not work outside of Amazon.  This means that if you want to route traffic in and out of our VPCs, you still need to operate your own load balancing platform.  Additionally, since ELB/ALBs cannot run locally, it is not possible to provide a developer experience that provices 100% symmetry between local, dev, test and production environments.

## What about Google Container Engine (GKE)?

See ECS and Kubernetes, above.

## What projects are most similar to Trident?

Trident is similar in some respects to [Istio](https://istio.io).  Like Istio, Trident is designed to orchestrate containers and expose them with Envoy, which Istio does as well.  However, Trident is designed to be simpler than Istio.  
