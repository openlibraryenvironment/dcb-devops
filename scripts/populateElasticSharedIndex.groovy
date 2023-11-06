#!/usr/bin/env groovy

@Grab('io.github.http-builder-ng:http-builder-ng-apache:1.0.4')

import groovyx.net.http.ApacheHttpBuilder
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.client.config.RequestConfig
import groovyx.net.http.HttpBuilder
import groovyx.net.http.FromServer
import static groovyx.net.http.ApacheHttpBuilder.configure
import groovy.json.JsonOutput
import groovy.json.JsonBuilder;
import groovy.json.JsonSlurper;

def target='default'

// Read or set up config
Map cfg = initialise();

if ( cfg[target].running==true ) {
  println("Already running, exiting");
  System.exit(0);
}
else {
  cfg[target].running=true;
  cfg[target].lastStarted=(new Date()).toString();
  updateConfig(cfg);
}

HttpBuilder keycloak = configure {
  request.uri = cfg[target].KEYCLOAK_BASE
}

HttpBuilder dcb_http = configure {
  request.uri = cfg[target].DCB_BASE
}

HttpBuilder es_http = configure {
  // request.uri = 'https://reshare-dcb-uat-es.sph.k-int.com'
  request.uri = cfg[target].ES_BASE
}

try {
  process(dcb_http, es_http, cfg, target);
}
catch ( Exception e ) {
  cfg[target].lastError = e.message
}
finally {
  cfg[target].running=false;
  cfg[target].lastFinished=(new Date()).toString();
  updateConfig(cfg);
}

System.exit(0)


private Map initialise() {
  String CONFIG_FILE='./cfg.json'
  Map cfg = [:]
  JsonSlurper j = new JsonSlurper();
  File config_file = new File(CONFIG_FILE);
  if ( !config_file.exists() ) {
    //
  }
  else {
    cfg = j.parse(config_file);
  }

  return cfg;
}

private void updateConfig(Map cfg) {
  String CONFIG_FILE='./cfg.json'
  File cfg_file = new File(CONFIG_FILE);
  File backup = new File(CONFIG_FILE+'.bak');
  cfg_file.renameTo(backup)
  String result_json = new JsonBuilder( cfg ).toPrettyString()
  cfg_file << result_json
}

private void process(HttpBuilder http, HttpBuilder es_http, Map config, String target, boolean shortstop=false) {
  int page_counter=0;
  // String since=null;
  // String since="2023-07-10T23:20:38.677389Z"
  String since=config[target].CURSOR
  println("Cursor: ${since}");

  // 
  boolean moreData = true;
  while ( moreData ) {
    boolean gotdata=false
    int retries=0;
    while ( moreData && !gotdata && retries++ < 5 ) {
      println("Get page[${page_counter++}] retries=[${retries}] of data with since=${since}");
      long PAGESIZE=10
      try {
        Map datapage = getPage(config[target].DCB_BASE, http, since,PAGESIZE);
        if ( datapage != null ) {
          println("Got page of ${datapage.content.size()} items... ${datapage.pageable} total num records=${datapage.totalSize}");
          if ( ( datapage.content.size() == 0 ) || ( shortstop ) ) {
            println("No content to post... terminate");
            moreData=false;
          }
          else {
            println("postPage ${config}");
            postPage(es_http, datapage, shortstop, page_counter, config[target]);
	    println("count");
            datapage.content.each { r ->
	    	since = r.dateUpdated;
                gotdata=true
    	    }
            println("new date updated: ${since}");
      	    Thread.sleep(1000);
          }
        }
      }
      catch(Exception e) {
        e.printStackTrace()
        System.exit(1);
      }
      config[target].CURSOR=since
      updateConfig(config);
    }
  }
}


private Map getPage(String base, HttpBuilder http, String since, long pagesize) {

  Map result = null;


  def http_res = http.get {
    request.uri.path = "/clusters".toString()
    request.uri.query = [
        'page':0,
        'size':pagesize
    ]
    if ( since != null ) {
      request.uri.query.since = since;
      println("${base}/clusters?size:${pagesize}&since=${since}");
    }
    else {
      println("${base}/clusters?size:${pagesize}");
    }
    request.accept='application/json'
    request.contentType='application/json'
    // request.headers['Authorization'] = 'Bearer '+token
  
    response.success { FromServer fs, Object body ->
      // println("Got response : ${body}");
      result = body;
      result.content.each { e ->
         // Just iterate over everything to make sure we have fetched it all
      }

      // result = body.entries[0].link.substring(body.entries[0]?.link.lastIndexOf('/')+1)
    }
    response.failure { FromServer fs, Object body ->
      println("Get Page : Problem body:${body} fs:${fs} status:${fs.getStatusCode()}");
    }
  }

  println("Got page since ${since}");
  return result;
}

