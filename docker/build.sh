#!/bin/bash

# To be executed from project root
docker build -t iudx/aaa-depl:latest -f docker/depl.dockerfile .
docker build -t iudx/aaa-dev:latest -f docker/dev.dockerfile .
