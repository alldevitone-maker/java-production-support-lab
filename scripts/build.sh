#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
npm ci --prefix frontend
npm run build --prefix frontend
mkdir -p backend/src/main/resources/static
cp -R frontend/dist/. backend/src/main/resources/static/
mvn -f backend/pom.xml -B verify
