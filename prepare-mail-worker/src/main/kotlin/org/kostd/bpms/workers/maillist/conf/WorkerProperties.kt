package org.kostd.bpms.workers.maillist.conf

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@ConfigurationProperties(prefix = "worker.preparemail.variable")
@Configuration
class WorkerProperties(
        /**
         * название переменной процесса, из которой достать список пользователей
         */
        var userlist: String = "USER_LIST_VARIABLE",
        /**
         * переменная процесса, из которой достать (и в которую положить) список почтовых адресов
         */
        var mailList: String = "MAIL_LIST_VARIABLE",
        /**
         * переменная процесса, в которую положить ошибки, возникшие в ходе обработки задач
         */
        var errors: String = "ERRORS_VARIABLE"
)