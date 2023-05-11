#!/usr/bin/env groovy

@Grab('io.github.http-builder-ng:http-builder-ng-apache:1.0.4')

import groovyx.net.http.ApacheHttpBuilder
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.client.config.RequestConfig
import groovyx.net.http.HttpBuilder
import groovyx.net.http.FromServer
import static groovyx.net.http.ApacheHttpBuilder.configure
import groovy.json.JsonOutput

private static final String ES_USER_PASS='elastic:SOMEPASSWOR'

HttpBuilder keycloak = configure {
  request.uri = 'https://YOUR_KEYCLOAK_DOMAIN_NAME'
}

HttpBuilder dcb_http = configure {
  request.uri = 'https://YOUR_DCB_DOMAIN_NAME'
}

HttpBuilder es_http = configure {
  request.uri = 'https://YOUR_ELASTICSEARCH_DOMAIN_NAME'
}

process(dcb_http, es_http);

private void process(HttpBuilder http, HttpBuilder es_http, boolean shortstop=false) {
  int page_counter=0;
  String since=null;
  boolean moreData = true;
  while ( moreData ) {
    println("Get page[${page_counter++}] of data with since=${since}");
    Map datapage = getPage(http, since, 250);
    if ( datapage != null ) {
      println("Got page of ${datapage.content.size()} items... ${datapage.pageable} total=${datapage.totalSize}");
      if ( ( datapage.content.size() == 0 ) || ( shortstop ) ) {
        moreData=false;
      }
      else {
        println("postPage");
        postPage(es_http, datapage, shortstop, page_counter);
        datapage.content.each { r ->
		since = r.dateUpdated;
	}
      }
    }
    else {
      println("datapage null: ${page}");
      System.exit(0)
    }
  }
}


private Map getPage(HttpBuilder http, String since, int pagesize) {

  Map result = null;

  def http_res = http.get {
    request.uri.path = "/clusters".toString()
    request.uri.query = [
        'page':0,
        'size':pagesize
    ]
    if ( since != null ) {
      request.uri.query.since = since;
    }
    request.accept='application/json'
    request.contentType='application/json'
    // request.headers['Authorization'] = 'Bearer '+token
  
    response.success { FromServer fs, Object body ->
      // println("Got response : ${body}");
      result = body;
      // result = body.entries[0].link.substring(body.entries[0]?.link.lastIndexOf('/')+1)
    }
    response.failure { FromServer fs, Object body ->
      println("Get Page : Problem body:${body} fs:${fs} status:${fs.getStatusCode()}");
    }
  }
}

private String getIdentifier(Map record, String type) {
  String result = null;
  Map m = record.identifiers?.find { it.namespace == type }
  if ( m != null ) {
    println("Found value ${m.value} for ${type}");
    result = m.value;
  }
  else {
  }

  return result;
}

private void checkFor(String field, Map record, StringWriter sw) {
  if ( record[field] != null ) {
    println("${field} is present : ${record[field]}");
    sw.write("\"${field}\": \"${esSafeValue(record[field])}\",".toString());
  }
  else {
  }
}

private postPage(HttpBuilder http, Map datapage, boolean shortstop, int page_counter) {

  StringWriter sw = new StringWriter();

  datapage.content.each { r ->

    if ( ( r.title != null ) && ( r.title.length() > 0 ) ) {
      String isbn = getIdentifier(r.selectedBib.canonicalMetadata, 'ISBN');
      String issn = getIdentifier(r.selectedBib.canonicalMetadata, 'ISSN');
      println(r.title);
      sw.write("{\"index\":{\"_id\":\"${r.clusterId}\"}}\n".toString());
      sw.write("{\"bibClusterId\": \"${r.clusterId}\",".toString())
      sw.write("\"title\": \"${esSafeValue(r.title)}\",".toString());

      checkFor('placeOfPublication',r.selectedBib.canonicalMetadata,sw);
      checkFor('publisher',r.selectedBib.canonicalMetadata,sw);
      checkFor('dateOfPublication',r.selectedBib.canonicalMetadata,sw);
      checkFor('derivedType',r.selectedBib.canonicalMetadata,sw);

      if ( isbn )
        sw.write("\"isbn\": \"${isbn}\",".toString());

      if ( issn )
        sw.write("\"issn\": \"${issn}\",".toString());

                // "\"sourceRecordId\": \"${esSafeValue(r.sourceRecordId)}\", "+
                // "\"sourceSystemId\":\"${esSafeValue(r.sourceSystemId)}\", "+
                // "\"sourceSystemCode\":\"KCTOWERS\" "+

      sw.write("\"metadata\":")
      sw.write(JsonOutput.toJson(r.selectedBib.canonicalMetadata));
      sw.write("}\n")
    }
    else {
      // println("NULL title encountered  : ${r?.sourceRecordId} ${r}");
      File f = new File("./bad".toString())
      f << r
    }
  }

  String reqs = sw.toString();

  if ( true ) {
    println("reqs....");
    File pagefile = new File("./pages/${page_counter}.json")
    pagefile << reqs
  }

  def http_res = http.put {
    request.uri.path = "/mobius-si/_bulk".toString()
    request.uri.query = [
      refresh:true,
      pretty:true
    ]
    request.accept='application/json'
    request.contentType='application/json'
    request.headers.'Authorization' = "Basic "+(ES_USER_PASS.bytes.encodeBase64().toString())

    request.body=reqs;
    // request.headers['Authorization'] = 'Bearer '+token
 
    response.success { FromServer fs, Object body ->
      println("Page posted OK");
      // println("Got response : ${body}");
      // result = body.entries[0].link.substring(body.entries[0]?.link.lastIndexOf('/')+1)
    }
    response.failure { FromServer fs, Object body ->
      println("Post Page : Problem body:${body} (${body?.class?.name}) fs:${fs} status:${fs.getStatusCode()}");
    }
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
      "client_secret":"CLIENT_SECRET",
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
