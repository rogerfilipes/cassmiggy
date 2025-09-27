# cassmiggy examples

Runnable demonstrations of cassmiggy. These are **not published artifacts** - they are kept
out of the main reactor and exist only to be read and run.

| Example                                    | Shows                                                   |
|--------------------------------------------|---------------------------------------------------------|
| [`plain-java-example`](plain-java-example) | Driving the core engine directly from a `main()` method |

The example applies two migrations from `src/main/resources/cql/app`:
`0001-ddl-create-users.cql` then `0002-dml-seed-users.cql`.

## Prerequisites

**1. Install the library into your local Maven repo first.** The example depends on
`cassmiggy-core` at version `1.0.0-SNAPSHOT`, so build the project root once:

```bash
mvn -f .. clean install -DskipTests
```

**2. A running Cassandra.** The quickest way:

```bash
docker run --rm -d --name cassandra -p 9042:9042 cassandra:4.1
```

**3. The `app` keyspace must exist.** cassmiggy never creates keyspaces, it manages only its own
history and lock tables. The plain-Java example creates the keyspace in code for convenience.

## Running

All commands are run from the repository root and target the examples aggregator with
`-f examples/pom.xml` (the examples are deliberately not part of the main build).

### Plain Java

```bash
mvn -f examples/pom.xml -pl plain-java-example exec:java
```

Override the connection with `CASSANDRA_HOST`, `CASSANDRA_PORT`, `CASSANDRA_DC`,
`CASSANDRA_KEYSPACE` environment variables if needed.

## Verifying

After the example runs, check that the rows landed:

```bash
docker exec -it cassandra cqlsh -e "SELECT email FROM app.users;"
```

Re-running an example is a no-op for already-applied files, that is the whole point: migrations are
ordered and applied exactly once, and a changed-after-apply file fails loudly.
