package com.example.edc.operator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.edc.operator.entity.OpAuditEventEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OpAuditEventMapper extends BaseMapper<OpAuditEventEntity> {
}
