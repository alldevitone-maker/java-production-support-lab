#!/usr/bin/env sh
set -eu
mkdir -p out
javac -d out src/main/java/lab/payments/*.java
exec java -cp out lab.payments.PaymentSupportLab
