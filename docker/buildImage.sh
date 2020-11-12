#!/bin/bash -e

set -euo pipefail

cd $(dirname $0)

IMAGE_NAME=jespertiberg/tibbeftp
JAR_FILE=../java/build/TibbeFTP.jar

if [ ! -e "${JAR_FILE}" ];then
	echo "Please build TibbeFTP first!"
	exit 1
fi

docker rmi "${IMAGE_NAME}" || test 0

cp "${JAR_FILE}" files/
docker build -t ${IMAGE_NAME} .
rm -f files/TibbeFTP.jar

echo ""
if [ "${1:-}" == "push" ];then
	echo "Pushing ${IMAGE_NAME} to repository"
	docker push ${IMAGE_NAME}
else
	echo "${IMAGE_NAME} built locally (not pushed to repository)"
fi
