#!/usr/bin/env bash

SCRIPT_PATH=$( cd "$(dirname "$0")" ; pwd -P )
APP=${1}
PROFILE=${2}
PORT=${3}
PROJECT=

if [[ -z ${PROFILE} ]]; then
  PROFILE="default"
fi

if [[ ${APP} == "account" ]]; then
  PROJECT="account-service"
fi

if [[ -z ${PORT} ]]; then
  cd "${SCRIPT_PATH}/../.." && ./gradlew :${PROJECT}:bootRun -Pargs="--spring.profiles.active=${PROFILE}"
else
  cd "${SCRIPT_PATH}/../.." && ./gradlew :${PROJECT}:bootRun -Pargs="--spring.profiles.active=${PROFILE} --server.port=$PORT"
fi
