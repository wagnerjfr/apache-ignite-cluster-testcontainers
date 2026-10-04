package com.example.apache_ignite_cluster_testcontainers;

import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.client.IgniteClient;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

@Slf4j
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IgniteClusterTest {

	// Use the Ignite 3 version you target; check config path / env vars for that tag.
	static final String IMAGE = "apacheignite/ignite:3.1.0";
	static final int REST_PORT = 10300;
	static final int CLIENT_PORT = 10800;
	static int testCount = 0;

	static final Network NETWORK = Network.newNetwork();

	@Container
	static final GenericContainer<?> node1 = node("node1");
	@Container
	static final GenericContainer<?> node2 = node("node2");
	@Container
	static final GenericContainer<?> node3 = node("node3");

	static IgniteClient client1;

	static GenericContainer<?> node(String name) {
		return new GenericContainer<>(IMAGE)
				.withNetwork(NETWORK)
				.withNetworkAliases(name)
				.withEnv("IGNITE_NODE_NAME", name)
				.withEnv("JVM_MAX_MEM", "512m")
				.withEnv("JVM_MIN_MEM", "512m")
				.withCopyFileToContainer(
						MountableFile.forClasspathResource("ignite-config.conf"),
						"/opt/ignite/etc/ignite-config.conf")
				.withExposedPorts(REST_PORT, CLIENT_PORT)
				.waitingFor(Wait.forHttp("/management/v1/node/state")
						.forPort(REST_PORT)
						.withStartupTimeout(Duration.ofMinutes(2)));
	}

	@BeforeEach
	void beforeEach(TestInfo testInfo) {
		log.info("Test {}: {}", ++testCount, testInfo.getDisplayName());
	}

	@BeforeAll
	static void initCluster() throws Exception {
		// Containers are already started by the @Testcontainers extension.
		String body = """
                {"metaStorageNodes":["node1"],
                 "cmgNodes":["node1"],
                 "clusterName":"testCluster"}""";

		try (HttpClient http = HttpClient.newHttpClient()) {
			HttpResponse<String> resp = http.send(
					HttpRequest.newBuilder(URI.create("http://%s:%d/management/v1/cluster/init"
									.formatted(node1.getHost(), node1.getMappedPort(REST_PORT))))
							.header("Content-Type", "application/json")
							.POST(HttpRequest.BodyPublishers.ofString(body))
							.build(),
					HttpResponse.BodyHandlers.ofString());
			Assertions.assertEquals(200, resp.statusCode(), resp.body());
		}

		for (GenericContainer<?> n : List.of(node1, node2, node3)) {
			awaitInitialized(n);
		}

		client1 = getIgniteClient(node1);
	}

	static void awaitInitialized(GenericContainer<?> n) throws Exception {
		try (HttpClient http = HttpClient.newHttpClient()) {
			HttpRequest req = HttpRequest.newBuilder(URI.create("http://%s:%d/management/v1/cluster/state"
					.formatted(n.getHost(), n.getMappedPort(REST_PORT)))).GET().build();

			long deadline = System.currentTimeMillis() + 60_000;
			while (System.currentTimeMillis() < deadline) {
				HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
				if (r.statusCode() == 200 && r.body().contains("clusterTag")) {
					return;
				}
				Thread.sleep(500);
			}
			Assertions.fail("Cluster not initialized on " + n.getContainerName());
		}
	}

	@AfterAll
	static void closeClient() throws Exception {
		if (client1 != null) {
			client1.close(); // containers are stopped by the extension
		}
	}

	@Test
	@Order(1)
	void clusterHasThreeNodes() {
		Assertions.assertEquals(3, client1.clusterNodes().size());
	}

	@Test
	@Order(2)
	void sqlRoundTrip() {
		client1.sql().execute(null, "CREATE TABLE IF NOT EXISTS customer(id INT PRIMARY KEY, name VARCHAR)");
		client1.sql().execute(null, "INSERT INTO customer VALUES (1, 'Alice')");

		checkData(client1);
	}

	@Test
	void checkTableNode2AndNode3() {
		try (IgniteClient client2 = getIgniteClient(node2)) {
			checkData(client2);
		}
		try (IgniteClient client3 = getIgniteClient(node3)) {
			checkData(client3);
		}
	}

	void checkData(IgniteClient client) {
		var rs = client.sql().execute(null, "SELECT name FROM customer WHERE id = 1");
		Assertions.assertTrue(rs.hasNext());
		Assertions.assertEquals("Alice", rs.next().stringValue("name"));
	}

	static IgniteClient getIgniteClient(GenericContainer<?> node) {
		return IgniteClient.builder()
				.addresses(node.getHost() + ":" + node.getMappedPort(CLIENT_PORT))
				.build();
	}
}
