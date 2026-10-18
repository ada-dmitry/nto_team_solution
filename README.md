# Командный backend НТО: решение «Заявки на пропуск»

Самостоятельное приложение API «Заявки на пропуск». Полное описание задания, требования и разбалловка — в [`TASK.md`](TASK.md). Kotlin 2.2.21, Spring Boot 3.5.7,
JDK 17, Gradle 9.7.1, PostgreSQL 17.10. Зависимости Spring/Jackson/JPA/Liquibase
зафиксированы BOM Spring Boot. Gradle wrapper (`./gradlew`) входит в заготовку: установите JDK 17,
Gradle ставить отдельно не нужно. Родительский репозиторий и индивидуальная часть не нужны.

## Обычная сборка (без Nix)

Установите JDK 17 и задайте `JAVA_HOME` на каталог JDK, а `PATH` — так, чтобы
`java` запускалась из него. Команды ниже рассчитаны на Bash (Linux/macOS/WSL).
Для первой сборки нужен доступ к services.gradle.org, GitHub, Gradle Plugin Portal
и Maven Central. Системный Gradle и Nix не требуются.

```bash
java -version
./gradlew --version
./gradlew bootJar
```

Исполняемый JAR: `build/libs/*.jar` (без суффикса `-plain`). Сборка `bootJar`
не требует БД и не запускает тесты. Тесты с PostgreSQL описаны ниже.

## Дополнительное окружение Nix

`flake.nix` и `flake.lock` предоставляют JDK 17, клиент PostgreSQL и Podman Compose,
но не подменяют Gradle Wrapper. При первом создании локального Git-репозитория
эти два файла должны быть добавлены в индекс, иначе Nix не увидит flake.

```bash
nix develop
java -version
./gradlew --version
```

Дальнейшие команды можно выполнять в этой оболочке; это необязательный вариант.
Для Docker используется `docker compose`, для Podman — `podman-compose`.

## Запуск

Нужны Docker с плагином Docker Compose и клиентские утилиты PostgreSQL 17
(`psql`, `createdb`, `pg_isready`) на `PATH`. Примеры рассчитаны на Bash
(Linux, macOS или WSL). Для первой сборки нужен доступ к Maven Central,
для образа PostgreSQL — к Docker Hub. Команды выполняются из каталога заготовки.

```bash
docker compose -p nto_team_local -f compose.yml up -d
# Ограниченное ожидание, затем проверка подключения с нужными реквизитами:
for i in $(seq 1 60); do pg_isready -h 127.0.0.1 -p 5435 -U nto -d nto_team && break; sleep 1; done
PGPASSWORD=nto psql -h 127.0.0.1 -p 5435 -U nto -d nto_team -v ON_ERROR_STOP=1 -c 'SELECT 1'
export DB_URL=jdbc:postgresql://127.0.0.1:5435/nto_team DB_USER=nto DB_PASSWORD=nto
export ADMIN_KEY=teaching-admin
./gradlew bootRun
```

Образ — `docker.io/library/postgres:17.10`, привязка БД только к localhost:5435.
Если порт занят, меняйте `compose.yml` и переменные окружения, не `application.yml`.
`SERVER_PORT` задаёт порт HTTP (по умолчанию 8080).
Учебный ключ не является секретом и не предназначен для публичного сервера.
Системное время в логике заменять нельзя: используйте внедрённый Clock.

Liquibase создаёт таблицы `staff` и `pass_request` и учебные данные один раз;
перезапуск не сбрасывает данные. Сотрудники: 1111, 2222, 3333, 4444; заявки 1–4
разных авторов во всех четырёх статусах. Не привязывайте реализацию к этим ID/кодам.
В решении реализованы вход, чтение, создание, отмена и решения охраны.

```bash
curl http://localhost:8080/api/1111/auth
curl http://localhost:8080/api/1111/requests
curl -X PATCH http://localhost:8080/api/admin/requests/1 \
  -H 'X-Admin-Key: teaching-admin' -H 'Content-Type: application/json' \
  -d '{"status":"APPROVED"}'
```

## Устройство решения

Контроллеры передают запросы сервисам; сервисы используют JPA-репозитории.
`Staff` и `PassRequest` соответствуют схеме Liquibase, ID генерируются PostgreSQL.
Схему создаёт только Liquibase; Hibernate использует `ddl-auto=validate`.
Валидация нормализует строки, строго разбирает даты и собирает ошибки всех полей.
Создание и переходы выполняются в реальных транзакциях.

