# Apache Ignite Cluster Testcontainers

Integration tests for Apache Ignite 3.x using Testcontainers and Spring Boot.

## Overview

This project demonstrates how to spin up a multi-node Apache Ignite 3 cluster using Testcontainers for integration testing. It creates a 3-node cluster, initializes it via the REST API, and runs SQL operations to verify cluster functionality.

## Prerequisites

- Java 21+
- Docker (for Testcontainers)
- Maven (or use the included Maven wrapper)

## Project Structure

```
src/
├── main/
│   └── java/.../ApacheIgniteClusterTestcontainersApplication.java  # Spring Boot entry point
└── test/
    ├── java/.../IgniteClusterTest.java        # Main integration test
    ├── java/.../TestcontainersConfiguration.java  # Test configuration
    └── resources/ignite-config.conf           # Ignite 3 cluster configuration
```

## Running Tests

```bash
# Using Maven wrapper
./mvnw test

# Or with Maven directly
mvn test
```

The test will:
1. Start 3 Ignite containers (`node1`, `node2`, `node3`) on a shared Docker network
2. Initialize the cluster via REST API on `node1`
3. Wait for all nodes to join the cluster
4. Run SQL tests to verify data replication across nodes

## Test Details

| Test | Description |
|------|-------------|
| `clusterHasThreeNodes` | Verifies all 3 nodes joined the cluster |
| `sqlRoundTrip` | Creates table, inserts data, queries back on node1 |
| `checkTableNode2AndNode3` | Verifies data is replicated to node2 and node3 |

## Configuration

### Ignite Configuration (`src/test/resources/ignite-config.conf`)

```hocon
ignite {
  network {
    port: 3344
    nodeFinder.netClusterNodes: [ "node1:3344", "node2:3344", "node3:3344" ]
  }
  clientConnector.port: 10800
  rest.port: 10300
}
```

### Ports

| Port | Purpose |
|------|---------|
| 10300 | REST API (cluster management) |
| 10800 | Thin client protocol (SQL, key-value) |
| 3344 | Internal node communication |

## Dependencies

- Spring Boot 4.1.1
- Apache Ignite 3.1.0 (client)
- Testcontainers (JUnit Jupiter integration)
- Lombok (logging)

## Customizing

### Change Ignite Version

Update the image in `IgniteClusterTest.java`:

```java
static final String IMAGE = "apacheignite/ignite:3.1.0";
```

And update the client dependency in `pom.xml`:

```xml
<dependency>
    <groupId>org.apache.ignite</groupId>
    <artifactId>ignite-client</artifactId>
    <version>3.1.0</version>
</dependency>
```

### Add More Nodes

Add additional `@Container` fields in `IgniteClusterTest.java`:

```java
@Container
static final GenericContainer<?> node4 = node("node4");
```

And update the `ignite-config.conf` node finder list accordingly.

## License

MIT