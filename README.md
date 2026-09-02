# screenplay-to-shotlist

### 🎬 [Живое демо — как выглядит результат](https://olegg000.github.io/screenplay-to-shotlist/)


> **EN (summary).** A Kotlin + Spring Boot service that turns a film script
> (PDF or DOCX) into a director's shot list. It parses the document, splits it
> into scenes by standard headers (ИНТ./НАТ./INT./EXT.), token-batches the
> scenes to fit the model's context window, asks an LLM (via Spring AI +
> OpenAI) to break each scene into shots, and streams back a ready-to-open CSV
> (UTF-8 with BOM, so Excel reads Cyrillic correctly). Built solo at the
> **MTS True Tech Champ** hackathon (3rd place).

Сервис на **Kotlin + Spring Boot + Spring AI**, который превращает сценарий
фильма (PDF или DOCX) в режиссёрский покадровый сценарий (shot list) в формате
CSV.

Проект сделан соло на хакатоне **MTS True Tech Champ** (3-е место).

## Что делает

На вход — файл сценария. На выходе — CSV-таблица режиссёрского сценария, где
каждая строка это отдельный кадр: номер сцены и кадра, локация, хронометраж,
план съёмки, содержание кадра с репликами, звук, реквизит, техника.

## Пайплайн

```
PDF / DOCX
   │
   ▼
[1] DocumentParserService   разбор файла в текст (Spring AI: PDF reader / Apache Tika)
   │
   ▼
[2] SplitterService         разбивка текста на сцены по заголовкам ИНТ./НАТ./INT./EXT.
   │
   ▼
[3] DocumentAiService       токен-батчинг сцен под лимит контекста,
   │                        промпт «режиссёр-постановщик» → LLM → JSON-массив кадров
   ▼
[4] CsvExportService        сборка CSV (UTF-8 + BOM для корректной кириллицы в Excel)
   │
   ▼
CSV (режиссёрский сценарий)
```

Токен-батчинг ([3]) оценивает «стоимость» каждой сцены (вход + ожидаемый JSON
на выходе), набивает батчи под безопасный лимит, а слишком крупные сцены дробит
на части. Между батчами выдерживается пауза, чтобы не упереться в rate limit
OpenAI.

## Демо-страница

В `docs/` лежит статическая витрина сервиса: разбор короткометражки на 14 кадров,
структура и колонки — ровно те, что уходят в CSV, с рабочей выгрузкой файла.
Публикуется на GitHub Pages из папки `docs/`, бекенд для неё не нужен.

Локально: `python3 -m http.server -d docs 8000` и открыть `http://localhost:8000`.

## Пример

Вход — `examples/sample-screenplay.txt`:

```
ИНТ. КУХНЯ В ПРОСТОКВАШИНО - УТРО

Тесная деревенская кухня. На столе кипит самовар. ШАРИК листает журнал
о фоторужьях. Входит МАТРОСКИН с ведром молока.

МАТРОСКИН (сердито)
Опять ты за свое! Мы же договорились: сначала дрова, потом журналы.
...
```

Выход (фрагмент) — `examples/sample-shotlist.csv`:

| № (общ.) | № Сцены | № Кадра | Локация | ХРН | План и способ съемки | Содержание кадра | Звук | Реквизит |
|---|---|---|---|---|---|---|---|---|
| 1 | 1 | 1 | ИНТ. КУХНЯ... - УТРО | 4 сек. | Общ. пл. статика | Тесная кухня, кипит самовар | Бульканье самовара | Самовар |
| 3 | 1 | 3 |  | 5 сек. | Ср. пл. | Входит Матроскин. МАТРОСКИН (сердито): Опять ты за свое!... | Скрип двери | Ведро с молоком |

> Пример CSV иллюстративный: конкретная разбивка на кадры зависит от ответа LLM.

## Стек

- Kotlin 1.9, JDK 21
- Spring Boot 3.5 (Spring Web)
- Spring AI 1.0 — OpenAI chat model, PDF document reader, Tika document reader
- springdoc-openapi (Swagger UI)
- Gradle (Kotlin DSL), wrapper в комплекте

## Быстрый старт

```bash
OPENAI_API_KEY=sk-... docker compose up --build
```

Приложение поднимется на `http://localhost:8080`, Swagger UI —
`http://localhost:8080/swagger-ui.html`. Ключ OpenAI в репозитории не хранится:
он читается из переменной окружения (образец — `.env.example`). Без ключа сервис
всё равно стартует, но обращение к модели вернёт ошибку авторизации.

Занят порт — `HOST_PORT=8081 OPENAI_API_KEY=sk-... docker compose up --build`.

### Без Docker

Gradle сам подтянет JDK 21, если в системе стоит другая версия.

```bash
export OPENAI_API_KEY=sk-...
./gradlew bootRun
```

## Эндпоинты

| Метод | Путь | Что делает |
| --- | --- | --- |
| `POST` | `/all-to-csv` | Принимает `multipart/form-data` с полем `file` (PDF, DOCX, текст) и возвращает готовый CSV как вложение |
| `GET` | `/swagger-ui.html` | Swagger UI (springdoc) |
| `GET` | `/v3/api-docs` | OpenAPI-схема в JSON |

Отправить сценарий и получить CSV:

```bash
curl -X POST http://localhost:8080/all-to-csv \
  -F "file=@examples/sample-screenplay.txt" \
  -o shotlist.csv
```

## Структура

```
src/main/kotlin/app/screenplay/
├── ScreenplayApplication.kt        точка входа Spring Boot
├── controller/MainController.kt    POST /all-to-csv
├── dto/ShotData.kt                 модели кадра и разобранной сцены
└── service/
    ├── DocumentParserService.kt    PDF/DOCX → текст
    ├── SplitterService.kt          текст → сцены
    ├── DocumentAiService.kt        батчинг + вызовы LLM → кадры
    └── CsvExportService.kt         кадры → CSV
examples/                           пример входного сценария и выходного CSV
Dockerfile                          многоступенчатая сборка: gradle:jdk21 → temurin:21-jre
docker-compose.yml                  запуск одной командой
```

## Об авторе

**Ковалик Олег Владиславович** — разработчик.

- Чемпионат «Профессионалы» 2025: 1 место (Самара) по мобильной разработке,
  3 место (Россия), 1 место в командном зачёте; 2 место (Самара) по блокчейну
- Волга-IT'2025: 3 место (Flutter / ОС Аврора)
- MTS True Tech Champ: 3 место (этот проект)
- Финалист РуКод (МФТИ)
- 1С:Профессионал 8.3
- Фриланс-контракты Solidity / FunC / Tact (под NDA)
