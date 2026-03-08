package com.example.edc.federated.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.FORBIDDEN;

@Service
public class FederatedAccessService {
    private final String syncToken;

    public FederatedAccessService(@Value("${edc.security.sync-token:federated-sync-key}") String syncToken) {
        this.syncToken = syncToken;
    }

    /**
     * 校验 Control Plane 发起的内部同步令牌。
     *
     * @param token 请求头 X-Sync-Token。
     */
    public void ensureSyncPermission(String token) {
        if (!StringUtils.hasText(token) || !syncToken.equals(token)) {
            throw new ResponseStatusException(FORBIDDEN, "Invalid X-Sync-Token");
        }
    }
}
