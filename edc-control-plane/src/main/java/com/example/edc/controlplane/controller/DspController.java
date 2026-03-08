package com.example.edc.controlplane.controller;

import com.example.edc.common.model.TransferProcess;
import com.example.edc.controlplane.dto.ContractNegotiationRequest;
import com.example.edc.controlplane.dto.DspNegotiationRequest;
import com.example.edc.controlplane.dto.DspTransferRequest;
import com.example.edc.controlplane.service.ControlPlaneService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/dsp")
public class DspController {
    private final ControlPlaneService controlPlaneService;

    public DspController(ControlPlaneService controlPlaneService) {
        this.controlPlaneService = controlPlaneService;
    }

    /**
     * 发起 DSP 目录请求。
     *
     * @param request DSP 目录请求体。
     * @return 目录数据集合。
     */
    @PostMapping("/catalog/request")
    public Map<String, Object> requestCatalog(@RequestBody Map<String, Object> request) {
        return Map.of(
                "providerId", "participant-A",
                "datasets", controlPlaneService.listCatalog()
        );
    }

    /**
     * 发起 DSP 协商。
     *
     * @param request DSP 协商请求。
     * @return DSP 协商结果。
     */
    @PostMapping("/negotiations")
    public Object negotiate(@Valid @RequestBody DspNegotiationRequest request) {
        return controlPlaneService.negotiateContract(new ContractNegotiationRequest(
                request.assetId(),
                request.consumerId(),
                request.offerId()
        ));
    }

    /**
     * 发起 DSP 传输。
     *
     * @param request DSP 传输请求。
     * @return 传输流程状态。
     */
    @PostMapping("/transfers")
    public TransferProcess transfer(@Valid @RequestBody DspTransferRequest request) {
        return controlPlaneService.startTransfer(request.agreementId(), request.protocol(), request.dataPlaneId());
    }
}
