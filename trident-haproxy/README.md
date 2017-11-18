scramjet
---------

scramjet is a supervisory daemon for haproxy which does the following:

* allows HAProxy to be deployed like any other Lending Club application
* allows configuration to be dynamically generated via MacGyver, GitHub, etcd, etc.


Future Goals:

* Integration of VRRP/keepalived




## Build and Run

To build and run scramjet:

```$ ./gradlew clean run -DTemplateDataDirectory="/path/to/template/data/folder"```
