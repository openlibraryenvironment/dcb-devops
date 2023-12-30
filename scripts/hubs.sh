#!/bin/bash

. ./setTarget.sh

DCB_ROOT_UUID=`uuidgen --sha1 -n @dns --name org.olf.dcb`
AGENCIES_NS_UUID=`uuidgen --sha1 -n $DCB_ROOT_UUID --name Agency`
HOSTLMS_NS_UUID=`uuidgen --sha1 -n $DCB_ROOT_UUID --name HostLms`
LOCATION_NS_UUID=`uuidgen --sha1 -n $DCB_ROOT_UUID --name Location`

TOKEN=`./login`

# Editable sheet: https://docs.google.com/spreadsheets/d/1P8f5GYSzfhG84n2f1RuDom6gmjgaW7a3grW_K2DKqY0/edit#gid=412322753

export IMPORT_REFERENCE=`date +%Y-%m-%dT%H:%M:%S%z`
export LN=0
echo "NO/SO	NO/SO	null	null	END
HUB-SE	SE	null	null	END
HUB-EA	EA	null	null	END
HUB-NW	NW	null	null	END
HUB-SW	SW	null	null	END
HUB-SW-123	SW-123	null	null	END
HUB-AR-SW	AR-SW	null	null	END
HUB-DAV	DAV	null	null	END
HUB-IA-SE	IA-SE	null	null	END
HUB-IA-NW	IA-NW	null	null	END" | while IFS="	" read HUBCODE DISPLAY LAT LON LASTCOL
do
  if [ $LN -eq 0 ]
  then
    echo Skip header
  else
    LOCATION_UUID=`uuidgen --sha1 -n $LOCATION_NS_UUID --name $HUBCODE`
    echo
    echo processing: $HUBCODE $DISPLAY lat=$LAT lon=$LON - HUB_UUID=$LOCATION_UUID
    echo
    curl -X POST "$TARGET/locations" -H "Content-Type: application/json"  -H "Authorization: Bearer $TOKEN" -d "{ 
      \"id\": \"$LOCATION_UUID\",
      \"code\":\"$HUBCODE\",
      \"name\":\"$DISPLAY\",
      \"type\":\"HUB\",
      \"isPickup\":false,
      \"latitude\": $LAT,
      \"longitude\": $LON,
      \"importReference\": \"cli-$IMPORT_REFERENCE\"
    }"
    echo
    echo
  fi
  ((LN=LN+1))
done
