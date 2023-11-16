#!/bin/bash

# TARGET="https://dcb-dev.sph.k-int.com"
# TARGET="https://dcb-uat.sph.k-int.com"

DCB_ROOT_UUID=`uuidgen --sha1 -n @dns --name org.olf.dcb`
AGENCIES_NS_UUID=`uuidgen --sha1 -n $DCB_ROOT_UUID --name Agency`
HOSTLMS_NS_UUID=`uuidgen --sha1 -n $DCB_ROOT_UUID --name HostLms`
LOCATION_NS_UUID=`uuidgen --sha1 -n $DCB_ROOT_UUID --name Location`

TOKEN=`./login`

export LN=0
curl -L "https://docs.google.com/spreadsheets/d/e/2PACX-1vTbJ3CgU6WYT4t5njNZPYHS8xjhjD8mevHVJK2oUWe5Zqwuwm_fbvv58hypPgDjXKlbr9G-8gVJz4zt/pub?gid=727698722&single=true&output=tsv" | while IFS="	" read CODE NAME LMS LAT LON PROFILE URL EXTRA
do
  if [ $LN -eq 0 ]
  then
    echo Skip header
  else
    AGENCY_UUID=`uuidgen --sha1 -n $AGENCIES_NS_UUID --name $CODE`
    echo
    echo $TARGET code=$CODE name=$NAME lms=$LMS LAT=$LAT LON=$LON PROFILE=$PROFILE URL=$URL UUID=$AGENCY_UUID
    echo
    if [ "$URL" = "a" ]
    then 
      echo "Posting without URL"
      curl -X POST "$TARGET/agencies" -H "Content-Type: application/json"  -H "Authorization: Bearer $TOKEN" -d "{ 
        \"id\":\"$AGENCY_UUID\", 
        \"code\":\"$CODE\",
        \"name\":\"$NAME\", 
        \"hostLMSCode\": \"$LMS\",
        \"authProfile\": \"$PROFILE\"
      }"
    else
      echo "Posting with URL"
      curl -X POST "$TARGET/agencies" -H "Content-Type: application/json"  -H "Authorization: Bearer $TOKEN" -d "{ 
        \"id\":\"$AGENCY_UUID\", 
        \"code\":\"$CODE\",
        \"name\":\"$NAME\", 
        \"hostLMSCode\": \"$LMS\",
        \"authProfile\": \"$PROFILE\",
        \"idpUrl\": \"$URL\"
      }"
    fi
    echo
    echo


  fi
  ((LN=LN+1))
done
