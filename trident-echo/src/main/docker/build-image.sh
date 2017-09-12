#!/bin/bash


SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

cd $SCRIPT_DIR

mkdir -p ${SCRIPT_DIR}/trident-echo
cp -v ${SCRIPT_DIR}/../../../build/libs/echo-all.jar $SCRIPT_DIR/trident-echo || exit 99

docker build . -t trident-echo || exit 2

docker tag trident-echo:latest lendingclub/trident-echo || exit 3
