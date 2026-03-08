package com.example.edc.controlplane.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.edc.controlplane.entity.CpDataPlaneInstanceEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CpDataPlaneInstanceMapper extends BaseMapper<CpDataPlaneInstanceEntity> {
}
