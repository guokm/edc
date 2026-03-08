/*
 *  Copyright (c) 2026
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 */

package org.eclipse.edc.test.e2e;

import io.restassured.common.mapper.TypeRef;
import org.eclipse.edc.junit.extensions.EmbeddedRuntime;
import org.eclipse.edc.junit.utils.LazySupplier;
import org.eclipse.edc.spi.security.Vault;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.configuration.ConfigFactory;

import java.net.URI;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.eclipse.edc.connector.controlplane.test.system.utils.PolicyFixtures.noConstraintPolicy;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.STARTED;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;

/**
 * Standalone runnable scenario:
 * 1) boots 2 control-plane runtimes (provider/consumer)
 * 2) boots 2 provider data-plane runtimes
 * 3) writes test assets + executes contract + transfer end-to-end
 * 4) validates PostgreSQL persisted rows
 */
public class CommercialDataspaceScenarioRunner {

    private static final String CONSUMER_ID = "urn:connector:consumer";
    private static final String PROVIDER_ID = "urn:connector:provider";

    private static final String CONSUMER_DB = "consumer";
    private static final String PROVIDER_DB = "provider";

    private static final String DB_HOST = env("EDC_DB_HOST", "localhost");
    private static final String DB_PORT = env("EDC_DB_PORT", "5432");
    private static final String DB_USER = env("EDC_DB_USER", "edc");
    private static final String DB_PASSWORD = env("EDC_DB_PASSWORD", "edc");
    private static final String DB_ADMIN = env("EDC_DB_ADMIN", "edc");

    private static final String CONSUMER_MANAGEMENT = "http://localhost:19191/management";
    private static final String CONSUMER_CONTROL = "http://localhost:19192/control";
    private static final String CONSUMER_PROTOCOL = "http://localhost:19193/protocol";

    private static final String PROVIDER_MANAGEMENT = "http://localhost:29191/management";
    private static final String PROVIDER_CONTROL = "http://localhost:29192/control";
    private static final String PROVIDER_PROTOCOL = "http://localhost:29193/protocol";

    private static final String PROVIDER_DP_1 = "provider-data-plane-1";
    private static final String PROVIDER_DP_2 = "provider-data-plane-2";

    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    public static void main(String[] args) {
        EmbeddedRuntime consumerCp = null;
        EmbeddedRuntime providerCp = null;
        EmbeddedRuntime providerDp1 = null;
        EmbeddedRuntime providerDp2 = null;
        var exitCode = 0;
        try {
            createDatabases();

            consumerCp = new EmbeddedRuntime("consumer-control-plane",
                    Runtimes.ControlPlane.MODULES[0], Runtimes.ControlPlane.MODULES[1], Runtimes.ControlPlane.SQL_MODULES[0])
                    .configurationProvider(() -> ConfigFactory.fromMap(controlPlaneConfig(
                            CONSUMER_ID,
                            "consumer-control-plane",
                            CONSUMER_MANAGEMENT,
                            CONSUMER_CONTROL,
                            CONSUMER_PROTOCOL,
                            CONSUMER_DB
                    )));

            providerCp = new EmbeddedRuntime("provider-control-plane",
                    Runtimes.ControlPlane.MODULES[0], Runtimes.ControlPlane.MODULES[1], Runtimes.ControlPlane.SQL_MODULES[0])
                    .configurationProvider(() -> ConfigFactory.fromMap(controlPlaneConfig(
                            PROVIDER_ID,
                            "provider-control-plane",
                            PROVIDER_MANAGEMENT,
                            PROVIDER_CONTROL,
                            PROVIDER_PROTOCOL,
                            PROVIDER_DB
                    )));

            providerDp1 = new EmbeddedRuntime(PROVIDER_DP_1, Runtimes.DataPlane.SQL_MODULES)
                    .configurationProvider(() -> ConfigFactory.fromMap(dataPlaneConfig(PROVIDER_DP_1, 29200, 29201, 29202)))
                    .registerSystemExtension(ServiceExtension.class, new HttpProxyDataPlaneExtension());

            providerDp2 = new EmbeddedRuntime(PROVIDER_DP_2, Runtimes.DataPlane.SQL_MODULES)
                    .configurationProvider(() -> ConfigFactory.fromMap(dataPlaneConfig(PROVIDER_DP_2, 29300, 29301, 29302)))
                    .registerSystemExtension(ServiceExtension.class, new HttpProxyDataPlaneExtension());

            consumerCp.boot();
            providerCp.boot();
            providerDp1.boot();
            providerDp2.boot();

            seedDataPlaneSecrets(providerDp1, providerDp2);

            var consumer = participant(CONSUMER_ID, "consumer", CONSUMER_MANAGEMENT, CONSUMER_PROTOCOL);
            var provider = participant(PROVIDER_ID, "provider", PROVIDER_MANAGEMENT, PROVIDER_PROTOCOL);

            assertTwoDataPlanesRegistered();
            runBusinessFlow(consumer, provider);
            assertPersistence();

            System.out.println("EDC 商业流程验证完成：2 个数据节点 + PostgreSQL 持久化 + 端到端传输成功");
        } catch (Exception e) {
            exitCode = 1;
            System.err.println("EDC 商业流程验证失败: " + e.getMessage());
            e.printStackTrace(System.err);
        } finally {
            shutdownRuntime(providerDp2, PROVIDER_DP_2);
            shutdownRuntime(providerDp1, PROVIDER_DP_1);
            shutdownRuntime(providerCp, "provider-control-plane");
            shutdownRuntime(consumerCp, "consumer-control-plane");
        }
        System.exit(exitCode);
    }

