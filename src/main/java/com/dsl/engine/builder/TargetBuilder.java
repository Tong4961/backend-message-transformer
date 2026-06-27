package com.dsl.engine.builder;

import java.util.List;
import java.util.Map;

public interface TargetBuilder {
    String getFormat();
    void setRoot(String rootName);
    void setValue(String expression, Object value);
    void initDefaults(String targetStructureJson, String format);
    String build();

    /**
     * 向目标数组追加一个新元素，返回该元素对象(ObjectNode/Element)
     */
    Object addArrayItem(String arrayExpression);

    /**
     * 在指定父元素内创建子数组并添加新元素
     * @param parentItem 父元素对象
     * @param relativePath 相对路径（如 "contacts"）
     * @return 新创建的数组元素对象
     */
    Object addArrayItemInParent(Object parentItem, String relativePath);

    /**
     * 获取目标数组的已有元素列表，用于复用已有数组项
     * @return 已有元素列表，如果数组不存在返回null
     */
    java.util.List<Object> getArrayItems(String arrayExpression);

    /**
     * 在数组元素内设置值（相对路径）
     */
    void setValueInArrayItem(Object arrayItem, String relativePath, Object value);
}
