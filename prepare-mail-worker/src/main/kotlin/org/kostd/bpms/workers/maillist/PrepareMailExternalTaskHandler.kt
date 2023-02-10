package org.kostd.bpms.workers.maillist

import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription
import org.camunda.bpm.client.task.ExternalTask
import org.camunda.bpm.client.task.ExternalTaskHandler
import org.camunda.bpm.client.task.ExternalTaskService
import org.camunda.community.rest.client.api.ProcessInstanceApiClient
import org.camunda.community.rest.client.api.UserApiClient
import org.camunda.community.rest.client.model.ActivityInstanceDto
import org.camunda.community.rest.client.model.UserProfileDto
import org.kostd.bpms.workers.maillist.conf.WorkerProperties
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import javax.inject.Inject

/**
 *	Из переменных процесса получает список пользователей camunda run, кому нужно отправить рассылку.
 *  По rest получает у camunda run почтовые адреса этих пользователей
 *  Из переменных процесса получает также почтовые адреса тех пользователей, которые не зарегистрированы в camunda run.
 *  На основании всего этого получает список адресов, на которые далее sendmail-worker будет отправлять письмо с
 *  предложением заполнить анкету.
 *  #TODO: вроде бы тут же удобно из google docs понимать, кто уже заполнил анкету и не отправлять им письмо еще раз
 *  (это если уже не первая итерация напоминания).

 */
@Component
@ExternalTaskSubscription(topicName = "review-360-prepare-mail")
class PrepareMailExternalTaskHandler(
        // готового remote-сервисы в camunda-rest-client есть не для всего, попробуем обойтись работой с feign-клиентами напрямуюы
        private val processInstanceApiClient: ProcessInstanceApiClient,
        private val userApiClient: UserApiClient
) : ExternalTaskHandler {

    var log: Logger = LoggerFactory.getLogger(PrepareMailExternalTaskHandler::class.java)

    @Inject
    private lateinit var workerProperties: WorkerProperties;

    /**
     * Получить имя(которое видно в cockpit и modeler`е) external-таска в remote-клиенте неожиданно оказалось
     * не так просто. У самого ExternalTask даже нет такого свойства, в отличие от user-task`а. ExternalTaskService
     * тоже не умеет возвращать имя. Приходится получать соответствующий ActivityInstance и брать уже его имя.
     * Сделать это тоже непросто. Во-первых, единственный найденный способ -- получить корневой ActivityInstance
     * экземпляра процесса, а у него спросить child`а с соответствующим id, и уже тогда брать его имя.
     * Во-вторых, RuntimeService в варианте remote не умеет получать корневой ActivityInstance (соотв. метод просто
     * not implemented yet). В общем, через сырой ProcessInstanceApiClient получаем корневой ActivityInstance
     * процесса, перебираем всех его детей в поисках того, который по id соответствует нашему таску, и берем
     * именно его имя. Вот так странно и непонятно. Зато увлекательно.
     */
    private fun _lookupTaskName(externalTask: ExternalTask): String {
        val rootActivityInstance: ActivityInstanceDto = processInstanceApiClient.getActivityInstanceTree(externalTask.processInstanceId).body!!;
        val taskName: String = rootActivityInstance.childActivityInstances.first { externalTask.activityId.equals(it.activityId) }.activityName;
        return taskName;
    }

    private fun _variableExists(externalTask: ExternalTask, variableName: String): Boolean {
        return externalTask.allVariables.keys.contains(variableName);
    }

    override fun execute(externalTask: ExternalTask, externalTaskService: ExternalTaskService) {
        log.debug("Tryin` to execute task name = ${_lookupTaskName(externalTask)} id = ${externalTask.id} " +
                "from process ${externalTask.processDefinitionKey}");
        // часто разработчики бизнес-процесса не слишком аккуратны, могут перепутать имена переменных или
        // даже не сделать переменных вообще. Поэтому, прежде чем работать с переменными процесса, мягенько
        // убедимся в их наличии. Начнем с переменной, куда складывать ошибки, чтобы было куда складывать ошибки  ))
        if (!_variableExists(externalTask, workerProperties.errors)) {
            val errm: String = "variable for errors with name ${workerProperties.errors} not exists, can`t proceed!";
            log.error(errm)
            //  кол-во retries 0, т.к. в данном processInstance это уже не починится.
            externalTaskService.handleFailure(externalTask, errm, errm, 0, 0);
            throw RuntimeException(errm);
        }
        var errors: String = externalTask.getVariable(workerProperties.errors);
        //  если одна из двух оставшихся переменных (список пользователей, список адресов) существует, а второй нет, это
        //  не блокирует нашу работу. Поэтому failure не делаем, а только лишь ругаемся в лог
        val userlistVarExists = _variableExists(externalTask, workerProperties.userlist);
        if (!userlistVarExists) {
            val errm: String = "variable with name ${workerProperties.userlist} not exists!";
            log.error(errm);
            errors += (if (errors.isBlank()) "" else "\n") + errm;
        }
        // #TODO: посмотреть свежим взглядом и убрать копипаст
        val maillistVarExists = _variableExists(externalTask, workerProperties.mailList);
        if (!maillistVarExists) {
            val errm: String = "variable with name ${workerProperties.userlist} not exists!";
            log.error(errm);
            errors += (if (errors.isBlank()) "" else "\n") + errm;
        }
        if ((!userlistVarExists && !maillistVarExists) ||
                (externalTask.getVariable<String>(workerProperties.userlist).isBlank() &&
                        externalTask.getVariable<String>(workerProperties.mailList).isBlank())
        ) {
            val errm: String = "both variables ${workerProperties.userlist} and " +
                    "${workerProperties.mailList} not exists or blank, can`t proceed!";
            externalTaskService.setVariables(externalTask, mapOf(workerProperties.errors to errors));
            // retry тут бесполезен, надо переделать процесс и задеплоить его заново
            externalTaskService.handleFailure(externalTask, errm, errm, 0, 0);
            throw  RuntimeException(errm);
        }

        //  сначала получим список пользователей и достанем их почтовые адреса
        val userList: String = externalTask.getVariable(workerProperties.userlist)!!;
        val userNames: List<String> = userList.split(",");
        val emails: MutableList<String> = mutableListOf();
        userNames.forEach {
            val userProfileDto: UserProfileDto = userApiClient.getUserProfile(it).body!!;
            if (userProfileDto.email.isBlank()) {
                errors += (if (errors.isBlank()) "" else "\n") + "email for user $it is empty!";
            }
            emails.add(userProfileDto.email);
        }
        //  теперь склеим сформированные нами почтовые адреса с теми, которые прям так и были заданы в переменной
        val mailList: String = externalTask.getVariable(workerProperties.mailList);
        emails.addAll(mailList.split(","));
        // запихнем результат и ошибки в те же самые переменные
        //  запихивать надо с устранением дублей, т.к. нас могут вызывать много раз в ходе процесса
        externalTaskService.setVariables(externalTask, mapOf(workerProperties.mailList to emails.distinct()
                .joinToString(","), workerProperties.errors to errors));

        externalTaskService.complete(externalTask, mapOf(workerProperties.mailList to emails.distinct()
                .joinToString(","), workerProperties.errors to errors));
    }
}