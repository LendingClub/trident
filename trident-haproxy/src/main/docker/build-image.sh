#!/bin/bash


SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

cd $SCRIPT_DIR

echo "script dir is "+$SCRIPT_DIR

mkdir -p ${SCRIPT_DIR}/trident-haproxy
cp -v ${SCRIPT_DIR}/../../../build/libs/haproxy-1.0.jar $SCRIPT_DIR/trident-haproxy || exit 1

docker build . -t trident-haproxy || exit 2

docker tag trident-haproxy trident-haproxy:latest || exit 4
