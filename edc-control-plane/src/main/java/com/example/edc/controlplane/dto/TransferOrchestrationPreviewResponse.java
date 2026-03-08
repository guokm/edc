package com.example.edc.controlplane.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class TransferOrchestrationPreviewResponse {
    private String agreementId;
    private String consumerId;
    private String providerId;
    private String requestedDataPlaneId;
    private String selectedDataPlaneId;
    private String selectedDataPlaneProtocol;
    private String selectedBy;
    private boolean readyToTransfer;
    private String message;
    private Map<String, Object> suggestedTransferRequest;
    private List<TransferOrchestrationStepResponse> steps;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    private LocalDateTime generatedAt;

    public String getAgreementId() {
        return agreementId;
    }

    public void setAgreementId(String agreementId) {
        this.agreementId = agreementId;
    }

    public String getConsumerId() {
        return consumerId;
    }

    public void setConsumerId(String consumerId) {
        this.consumerId = consumerId;
    }

    public String getProviderId() {
        return providerId;
    }

    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }

    public String getRequestedDataPlaneId() {
        return requestedDataPlaneId;
    }

    public void setRequestedDataPlaneId(String requestedDataPlaneId) {
        this.requestedDataPlaneId = requestedDataPlaneId;
    }

    public String getSelectedDataPlaneId() {
        return selectedDataPlaneId;
    }

    public void setSelectedDataPlaneId(String selectedDataPlaneId) {
        this.selectedDataPlaneId = selectedDataPlaneId;
    }

    public String getSelectedDataPlaneProtocol() {
        return selectedDataPlaneProtocol;
    }

    public void setSelectedDataPlaneProtocol(String selectedDataPlaneProtocol) {
        this.selectedDataPlaneProtocol = selectedDataPlaneProtocol;
    }

    public String getSelectedBy() {
        return selectedBy;
    }

    public void setSelectedBy(String selectedBy) {
        this.selectedBy = selectedBy;
    }

    public boolean isReadyToTransfer() {
        return readyToTransfer;
    }

    public void setReadyToTransfer(boolean readyToTransfer) {
        this.readyToTransfer = readyToTransfer;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Map<String, Object> getSuggestedTransferRequest() {
        return suggestedTransferRequest;
    }

    public void setSuggestedTransferRequest(Map<String, Object> suggestedTransferRequest) {
        this.suggestedTransferRequest = suggestedTransferRequest;
    }

    public List<TransferOrchestrationStepResponse> getSteps() {
        return steps;
    }

    public void setSteps(List<TransferOrchestrationStepResponse> steps) {
        this.steps = steps;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(LocalDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }
}
