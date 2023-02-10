package org.kostd.bpms.workers.maillist

import org.camunda.community.rest.EnableCamundaRestClient
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableCamundaRestClient
class PrepareMailWorkerApplication

fun main(args: Array<String>) {
    runApplication<PrepareMailWorkerApplication>(*args)
}
