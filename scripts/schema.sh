#!/bin/bash

ES_PW=`kubectl get secret reshare-dcb-es-es-elastic-user -n dcb-uat -o go-template='{{.data.elastic | base64decode}}'`
# ES_HOME="https://reshare-dcb-uat-es.sph.k-int.com/mobius-si"
ES_HOME="https://reshare-dcb-uat-es.sph.k-int.com"
ES_CREDS="elastic:$ES_PW"

echo Drop old
curl -u "$ES_CREDS" -X DELETE "$ES_HOME/mobius-si"



echo Create new
curl -u "$ES_CREDS" -X PUT "$ES_HOME/mobius-si" \
      -H 'Content-Type: application/json' -d'{
  "mappings": {
    "properties": {
      "title": {
        "type":  "text",
        "fields": {
          "keyword": {
            "type": "keyword",
            "ignore_above": 256   
          }
        }
      },
      "bibClusterId":{
        "type": "text",
        "fields": {
          "keyword": {
            "type": "keyword",
            "ignore_above": 256   
          }
        }

      },
      "members": {
        "properties": {
          "bibId": {
            "type": "keyword"
          },
          "sourceSystem": {
            "type": "keyword"
          }
        }
      },
      "isbn":{
        "type":  "text",
        "fields": {
          "keyword": {
            "type": "keyword",
            "ignore_above": 256   
          }
        }
      },
      "issn":{
        "type":  "text",
        "fields": {
          "keyword": {
            "type": "keyword",
            "ignore_above": 256   
          }
        }
      },
      "metadata": {
        "properties": {
          "agents":{
            "properties":{
              "label":{
                "type":"text",
                "fields": {
                  "keyword": {
                    "type": "keyword",
                    "ignore_above": 256   
                  }
                }
              },
              "subtype":{
                "type":"keyword"
              }
            }
          },
          "subjects":{
            "properties":{
              "label":{
                "type": "text",
                "fields": {
                  "keyword": {
                    "type": "keyword",
                    "ignore_above": 256   
                  }
                }
              },
              "subtype":{
                "type": "keyword"
              }
            }
          },
          "identifiers":{
            "properties":{
              "value":{
                "type": "keyword"
              },
              "namespace":{
                "type": "keyword"
              }
            }
          }
        }
      }
    }
  }
}'

echo done
