-- EDC 商业数据空间 MySQL 初始化脚本
-- 规则：任何表结构变更必须同步修改本文件与对应 SchemaService。

CREATE DATABASE IF NOT EXISTS edc DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE edc;

-- 控制面：资产表
CREATE TABLE IF NOT EXISTS edc_cp_asset (
  id VARCHAR(128) PRIMARY KEY COMMENT '资产ID',
  name VARCHAR(255) NOT NULL COMMENT '资产名称',
  description TEXT COMMENT '资产描述',
  classification VARCHAR(64) NOT NULL COMMENT '数据分级',
  owner_id VARCHAR(128) NOT NULL COMMENT '资产所有者ID',
  metadata_json TEXT NOT NULL COMMENT '资产元数据(JSON)',
  created_at TIMESTAMP NOT NULL COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='控制面资产表';

-- 控制面：合同要约表
CREATE TABLE IF NOT EXISTS edc_cp_contract_offer (
  id VARCHAR(128) PRIMARY KEY COMMENT '要约ID',
  asset_id VARCHAR(128) NOT NULL COMMENT '资产ID',
  policy_id VARCHAR(128) NOT NULL COMMENT '策略ID',
  provider_id VARCHAR(128) NOT NULL COMMENT '提供方ID',
  created_at TIMESTAMP NOT NULL COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='控制面合同要约表';

-- 控制面：合同协商表
CREATE TABLE IF NOT EXISTS edc_cp_contract_negotiation (
  id VARCHAR(128) PRIMARY KEY COMMENT '协商ID',
  asset_id VARCHAR(128) NOT NULL COMMENT '资产ID',
  consumer_id VARCHAR(128) NOT NULL COMMENT '消费方ID',
  offer_id VARCHAR(128) COMMENT '要约ID',
  policy_id VARCHAR(128) NOT NULL COMMENT '策略ID',
  state VARCHAR(64) NOT NULL COMMENT '协商状态',
  created_at TIMESTAMP NOT NULL COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='控制面合同协商表';

-- 控制面：合同协议表
CREATE TABLE IF NOT EXISTS edc_cp_contract_agreement (
  id VARCHAR(128) PRIMARY KEY COMMENT '协议ID',
  negotiation_id VARCHAR(128) NOT NULL COMMENT '协商ID',
  asset_id VARCHAR(128) NOT NULL COMMENT '资产ID',
  offer_id VARCHAR(128) COMMENT '要约ID',
  consumer_id VARCHAR(128) NOT NULL COMMENT '消费方ID',
  provider_id VARCHAR(128) NOT NULL COMMENT '提供方ID',
  valid_from TIMESTAMP NOT NULL COMMENT '生效时间',
  valid_to TIMESTAMP NOT NULL COMMENT '失效时间',
  status VARCHAR(64) NOT NULL COMMENT '协议状态',
  created_at TIMESTAMP NOT NULL COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='控制面合同协议表';

-- 控制面：传输流程表
CREATE TABLE IF NOT EXISTS edc_cp_transfer_process (
  id VARCHAR(128) PRIMARY KEY COMMENT '传输流程ID',
  agreement_id VARCHAR(128) NOT NULL COMMENT '协议ID',
  protocol VARCHAR(64) NOT NULL COMMENT '传输协议',
  data_plane_id VARCHAR(128) NOT NULL COMMENT '数据面ID',
  state VARCHAR(64) NOT NULL COMMENT '流程状态',
  edr_endpoint TEXT COMMENT 'EDR访问地址',
  edr_auth_token TEXT COMMENT 'EDR认证令牌',
  created_at TIMESTAMP NOT NULL COMMENT '创建时间',
  updated_at TIMESTAMP NOT NULL COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='控制面传输流程表';

-- 控制面：数据面实例注册表
CREATE TABLE IF NOT EXISTS edc_cp_data_plane_instance (
  id VARCHAR(128) PRIMARY KEY COMMENT '数据面实例ID',
  public_api_base_url TEXT NOT NULL COMMENT '数据面公开API地址',
  control_api_base_url TEXT NOT NULL COMMENT '数据面控制API地址',
  protocol VARCHAR(64) NOT NULL COMMENT '支持协议',
  status VARCHAR(64) NOT NULL COMMENT '实例状态',
  last_seen_at TIMESTAMP NOT NULL COMMENT '最后心跳时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='控制面数据面实例注册表';

-- 数据面：传输流程表
CREATE TABLE IF NOT EXISTS edc_dp_transfer_process (
  id VARCHAR(128) PRIMARY KEY COMMENT '传输流程ID',
  data_plane_id VARCHAR(128) NOT NULL COMMENT '数据面ID',
  state VARCHAR(64) NOT NULL COMMENT '流程状态',
  started_at TIMESTAMP NOT NULL COMMENT '启动时间',
  updated_at TIMESTAMP NOT NULL COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据面传输流程表';

-- 数据面：EDR表
CREATE TABLE IF NOT EXISTS edc_dp_edr (
  transfer_process_id VARCHAR(128) PRIMARY KEY COMMENT '传输流程ID',
  endpoint TEXT NOT NULL COMMENT 'EDR访问地址',
  auth_key VARCHAR(64) NOT NULL COMMENT '认证头名称',
  auth_token TEXT NOT NULL COMMENT '认证令牌',
  expires_at TIMESTAMP NOT NULL COMMENT '过期时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据面EDR表';

-- 身份中心：凭证表
CREATE TABLE IF NOT EXISTS edc_ih_credential (
  id VARCHAR(128) PRIMARY KEY COMMENT '凭证ID',
  type VARCHAR(128) NOT NULL COMMENT '凭证类型',
  issuer VARCHAR(128) NOT NULL COMMENT '签发方',
  claims_json TEXT NOT NULL COMMENT '凭证声明(JSON)',
  issued_at TIMESTAMP NOT NULL COMMENT '签发时间',
  expires_at TIMESTAMP COMMENT '过期时间',
  participant_id VARCHAR(128) NOT NULL COMMENT '参与方ID'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='身份中心凭证表';

-- 身份中心：展示对象表
CREATE TABLE IF NOT EXISTS edc_ih_presentation (
  id VARCHAR(128) PRIMARY KEY COMMENT '展示对象ID',
  credential_id VARCHAR(128) NOT NULL COMMENT '凭证ID',
  holder_did VARCHAR(255) NOT NULL COMMENT '持有者DID',
  audience VARCHAR(255) COMMENT '受众',
  source VARCHAR(32) NOT NULL COMMENT '来源(IDENTITY/DCP)',
  verified INT NOT NULL COMMENT '是否已验证(0/1)',
  created_at TIMESTAMP NOT NULL COMMENT '创建时间',
  verified_at TIMESTAMP COMMENT '验证时间',
  participant_id VARCHAR(128) NOT NULL COMMENT '参与方ID'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='身份中心展示对象表';

-- 发行服务：签发表
CREATE TABLE IF NOT EXISTS edc_is_issuance (
  issuance_id VARCHAR(128) PRIMARY KEY COMMENT '签发单ID',
  credential_id VARCHAR(128) NOT NULL COMMENT '凭证ID',
  type VARCHAR(128) NOT NULL COMMENT '凭证类型',
  issuer VARCHAR(128) NOT NULL COMMENT '签发方',
  claims_json TEXT NOT NULL COMMENT '凭证声明(JSON)',
  issued_at TIMESTAMP NOT NULL COMMENT '签发时间',
  expires_at TIMESTAMP COMMENT '过期时间',
  status VARCHAR(32) NOT NULL COMMENT '签发状态',
  participant_id VARCHAR(128) NOT NULL COMMENT '参与方ID'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发行服务签发表';

-- 联邦目录：目录项表
CREATE TABLE IF NOT EXISTS edc_fc_catalog_item (
  id VARCHAR(128) PRIMARY KEY COMMENT '目录项ID',
  dataset_id VARCHAR(128) NOT NULL COMMENT '数据集ID',
  asset_id VARCHAR(128) NOT NULL COMMENT '资产ID',
  asset_name VARCHAR(255) NOT NULL COMMENT '资产名称',
  asset_description TEXT COMMENT '资产描述',
  classification VARCHAR(64) NOT NULL COMMENT '数据分级',
  owner_id VARCHAR(128) NOT NULL COMMENT '所有者ID',
  metadata_json TEXT NOT NULL COMMENT '资产元数据(JSON)',
  offer_id VARCHAR(128) NOT NULL COMMENT '要约ID',
  policy_id VARCHAR(128) NOT NULL COMMENT '策略ID',
  provider_id VARCHAR(128) NOT NULL COMMENT '提供方ID',
  created_at TIMESTAMP NOT NULL COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='联邦目录目录项表';

-- 联邦目录：爬取任务表
CREATE TABLE IF NOT EXISTS edc_fc_crawl_job (
  id VARCHAR(128) PRIMARY KEY COMMENT '爬取任务ID',
  participant_id VARCHAR(128) NOT NULL COMMENT '参与方ID',
  status VARCHAR(32) NOT NULL COMMENT '任务状态',
  started_at TIMESTAMP NOT NULL COMMENT '开始时间',
  finished_at TIMESTAMP COMMENT '结束时间',
  item_count INT NOT NULL COMMENT '本次新增目录项数量'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='联邦目录爬取任务表';

-- 运营服务：企业组织表
CREATE TABLE IF NOT EXISTS edc_op_organization (
  id VARCHAR(128) PRIMARY KEY COMMENT '组织ID',
  name VARCHAR(255) NOT NULL COMMENT '企业/平台组织名称',
  credit_code VARCHAR(128) COMMENT '统一社会信用代码或内部组织编码',
  contact_name VARCHAR(128) COMMENT '联系人姓名',
  contact_phone VARCHAR(64) COMMENT '联系人电话',
  contact_email VARCHAR(128) COMMENT '联系人邮箱',
  status VARCHAR(32) NOT NULL COMMENT '组织状态(ACTIVE/SUSPENDED/EXITED)',
  created_at TIMESTAMP NOT NULL COMMENT '创建时间',
  updated_at TIMESTAMP NOT NULL COMMENT '更新时间',
  UNIQUE KEY uk_edc_op_org_credit_code (credit_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运营服务企业组织表';

-- 运营服务：参与方表
CREATE TABLE IF NOT EXISTS edc_op_participant (
  id VARCHAR(128) PRIMARY KEY COMMENT '运营侧参与方记录ID',
  participant_id VARCHAR(128) NOT NULL COMMENT '数据空间参与方ID，对应协商/目录/计费中的 participantId',
  organization_id VARCHAR(128) NOT NULL COMMENT '所属组织ID',
  display_name VARCHAR(255) NOT NULL COMMENT '参与方展示名称',
  role_type VARCHAR(64) NOT NULL COMMENT '参与方类型(PROVIDER/CONSUMER/OPERATOR)',
  status VARCHAR(32) NOT NULL COMMENT '参与方状态(ACTIVE/SUSPENDED/EXITED)',
  created_at TIMESTAMP NOT NULL COMMENT '创建时间',
  updated_at TIMESTAMP NOT NULL COMMENT '更新时间',
  UNIQUE KEY uk_edc_op_participant_id (participant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运营服务参与方表';

-- 运营服务：用户账号表
CREATE TABLE IF NOT EXISTS edc_op_user_account (
  id VARCHAR(128) PRIMARY KEY COMMENT '用户账号ID',
  username VARCHAR(128) NOT NULL COMMENT '登录用户名',
  display_name VARCHAR(128) NOT NULL COMMENT '用户展示名称',
  organization_id VARCHAR(128) NOT NULL COMMENT '所属组织ID',
  participant_id VARCHAR(128) NOT NULL COMMENT '绑定参与方ID',
  role_code VARCHAR(64) NOT NULL COMMENT '角色编码(PLATFORM_ADMIN/PROVIDER_ADMIN/CONSUMER_ADMIN/AUDITOR等)',
  password_hash VARCHAR(512) NOT NULL COMMENT 'PBKDF2密码摘要，不存储明文密码',
  status VARCHAR(32) NOT NULL COMMENT '账号状态(ACTIVE/DISABLED)',
  created_at TIMESTAMP NOT NULL COMMENT '创建时间',
  updated_at TIMESTAMP NOT NULL COMMENT '更新时间',
  UNIQUE KEY uk_edc_op_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运营服务用户账号表';

-- 运营服务：登录会话表
CREATE TABLE IF NOT EXISTS edc_op_login_session (
  token VARCHAR(256) PRIMARY KEY COMMENT '运营登录令牌',
  user_id VARCHAR(128) NOT NULL COMMENT '用户账号ID',
  expires_at TIMESTAMP NOT NULL COMMENT '令牌过期时间',
  created_at TIMESTAMP NOT NULL COMMENT '创建时间',
  last_seen_at TIMESTAMP NOT NULL COMMENT '最后访问时间',
  status VARCHAR(32) NOT NULL COMMENT '会话状态(ACTIVE/REVOKED/EXPIRED)'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运营服务登录会话表';

-- 运营服务：会员表
CREATE TABLE IF NOT EXISTS edc_op_membership (
  id VARCHAR(128) PRIMARY KEY COMMENT '会员ID',
  participant_id VARCHAR(128) NOT NULL COMMENT '参与方ID',
  level VARCHAR(64) NOT NULL COMMENT '会员等级',
  valid_from TIMESTAMP NOT NULL COMMENT '生效时间',
  valid_to TIMESTAMP COMMENT '失效时间',
  status VARCHAR(32) NOT NULL COMMENT '会员状态'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运营服务会员表';

-- 运营服务：策略表
CREATE TABLE IF NOT EXISTS edc_op_policy (
  id VARCHAR(128) PRIMARY KEY COMMENT '策略ID',
  type VARCHAR(64) NOT NULL COMMENT '策略类型',
  rules_json TEXT NOT NULL COMMENT '策略规则(JSON)',
  created_at TIMESTAMP NOT NULL COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运营服务策略表';

-- 运营服务：审计事件表
CREATE TABLE IF NOT EXISTS edc_op_audit_event (
  id VARCHAR(128) PRIMARY KEY COMMENT '审计事件ID',
  event_type VARCHAR(128) NOT NULL COMMENT '事件类型',
  actor_id VARCHAR(128) NOT NULL COMMENT '操作者ID',
  payload_json TEXT COMMENT '事件载荷(JSON)',
  signature VARCHAR(256) COMMENT '签名',
  created_at TIMESTAMP NOT NULL COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运营服务审计事件表';

-- 运营服务：账单记录表
CREATE TABLE IF NOT EXISTS edc_op_billing_record (
  id VARCHAR(128) PRIMARY KEY COMMENT '账单ID',
  agreement_id VARCHAR(128) NOT NULL COMMENT '协议ID',
  pricing_model VARCHAR(64) NOT NULL COMMENT '计费模式',
  amount DECIMAL(18,4) NOT NULL COMMENT '金额',
  currency VARCHAR(16) NOT NULL COMMENT '币种',
  period_start TIMESTAMP COMMENT '计费开始时间',
  period_end TIMESTAMP COMMENT '计费结束时间',
  created_at TIMESTAMP NOT NULL COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运营服务账单记录表';

-- 运营服务：计费方案表（按服务编码定义额度）
CREATE TABLE IF NOT EXISTS edc_op_billing_plan (
  id VARCHAR(128) PRIMARY KEY COMMENT '计费方案ID',
  participant_id VARCHAR(128) NOT NULL COMMENT '参与方ID',
  service_code VARCHAR(128) NOT NULL COMMENT '服务编码',
  quota_limit INT NOT NULL COMMENT '周期可用次数上限',
  unit_price DECIMAL(18,4) NOT NULL COMMENT '单次价格',
  status VARCHAR(32) NOT NULL COMMENT '方案状态',
  updated_at TIMESTAMP NOT NULL COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运营服务计费方案表';

-- 运营服务：使用计数表（按月统计）
CREATE TABLE IF NOT EXISTS edc_op_usage_counter (
  id VARCHAR(128) PRIMARY KEY COMMENT '计数记录ID',
  participant_id VARCHAR(128) NOT NULL COMMENT '参与方ID',
  service_code VARCHAR(128) NOT NULL COMMENT '服务编码',
  period_month VARCHAR(6) NOT NULL COMMENT '统计月份(yyyyMM)',
  used_count INT NOT NULL COMMENT '已使用次数',
  quota_limit INT NOT NULL COMMENT '次数上限快照',
  unit_price DECIMAL(18,4) NOT NULL COMMENT '单价快照',
  last_check_at TIMESTAMP NOT NULL COMMENT '最后校验时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运营服务使用计数表';