private String getIdentifier(Map record, String type) {
  String result = null;
  Map m = record?.identifiers?.find { it.namespace == type }
  if ( m != null ) {
    // println("Found value ${m.value} for ${type}");
    result = m.value;
  }
  else {
  }

  return result;
}

private void extractPrimaryAuthor(Map record, StringWriter sw) {
  if ( record != null ) {
    Map primaryAuthor= ( record.agents?.find { it.subtype?.equals('name-personal') } )
    if ( primaryAuthor != null ) {
        sw.write("\"primaryAuthor\": \"${primaryAuthor.label}\",".toString());
    }
  }
}

private void extractYearOfPublication(Map record, StringWriter sw) {
  if ( record != null ) {
    String dateOfPublication = record['dateOfPublication'];
    if ( dateOfPublication != null ) {
      def match = ( dateOfPublication =~ /(?:(?:19|20)[0-9]{2})/ )
      if ( match.size() == 1 ) {
        sw.write("\"yearOfPublication\": ${match[0]},".toString());
      }
    }
  }
}

private void checkFor(String field, Map record, StringWriter sw) {
  
  if ( ( record != null ) && 
       ( record[field] != null ) ) {
    // println("${field} is present : ${record[field]}");
    sw.write("\"${field}\": \"${esSafeValue(record[field])}\",".toString());
  }
  else {
  }
}

private postPage(HttpBuilder http, Map datapage, boolean shortstop, int page_counter, Map config) {

  StringWriter sw = new StringWriter();

  int ctr=0;
  int delete_ctr=0;
  int bad_ctr=0;

  datapage?.content?.each { r ->

    // println("Record ${ctr}");
    try {
      if ( ( r.deleted == true ) || 
           ( r.bibs == null ) ||
           ( r.bibs.size() == 0 ) ) {
        delete_ctr++;
        deleteCluster(r.clusterId, http, datapage, shortstop, page_counter, config);
      }
      else if ( ( r != null ) &&
                ( r.title != null ) && 
                ( r.title.length() > 0 ) &&
                ( r.bibs?.size() > 0 ) &&
		( r.bibs?.size() < 1000 ) 
              ) {

        List bib_members = [];
  
	if ( r.selectedBib == null ) {
          println("Record ${ctr} has no selected bib ${r}. Defaulting to first record");
	  r.selectedBib = r.bibs[0];
        }
	else {
          if ( r.bibs?.size() > 100 ) {
	    println("bib has a worryingly high number of attached bibs... ${r.clusterId}=${r.bibs?.size()} ${r.title}");
	  }
	}

        // Add in the IDs of all bib records in this cluster so we can access the cluster via any of it's member record IDs
        // bib_members.add(r.selectedBib.bibId);
        r.bibs.each { memberbib -> 
          // println("Looking for ${memberbib} in ${bib_members}");
          if ( bib_members.find{ it.bibId == memberbib.bibId } == null ) {
            if ( memberbib.bibId == r.selectedBib.bibId )
              memberbib['primary']="true"
            bib_members.add(memberbib);
          }
        }
  
        String isbn = getIdentifier(r.selectedBib.canonicalMetadata, 'ISBN');
        String issn = getIdentifier(r.selectedBib.canonicalMetadata, 'ISSN');
        // println(r.title);
        // sw.write("{\"index\":{\"_id\":\"${r.clusterId}\"}}\n".toString());
        sw.write("{\"index\":{\"_id\":\"${r.selectedBib.bibId}\"}}\n".toString());
        sw.write("{\"bibClusterId\": \"${r.clusterId}\",".toString())
        sw.write("\"title\": \"${esSafeValue(r.title)}\",".toString());
  
        boolean first = true;
        sw.write("\"members\": [")
        bib_members.each { member ->
          if ( first ) 
            first=false
          else 
            sw.write(", ");
  
          sw.write("{\"bibId\":\"${member.bibId}\",\"title\":\"${member.title}\",\"sourceRecordId\":\"${member.sourceRecordId}\",\"sourceSystem\":\"${member.sourceSystem}\"}");
        }
        sw.write("],");
  
        checkFor('placeOfPublication',r.selectedBib.canonicalMetadata,sw);
        checkFor('publisher',r.selectedBib.canonicalMetadata,sw);
        checkFor('dateOfPublication',r.selectedBib.canonicalMetadata,sw);
        checkFor('derivedType',r.selectedBib.canonicalMetadata,sw);
  
        extractYearOfPublication(r.selectedBib.canonicalMetadata, sw);
        extractPrimaryAuthor(r.selectedBib.canonicalMetadata, sw);
  
        if ( isbn )
          sw.write("\"isbn\": \"${isbn}\",".toString());
  
        if ( issn )
          sw.write("\"issn\": \"${issn}\",".toString());
        sw.write("\"metadata\":")
        sw.write(JsonOutput.toJson(r.selectedBib.canonicalMetadata));
        sw.write("}\n")
  
        ctr++;
      }
      else {
        bad_ctr++;
        println("BAD title encountered  : ${r?.sourceRecordId} ${r}");
        File f = new File("./bad".toString())
        f << r
      }
  
    }
    catch ( Exception e ) {
      e.printStackTrace();
      System.exit(1);
    }
  }


  println("POST PHASE");

  String reqs = sw.toString();

  if ( false ) {
    println("reqs....");
    File pagefile = new File("./pages/${page_counter}.json")
    if ( pagefile.exists() )
      pagefile.delete();
    pagefile << reqs
  }

  boolean posted=false;
  int retry=0;

  if ( ctr > 0 ) {
    println("Posting  : ${ctr} records/${delete_ctr} deletes/${bad_ctr} bad");
    while ( !posted && retry++ < 5 ) {
      println("Posting[${retry}] ./pages/${page_counter}.json to elasticsearch payload size=${reqs.length()}");
      try {
        def http_res = http.put {
          request.uri.path = "/mobius-si/_bulk".toString()
          request.uri.query = [
            refresh:true,
            pretty:true
          ]
          request.accept='application/json'
          request.contentType='application/json'
          request.headers.'Authorization' = "Basic "+("${config.ES_UN}:${config.ES_PW}".toString().bytes.encodeBase64().toString())
    
          request.body=reqs;
          // request.headers['Authorization'] = 'Bearer '+token
     
          response.success { FromServer fs, Object body ->
            println("Page of ${ctr} posted OK");
            posted=true
          }
          response.failure { FromServer fs, Object body ->
            println("Post Page : Problem body:${body} (${body?.class?.name}) fs:${fs} status:${fs.getStatusCode()}");
          }
        }
      }
      catch ( Exception e ) {
        println("Problem: ${e.message}");
        System.exit(1);
      }

      if ( !posted ) {
        Thread.sleep(1000)
      }
    }
  }
  else {
    println("No records in page (deletes=${delete_ctr},bad=${bad_ctr})");
  }
}

