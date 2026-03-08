package com.example.edc.issuer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;

import java.time.LocalDateTime;

@TableName("edc_is_issuance")
public class IsIssuanceEntity extends Model<IsIssuanceEntity> {
    @TableId(value = "issuance_id", type = IdType.INPUT)
    private String issuanceId;
    @TableField("credential_id")
    private String credentialId;
    @TableField("type")
    private String type;
    @TableField("issuer")
    private String issuer;
    @TableField("claims_json")
    private String claimsJson;
    @TableField("issued_at")
    private LocalDateTime issuedAt;
    @TableField("expires_at")
    private LocalDateTime expiresAt;
    @TableField("status")
    private String status;
    @TableField("participant_id")
    private String participantId;

    public String getIssuanceId() {
        return issuanceId;
    }

    public void setIssuanceId(String issuanceId) {
        this.issuanceId = issuanceId;
    }

    public String getCredentialId() {
        return credentialId;
    }

    public void setCredentialId(String credentialId) {
        this.credentialId = credentialId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getClaimsJson() {
        return claimsJson;
    }

    public void setClaimsJson(String claimsJson) {
        this.claimsJson = claimsJson;
    }

    public LocalDateTime getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(LocalDateTime issuedAt) {
        this.issuedAt = issuedAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getParticipantId() {
        return participantId;
    }

    public void setParticipantId(String participantId) {
        this.participantId = participantId;
    }
}
