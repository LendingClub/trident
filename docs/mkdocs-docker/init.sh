#!/bin/bash

env

cd /mkdocs

if [ $1 = "serve" ]; then
mkdocs serve --dev-addr=0.0.0.0:8000
else
    mkdocs build 
fi