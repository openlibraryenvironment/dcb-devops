#!/bin/bash

curl -u "elastic:LrIrNpc9360J7xc1EgI5987q" -X DELETE "https://reshare-hub-es.libsdev.k-int.com/mobius-si"

curl -u "elastic:LrIrNpc9360J7xc1EgI5987q" -X PUT "https://reshare-hub-es.libsdev.k-int.com/mobius-si" \
      -H 'Content-Type: application/json' -d'{
  "mappings": {
    "properties": {
      "title": {
        "type":  "text",
        "index_options": "freqs",
        "fields": {
          "keyword": {
            "type": "keyword",
            "ignore_above": 256   
          }
        }
      }
    }
  }
}
'
