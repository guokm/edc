package com.example.edc.controlplane.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;

import java.time.LocalDateTime;

@TableName("edc_cp_data_plane_instance")
public class CpDataPlaneInstanceEntity extends Model<CpDataPlaneInstanceEntity> {
    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    @TableField("public_api_base_url")
    private String publicApiBaseUrl;
    @TableField("control_api_base_url")
    private String controlApiBaseUrl;
    @TableField("protocol")
    private String protocol;
    @TableField("status")
    private String status;
    @TableField("last_seen_at")
    private LocalDateTime lastSeenAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPublicApiBaseUrl() {
        return publicApiBaseUrl;
    }

    public void setPublicApiBaseUrl(String publicApiBaseUrl) {
        this.publicApiBaseUrl = publicApiBaseUrl;
    }

    public String getControlApiBaseUrl() {
        return controlApiBaseUrl;
    }

    public void setControlApiBaseUrl(String controlApiBaseUrl) {
        this.controlApiBaseUrl = controlApiBaseUrl;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(LocalDateTime lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }
}
