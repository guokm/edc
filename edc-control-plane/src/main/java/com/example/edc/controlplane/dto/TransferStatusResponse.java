package com.example.edc.controlplane.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;

import java.time.LocalDateTime;

public class TransferStatusResponse {
    private String transferProcessId;
    private String agreementId;
    private String dataPlaneId;
    private String controlState;
    private String dataPlaneState;
    private String edrEndpoint;
    private String dataPlaneEdrEndpoint;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    private LocalDateTime createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    private LocalDateTime updatedAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    private LocalDateTime dataPlaneUpdatedAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    private LocalDateTime dataPlaneEdrExpiresAt;

    public String getTransferProcessId() {
        return transferProcessId;
    }

    public void setTransferProcessId(String transferProcessId) {
        this.transferProcessId = transferProcessId;
    }

    public String getAgreementId() {
        return agreementId;
    }

    public void setAgreementId(String agreementId) {
        this.agreementId = agreementId;
    }

    public String getDataPlaneId() {
        return dataPlaneId;
    }

    public void setDataPlaneId(String dataPlaneId) {
        this.dataPlaneId = dataPlaneId;
    }

    public String getControlState() {
        return controlState;
    }

    public void setControlState(String controlState) {
        this.controlState = controlState;
    }

    public String getDataPlaneState() {
        return dataPlaneState;
    }

    public void setDataPlaneState(String dataPlaneState) {
        this.dataPlaneState = dataPlaneState;
    }

    public String getEdrEndpoint() {
        return edrEndpoint;
    }

    public void setEdrEndpoint(String edrEndpoint) {
        this.edrEndpoint = edrEndpoint;
    }

    public String getDataPlaneEdrEndpoint() {
        return dataPlaneEdrEndpoint;
    }

    public void setDataPlaneEdrEndpoint(String dataPlaneEdrEndpoint) {
        this.dataPlaneEdrEndpoint = dataPlaneEdrEndpoint;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getDataPlaneUpdatedAt() {
        return dataPlaneUpdatedAt;
    }

    public void setDataPlaneUpdatedAt(LocalDateTime dataPlaneUpdatedAt) {
        this.dataPlaneUpdatedAt = dataPlaneUpdatedAt;
    }

    public LocalDateTime getDataPlaneEdrExpiresAt() {
        return dataPlaneEdrExpiresAt;
    }

    public void setDataPlaneEdrExpiresAt(LocalDateTime dataPlaneEdrExpiresAt) {
        this.dataPlaneEdrExpiresAt = dataPlaneEdrExpiresAt;
    }
}
