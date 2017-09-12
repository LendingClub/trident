#!/bin/bash


SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
TRIDENT_SRC_DIR=$SCRIPT_DIR/../../..


JAR_FILE=$(find ${TRIDENT_SRC_DIR}/build/libs -name '*.jar' | head -1 )

cp -v ${JAR_FILE} ${SCRIPT_DIR}/trident/lib/trident.jar || exit 99

cd ${SCRIPT_DIR}

docker info
if [ ! $? -eq 0 ]; then
    echo ERROR: docker operational
    exit 0
fi


docker build . -t trident || exit 99

docker tag trident:latest lendingclub/trident || exit 99

