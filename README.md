# cassmiggy

Flyway-style schema migrations for **Apache Cassandra**. `cassmiggy` applies `.cql` files to a keyspace (or several), in order and exactly once.


## Requirements

- Java 21+
- Apache Cassandra 3.11 or 4.x
- Apache Cassandra Java Driver 4.x (`org.apache.cassandra:java-driver-core`) - comes transitively with `cassmiggy-core`, so you don't need to declare it yourself (you're already using it to build your `CqlSession`).

# Features

- **History table**: a per-keyspace `schema_migration_history` table records what ran, when, how long it took, and the outcome. (table name is configurable)
- **Lock table**: in a multi-node scenario, only one node should run the process. This table registers who owns the lock, if anyone. Since this is Cassandra there is no `SELECT ... FOR UPDATE` (a pessimistic lock); the closest thing is lightweight transactions: https://docs.datastax.com/en/cql-oss/3.3/cql/cql_using/useInsertLWT.html
- **Checksum validation**: calculates a checksum for each applied file, and verifies that a previously applied file has not been modified.
- **CQL parsing**: files are split into statements with an ANTLR4 CQL grammar (https://github.com/antlr/grammars-v4/tree/master/cql3). The reason for doing this was headaches with comments in the `.cql` files.

This engine does not create keyspaces.

## Modules

| Module            | Coordinates                    | Use it for                                           |
|-------------------|--------------------------------|------------------------------------------------------|
| `cassmiggy-core`  | `cassmiggy:cassmiggy-core`     | Plain Java; the engine and `KeyspaceMigrationRunner` |

A runnable demo (plain Java) lives under [`examples/`](examples/README.md).


## How it works

1. Creates its infrastructure tables (`schema_migration_history` and `schema_migration_lock`) if they don't exist, idempotent (`CREATE TABLE IF NOT EXISTS`).
2. Acquires a lock (LWT).
3. Discovers migration files.
4. Validates checksums of the already-applied migrations to verify they were not modified.
5. Calculates the pending migrations.
6. Parses each pending file into statements (ANTLR4 turns the block of text into individual statements) and executes them one by one, waiting for schema agreement after DDL.
7. Records the outcome (success/failure, duration, statement count) in the history table.
8. Releases the lock.


## Quick start

```xml
<dependency>
  <groupId>cassmiggy</groupId>
  <artifactId>cassmiggy-core</artifactId>
  <version>1.0.0</version>
</dependency>
```

```java
import com.datastax.oss.driver.api.core.CqlSession;
import cassmiggy.Config;
import cassmiggy.model.MigrationResult;
import cassmiggy.SchemaMigrator;

try (CqlSession session = CqlSession.builder().build()) {
    Config config = Config.builder()
            .withSession(session)
            .withKeyspace("app")                 // must already exist
            .withMigrationsLocation("cql/app")   // classpath location of the .cql files
            .build();

    MigrationResult result = new SchemaMigrator(config).migrate();
    System.out.println(result.getAppliedCount() + " migrations applied");
}
```

## Configurations

| `Config.Builder` method            | Default                       | Purpose                                          |
|------------------------------------|-------------------------------|--------------------------------------------------|
| `withSession`                      | -                             | The `CqlSession` to run through (required)       |
| `withKeyspace`                     | -                             | Target keyspace (required; must exist)           |
| `withMigrationsLocation(String)`   | -                             | Classpath location of `.cql` files               |
| `withMigrationsLocation(Path)`     | -                             | Filesystem location of `.cql` files               |
| `withHistoryTable`                 | `schema_migration_history`    | History table name                               |
| `withLockTable`                    | `schema_migration_lock`       | Lock table name                                  |
| `withLockTimeout`                  | 5 min                         | Max wait to acquire the lock                     |
| `withSchemaAgreementTimeout`       | 30 s                          | Max wait for schema agreement after DDL          |
| `withMissingMigrationBehavior`     | `FAIL`                        | What to do if an applied file is now missing     |
| `withAutoKeyspace`                 | `true`                        | Scope statements to the keyspace per request/USE |



## Migration files

- Discovered from the configured classpath (or filesystem) location, recursively.
- Identified and **ordered by their relative path** and file name, alphabetically. I highly recommend using zero-padded numeric prefixes:
  `0001-ddl-create-users.cql`, `0002-ddl-create-products.cql`, `0003-dml-seed.cql`.
- Each file may contain multiple statements separated by `;`.
- A file is identified by its path. A checksum is calculated over its contents.




### Multiple keyspaces

`KeyspaceMigrationRunner` wraps the engine with sensible defaults and a helper for running migrations across multiple keyspaces:

```java
import cassmiggy.KeyspaceMigrationRunner;
import cassmiggy.KeyspaceMigrationRunner.KeyspaceMigration;
import java.util.List;

KeyspaceMigrationRunner runner = KeyspaceMigrationRunner.builder()
        .session(session)
        .build();

runner.migrateAll(List.of(
        new KeyspaceMigration("app", "cql/app"),
        new KeyspaceMigration("shared", "cql/shared")));
```

## Custom CQL parsing

By default, `.cql` files are split into statements by the bundled ANTLR CQL grammar (https://github.com/antlr/grammars-v4/tree/master/cql3). That grammar
is compiled into the jar **at build time** and is not swappable at runtime, but the *parser* is.
Statement parsing sits behind the `MigrationParser` SPI, so you can replace it wholesale: a
different CQL dialect, a more permissive splitter, or your own ANTLR grammar compiled in your own
module and wrapped behind `MigrationParser`.

Implement `MigrationParser`, then pass it directly to `SchemaMigrator`. The migrator still uses
the default Cassandra-backed discovery, history, and lock implementations:

```java
MigrationParser myParser = new MyCqlParser();

SchemaMigrator migrator = new SchemaMigrator(config, myParser);
migrator.migrate();
```



## Building & testing

```bash
mvn clean install
```

Integration tests use [Testcontainers](https://java.testcontainers.org/) to spin up real
Cassandra 3.11 and 4.1 containers, so **Docker must be running**.

> The build pins the Docker Remote API version (`-Dapi.version=1.43` via Surefire) because very
> recent Docker Engine releases can otherwise reject Testcontainers' client handshake. Override it
> if your daemon needs a different version: `mvn test -Dapi.version=1.51`.
