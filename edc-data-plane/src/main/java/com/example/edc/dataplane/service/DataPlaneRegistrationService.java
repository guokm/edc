package com.example.edc.dataplane.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Service
public class DataPlaneRegistrationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DataPlaneRegistrationService.class);

    private final RestClient restClient;
    private final String dataPlaneId;
    private final String publicApiBaseUrl;
    private final String controlApiBaseUrl;
    private final String controlPlaneBaseUrl;
    private final String protocol;

    public DataPlaneRegistrationService(
            @Value("${edc.dataplane.id:dp-1}") String dataPlaneId,
            @Value("${edc.dataplane.public-api-base-url:http://localhost:8182}") String publicApiBaseUrl,
            @Value("${edc.dataplane.control-api-base-url:http://localhost:8182}") String controlApiBaseUrl,
            @Value("${edc.control-plane.base-url:http://localhost:8181}") String controlPlaneBaseUrl,
            @Value("${edc.dataplane.protocol:DSP}") String protocol) {
        this.dataPlaneId = dataPlaneId;
        this.publicApiBaseUrl = publicApiBaseUrl;
        this.controlApiBaseUrl = controlApiBaseUrl;
        this.controlPlaneBaseUrl = controlPlaneBaseUrl;
        this.protocol = protocol;
        this.restClient = RestClient.builder().build();
    }

    /**
     * 应用启动后立即向控制面发起注册。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void registerOnStartup() {
        register();
    }

    /**
     * 定时上报 Data Plane 心跳（复用注册接口）。
     */
    @Scheduled(fixedDelayString = "${edc.dataplane.registration-interval-ms:30000}")
    public void registerHeartbeat() {
        register();
    }

    /**
     * 向控制面发送当前 Data Plane 节点信息。
     */
    public void register() {
        try {
            restClient.post()
                    .uri(controlPlaneBaseUrl + "/api/dataplanes/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "id", dataPlaneId,
                            "publicApiBaseUrl", publicApiBaseUrl,
                            "controlApiBaseUrl", controlApiBaseUrl,
                            "protocol", protocol
                    ))
                    .retrieve()
                    .toBodilessEntity();
            LOGGER.info("Data plane [{}] registered to control plane", dataPlaneId);
        } catch (RestClientException ex) {
            LOGGER.warn("Data plane registration failed for [{}]: {}", dataPlaneId, ex.getMessage());
        }
    }
}
