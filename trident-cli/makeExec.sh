#!/bin/bash


SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
TRIDENT_CLI=${SCRIPT_DIR}/build/libs/trident
rm -f $TRIDENT_CLI
FAT_JAR=${SCRIPT_DIR}/build/libs//trident-cli-all.jar
cat $SCRIPT_DIR/jar-exec.sh >${TRIDENT_CLI} || exit 99

if [ ! -f $FAT_JAR ]; then
    echo "error: fat jar not found: ${FAT_JAR}"
    exit 99
fi

ls -al ${FAT_JAR}
cat $FAT_JAR >>${TRIDENT_CLI} || exit 99

chmod +x $TRIDENT_CLI

# verify that it works
${TRIDENT_CLI} help || exit 98

mkdir -p ../src/main/resources/cli
cp -f ${TRIDENT_CLI} ../src/main/resources/cli/trident || exit 97
chmod +x ../src/main/resources/cli/trident
echo "executable: ${TRIDENT_CLI}"