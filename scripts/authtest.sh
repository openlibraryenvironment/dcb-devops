#!/bin/bash


TOKEN=`./login`

TARGET="https://dcb-dev.sph.k-int.com"

echo Test login
echo
curl -X GET $TARGET/hostlmss -H "Content-Type: application/json"  -H "Authorization: Bearer $TOKEN"