`RequestAccessAdvice` проверяет доступ и наличие заявки до преобразования JSON,
чтобы выполнить порядок из ТЗ §6.3 даже при некорректном или пустом теле.
Проверка ключа охраны остаётся в готовом interceptor; сервисы повторно проверяют
доступ/существование внутри своих транзакций. Между запросами данные не кешируются.

**Защищены** `build.gradle.kts` (включая зависимости), `settings.gradle.kts`,
`gradle.properties`, весь `src/main/resources/`, а в `src/main/kotlin/ru/nto/team/`:
`Application.kt`, `ClockConfiguration.kt`, `RequestBodies.kt`, `JsonConfiguration.kt`,
`MalformedBodyAdvice.kt`, `AdminKeyInterceptor.kt`, `WebConfiguration.kt`.
При оценке защищённые файлы восстанавливаются. Они не изменены в этом решении.
Собственные тесты находятся в `src/test`, закрытых тестов организаторов здесь нет.

## Контракт

Краткая сводка; полное описание — в [`TASK.md`](TASK.md).

JSON UTF-8, даты строго `yyyy-MM-dd'T'HH:mm:ss` без смещения, зона Europe/Moscow.
Текущее время — из Clock, серверные отметки сохраняются до секунды.
Профиль: `{"name":"ФИО сотрудника"}`.
Элемент списка: `id: Long`, `fullName`, `validFrom`, `validTo`, `status`.
Детали (также ответы создания/отмены/охраны): `id`, `fullName`, `phone`,
`organization`, `purpose`, `validFrom`, `validTo`, `status`, `comment`, `createdAt`,
`updatedAt`; nullable `organization` и `comment` присутствуют как null.

| Метод | Успех |
|---|---|
| GET `/api/{code}/auth` | 200, профиль |
| GET `/api/{code}/requests[?status=...]` | 200, массив |
| GET `/api/{code}/requests/{id}` | 200, детали |
| POST `/api/{code}/requests` | 201, детали |
| POST `/api/{code}/requests/{id}/cancel` | 200, детали |
| PATCH `/api/admin/requests/{id}` | 200, детали |

Код: ровно четыре `[A-Za-z0-9]`, регистр значим, пробелы не удаляются.
Неверный формат → `400 {"error":"Invalid code format"}`, неизвестный код →
`401 {"error":"Unauthorized"}` на всех пользовательских методах.
Чужая или отсутствующая заявка при чтении/отмене → `404 {"error":"Request not found"}`.
Служебный метод: правильный `X-Admin-Key`, иначе готовый обработчик возвращает
`403 {"error":"Forbidden"}`. Отсутствующая заявка у охраны → тот же 404.

Список — только собственные заявки; сортировка `validFrom ASC, id ASC`, пустой — `[]`.
Фильтр принимает только `PENDING`, `APPROVED`, `REJECTED`, `CANCELLED`;
пустое, неизвестное значение, пробелы или другой регистр → `400 {"error":"Invalid status"}`.

Создание получает строки `fullName`, `phone`, `organization`, `purpose`, `validFrom`,
`validTo`. Автор — из кода; статус PENDING, comment null, createdAt=updatedAt=now.
Перед проверкой и сохранением уберите крайние пробелы у всех строк:

- fullName: обязательно, 5–150 символов;
- phone: обязательно, `+7` и ровно десять ASCII-цифр, внутри пробелы/разделители запрещены;
- organization: необязательно, до 150 символов; пропуск/null/пустое → null;
- purpose: обязательно, 5–200 символов;
- validFrom: обязательная корректная дата в точном формате, ≥ now;
- validTo: обязательная корректная дата, > validFrom и в тот же календарный день.

Пропущенные/null/неразбираемые поля — `400 {"errors":{"field":"непустой текст",...}}`.
Верните все доступные ошибки сразу; сравнивайте только успешно разобранные даты.
Тексты полевых ошибок свободные, имена полей фиксированы.
Некорректный JSON или нестроковое значение поля → готовый `400 {"error":"Invalid request body"}`.

Дубликат: тот же автор и нормализованный телефон, существующий статус
PENDING/APPROVED и `newFrom < existingTo && existingFrom < newTo`.
Соприкосновение границ разрешено; другие авторы/телефоны/REJECTED/CANCELLED не мешают.
Дубликат → `409 {"error":"Duplicate request"}`.

