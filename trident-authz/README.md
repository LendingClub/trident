# trident-authz

This is a Docker AuthZ plugin that implements docker's V2 plugin architecture.

## Options

| Option | Description | Example|
|--------|-------------|--------|
|TRIDENT_URL | Base URL for accessing trident | https://trident.example.com|
|FAIL_OPEN | Should authorization be granted for communication error | true / false |

## Building

The plugin can be built by running ```./gradlew build```.

This will build the java daemon, build a container around it, export the container image as a rootfs, and build the plugin.

## Install

The plugin that you just built can be configured with the following, obviously sustituting your URL:

```shell
docker plugin set trident-authz TRIDENT_URL=https://trident.example.com
```

After the plugin has been configured, you can enable it:

```shell
$ docker plugin enable trident-authz
trident-authz

$ docker plugin ls
ID                  NAME                   DESCRIPTION                       ENABLED
8520816a472d        trident-authz:latest   Authorization plugin for Docker   true
```shell

In order to actually take effect, you need to change the dockerd startup option to include ```--authorization-plugin=trident-authz```.

On Docker for Mac, this can be done by getting access to the Moby console and editing ```/etc/init.d/docker``` and setting DOCKER_OPTS.

From there you will need to restart docker.  On Docker for Mac, this can be done with ```/etc/init.d/docker restart```

NOTE: Clicking restart in the menu bar of Docker for Mac, will reboot the Moby Linux VM, which also resets any changes to /etc/init.d/docker

## Debugging

###  Logs

The logs from the plugin will go to /var/log/docker.log, which is very helpful.

## Interactive Debugging

If you need an interactive shell to the plugin, you need to use ```docker-runc```.  If you are using Docker for Mac, this must be done from within the Moby VM.

First, find the ID of the "container" running the plugin.  It wil not be visible running ```docker ps```.

```shell
docker-runc list
ID                                                                 PID         STATUS      BUNDLE                                                                                       CREATED                          OWNER
8520816a472d2970ed6dc3697d0de01138e0a97bc82febf6beefce480f9f2834   4685        running     /run/docker/libcontainerd/8520816a472d2970ed6dc3697d0de01138e0a97bc82febf6beefce480f9f2834   2017-10-01T20:30:47.239722386Z   root
rngd                                                               1772        running     /containers/rngd                                                                             2017-10-01T20:02:20.084026139Z   root

Then establish an interactive session:
```shell
docker-runc exec -t 8520816a472d2970ed6dc3697d0de01138e0a97bc82febf6beefce480f9f2834 sh
```

Now you can look at the process tree and poke around inside the plugin:
```java
/ # ps -ef
PID   USER     TIME   COMMAND
    1 root       0:00 java -jar /trident-authz/trident-authz-all.jar
    7 root       0:00 socat UNIX-LISTEN:/run/docker/plugins/trident-authz.sock,
   35 root       0:00 sh
   41 root       0:00 ps -ef
```java
