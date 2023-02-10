package org.kostd.bpms.workers.maillist

import org.camunda.bpm.engine.ProcessEngineConfiguration
import org.camunda.community.rest.EnableCamundaRestClient
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cloud.commons.httpclient.HttpClientConfiguration
import org.springframework.cloud.openfeign.FeignAutoConfiguration

@SpringBootApplication
@EnableCamundaRestClient
class PrepareMailWorkerApplication

fun main(args: Array<String>) {
	runApplication<PrepareMailWorkerApplication>(*args)
}
