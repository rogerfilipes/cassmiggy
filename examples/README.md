# cassmiggy examples

Runnable demonstrations of the two ways to use cassmiggy. These are **not published
artifacts**, they are kept out of the main reactor and exist only to be read and run.

| Example                                    | Shows                                                              |
|--------------------------------------------|-------------------------------------------------------------------|
| [`plain-java-example`](plain-java-example) | Driving the core engine directly from a `main()` method           |
| [`quarkus-example`](quarkus-example)       | Startup migrations via the `cassmiggy-quarkus` adapter, with a REST endpoint |

Each example applies the same two migrations from `src/main/resources/cql/app`:
`0001-ddl-create-users.cql` then `0002-dml-seed-users.cql`.

## Prerequisites

**1. Install the libraries into your local Maven repo first.** The examples depend on
`cassmiggy-core` and `-quarkus` at version `1.0.0-SNAPSHOT`, so build the project root
once:

```bash
mvn -f .. clean install -DskipTests
```

**2. A running Cassandra.** The quickest way:

```bash
docker run --rm -d --name cassandra -p 9042:9042 cassandra:4.1
```

**3. The `app` keyspace must exist.** cassmiggy never creates keyspaces, it manages only its own
history and lock tables. The plain-Java example creates the keyspace in code for convenience, and
the Quarkus example ships its own Cassandra + keyspace via Docker Compose (see below). If you instead
point the Quarkus example at this shared Cassandra, create the keyspace once:

```bash
docker exec -it cassandra cqlsh -e \
  "CREATE KEYSPACE IF NOT EXISTS app WITH replication = {'class':'SimpleStrategy','replication_factor':1};"
```

## Running

All commands are run from the repository root and target the examples aggregator with
`-f examples/pom.xml` (the examples are deliberately not part of the main build).

### Plain Java

```bash
mvn -f examples/pom.xml -pl plain-java-example exec:java
```

Override the connection with `CASSANDRA_HOST`, `CASSANDRA_PORT`, `CASSANDRA_DC`,
`CASSANDRA_KEYSPACE` environment variables if needed.

### Quarkus

This example is self-contained, it ships its own Cassandra via
[`quarkus-example/docker-compose.yml`](quarkus-example/docker-compose.yml), which also creates the
`app` keyspace, so you can skip the shared Cassandra in the Prerequisites above:

```bash
docker compose -f examples/quarkus-example/docker-compose.yml up -d   # Cassandra + 'app' keyspace
mvn -f examples/pom.xml -pl quarkus-example quarkus:dev
```

When you're done:

```bash
docker compose -f examples/quarkus-example/docker-compose.yml down
```

Migrations run on the Quarkus `StartupEvent`, then the app stays up serving a small REST
endpoint backed by the migrated table:

```bash
curl localhost:8080/users
# [{"id":"11111111-...","email":"alice@example.com","createdAt":"2024-01-01T00:00:00Z"}, ...]
```

Settings live in `quarkus-example/src/main/resources/application.properties`.

> The `cassandra-quarkus-client` version in `quarkus-example/pom.xml` is pinned independently of
> the Quarkus platform version; if you bump Quarkus, align it with a compatible extension release.

## Verifying

After any example runs, check that the rows landed:

```bash
docker exec -it cassandra cqlsh -e "SELECT email FROM app.users;"
```

Re-running an example is a no-op for already-applied files, that is the whole point: migrations are
ordered and applied exactly once, and a changed-after-apply file fails loudly.
