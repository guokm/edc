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
import org.eclipse.edc.junit.annotations.EndToEndTest;
import org.eclipse.edc.junit.annotations.PostgresqlIntegrationTest;
import org.eclipse.edc.junit.annotations.Runtime;
import org.eclipse.edc.junit.extensions.ComponentRuntimeExtension;
import org.eclipse.edc.junit.extensions.RuntimeExtension;
import org.eclipse.edc.junit.utils.Endpoints;
import org.eclipse.edc.spi.security.Vault;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.configuration.ConfigFactory;
import org.eclipse.edc.sql.testfixtures.PostgresqlEndToEndExtension;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.STARTED;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;

@EndToEndTest
@PostgresqlIntegrationTest
@SuppressWarnings("JUnitMalformedDeclaration")
public class CommercialDataspacePostgresClusteredDataPlaneEndToEndTest extends TransferEndToEndTestBase {

    static final String PROVIDER_DP_1 = "provider-data-plane-1";
    static final String PROVIDER_DP_2 = "provider-data-plane-2";
    static final String CONSUMER_DB = "consumer";
    static final String PROVIDER_DB = "provider";

    @Order(0)
    @RegisterExtension
    static final PostgresqlEndToEndExtension POSTGRESQL_EXTENSION = new PostgresqlEndToEndExtension();

    @Order(1)
    @RegisterExtension
    static final BeforeAllCallback CREATE_DATABASES = context -> {
        POSTGRESQL_EXTENSION.createDatabase(CONSUMER_DB);
        POSTGRESQL_EXTENSION.createDatabase(PROVIDER_DB);
    };

    @RegisterExtension
    static final RuntimeExtension CONSUMER_CONTROL_PLANE = ComponentRuntimeExtension.Builder.newInstance()
            .name(CONSUMER_CP)
            .modules(Runtimes.ControlPlane.MODULES)
            .modules(Runtimes.ControlPlane.SQL_MODULES)
            .endpoints(Runtimes.ControlPlane.ENDPOINTS.build())
            .configurationProvider(() -> Runtimes.ControlPlane.config(CONSUMER_ID))
            .configurationProvider(() -> POSTGRESQL_EXTENSION.configFor(CONSUMER_DB))
            .paramProvider(TransferEndToEndParticipant.class, TransferEndToEndParticipant::forContext)
            .build();

    static final Endpoints PROVIDER_ENDPOINTS = Runtimes.ControlPlane.ENDPOINTS.build();

    @RegisterExtension
    static final RuntimeExtension PROVIDER_CONTROL_PLANE = ComponentRuntimeExtension.Builder.newInstance()
            .name(PROVIDER_CP)
            .modules(Runtimes.ControlPlane.MODULES)
            .modules(Runtimes.ControlPlane.SQL_MODULES)
            .endpoints(PROVIDER_ENDPOINTS)
            .configurationProvider(() -> Runtimes.ControlPlane.config(PROVIDER_ID))
            .configurationProvider(() -> POSTGRESQL_EXTENSION.configFor(PROVIDER_DB))
            .paramProvider(TransferEndToEndParticipant.class, TransferEndToEndParticipant::forContext)
            .build();

    @RegisterExtension
    static final RuntimeExtension PROVIDER_DATA_PLANE_1 = ComponentRuntimeExtension.Builder.newInstance()
            .name(PROVIDER_DP_1)
            .modules(Runtimes.DataPlane.SQL_MODULES)
            .endpoints(Runtimes.DataPlane.ENDPOINTS.build())
            .configurationProvider(Runtimes.DataPlane::config)
            .configurationProvider(() -> Runtimes.ControlPlane.dataPlaneSelectorFor(PROVIDER_ENDPOINTS))
            .configurationProvider(() -> POSTGRESQL_EXTENSION.configFor(PROVIDER_DB))
            .configurationProvider(() -> ConfigFactory.fromMap(Map.of("edc.runtime.id", PROVIDER_DP_1)))
            .build()
            .registerSystemExtension(ServiceExtension.class, new HttpProxyDataPlaneExtension());

