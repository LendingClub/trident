#!/bin/bash

set -x


SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
TRIDENT_AGENT_SRC_DIR=${SCRIPT_DIR}/../../..


DOCKER=$(which docker)

mkdir -p $SCRIPT_DIR/trident-agent/

cp ${TRIDENT_AGENT_SRC_DIR}/build/libs/trident-agent-all.jar $SCRIPT_DIR/trident-agent/ || exit 99

if [ ! -x "$DOCKER" ]; then
    echo
    exit 99
fi


cd ${SCRIPT_DIR}


docker build . -t trident-agent || exit 99

docker tag trident-agent:latest lendingclub/trident-agent  || exit 99