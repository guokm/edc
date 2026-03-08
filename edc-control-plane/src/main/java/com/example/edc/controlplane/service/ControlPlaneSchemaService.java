package com.example.edc.controlplane.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

@Service
public class ControlPlaneSchemaService {
    private final JdbcTemplate jdbcTemplate;
    private final ControlPlaneService controlPlaneService;

    public ControlPlaneSchemaService(JdbcTemplate jdbcTemplate, ControlPlaneService controlPlaneService) {
        this.jdbcTemplate = jdbcTemplate;
        this.controlPlaneService = controlPlaneService;
    }

    /**
     * 初始化控制面相关数据表并写入基础种子数据。
     * 表结构定义需要与 docs/sql/mysql-schema.sql 保持一致。
     */
    @PostConstruct
    public void initialize() {
        jdbcTemplate.execute("""
                create table if not exists edc_cp_asset (
                  id varchar(128) primary key,
                  name varchar(255) not null,
                  description text,
                  classification varchar(64) not null,
                  owner_id varchar(128) not null,
                  metadata_json text not null,
                  created_at timestamp not null
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists edc_cp_contract_offer (
                  id varchar(128) primary key,
                  asset_id varchar(128) not null,
                  policy_id varchar(128) not null,
                  provider_id varchar(128) not null,
                  created_at timestamp not null
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists edc_cp_contract_negotiation (
                  id varchar(128) primary key,
                  asset_id varchar(128) not null,
                  consumer_id varchar(128) not null,
                  offer_id varchar(128),
                  policy_id varchar(128) not null,
                  state varchar(64) not null,
                  created_at timestamp not null
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists edc_cp_contract_agreement (
                  id varchar(128) primary key,
                  negotiation_id varchar(128) not null,
                  asset_id varchar(128) not null,
                  offer_id varchar(128),
                  consumer_id varchar(128) not null,
                  provider_id varchar(128) not null,
                  valid_from timestamp not null,
                  valid_to timestamp not null,
                  status varchar(64) not null,
                  created_at timestamp not null
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists edc_cp_transfer_process (
                  id varchar(128) primary key,
                  agreement_id varchar(128) not null,
                  protocol varchar(64) not null,
                  data_plane_id varchar(128) not null,
                  state varchar(64) not null,
                  edr_endpoint text,
                  edr_auth_token text,
                  created_at timestamp not null,
                  updated_at timestamp not null
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists edc_cp_data_plane_instance (
                  id varchar(128) primary key,
                  public_api_base_url text not null,
                  control_api_base_url text not null,
                  protocol varchar(64) not null,
                  status varchar(64) not null,
                  last_seen_at timestamp not null
                )
                """);
        ensureColumnExists("edc_cp_contract_negotiation", "offer_id", "varchar(128)");
        ensureColumnExists("edc_cp_contract_agreement", "offer_id", "varchar(128)");

        controlPlaneService.ensureSeedData();
    }

    private void ensureColumnExists(String tableName, String columnName, String columnDefinition) {
        var count = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from information_schema.columns
                        where table_schema = database()
                          and table_name = ?
                          and column_name = ?
                        """,
                Long.class,
                tableName,
                columnName
        );
        if (count == null || count == 0L) {
            jdbcTemplate.execute("alter table " + tableName + " add column " + columnName + " " + columnDefinition);
        }
    }
}
