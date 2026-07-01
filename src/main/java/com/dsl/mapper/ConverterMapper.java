package com.dsl.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dsl.entity.Converter;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface ConverterMapper extends BaseMapper<Converter> {
    @Delete("DELETE FROM template_converter WHERE code = #{code}")
    int physicalDeleteByCode(@Param("code") String code);
}
