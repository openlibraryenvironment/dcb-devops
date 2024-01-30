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

// def target='default'
// def target='cs00000int_0004'
def target='cs000000013'

println("Profiling ${target}");

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
  println("Problem ${e}");
  e.printStackTrace()
  cfg[target].lastError = e.message
}
finally {
  cfg[target].running=false;
  cfg[target].lastFinished=(new Date()).toString();
  updateConfig(cfg);
}

println("Exiting");
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
		pageno:0,
    totalElapsed:0,
		identifiers: [:]
  ]

  Map r = null;
	do { 
		println("${new Date()} fetch page ${context.pageno}");
		r = getPage(http, cfg, context, r?.resumption, context.pageno);
		def rolling_average = context.totalElapsed / context.pageno;
		println("Got response[${context.pageno}]: ${r.numRecords} in ${r.elapsed}ms - rolling average:${rolling_average}");
		println("DATA,${context.pageno},${r.numRecords},${r.elapsed},${rolling_average},${context.highest_datestamp},${r.last_identifier_on_page}");
		byte[] decoded_resumption = null;

		if ( r.resumption ) {
			decoded_resumption = java.util.Base64.getDecoder().decode(r.resumption.toString().getBytes());
			List<String> components = Arrays.asList((new String(decoded_resumption)).split('&'));
			// println("Decoded resumption: ${components}");
			String expiration = components.find { it.startsWith('expir')};
			String offset = components.find { it.startsWith('offset')};
			println("Expiration: ${expiration}");
			println("Offset: ${offset}");
		}

		if ( context.pageno % 100 == 0 ) {
			println("Sleeping 5");
			Thread.sleep(300000);
		}
		else
			Thread.sleep(1000);

	} while ( r.resumption?:'' != '' )

}


private Map getPage(HttpBuilder http, Map cfg, Map context, String resumption, Integer pageno) {
	Map get_page_result = [
		startTime: System.currentTimeMillis()
	];

  println("request OAI page ${new Date()}");
  http.get {
		request.contentType = 'text/xml'
		request.accept = 'text/xml'
    request.uri.path = "/oai".toString()

		if ( resumption != null ) {
      request.uri.query = [
        'verb':'ListRecords',
				'apikey':cfg.apikey,
				'resumptionToken':resumption
			]
		}
		else {
      request.uri.query = [
        'verb':'ListRecords',
        'metadataPrefix':'marc21_withholdings',
				'apikey':cfg.apikey
      ]
		}

    response.success { FromServer fs, Object body ->
			get_page_result.endTime=System.currentTimeMillis();
			context.pageno++;
			get_page_result.elapsed=get_page_result.endTime-get_page_result.startTime;
			context.totalElapsed += get_page_result.elapsed
			get_page_result.numRecords=body.ListRecords.record.size();
			get_page_result.resumption=body.ListRecords.resumptionToken.text();

			String highest_datestamp = null;
			body.ListRecords.record.each { r ->
				String this_datestamp = r.header.datestamp.toString();
				if ( ( highest_datestamp == null ) || ( this_datestamp > highest_datestamp ) ) {
					highest_datestamp = this_datestamp;
				}
				String record_identifier = r.header.identifier;
				if ( context.identifiers[record_identifier] == null )
					context.identifiers[record_identifier] = []
				else
					println("**DUPLICATE ID ${record_identifier} seen before: ${context.identifiers[record_identifier]}");
				context.identifiers[record_identifier].add(pageno)
				
				get_page_result.last_identifier_on_page = r.header.identifier
			}

			if ( ( context.highest_datestamp == null ) || ( highest_datestamp > context.highest_datestamp ) ) {
				context.highest_datestamp = highest_datestamp;
				println("updating highest datestamp to ${context.highest_datestamp}");
			}
    }
    response.failure { FromServer fs, Object body ->
      println("Get Page : Problem body at ${date}:${new String(body)} fs:${fs} status:${fs.getStatusCode()}");
    }
  }

  return get_page_result;
}
