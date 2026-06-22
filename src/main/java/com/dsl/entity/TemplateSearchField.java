package com.dsl.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("template_search_field")
public class TemplateSearchField {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long templateId;

    /** 检索编码 */
    private String fieldCode;

    /** 检索名称 */
    private String fieldName;

    /** 节点路径 */
    private String fieldPath;

    /** 数据类型 */
    private String fieldType;

    /** 是否模糊查询 */
    private Boolean fuzzyFlag;

    /** 是否唯一 */
    private Boolean uniqueFlag;

    /** 描述 */
    private String description;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
