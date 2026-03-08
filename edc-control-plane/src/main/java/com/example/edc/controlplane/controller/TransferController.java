package com.example.edc.controlplane.controller;

import com.example.edc.common.model.TransferProcess;
import com.example.edc.controlplane.dto.TransferEdrResponse;
import com.example.edc.controlplane.dto.TransferOrchestrationPreviewResponse;
import com.example.edc.controlplane.dto.TransferRequest;
import com.example.edc.controlplane.dto.TransferStatusResponse;
import com.example.edc.controlplane.service.ControlPlaneAccessService;
import com.example.edc.controlplane.service.ControlPlaneService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {
    private final ControlPlaneAccessService accessService;
    private final ControlPlaneService controlPlaneService;

    public TransferController(ControlPlaneAccessService accessService, ControlPlaneService controlPlaneService) {
        this.accessService = accessService;
        this.controlPlaneService = controlPlaneService;
    }

    /**
     * 发起传输流程。
     *
     * @param request 传输请求，包含协议、协议 ID、可选数据面 ID。
     * @return 传输流程信息。
     */
    @PostMapping
    public TransferProcess startTransfer(@Valid @RequestBody TransferRequest request) {
        return controlPlaneService.startTransfer(request.agreementId(), request.protocol(), request.dataPlaneId());
    }

    /**
     * 根据传输流程 ID 查询 EDR 信息。
     *
     * @param transferProcessId 传输流程 ID。
     * @return EDR 数据地址及凭证。
     */
    @GetMapping("/{transferProcessId}/edr")
    public TransferEdrResponse resolveEdr(@PathVariable("transferProcessId") String transferProcessId) {
        return controlPlaneService.resolveEdr(transferProcessId);
    }

    /**
     * 查询传输状态列表（控制面 + 数据面）。
     *
     * @return 传输状态集合。
     */
    @GetMapping("/status")
    public List<TransferStatusResponse> listTransferStatus() {
        return controlPlaneService.listTransferStatuses();
    }

    /**
     * 查询单个传输流程的全链路轨迹。
     *
     * @param transferProcessId 传输流程 ID。
     * @return 轨迹信息。
     */
    @GetMapping("/{transferProcessId}/trace")
    public Map<String, Object> getTransferTrace(@PathVariable("transferProcessId") String transferProcessId) {
        return controlPlaneService.getTransferTrace(transferProcessId);
    }

    /**
     * 按协议 ID 预演 Control Plane 编排与门禁流程（不执行真实传输）。
     *
     * <p>该接口用于页面演示：会员校验、签发资格校验、计费只读校验、Data Plane 选择。</p>
     *
     * @param agreementId 协议 ID。
     * @param dataPlaneId 指定数据面 ID，可为空（为空时按轮询策略选择）。
     * @param participantId 调用方参与方（请求头 X-Participant-Id，必须为 operator）。
     * @param operatorToken 运营方权限令牌（请求头 X-Operator-Token）。
     * @return 编排门禁预演结果。
     */
    @GetMapping("/orchestration/preview")
    public TransferOrchestrationPreviewResponse previewOrchestration(
            @RequestParam("agreementId") String agreementId,
            @RequestParam(value = "dataPlaneId", required = false) String dataPlaneId,
            @RequestHeader(value = "X-Participant-Id", required = false) String participantId,
            @RequestHeader(value = "X-Operator-Token", required = false) String operatorToken) {
        accessService.ensureOrchestrationDemoPermission(participantId, operatorToken);
        return controlPlaneService.previewTransferOrchestration(agreementId, dataPlaneId);
    }
}
