# prepare-mail-worker
Существует для учебно-тренировочного процесса review 360. Отвечает за подготовку почтовых адресов коллег, от которых ожидается заполнение
анкеты на ревью данного сотрудника. 
Предполагается, что на стартовой форме процесса руководитель выбирает пользователей. Часть из них зарегистрирована
в Camunda Run, то есть можно указать пользователя, а worker сам достанет почтовый адрес из профиля сотрудника в Camunda. Но многие сотрудники
не регистрируются в Камунде, т.к. для их работы это не требуется. Их почтовые адреса руководитель вводит на стартовой форме процесса явно.

Задача данного worker`а -- получить из переменной процесса список пользователей Сamunda, кому нужно отправить рассылку.
Далее по rest получить у Сamunda Run почтовые адреса этих пользователей. Слепить (с устранением дубликатов) со списком адресов, явно введенных
руководителем и полученных в другой переменной. Итоговый список адресов для почтовой рассылки положить в переменную, откуда их возьмет
почтовый worker (например, [сommunity worker](https://github.com/camunda-community-hub/camunda-platform-7-mail)).


# Настройка
* Имей развернутую Camunda (можно не обязательно в варианте Run, главное чтобы был доступен Rest API).
* Укажи адрес rest endpoint`а camunda в application.properties. 
* Тот же endpoint укажи для external task client`а (он тоже работает по rest)
* Укажи логин и пароль для подключения в application.properties, а потом его base64-encoded вариант еще раз в application.yml. #TODO: сделать единообразно.
* Укажи ожидаемые данным worker`ом имена переменных в application.properties (они должны быть заведены в самом процессе 360-review)
* В Camunda должен быть залит процесс с external task`ом, у которого topic называется review-360-prepare-mail. #TODO: сделать имя топика конфигурируемым
* Процесс должен находиться в этом таске.

# Запуск
Как обычно, ./gradlew bootRun или через IDE


# Проверка
Сразу после запуска worker старается подключиться к Camunda, и если у него это получится, сделать fetchAndLock задач с соотв. топиком. Далее сообщает 
о ходе обработки задач в лог, а также пишет ошибки в соответствующую переменную процесса. При блокирующих ошибках делает failure задаче, причем с 
retry = 0, то есть по такой задаче PE создаст инцидент, повторно эта задача обрабатываться не будет. При неблокирующих -- пишет в лог и в переменную
процесса (в конце своей работы), но пытается отработать до конца. В случае успеха, завершает(complete) задачу. 