    private static void runBusinessFlow(TransferEndToEndParticipant consumer, TransferEndToEndParticipant provider) {
        var noConstraintPolicyId = provider.createPolicyDefinition(noConstraintPolicy());
        var counter = new AtomicInteger(1);

        for (var i = 0; i < 3; i++) {
            var assetId = "asset-" + UUID.randomUUID();
            createProviderResources(provider, noConstraintPolicyId, assetId, counter.getAndIncrement());

            var transferProcessId = consumer.requestAssetFrom(assetId, provider)
                    .withTransferType("HttpData-PULL")
                    .execute();

            consumer.awaitTransferToBeInState(transferProcessId, STARTED);

            var edr = await().atMost(TIMEOUT).until(() -> consumer.getEdr(transferProcessId), Objects::nonNull);
            var message = "msg-" + i;
            await().atMost(TIMEOUT).untilAsserted(() ->
                    consumer.pullData(edr, Map.of("message", message), body -> {
                    })
            );
        }
    }

    private static void createProviderResources(TransferEndToEndParticipant provider, String policyId, String assetId, int index) {
        provider.createAsset(assetId, Map.of("description", "generated-" + index), httpSourceDataAddress(index));
        provider.createContractDefinition(assetId, UUID.randomUUID().toString(), policyId, policyId);
    }

    private static Map<String, Object> httpSourceDataAddress(int index) {
        return new HashMap<>(Map.of(
                EDC_NAMESPACE + "name", "generated-transfer-" + index,
                EDC_NAMESPACE + "baseUrl", "http://any/source",
                EDC_NAMESPACE + "type", "HttpData"
        ));
    }

    private static void seedDataPlaneSecrets(EmbeddedRuntime providerDp1, EmbeddedRuntime providerDp2) {
        var privateKey = org.eclipse.edc.junit.testfixtures.TestUtils.getResourceFileContentAsString("certs/key.pem");
        var publicKey = org.eclipse.edc.junit.testfixtures.TestUtils.getResourceFileContentAsString("certs/cert.pem");

        var dp1Vault = providerDp1.getService(Vault.class);
        var dp2Vault = providerDp2.getService(Vault.class);

        dp1Vault.storeSecret("private-key", privateKey);
        dp1Vault.storeSecret("public-key", publicKey);
        dp2Vault.storeSecret("private-key", privateKey);
        dp2Vault.storeSecret("public-key", publicKey);
    }

    private static void assertTwoDataPlanesRegistered() {
        await().atMost(TIMEOUT).untilAsserted(() -> {
            var dataPlanes = given()
                    .baseUri(PROVIDER_CONTROL)
                    .when()
                    .get("/v1/dataplanes")
                    .then()
                    .statusCode(200)
                    .extract()
                    .as(new TypeRef<List<Map<String, Object>>>() {
                    });

            if (dataPlanes.size() < 2) {
                throw new IllegalStateException("Expected at least 2 data planes but got " + dataPlanes.size());
            }
        });
    }

    private static void assertPersistence() {
        var providerAssetCount = assertTableRowCountAtLeast(PROVIDER_DB, "edc_asset", 3);
        var providerContractNegotiationCount = assertTableRowCountAtLeast(PROVIDER_DB, "edc_contract_negotiation", 3);
        var providerTransferCount = assertTableRowCountAtLeast(PROVIDER_DB, "edc_transfer_process", 3);
        var providerDataPlaneCount = assertTableRowCountAtLeast(PROVIDER_DB, "edc_data_plane_instance", 2);
        var consumerTransferCount = assertTableRowCountAtLeast(CONSUMER_DB, "edc_transfer_process", 3);

        System.out.printf("持久化校验通过: provider.edc_asset=%d, provider.edc_contract_negotiation=%d, provider.edc_transfer_process=%d, provider.edc_data_plane_instance=%d, consumer.edc_transfer_process=%d%n",
                providerAssetCount,
                providerContractNegotiationCount,
                providerTransferCount,
                providerDataPlaneCount,
                consumerTransferCount
        );
    }

    private static int assertTableRowCountAtLeast(String database, String table, int expectedMin) {
        var sql = "select count(*) from " + table;
        try (var connection = DriverManager.getConnection(jdbcUrl(database), DB_USER, DB_PASSWORD);
             var statement = connection.createStatement();
             var resultSet = statement.executeQuery(sql)) {

            if (!resultSet.next()) {
                throw new IllegalStateException("No rows returned for count query: " + sql);
            }
            var count = resultSet.getInt(1);
            if (count < expectedMin) {
                throw new IllegalStateException("Table " + table + " expected >= " + expectedMin + " rows but got " + count);
            }
            return count;
        } catch (SQLException e) {
            throw new RuntimeException("Failed persistence check for table " + table, e);
        }
    }

