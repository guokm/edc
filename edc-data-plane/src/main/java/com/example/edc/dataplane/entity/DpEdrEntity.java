package com.example.edc.dataplane.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;

import java.time.LocalDateTime;

@TableName("edc_dp_edr")
public class DpEdrEntity extends Model<DpEdrEntity> {
    @TableId(value = "transfer_process_id", type = IdType.INPUT)
    private String transferProcessId;
    @TableField("endpoint")
    private String endpoint;
    @TableField("auth_key")
    private String authKey;
    @TableField("auth_token")
    private String authToken;
    @TableField("expires_at")
    private LocalDateTime expiresAt;

    public String getTransferProcessId() {
        return transferProcessId;
    }

    public void setTransferProcessId(String transferProcessId) {
        this.transferProcessId = transferProcessId;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAuthKey() {
        return authKey;
    }

    public void setAuthKey(String authKey) {
        this.authKey = authKey;
    }

    public String getAuthToken() {
        return authToken;
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
}
