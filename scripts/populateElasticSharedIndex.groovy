#!/usr/bin/env groovy

@Grab('io.github.http-builder-ng:http-builder-ng-apache:1.0.4')

import groovyx.net.http.ApacheHttpBuilder
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.client.config.RequestConfig
import groovyx.net.http.HttpBuilder
import groovyx.net.http.FromServer
import static groovyx.net.http.ApacheHttpBuilder.configure

private static final String ES_USER_PASS='elastic:SOMEPASSWORD'

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
    Map datapage = getPage(http, since, 1000);
    if ( datapage != null ) {
      println("Got page of ${datapage.content.size()} items... ${datapage.pageable} total=${datapage.totalSize}");
      if ( ( datapage.content.size() == 0 ) || ( shortstop ) ) {
        moreData=false;
      }
      else {
        println("postPage");
        postPage(es_http, datapage, shortstop);
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


private postPage(HttpBuilder http, Map datapage, boolean shortstop) {

  StringWriter sw = new StringWriter();

  datapage.content.each { r ->

    String derived_type_of_first_bib = r.bibs.size() > 0 ? r.bibs[0].derivedType : "";

    if ( ( r.title != null ) && ( r.title.length() > 0 ) ) {
      sw.write("{\"index\":{\"_id\":\"${r.clusterId}\"}}\n".toString());
      sw.write("{\"bibClusterId\": \"${r.clusterId}\",".toString())
      sw.write("\"title\": \"${esSafeValue(r.title)}\",".toString());
      sw.write("\"derivedType\": \"${esSafeValue(derived_type_of_first_bib)}\",".toString());
                // "\"sourceRecordId\": \"${esSafeValue(r.sourceRecordId)}\", "+
                // "\"sourceSystemId\":\"${esSafeValue(r.sourceSystemId)}\", "+
                // "\"sourceSystemCode\":\"KCTOWERS\" "+
      sw.write("\"bibs\": [");
      r.bibs.each { bib ->
        sw.write("{");
        sw.write("\"bibId\": \""+bib.bibId+"\", ");
        sw.write("\"title\": \""+bib.title+"\", ");
        sw.write("\"sourceRecordId\": \""+bib.sourceRecordId+"\", ");
        sw.write("\"sourceSystemId\": \""+bib.sourceSystemId+"\", ");
        sw.write("\"sourceSystemCode\": \""+bib.sourceSystemCode+"\", ");
        sw.write("\"recordStatus\": \""+bib.recordStatus+"\", ");
        sw.write("\"typeOfRecord\": \""+bib.typeOfRecord+"\", ");
        sw.write("\"derivedType\": \""+bib.derivedType+"\"");
        sw.write("}");
      }
      sw.write(" ]");
      sw.write("}\n")
    }
    else {
      // println("NULL title encountered  : ${r?.sourceRecordId} ${r}");
      File f = new File("./bad".toString())
      f << r
    }
  }

  String reqs = sw.toString();

  if ( shortstop == true )
    println(reqs)

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
      // println("Got response : ${body}");
      // result = body.entries[0].link.substring(body.entries[0]?.link.lastIndexOf('/')+1)
    }
    response.failure { FromServer fs, Object body ->
      println("Post Page : Problem body:${body} (${body?.class?.name}) fs:${fs} status:${fs.getStatusCode()}");
    }
  }

}

private String esSafeValue(String v) {
  return v.replaceAll("\"","\\\\\"");
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
