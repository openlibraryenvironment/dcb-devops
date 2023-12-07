#!/bin/bash

. ./setTarget.sh

DCB_ROOT_UUID=`uuidgen --sha1 -n @dns --name org.olf.dcb`
AGENCIES_NS_UUID=`uuidgen --sha1 -n $DCB_ROOT_UUID --name Agency`
HOSTLMS_NS_UUID=`uuidgen --sha1 -n $DCB_ROOT_UUID --name HostLms`
LOCATION_NS_UUID=`uuidgen --sha1 -n $DCB_ROOT_UUID --name Location`

TOKEN=`./login`

export IMPORT_REFERENCE=`date +%Y-%m-%dT%H:%M:%S%z`
export LN=0
curl -L "https://docs.google.com/spreadsheets/d/e/2PACX-1vTbJ3CgU6WYT4t5njNZPYHS8xjhjD8mevHVJK2oUWe5Zqwuwm_fbvv58hypPgDjXKlbr9G-8gVJz4zt/pub?gid=412322753&single=true&output=tsv" | while IFS="	" read AGENCY PICKUP DISPLAY PRINT DELIVERY LASTCOL
do
  if [ $LN -eq 0 ]
  then
    echo Skip header
  else
    AGENCY_UUID=`uuidgen --sha1 -n $AGENCIES_NS_UUID --name $AGENCY`
    LOCATION_UUID=`uuidgen --sha1 -n $LOCATION_NS_UUID --name $PICKUP`
    echo
    echo processing: $AGENCY $PICKUP $DISPLAY $PRINT $DELIVERY - \"$AGENCY\"=$AGENCY_UUID \"$PICKUP\"=$LOCATION_UUID
    echo
    echo "Posting without URL"
    curl -X POST "$TARGET/locations" -H "Content-Type: application/json"  -H "Authorization: Bearer $TOKEN" -d "{ 
      \"id\": \"$LOCATION_UUID\",
      \"code\":\"$PICKUP\",
      \"name\":\"$DISPLAY\",
      \"type\":\"PICKUP\",
      \"agency\":\"$AGENCY_UUID\",
      \"isPickup\":true,
      \"printLabel\":\"PRINT\",
      \"deliveryStops\":\"DELIVERY\",
      \"importReference\": \"cli-$IMPORT_REFERENCE\"
    }"
    echo
    echo
  fi
  ((LN=LN+1))
done
