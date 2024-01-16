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
  println("Already running, exiting - if this is in error please manually adjust cfg.json");
  System.exit(0);
}
else {
  cfg[target].running=true;
  cfg[target].lastStarted=(new Date()).toString();
  updateConfig(cfg);
}

HttpBuilder folio = configure {
  // request.uri = 'https://reshare-dcb-uat-es.sph.k-int.com'
  request.uri = cfg[target]."base-url"
}

try {
  process(folio, cfg[target]);
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

private void process(HttpBuilder http, Map cfg) {
	Map context = [
		pageno:0
  ]
  Map r = getPage(http, cfg, context);
  println("Got response: ${r}");
}


private Map getPage(HttpBuilder http, Map cfg, Map context) {
	Map get_page_result = [
		startTime: System.currentTimeMillis()
	];

  println("request OAI page");
  http.get {
		request.contentType = 'text/xml'
		request.accept = 'text/xml'
    request.uri.path = "/oai".toString()
    request.uri.query = [
        'verb':'ListRecords',
        'metadataPrefix':'marc21_withholdings',
				'apikey':cfg.apikey
    ]
    // if ( since != null ) {
    //   request.uri.query.since = since;
    //   println("${base}/clusters?size:${pagesize}&since=${since}");
    // }
    // else {
    //   println("${base}/clusters?size:${pagesize}");
    // }

    // request.accept='application/xml'
    // request.headers['Authorization'] = 'Bearer '+token

    response.success { FromServer fs, Object body ->
      println("Got success response : ${fs.getContentType()}");
			get_page_result.endTime=System.currentTimeMillis();
			context.pageno++;
			get_page_result.elapsed=get_page_result.endTime-get_page_result.startTime;
			get_page_result.numRecords=0;
    }
    response.failure { FromServer fs, Object body ->
      println("Get Page : Problem body:${body} fs:${fs} status:${fs.getStatusCode()}");
    }
  }

	println("Return");
  return get_page_result;
}
