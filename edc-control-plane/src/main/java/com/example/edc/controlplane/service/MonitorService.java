package com.example.edc.controlplane.service;

import com.example.edc.controlplane.dto.DataPlaneRuntimeResponse;
import com.example.edc.controlplane.dto.MonitorCheckResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class MonitorService {
    private final RestClient restClient;
    private final String controlPlaneBaseUrl;
    private final String dataPlane1BaseUrl;
    private final String dataPlane2BaseUrl;
    private final String identityHubBaseUrl;
    private final String issuerServiceBaseUrl;
    private final String federatedCatalogBaseUrl;
    private final String operatorServiceBaseUrl;
    private final String frontendBaseUrl;

    public MonitorService(
            @Value("${edc.monitor.control-plane-base-url:http://localhost:8181}") String controlPlaneBaseUrl,
            @Value("${edc.monitor.data-plane-1-base-url:http://localhost:8182}") String dataPlane1BaseUrl,
            @Value("${edc.monitor.data-plane-2-base-url:http://localhost:8187}") String dataPlane2BaseUrl,
            @Value("${edc.monitor.identity-hub-base-url:http://localhost:8183}") String identityHubBaseUrl,
            @Value("${edc.monitor.issuer-service-base-url:http://localhost:8184}") String issuerServiceBaseUrl,
            @Value("${edc.monitor.federated-catalog-base-url:http://localhost:8185}") String federatedCatalogBaseUrl,
            @Value("${edc.monitor.operator-service-base-url:http://localhost:8186}") String operatorServiceBaseUrl,
            @Value("${edc.monitor.frontend-base-url:http://localhost:8080}") String frontendBaseUrl) {
        this.controlPlaneBaseUrl = controlPlaneBaseUrl;
        this.dataPlane1BaseUrl = dataPlane1BaseUrl;
        this.dataPlane2BaseUrl = dataPlane2BaseUrl;
        this.identityHubBaseUrl = identityHubBaseUrl;
        this.issuerServiceBaseUrl = issuerServiceBaseUrl;
        this.federatedCatalogBaseUrl = federatedCatalogBaseUrl;
        this.operatorServiceBaseUrl = operatorServiceBaseUrl;
        this.frontendBaseUrl = frontendBaseUrl;
        this.restClient = RestClient.builder().build();
    }

    /**
     * 汇总查询各模块健康状态，便于前端统一展示。
     *
     * @return 健康检查结果集合。
     */
    public List<MonitorCheckResponse> listHealthChecks() {
        var result = new ArrayList<MonitorCheckResponse>();
        result.add(probeGet("Control Plane", "健康检查", controlPlaneBaseUrl + "/actuator/health", Map.of()));
        result.add(probeGet("Data Plane 1", "健康检查", dataPlane1BaseUrl + "/actuator/health", Map.of()));
        result.add(probeGet("Data Plane 2", "健康检查", dataPlane2BaseUrl + "/actuator/health", Map.of()));
        result.add(probeGet("Identity Hub", "健康检查", identityHubBaseUrl + "/actuator/health", Map.of()));
        result.add(probeGet("Issuer Service", "健康检查", issuerServiceBaseUrl + "/actuator/health", Map.of()));
        result.add(probeGet("Federated Catalog", "健康检查", federatedCatalogBaseUrl + "/actuator/health", Map.of()));
        result.add(probeGet("Operator Services", "健康检查", operatorServiceBaseUrl + "/actuator/health", Map.of()));
        result.add(probeGet("Frontend", "首页可用性", frontendBaseUrl, Map.of()));
        return result;
    }

    /**
     * 汇总查询治理相关接口状态，避免前端跨端口检测失败。
     *
     * @return 治理接口检查结果集合。
     */
    public List<MonitorCheckResponse> listGovernanceChecks() {
        var result = new ArrayList<MonitorCheckResponse>();
        result.add(probeGet("Identity Hub", "DID 查询", identityHubBaseUrl + "/api/identity/did", Map.of()));
        result.add(probeGet("Issuer Service", "服务健康", issuerServiceBaseUrl + "/actuator/health", Map.of()));
        result.add(probeGet(
                "Federated Catalog",
                "目录查询",
                federatedCatalogBaseUrl + "/api/federated/catalog",
                Map.of("X-Participant-Id", "participant-a")
        ));
        result.add(probeGet("Operator Services", "会员列表", operatorServiceBaseUrl + "/api/memberships", Map.of()));
        result.add(probeGet("Operator Services", "账单列表", operatorServiceBaseUrl + "/api/billing/records", Map.of()));
        result.add(probePost(
                "Operator Services",
                "计费校验",
                operatorServiceBaseUrl + "/api/billing/usage/check",
                Map.of(
                        "participantId", "participant-a",
                        "serviceCode", "FEDERATED_CATALOG_QUERY"
                ),
                Map.of()
        ));
        return result;
    }

    /**
     * 查询双数据面运行摘要，便于节点页展示当前传输承载情况。
     *
     * @return 数据面运行摘要集合。
     */
    public List<DataPlaneRuntimeResponse> listDataPlaneRuntime() {
        return List.of(
                probeDataPlaneInfo("dp-1", dataPlane1BaseUrl + "/api/dataplane/info"),
                probeDataPlaneInfo("dp-2", dataPlane2BaseUrl + "/api/dataplane/info")
        );
    }

    private DataPlaneRuntimeResponse probeDataPlaneInfo(String fallbackDataPlaneId, String endpoint) {
        var response = new DataPlaneRuntimeResponse();
        response.setCheckedAt(LocalDateTime.now());
        response.setDataPlaneId(fallbackDataPlaneId);
        response.setPublicApiBaseUrl(endpoint);
        try {
            var body = restClient.get()
                    .uri(endpoint)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            response.setStatus("UP");
            response.setStatusCode(200);
            response.setMessage("OK");
            if (body != null) {
                var dataPlaneId = body.get("dataPlaneId");
                if (dataPlaneId instanceof String value) {
                    response.setDataPlaneId(value);
                }
                var publicApiBaseUrl = body.get("publicApiBaseUrl");
                if (publicApiBaseUrl instanceof String value) {
                    response.setPublicApiBaseUrl(value);
                }
                var transferCount = body.get("transferCount");
                if (transferCount instanceof Number value) {
                    response.setTransferCount(value.longValue());
                } else {
                    response.setTransferCount(0L);
                }
            } else {
                response.setTransferCount(0L);
            }
        } catch (RestClientResponseException ex) {
            response.setStatus("DOWN");
            response.setStatusCode(ex.getStatusCode().value());
            response.setMessage(shortMessage(ex.getResponseBodyAsString(), ex.getMessage()));
            response.setTransferCount(0L);
        } catch (Exception ex) {
            response.setStatus("DOWN");
            response.setStatusCode(null);
            response.setMessage(shortMessage("", ex.getMessage()));
            response.setTransferCount(0L);
        }
        return response;
    }

    private MonitorCheckResponse probeGet(String moduleName, String checkName, String endpoint, Map<String, String> headers) {
        var response = baseResponse(moduleName, checkName, "GET", endpoint);
        try {
            var request = restClient.get().uri(endpoint);
            headers.forEach((key, value) -> request.header(key, value));
            var entity = request.retrieve().toBodilessEntity();
            response.setStatusCode(entity.getStatusCode().value());
            response.setStatus("UP");
            response.setMessage("OK");
        } catch (RestClientResponseException ex) {
            response.setStatusCode(ex.getStatusCode().value());
            response.setStatus("DOWN");
            response.setMessage(shortMessage(ex.getResponseBodyAsString(), ex.getMessage()));
        } catch (Exception ex) {
            response.setStatusCode(null);
            response.setStatus("DOWN");
            response.setMessage(shortMessage("", ex.getMessage()));
        }
        return response;
    }

    private MonitorCheckResponse probePost(
            String moduleName,
            String checkName,
            String endpoint,
            Map<String, Object> body,
            Map<String, String> headers) {
        var response = baseResponse(moduleName, checkName, "POST", endpoint);
        try {
            var request = restClient.post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON);
            headers.forEach((key, value) -> request.header(key, value));
            var entity = request.body(body)
                    .retrieve()
                    .toBodilessEntity();
            response.setStatusCode(entity.getStatusCode().value());
            response.setStatus("UP");
            response.setMessage("OK");
        } catch (RestClientResponseException ex) {
            response.setStatusCode(ex.getStatusCode().value());
            response.setStatus("DOWN");
            response.setMessage(shortMessage(ex.getResponseBodyAsString(), ex.getMessage()));
        } catch (Exception ex) {
            response.setStatusCode(null);
            response.setStatus("DOWN");
            response.setMessage(shortMessage("", ex.getMessage()));
        }
        return response;
    }

    private MonitorCheckResponse baseResponse(String moduleName, String checkName, String method, String endpoint) {
        var response = new MonitorCheckResponse();
        response.setModuleName(moduleName);
        response.setCheckName(checkName);
        response.setMethod(method);
        response.setEndpoint(endpoint);
        response.setCheckedAt(LocalDateTime.now());
        return response;
    }

    private String shortMessage(String body, String fallback) {
        var source = body == null || body.isBlank() ? fallback : body;
        if (source == null || source.isBlank()) {
            return "Request failed";
        }
        return source.length() > 120 ? source.substring(0, 120) + "..." : source;
    }
}
