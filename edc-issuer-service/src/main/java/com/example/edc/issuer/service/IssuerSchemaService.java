package com.example.edc.issuer.service;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class IssuerSchemaService {
    private final JdbcTemplate jdbcTemplate;

    public IssuerSchemaService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 初始化发行服务持久化表。
     */
    @PostConstruct
    public void initialize() {
        jdbcTemplate.execute("""
                create table if not exists edc_is_issuance (
                  issuance_id varchar(128) primary key,
                  credential_id varchar(128) not null,
                  type varchar(128) not null,
                  issuer varchar(128) not null,
                  claims_json text not null,
                  issued_at timestamp not null,
                  expires_at timestamp,
                  status varchar(32) not null,
                  participant_id varchar(128) not null
                )
                """);
    }
}