Отмена автором: только PENDING → CANCELLED. Охрана: тело `{"status":"APPROVED"}`
или `{"status":"REJECTED","comment":"Причина"}`. Только PENDING → APPROVED/REJECTED.
Статус и комментарий нормализуются. Для REJECTED comment обязателен, 1–500 символов;
для APPROVED строковый comment игнорируется и сохраняется null.
Недопустимый целевой статус — ошибка поля status, плохой комментарий — поля comment.
Запрещённый переход, в том числе повтор того же решения/отмены →
`409 {"error":"Invalid status transition"}`.

Порядок: доступ → наличие/владение → тело → переход; создание:
доступ → валидация → дубликат. Доступ и наличие заявки проверяются и до разбора JSON.
Запись/смена статуса транзакционны: изменения обязаны реально commit в PostgreSQL.
Ошибка не меняет БД; успех сохраняет createdAt и берёт updatedAt из Clock.
Если часы не сдвинулись, updatedAt может остаться тем же. Ничего не кешируйте:
проверки меняют БД напрямую. Автоматически создавать visitor/pass не надо.
Конкурентные и нагрузочные сценарии не оцениваются.

## Тесты

**Внимание:** тесты сбрасывают данные. Создайте отдельную одноразовую БД, не используйте
рабочую БД или БД с ручными данными. Запускайте проверки последовательно.

Вариант с отдельным одноразовым контейнером PostgreSQL 17.10, без учебного тома
(сначала убедитесь, что порт 5435 свободен):

```bash
podman run -d --name nto_team_solution_test \
  -p 127.0.0.1:5435:5432 \
  -e POSTGRES_DB=nto_team_solution_test -e POSTGRES_USER=nto -e POSTGRES_PASSWORD=nto \
  docker.io/library/postgres:17.10
for i in $(seq 1 60); do pg_isready -h 127.0.0.1 -p 5435 -U nto -d nto_team_solution_test && break; sleep 1; done
PGPASSWORD=nto psql -h 127.0.0.1 -p 5435 -U nto -d nto_team_solution_test -v ON_ERROR_STOP=1 -c 'SELECT 1'
export DB_URL=jdbc:postgresql://127.0.0.1:5435/nto_team_solution_test
export DB_USER=nto DB_PASSWORD=nto
./gradlew clean test bootJar --no-build-cache
```

С Docker замените `podman` на `docker`. Не запускайте этот контейнер одновременно
с учебной БД на том же порту. Если отдельный сервер PostgreSQL уже запущен, создайте
на нём отдельную БД командой `PGPASSWORD=nto createdb -h 127.0.0.1 -p 5435 -U nto nto_team_solution_test`.
Собственные интеграционные тесты отказываются очищать БД, имя которой не начинается
с `nto_team_solution_test`. Это дополнительный предохранитель, а не замена отдельной БД.
Для другого порта меняйте `DB_URL` и аргументы клиентских команд.

Тесты используют управляемый `Clock`, последовательно подготавливают данные через JDBC
и проверяют фиксацию изменений вне откатываемой тестовой транзакции. Набор покрывает
модель, чтение/доступ, валидацию/создание, переходы и сквозной JSON-контракт.
Пример выборочного запуска: `./gradlew test --tests 'ru.nto.team.ContractIntegrationTest' --rerun-tasks`.
Отчёт: `build/reports/tests/test/index.html`. Прохождение собственных тестов не гарантирует балл.

Остановка тестового контейнера без удаления: `podman stop nto_team_solution_test`.

## Сдача (вручную, после отдельного разрешения)

1. Создайте публичный GitHub-репозиторий и разместите проект в его корне
   (`settings.gradle.kts` должен быть в корне). Перед публикацией проверьте отсутствие секретов.
2. Сделайте коммит и push в ветку по умолчанию.
3. Отправьте ссылку на репозиторий в задание Moodle. Pull Request не нужен.

Проверяется последний коммит ветки по умолчанию на момент отправки ссылки;
после новых изменений ссылку нужно отправить повторно. Сроки определяет регламент.
Публичный сервер разворачивать не требуется. Коммиты, push, создание репозитория
и отправка в Moodle не выполняются автоматически.

Подтверждённые замечания к заготовке: [`TASK_FEEDBACK.md`](TASK_FEEDBACK.md).

Остановка локальной БД без потери данных: `docker compose -p nto_team_local -f compose.yml down`.
Не добавляйте `-v`: это удалит учебный том.
