#!/bin/bash


if [ -x "$JAVA_HOME/bin/java" ]; then
    JAVA_CMD=${JAVA_HOME}/bin/java
else
    JAVA_CMD=java
fi


# This script gets pre-pended to the fat jar and makes it act like an executable
TRIDENT_TEMP_DIR=$(cd && pwd)/.trident/temp
mkdir -p $TRIDENT_TEMP_DIR

exec ${JAVA_CMD} -Dcli.exe="$0" -Dcli.launch=true -Djava.io.tmpdir=${LC2_TEMP_DIR} -jar $0 "$@"
