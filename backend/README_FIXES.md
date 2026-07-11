# Backend fixes

## Main changes

1. Gemini calls have a configurable total timeout (`GEMINI_TIMEOUT_MS`, default 20000 ms).
2. `DictionaryService.analyze()` is no longer transactional. Short database operations are isolated in `DictionaryPersistenceService`.
3. Results returned by Gemini for `word` mode are saved automatically to PostgreSQL.
4. Concurrent requests for the same normalized word share one in-flight Gemini call within the current backend instance.
5. PostgreSQL indexes are created by `src/main/resources/schema.sql` after Hibernate updates the schema.
6. Search is executed and ranked in PostgreSQL; the backend no longer loads the entire table with `findAll()`.
7. `pg_trgm` and a GIN index support contains/fuzzy search.
8. Hibernate batch fetching avoids N+1 collection queries for meanings, examples and related words.
9. Controller request bodies and query parameters are validated.
10. API errors return appropriate HTTP statuses through `GlobalExceptionHandler`.
11. CORS is limited to configured origins.
12. Dictionary detail/existence and grammar history APIs were added.
13. Grammar analysis results are stored in `grammar_checks`, including the complete JSON result.

## Environment variables

```properties
DB_URL=jdbc:postgresql://localhost:5432/ai_dictionary
DB_USERNAME=postgres
DB_PASSWORD=your_password
GEMINI_API_KEY=your_key
CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:5173
GEMINI_TIMEOUT_MS=20000
SPRING_SQL_INIT_MODE=always
JPA_DDL_AUTO=update
```

By default, `schema.sql` runs at startup. For an existing large production table, set `SPRING_SQL_INIT_MODE=never` and run `database/create_indexes_concurrently.sql` outside a transaction so index creation does not take the normal blocking path.

The PostgreSQL account must be allowed to execute:

```sql
CREATE EXTENSION IF NOT EXISTS pg_trgm;
```

## API endpoints

```text
GET  /api/dictionary/health
POST /api/dictionary/analyze
POST /api/dictionary/save
GET  /api/dictionary/search?keyword=truong&sourceLanguage=vi&targetLanguage=zh
GET  /api/dictionary/entries/{id}
GET  /api/dictionary/exists?word=truong&sourceLanguage=vi&targetLanguage=zh
GET  /api/dictionary/grammar-history?limit=20
GET  /api/dictionary/grammar-history/{id}
```

## HTTP statuses

```text
200 OK                  Successful lookup/update
201 Created             New dictionary entry saved
400 Bad Request         Invalid input
404 Not Found           Detail ID does not exist
409 Conflict            Database constraint conflict
429 Too Many Requests   Gemini quota/rate limit after fallback
502 Bad Gateway         Invalid/error response from Gemini
503 Service Unavailable Gemini API key is missing
504 Gateway Timeout     Gemini exceeded configured timeout
```

## Optional unique index for multiple backend instances

The current in-flight request map deduplicates Gemini calls inside one backend instance. When deploying multiple instances, first resolve duplicate lookup keys and then execute:

```text
database/optional_unique_lookup_index.sql
```

For full cross-instance request coalescing, use a distributed lock such as Redis. The optional unique index prevents duplicate rows, but it cannot prevent two different instances from beginning the Gemini call at exactly the same time.

## Database search

The search repository first retrieves at most 20 ranked IDs in PostgreSQL, then loads those entities. When source/target languages are supplied, the query filters them before ranking so the composite indexes can be used effectively. Exact and prefix matches use B-tree indexes; contains/fuzzy matching uses `pg_trgm` GIN.

Use this query to inspect the execution plan:

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT de.id
FROM dictionary_entries de
WHERE de.normalized_search_keyword = 'truong hoc';
```
