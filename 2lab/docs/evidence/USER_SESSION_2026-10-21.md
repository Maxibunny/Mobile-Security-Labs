# Подтверждённые строки из присланного пользователем лога 21.10.2026

**Источник:** текстовый журнал `Вставленный текст(20261021-161152).txt`, присланный пользователем. **Это выдержка**, а не полный сырой вывод `scripts/check.sh all` и не новый запуск на машине сборки документации. Идентифицирующие путь и имя пользователя сведения опущены.

```text
[18:27:25] [INFO] gradle_build: OK (exit code 0)
    BUILD SUCCESSFUL in 1s
[18:28:39] [INFO] install_notes_1: OK (exit code 0)
    Performing Push Install
    Success
[18:28:45] [INFO] install_client_1: OK (exit code 0)
    Performing Push Install
    Success
[18:42:58] [INFO] security-check: OK (exit code 0)
    PASS  content insert отклонён
    PASS  content delete отклонён
    ИТОГО: PASS=18 FAIL=0
[18:42:58] [INFO] scripts/check.sh all завершился с кодом 0. Все проверки скрипта пройдены.
[18:43:21] [INFO] instrumented-tests: OK (exit code 0)
    BUILD SUCCESSFUL in 22s
[18:43:28] [INFO] Сценарий завершён без критических ошибок. Проверяйте статус тестов выше.
```

**Что не следует выводить из этого:** `monkey` завершился с кодом 0, но это не подтверждает, что пользователь визуально увидел нужный экран. Сам `check.sh all` отдельно пишет, что случай «клиент с другой подписью» требует ручного теста. Не считайте этот файл реальным stderr/stdout после внесения новых комментариев; при сдаче по возможности сохраните свежий `docs/evidence/check-all.txt` через `scripts/collect_evidence.sh`.
