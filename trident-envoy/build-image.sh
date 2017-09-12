#!/bin/bash

docker build . -t trident-envoy || exit 1

docker tag trident-envoy:latest lendingclub/trident-envoy || exit 1
