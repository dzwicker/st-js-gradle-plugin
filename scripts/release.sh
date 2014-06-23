#!/bin/bash -e

script_dir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
base_dir=${script_dir/\/scripts/}

echo "Base dir: $base_dir"
#export JAVA_HOME="$(/usr/libexec/java_home -v 1.7*)"
(cd ${base_dir}
    ./gradlew clean check assemble uploadArchives
)
