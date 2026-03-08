package com.example.edc.dataplane.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

@Service
public class DataPlaneSchemaService {
    private final JdbcTemplate jdbcTemplate;

    public DataPlaneSchemaService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 初始化数据面持久化表结构。
     * 表结构定义需要与 docs/sql/mysql-schema.sql 保持一致。
     */
    @PostConstruct
    public void initialize() {
        jdbcTemplate.execute("""
                create table if not exists edc_dp_transfer_process (
                  id varchar(128) primary key,
                  data_plane_id varchar(128) not null,
                  state varchar(64) not null,
                  started_at timestamp not null,
                  updated_at timestamp not null
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists edc_dp_edr (
                  transfer_process_id varchar(128) primary key,
                  endpoint text not null,
                  auth_key varchar(64) not null,
                  auth_token text not null,
                  expires_at timestamp not null
                )
                """);
    }
}
