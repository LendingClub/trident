
* Attach secondary volume (LC)
* Provisioning API should be able receive TLS CA information
* Factory to be able to create (authenticated) docker client to swarm manager and individual nodes.
* Factory to be able to obtain an AWS client for a given cluster/ASG.
* AWS terminated node detection.  For each node in the cluster (docker node ls) that is not responding, go to AWS and verify that it still exists.  If it is terminated, remove it from the swarm.
* Better ASG name (to include swarm cluster name) so that it is identifiable in the AWS console
* Improve logging of curl-bash initialization dance
* Consider making client side of curl-bash loop stateless.  That is, simply drop into a loop of "do you have anything for me to do?"
* Timeout/failure of curl-bash loop should be self-termination (shutdown -h now) after some time limit.
* Initialize AWS Config on start (LC)