private void deleteCluster(String cluster_id, HttpBuilder http, Map datapage, boolean shortstop, int page_counter, Map config) {
  println("delete ${cluster_id}");
  try {
    def http_res = http.post {
      request.uri.path = "/mobius-si/_delete_by_query".toString()
      // request.uri.query = [:]
      request.accept='application/json'
      request.contentType='application/json'
      request.headers.'Authorization' = "Basic "+("${config.ES_UN}:${config.ES_PW}".toString().bytes.encodeBase64().toString())

      request.body=[
        "query": [
          "match": [
            "bibClusterId.keyword" : cluster_id
          ]
        ]
      ]
      response.success { FromServer fs, Object body ->
        // println("delete of ${cluster_id} OK");
      }
      response.failure { FromServer fs, Object body ->
        println("Delete cluster : Problem body:${body} (${body?.class?.name}) fs:${fs} status:${fs.getStatusCode()}");
      }
    }
  }
  catch ( Exception e ) {
    e.printStackTrace();
    System.exit(1);
  }
}

private String esSafeValue(String v) {
  if ( v != null )
    return v.replaceAll("\"","\\\\\"");

  return null;
}

private Map getHostLmss(HttpBuilder http, String token) {
  Map result = null;
  def http_res = http.get {
    request.uri.path = "/hostlmss"

    request.accept='application/json'
    request.contentType='application/json'
    request.headers.'Authorization' = "Bearer "+token

    response.success { FromServer fs, Object body ->
      println("Got response : ${body}");
      // result = body.entries[0].link.substring(body.entries[0]?.link.lastIndexOf('/')+1)
    }
    response.failure { FromServer fs, Object body ->
      println("Get LMS : Problem body: ${body.class.name} ${body} (${new String(body)}) fs:${fs} status:${fs.getStatusCode()}");
    }
    return result;
  }
}

private String getLogin(HttpBuilder http, String user, String pass) {
  String result = null;
  def http_res = http.post {
    request.uri.path = "/realms/reshare-hub/protocol/openid-connect/token"

    request.contentType = 'application/x-www-form-urlencoded'

    request.body = [
      "client_id":"dcb",
      "client_secret":"RncJxvqxtOpeboB6dYFegzF47q8gyK2x",
      "username":user,
      "password":pass,
      "grant_type":"password"
    ]

    response.success { FromServer fs, Object body ->
      // println("Got response : ${body}");
      result = body.access_token;
      // result = body.entries[0].link.substring(body.entries[0]?.link.lastIndexOf('/')+1)
    }
    response.failure { FromServer fs, Object body ->
      println("Get Login Problem ${body.class.name} ${body} ${fs} ${fs.getStatusCode()}");
    }
    return result;
  }

}
