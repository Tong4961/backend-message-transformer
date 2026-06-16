package com.dsl.engine.parser;

import com.dsl.engine.model.TreeNode;
import java.util.List;

public interface SourceParser {
    String getFormat();
    List<TreeNode> parseStructure(String data);
    Object extractValue(String data, String expression);
}
