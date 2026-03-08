package com.example.edc.dataplane.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.edc.dataplane.dto.DataPullResponse;
import com.example.edc.dataplane.dto.TransferEdrResponse;
import com.example.edc.dataplane.entity.DpEdrEntity;
import com.example.edc.dataplane.entity.DpTransferProcessEntity;
import com.example.edc.dataplane.mapper.DpEdrMapper;
import com.example.edc.dataplane.mapper.DpTransferProcessMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class DataPlaneTransferService {
    private final DpTransferProcessMapper transferProcessMapper;
    private final DpEdrMapper edrMapper;
    private final String dataPlaneId;
    private final String publicApiBaseUrl;

    public DataPlaneTransferService(
            DpTransferProcessMapper transferProcessMapper,
            DpEdrMapper edrMapper,
            @Value("${edc.dataplane.id:dp-1}") String dataPlaneId,
            @Value("${edc.dataplane.public-api-base-url:http://localhost:8182}") String publicApiBaseUrl) {
        this.transferProcessMapper = transferProcessMapper;
        this.edrMapper = edrMapper;
        this.dataPlaneId = dataPlaneId;
        this.publicApiBaseUrl = publicApiBaseUrl;
    }

    /**
     * 启动传输并生成 EDR。
     *
     * @param transferProcessId 传输流程 ID。
     * @return 启动结果及 EDR 信息。
     */
    public Map<String, Object> start(String transferProcessId) {
        var now = LocalDateTime.now();
        var transfer = transferProcessMapper.selectOne(new LambdaQueryWrapper<DpTransferProcessEntity>()
                .eq(DpTransferProcessEntity::getId, transferProcessId)
                .last("limit 1"));
        if (transfer == null) {
            transfer = new DpTransferProcessEntity();
            transfer.setId(transferProcessId);
            transfer.setDataPlaneId(dataPlaneId);
            transfer.setStartedAt(now);
            transfer.setState("STARTED");
            transfer.setUpdatedAt(now);
            transferProcessMapper.insert(transfer);
        } else {
            transfer.setState("STARTED");
            transfer.setUpdatedAt(now);
            transferProcessMapper.updateById(transfer);
        }

        var edrToken = "edr-token-" + UUID.randomUUID();
        var edr = edrMapper.selectOne(new LambdaQueryWrapper<DpEdrEntity>()
                .eq(DpEdrEntity::getTransferProcessId, transferProcessId)
                .last("limit 1"));
        if (edr == null) {
            edr = new DpEdrEntity();
            edr.setTransferProcessId(transferProcessId);
        }
        edr.setEndpoint(publicApiBaseUrl + "/api/data/" + transferProcessId);
        edr.setAuthKey("Authorization");
        edr.setAuthToken(edrToken);
        edr.setExpiresAt(now.plusHours(1));
        if (edrMapper.selectCount(new LambdaQueryWrapper<DpEdrEntity>()
                .eq(DpEdrEntity::getTransferProcessId, transferProcessId)) == 0) {
            edrMapper.insert(edr);
        } else {
            edrMapper.updateById(edr);
        }

        var response = new TransferEdrResponse();
        response.setTransferProcessId(transferProcessId);
        response.setEndpoint(edr.getEndpoint());
        response.setAuthKey(edr.getAuthKey());
        response.setAuthToken(edr.getAuthToken());
        response.setExpiresAt(edr.getExpiresAt());

        return Map.of(
                "signalId", "sig-" + UUID.randomUUID(),
                "transferProcessId", transferProcessId,
                "dataPlaneId", dataPlaneId,
                "state", "STARTED",
                "timestamp", now,
                "edr", response
        );
    }

    /**
     * 更新传输状态。
     *
     * @param transferProcessId 传输流程 ID。
     * @param targetState 目标状态。
     * @return 状态更新后的结果。
     */
    public Map<String, Object> updateState(String transferProcessId, String targetState) {
        var now = LocalDateTime.now();
        var transfer = transferProcessMapper.selectOne(new LambdaQueryWrapper<DpTransferProcessEntity>()
                .eq(DpTransferProcessEntity::getId, transferProcessId)
                .last("limit 1"));
        if (transfer == null) {
            transfer = new DpTransferProcessEntity();
            transfer.setId(transferProcessId);
            transfer.setDataPlaneId(dataPlaneId);
            transfer.setStartedAt(now);
            transfer.setState(targetState);
            transfer.setUpdatedAt(now);
            transferProcessMapper.insert(transfer);
        } else {
            transfer.setState(targetState);
            transfer.setUpdatedAt(now);
            transferProcessMapper.updateById(transfer);
        }

        return Map.of(
                "signalId", "sig-" + UUID.randomUUID(),
                "transferProcessId", transferProcessId,
                "dataPlaneId", dataPlaneId,
                "state", targetState,
                "timestamp", now
        );
    }

    /**
     * 查询指定传输流程的 EDR。
     *
     * @param transferProcessId 传输流程 ID。
     * @return EDR 响应。
     */
    public TransferEdrResponse resolveEdr(String transferProcessId) {
        var edr = edrMapper.selectOne(new LambdaQueryWrapper<DpEdrEntity>()
                .eq(DpEdrEntity::getTransferProcessId, transferProcessId)
                .last("limit 1"));
        if (edr == null) {
            throw new ResponseStatusException(NOT_FOUND, "EDR not found for transfer: " + transferProcessId);
        }

        var response = new TransferEdrResponse();
        response.setTransferProcessId(transferProcessId);
        response.setEndpoint(edr.getEndpoint());
        response.setAuthKey(edr.getAuthKey());
        response.setAuthToken(edr.getAuthToken());
        response.setExpiresAt(edr.getExpiresAt());
        return response;
    }

    /**
     * 基于 EDR 鉴权拉取数据。
     *
     * @param transferProcessId 传输流程 ID。
     * @param authorization 授权令牌。
     * @param message 客户端透传消息。
     * @return 数据拉取结果。
     */
    public DataPullResponse pullData(String transferProcessId, String authorization, String message) {
        var edr = edrMapper.selectOne(new LambdaQueryWrapper<DpEdrEntity>()
                .eq(DpEdrEntity::getTransferProcessId, transferProcessId)
                .last("limit 1"));
        if (edr == null) {
            throw new ResponseStatusException(NOT_FOUND, "Transfer payload not found: " + transferProcessId);
        }
        if (!StringUtils.hasText(authorization) || !authorization.equals(edr.getAuthToken())) {
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid EDR token");
        }

        var response = new DataPullResponse();
        response.setTransferProcessId(transferProcessId);
        response.setDataPlaneId(dataPlaneId);
        response.setPayload("data-" + transferProcessId);
        response.setMessage(message == null ? "" : message);
        response.setServedAt(LocalDateTime.now());
        return response;
    }

    /**
     * 查询当前 Data Plane 运行摘要。
     *
     * @return 节点信息与传输数量。
     */
    public Map<String, Object> info() {
        var count = transferProcessMapper.selectCount(new LambdaQueryWrapper<>());
        return Map.of(
                "dataPlaneId", dataPlaneId,
                "publicApiBaseUrl", publicApiBaseUrl,
                "transferCount", count == null ? 0L : count
        );
    }
}
