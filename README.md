![Trident](https://raw.githubusercontent.com/LendingClub/trident/master/docs/images/trident.png) 

# Trident

You can access it at http://localhost:8080/home



## Tracing Docker REST Calls

The following is very useful for debugging Docker API calls.  It will open a plain text socket on 2376 and route
the requests to the local UNIX domain socket.

```shell
socat -v -ls TCP-LISTEN:2376,reuseaddr,fork UNIX-CLIENT:/var/run/docker.sock
```

You can then set DOCKER_HOST=tcp://localhost:2376 and see the full request-response logging.
