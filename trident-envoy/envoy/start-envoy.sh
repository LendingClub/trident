#!/bin/bash -x

# This script is meant to be invoked by the hot-restarter

ulimit -n 102400

SERVICE_NODE=$(hostname -s)


ENVOY_LOG_LEVEL=${ENVOY_LOG_LEVEL-"info"}

exec /usr/local/bin/envoy \
    --log-level ${ENVOY_LOG_LEVEL} \
    -c /envoy/config/envoy.cfg \
    --service-cluster ${TSD_CLUSTER} \
    --service-node ${TSD_NODE} \
    --service-zone ${TSD_ZONE}

