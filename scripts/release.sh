#!/bin/bash -e

script_dir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
base_dir=${script_dir/\/scripts/}

echo "Base dir: $base_dir"
(cd ${base_dir}
    ./gradlew clean test release
)
