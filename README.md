# task-manager-cicd

Учебный Spring Boot REST API для управления задачами с поддержкой приоритетов.

Проект предназначен для лабораторных работ по CI/CD:

- **Лабораторная работа № 1** — Jenkins Declarative Pipeline
- **Лабораторная работа № 2** — GitHub Actions Workflow

## Технологии

| Компонент              | Версия / Технология      |
|------------------------|--------------------------|
| Java                   | 21                       |
| Spring Boot            | 3.3.5                    |
| Maven                  | 3.9+                     |
| JUnit 5 + Mockito      | через spring-boot-starter-test |
| Maven Surefire         | модульные тесты (*Test)  |
| Maven Failsafe         | интеграционные тесты (*IT) |
| JaCoCo                 | покрытие кода            |
| CI                     | Jenkins + GitHub Actions |

## Возможности API

- `POST /api/tasks` — создание задачи (priority опционален, по умолчанию NORMAL)
- `GET /api/tasks` — список всех задач
- `GET /api/tasks?priority=HIGH` — фильтрация по приоритету
- `GET /api/tasks/{id}` — получение задачи
- `PATCH /api/tasks/{id}/status` — смена статуса
- `PATCH /api/tasks/{id}/priority` — смена приоритета
- `DELETE /api/tasks/{id}` — удаление

Приоритеты: `LOW`, `NORMAL`, `HIGH`  
Статусы: `NEW`, `IN_PROGRESS`, `DONE`

## Локальный запуск

```bash
mvn -B -ntp clean verify
mvn spring-boot:run
```

## CI

- **Jenkinsfile** — Declarative Pipeline для Windows Agent `windows-java21-agent`
- **.github/workflows/ci.yml** — GitHub Actions на `windows-latest`

### Артефакты GitHub Actions

- `test-reports-windows` — XML/HTML отчёты Surefire, Failsafe, JaCoCo
- `task-manager-jar` — собранный JAR (только при SUCCESS)
