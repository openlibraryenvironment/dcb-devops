#!/bin/bash

. ./setTarget.sh

DCB_ROOT_UUID=`uuidgen --sha1 -n @dns --name org.olf.dcb`
AGENCIES_NS_UUID=`uuidgen --sha1 -n $DCB_ROOT_UUID --name Agency`
HOSTLMS_NS_UUID=`uuidgen --sha1 -n $DCB_ROOT_UUID --name HostLms`
LOCATION_NS_UUID=`uuidgen --sha1 -n $DCB_ROOT_UUID --name Location`

TOKEN=`./login`

export IMPORT_REFERENCE=`date +%Y-%m-%dT%H:%M:%S%z`
export LN=0
curl -s -L "https://docs.google.com/spreadsheets/d/e/2PACX-1vTbJ3CgU6WYT4t5njNZPYHS8xjhjD8mevHVJK2oUWe5Zqwuwm_fbvv58hypPgDjXKlbr9G-8gVJz4zt/pub?gid=412322753&single=true&output=tsv" | while IFS="	" read AGENCY PICKUP DISPLAY PRINT DELIVERY LASTCOL
do
  # Skip the header and any rows that start with a #
  if [ $LN -eq 0 ] || [[ $AGENCY == \#* ]]
  then
    # Skip header or comment
    true
  else
    AGENCY_UUID=`uuidgen --sha1 -n $AGENCIES_NS_UUID --name $AGENCY`
    LOCATION_UUID=`uuidgen --sha1 -n $LOCATION_NS_UUID --name $AGENCY:$PICKUP`
    # echo processing: $AGENCY $PICKUP $DISPLAY $PRINT $DELIVERY - \"$AGENCY\"=$AGENCY_UUID \"$PICKUP\"=$LOCATION_UUID :
    RESULT=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$TARGET/locations" -H "Content-Type: application/json"  -H "Authorization: Bearer $TOKEN" -d "{ 
      \"id\": \"$LOCATION_UUID\",
      \"code\":\"$PICKUP\",
      \"name\":\"$DISPLAY\",
      \"type\":\"PICKUP\",
      \"agency\":\"$AGENCY_UUID\",
      \"isPickup\":true,
      \"printLabel\":\"$PRINT\",
      \"deliveryStops\":\"$DELIVERY\",
      \"importReference\": \"cli-$IMPORT_REFERENCE\"
    }")

    if [ $RESULT -eq 200 ]
    then
      echo "OK: $PICKUP/$DISPLAY @ $AGENCY"
    else
      echo "FAIL: $PICKUP/$DISPLAY @ $AGENCY"
    fi
  fi
  ((LN=LN+1))
done
