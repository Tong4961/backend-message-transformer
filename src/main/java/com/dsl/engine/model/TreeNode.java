package com.dsl.engine.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class TreeNode {
    private String name;
    private String path;
    private String nodeType; // ELEMENT, ATTRIBUTE
    private boolean array;
    private String dataType;
    private String sampleValue;
    private List<TreeNode> children = new ArrayList<>();

    public TreeNode() {}

    public TreeNode(String name, String path, String nodeType) {
        this.name = name;
        this.path = path;
        this.nodeType = nodeType;
    }

    public void addChild(TreeNode child) {
        this.children.add(child);
    }
}
