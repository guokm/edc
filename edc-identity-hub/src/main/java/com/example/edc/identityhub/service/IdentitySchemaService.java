package com.example.edc.identityhub.service;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class IdentitySchemaService {
    private final JdbcTemplate jdbcTemplate;

    public IdentitySchemaService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 初始化身份中心持久化表。
     */
    @PostConstruct
    public void initialize() {
        jdbcTemplate.execute("""
                create table if not exists edc_ih_credential (
                  id varchar(128) primary key,
                  type varchar(128) not null,
                  issuer varchar(128) not null,
                  claims_json text not null,
                  issued_at timestamp not null,
                  expires_at timestamp,
                  participant_id varchar(128) not null
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists edc_ih_presentation (
                  id varchar(128) primary key,
                  credential_id varchar(128) not null,
                  holder_did varchar(255) not null,
                  audience varchar(255),
                  source varchar(32) not null,
                  verified int not null,
                  created_at timestamp not null,
                  verified_at timestamp,
                  participant_id varchar(128) not null
                )
                """);
    }
}