    private static TransferEndToEndParticipant participant(String id, String name, String managementBaseUrl, String protocolBaseUrl) {
        return TransferEndToEndParticipant.Builder.newInstance()
                .id(id)
                .name(name)
                .managementUrl(new LazySupplier<>(() -> URI.create(managementBaseUrl)))
                .protocolUrl(new LazySupplier<>(() -> URI.create(protocolBaseUrl)))
                .build();
    }

    private static Map<String, String> controlPlaneConfig(
            String participantId,
            String componentId,
            String managementUrl,
            String controlUrl,
            String protocolUrl,
            String databaseName) {

        var management = URI.create(managementUrl);
        var control = URI.create(controlUrl);
        var protocol = URI.create(protocolUrl);

        return new HashMap<>() {
            {
                put("edc.participant.id", participantId);
                put("edc.component.id", componentId);

                put("web.http.port", String.valueOf(management.getPort() - 1));
                put("web.http.path", "/api");
                put("web.http.management.port", String.valueOf(management.getPort()));
                put("web.http.management.path", management.getPath());
                put("web.http.control.port", String.valueOf(control.getPort()));
                put("web.http.control.path", control.getPath());
                put("web.http.protocol.port", String.valueOf(protocol.getPort()));
                put("web.http.protocol.path", protocol.getPath());

                put("edc.dsp.callback.address", protocolUrl);
                put("edc.management.context.enabled", "true");

                put("edc.transfer.send.retry.limit", "3");
                put("edc.transfer.send.retry.base-delay.ms", "500");
                put("edc.transfer.state-machine.iteration-wait-millis", "50");
                put("edc.negotiation.consumer.send.retry.limit", "1");
                put("edc.negotiation.provider.send.retry.limit", "1");
                put("edc.negotiation.consumer.send.retry.base-delay.ms", "100");
                put("edc.negotiation.provider.send.retry.base-delay.ms", "100");
                put("edc.negotiation.consumer.state-machine.iteration-wait-millis", "50");
                put("edc.negotiation.provider.state-machine.iteration-wait-millis", "50");
                put("edc.data.plane.selector.state-machine.iteration-wait-millis", "100");
                put("edc.core.retry.retries.max", "1");

                put("edc.datasource.default.url", jdbcUrl(databaseName));
                put("edc.datasource.default.user", DB_USER);
                put("edc.datasource.default.password", DB_PASSWORD);
                put("edc.sql.schema.autocreate", "true");
            }
        };
    }

    private static Map<String, String> dataPlaneConfig(String runtimeId, int webPort, int controlPort, int publicPort) {
        return new HashMap<>() {
            {
                put("edc.component.id", runtimeId);
                put("edc.runtime.id", runtimeId);

                put("web.http.port", String.valueOf(webPort));
                put("web.http.path", "/api");
                put("web.http.control.port", String.valueOf(controlPort));
                put("web.http.control.path", "/control");
                put("web.http.public.port", String.valueOf(publicPort));
                put("web.http.public.path", "/public");

                put("edc.transfer.proxy.token.signer.privatekey.alias", "private-key");
                put("edc.transfer.proxy.token.verifier.publickey.alias", "public-key");
                put("edc.dataplane.http.sink.partition.size", "1");
                put("edc.dataplane.send.retry.limit", "1");
                put("edc.dataplane.state-machine.iteration-wait-millis", "50");
                put("edc.dpf.selector.url", PROVIDER_CONTROL + "/v1/dataplanes");

                put("edc.datasource.default.url", jdbcUrl(PROVIDER_DB));
                put("edc.datasource.default.user", DB_USER);
                put("edc.datasource.default.password", DB_PASSWORD);
                put("edc.sql.schema.autocreate", "true");
            }
        };
    }

    private static void createDatabases() {
        try (var connection = DriverManager.getConnection(jdbcUrl(DB_ADMIN), DB_USER, DB_PASSWORD);
             var statement = connection.createStatement()) {
            statement.execute("drop database if exists " + CONSUMER_DB);
            statement.execute("create database " + CONSUMER_DB);
            statement.execute("drop database if exists " + PROVIDER_DB);
            statement.execute("create database " + PROVIDER_DB);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize databases. Ensure PostgreSQL is running.", e);
        }
    }

    private static void shutdownRuntime(EmbeddedRuntime runtime, String name) {
        if (runtime == null) {
            return;
        }
        try {
            runtime.shutdown();
        } catch (Exception e) {
            System.err.println("Runtime shutdown warning [" + name + "]: " + e.getMessage());
        }
    }

    private static String jdbcUrl(String databaseName) {
        return "jdbc:postgresql://%s:%s/%s".formatted(DB_HOST, DB_PORT, databaseName);
    }

    private static String env(String key, String defaultValue) {
        return System.getenv().getOrDefault(key, defaultValue);
    }
}
