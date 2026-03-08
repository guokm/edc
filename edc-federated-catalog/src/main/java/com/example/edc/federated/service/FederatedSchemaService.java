package com.example.edc.federated.service;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class FederatedSchemaService {
    private final JdbcTemplate jdbcTemplate;
    private final FederatedCatalogService federatedCatalogService;

    public FederatedSchemaService(JdbcTemplate jdbcTemplate, FederatedCatalogService federatedCatalogService) {
        this.jdbcTemplate = jdbcTemplate;
        this.federatedCatalogService = federatedCatalogService;
    }

    /**
     * 初始化联邦目录相关表结构，并写入种子数据。
     */
    @PostConstruct
    public void initialize() {
        jdbcTemplate.execute("""
                create table if not exists edc_fc_catalog_item (
                  id varchar(128) primary key,
                  dataset_id varchar(128) not null,
                  asset_id varchar(128) not null,
                  asset_name varchar(255) not null,
                  asset_description text,
                  classification varchar(64) not null,
                  owner_id varchar(128) not null,
                  metadata_json text not null,
                  offer_id varchar(128) not null,
                  policy_id varchar(128) not null,
                  provider_id varchar(128) not null,
                  created_at timestamp not null
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists edc_fc_crawl_job (
                  id varchar(128) primary key,
                  participant_id varchar(128) not null,
                  status varchar(32) not null,
                  started_at timestamp not null,
                  finished_at timestamp,
                  item_count int not null
                )
                """);

        federatedCatalogService.ensureSeedData();
    }
}
