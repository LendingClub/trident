#!/bin/bash


SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

cd $SCRIPT_DIR

mkdir -p ${SCRIPT_DIR}/trident-authz
cp -v ${SCRIPT_DIR}/../../../build/libs/trident-authz-all.jar $SCRIPT_DIR/trident-authz || exit 99

docker build . -t trident-authz-temp || exit 2

rm -rf ${SCRIPT_DIR}/rootfs
mkdir -p ${SCRIPT_DIR}/rootfs

CONTAINER_ID=$(docker create trident-authz-temp)

docker export ${CONTAINER_ID} | tar -x -C rootfs

echo "Disabling trident-authz plugin..."
docker plugin disable trident-authz

echo "Removing trident-authz plugin..."
docker plugin rm trident-authz

echo "Creating new trident-authz plugin..."
docker plugin create trident-authz .

echo
docker plugin ls

echo
echo

cat <<EOF
Enable plugin with:

    docker plugin enable trident-authz

NOTE: trident-authz must be enabled with the --authroization-plugin option to dockerd.  The
      correct option would be:  --authorization-plugin trident-authz
      
      This can be set in the moby vm (Docker for Mac) by editing /etc/init.d/docker and inserting this into DOCKER_OPTS.
EOF


