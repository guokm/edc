package com.example.edc.controlplane.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;

import java.time.LocalDateTime;

@TableName("edc_cp_transfer_process")
public class CpTransferProcessEntity extends Model<CpTransferProcessEntity> {
    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    @TableField("agreement_id")
    private String agreementId;
    @TableField("protocol")
    private String protocol;
    @TableField("data_plane_id")
    private String dataPlaneId;
    @TableField("state")
    private String state;
    @TableField("edr_endpoint")
    private String edrEndpoint;
    @TableField("edr_auth_token")
    private String edrAuthToken;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAgreementId() {
        return agreementId;
    }

    public void setAgreementId(String agreementId) {
        this.agreementId = agreementId;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getDataPlaneId() {
        return dataPlaneId;
    }

    public void setDataPlaneId(String dataPlaneId) {
        this.dataPlaneId = dataPlaneId;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getEdrEndpoint() {
        return edrEndpoint;
    }

    public void setEdrEndpoint(String edrEndpoint) {
        this.edrEndpoint = edrEndpoint;
    }

    public String getEdrAuthToken() {
        return edrAuthToken;
    }

    public void setEdrAuthToken(String edrAuthToken) {
        this.edrAuthToken = edrAuthToken;
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
}
