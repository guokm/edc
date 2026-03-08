package com.example.edc.operator.service;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class OperatorSchemaService {
    private final JdbcTemplate jdbcTemplate;
    private final OperatorService operatorService;

    public OperatorSchemaService(JdbcTemplate jdbcTemplate, OperatorService operatorService) {
        this.jdbcTemplate = jdbcTemplate;
        this.operatorService = operatorService;
    }

    /**
     * 初始化运营治理与计费相关表结构，并补齐默认计费计划与默认会员。
     */
    @PostConstruct
    public void initialize() {
        jdbcTemplate.execute("""
                create table if not exists edc_op_membership (
                  id varchar(128) primary key,
                  participant_id varchar(128) not null,
                  level varchar(64) not null,
                  valid_from timestamp not null,
                  valid_to timestamp,
                  status varchar(32) not null
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists edc_op_policy (
                  id varchar(128) primary key,
                  type varchar(64) not null,
                  rules_json text not null,
                  created_at timestamp not null
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists edc_op_audit_event (
                  id varchar(128) primary key,
                  event_type varchar(128) not null,
                  actor_id varchar(128) not null,
                  payload_json text,
                  signature varchar(256),
                  created_at timestamp not null
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists edc_op_billing_record (
                  id varchar(128) primary key,
                  agreement_id varchar(128) not null,
                  pricing_model varchar(64) not null,
                  amount decimal(18,4) not null,
                  currency varchar(16) not null,
                  period_start timestamp,
                  period_end timestamp,
                  created_at timestamp not null
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists edc_op_billing_plan (
                  id varchar(128) primary key,
                  participant_id varchar(128) not null,
                  service_code varchar(128) not null,
                  quota_limit int not null,
                  unit_price decimal(18,4) not null,
                  status varchar(32) not null,
                  updated_at timestamp not null
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists edc_op_usage_counter (
                  id varchar(128) primary key,
                  participant_id varchar(128) not null,
                  service_code varchar(128) not null,
                  period_month varchar(6) not null,
                  used_count int not null,
                  quota_limit int not null,
                  unit_price decimal(18,4) not null,
                  last_check_at timestamp not null
                )
                """);

        operatorService.ensureDefaultBillingPlans();
        operatorService.ensureDefaultMemberships();
    }
}
