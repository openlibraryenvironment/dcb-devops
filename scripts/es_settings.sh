#!/bin/bash

ES_PW=`kubectl get secret reshare-dcb-es-es-elastic-user -n dcb-uat -o go-template='{{.data.elastic | base64decode}}'`
# ES_HOME="https://reshare-dcb-uat-es.sph.k-int.com/mobius-si"
ES_HOME="https://dcb-uat-es.sph.k-int.com"
ES_CREDS="elastic:$ES_PW"


echo Drop old
curl -X PUT -H 'Content-Type: application/json' -u "$ES_CREDS" "$ES_HOME/mobius-si/_settings" -d '{
  "index":{
    "refresh_interval": "-1"
  }
}'