    @RegisterExtension
    static final RuntimeExtension PROVIDER_DATA_PLANE_2 = ComponentRuntimeExtension.Builder.newInstance()
            .name(PROVIDER_DP_2)
            .modules(Runtimes.DataPlane.SQL_MODULES)
            .endpoints(Runtimes.DataPlane.ENDPOINTS.build())
            .configurationProvider(Runtimes.DataPlane::config)
            .configurationProvider(() -> Runtimes.ControlPlane.dataPlaneSelectorFor(PROVIDER_ENDPOINTS))
            .configurationProvider(() -> POSTGRESQL_EXTENSION.configFor(PROVIDER_DB))
            .configurationProvider(() -> ConfigFactory.fromMap(Map.of("edc.runtime.id", PROVIDER_DP_2)))
            .build()
            .registerSystemExtension(ServiceExtension.class, new HttpProxyDataPlaneExtension());

    @BeforeAll
    static void setupDataPlaneSecrets(@Runtime(PROVIDER_DP_1) Vault dp1, @Runtime(PROVIDER_DP_2) Vault dp2) {
        dp1.storeSecret("private-key", privateKey);
        dp1.storeSecret("public-key", publicKey);
        dp2.storeSecret("private-key", privateKey);
        dp2.storeSecret("public-key", publicKey);
    }

    @Test
    void shouldRunCommercialFlowWithTwoDataPlaneNodesAndPersistentState(
            @Runtime(CONSUMER_CP) TransferEndToEndParticipant consumer,
            @Runtime(PROVIDER_CP) TransferEndToEndParticipant provider) {

        assertTwoDataPlaneNodesRegistered();

        for (var i = 0; i < 3; i++) {
            var assetId = "asset-" + UUID.randomUUID();
            createResourcesOnProvider(provider, assetId, httpSourceDataAddress(i));

            var transferProcessId = consumer.requestAssetFrom(assetId, provider)
                    .withTransferType("HttpData-PULL")
                    .execute();

            consumer.awaitTransferToBeInState(transferProcessId, STARTED);

            var edr = await().atMost(timeout).until(() -> consumer.getEdr(transferProcessId), Objects::nonNull);
            var message = "message-" + i;
            await().atMost(timeout)
                    .untilAsserted(() -> consumer.pullData(edr, Map.of("message", message), body -> assertThat(body).isEqualTo("data")));
        }

        assertTableRowCountAtLeast(PROVIDER_DB, "edc_asset", 3);
        assertTableRowCountAtLeast(PROVIDER_DB, "edc_contract_negotiation", 3);
        assertTableRowCountAtLeast(PROVIDER_DB, "edc_transfer_process", 3);
        assertTableRowCountAtLeast(CONSUMER_DB, "edc_transfer_process", 3);
    }

    private void assertTwoDataPlaneNodesRegistered() {
        var controlEndpoint = Objects.requireNonNull(PROVIDER_ENDPOINTS.getEndpoint("control")).get().toString();
        await().atMost(timeout).untilAsserted(() -> {
            var dataPlanes = given()
                    .baseUri(controlEndpoint)
                    .when()
                    .get("/v1/dataplanes")
                    .then()
                    .log().ifValidationFails()
                    .statusCode(200)
                    .extract()
                    .as(new TypeRef<List<Map<String, Object>>>() {
                    });

            assertThat(dataPlanes).hasSizeGreaterThanOrEqualTo(2);
        });
    }

    private static Map<String, Object> httpSourceDataAddress(int index) {
        return new HashMap<>(Map.of(
                EDC_NAMESPACE + "name", "transfer-test-" + index,
                EDC_NAMESPACE + "baseUrl", "http://any/source",
                EDC_NAMESPACE + "type", "HttpData"
        ));
    }

    private static void assertTableRowCountAtLeast(String databaseName, String tableName, int minRows) {
        var jdbcUrl = POSTGRESQL_EXTENSION.getJdbcUrl(databaseName);
        try (var connection = DriverManager.getConnection(jdbcUrl, POSTGRESQL_EXTENSION.getUsername(), POSTGRESQL_EXTENSION.getPassword());
             var statement = connection.createStatement();
             var resultSet = statement.executeQuery("select count(*) from " + tableName)) {

            if (!resultSet.next()) {
                throw new IllegalStateException("No count row returned for table " + tableName);
            }
            var count = resultSet.getInt(1);
            assertThat(count)
                    .withFailMessage("Expected %s to have at least %s rows but got %s", tableName, minRows, count)
                    .isGreaterThanOrEqualTo(minRows);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query table " + tableName + " in database " + databaseName, e);
        }
    }
}
