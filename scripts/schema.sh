#!/bin/bash

ES_PW=`kubectl get secret reshare-dcb-es-es-elastic-user -n dcb-dev -o go-template='{{.data.elastic | base64decode}}'`
# ES_HOME="https://reshare-dcb-uat-es.sph.k-int.com/mobius-si"
ES_HOME="https://reshare-dcb-dev-es.sph.k-int.com"
ES_CREDS="elastic:$ES_PW"

echo Drop old
curl -u "$ES_CREDS" -X DELETE "$ES_HOME/mobius-si"



echo Create new
curl -u "$ES_CREDS" -X PUT "$ES_HOME/mobius-si" \
      -H 'Content-Type: application/json' -d'{
  "settings": {
    "analysis": {
      "analyzer": {
        "default": {
          "tokenizer": "whitespace",
          "filter": [ "dcb_stopwords_filter" ]
        }
      },
      "filter": {
        "dcb_stopwords_filter": {
          "type": "stop",
          "ignore_case": true,
          "stopwords": [ "a", "an", "the", "in", "of", "on", "are" , "be", "if", "into", "which" ]
        }
      }
    }
  },
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
      "primaryAuthor":{
        "type":  "text",
        "fields": {
          "keyword": {
            "type": "keyword",
            "ignore_above": 256   
          }
        }
      },
      "yearOfPublication":{
        "type": "long"
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
            "type": "text",
            "fields": {
              "keyword": {
                "type": "keyword",
                "ignore_above": 256   
              }
            }
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
