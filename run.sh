#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
sh scripts/build.sh
exec java -jar backend/target/payops-lab-0.2.0.jar
