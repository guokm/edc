package com.example.edc.controlplane.controller;

import com.example.edc.controlplane.dto.ContractNegotiationRequest;
import com.example.edc.controlplane.dto.ContractNegotiationResponse;
import com.example.edc.controlplane.dto.ContractAgreementResponse;
import com.example.edc.controlplane.service.ControlPlaneService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/contracts")
public class ContractController {
    private final ControlPlaneService controlPlaneService;

    public ContractController(ControlPlaneService controlPlaneService) {
        this.controlPlaneService = controlPlaneService;
    }

    /**
     * 发起合同协商并在 Offer 匹配时生成协议。
     *
     * @param request 协商请求，包含资产 ID、消费者 ID、Offer ID。
     * @return 协商结果，包含协商 ID 与协议 ID。
     */
    @PostMapping("/negotiations")
    public ContractNegotiationResponse negotiate(@Valid @RequestBody ContractNegotiationRequest request) {
        return controlPlaneService.negotiateContract(request);
    }

    /**
     * 查询协商列表。
     *
     * @return 协商记录集合。
     */
    @GetMapping("/negotiations")
    public List<ContractNegotiationResponse> listNegotiations() {
        return controlPlaneService.listNegotiations();
    }

    /**
     * 查询协议列表。
     *
     * @return 协议记录集合。
     */
    @GetMapping("/agreements")
    public List<ContractAgreementResponse> listAgreements() {
        return controlPlaneService.listAgreements();
    }
}
