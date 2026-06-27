package com.dsl.engine.model;

/**
 * Wrapper for source parser context nodes that carries parent reference
 * Used for grouping array elements by parent (e.g. telecoms grouped by provider)
 */
public class NodeWithParent {
    private final Object node;
    private final Object parent;
    private final String parentId;  // Unique identifier for parent grouping

    public NodeWithParent(Object node, Object parent, String parentId) {
        this.node = node;
        this.parent = parent;
        this.parentId = parentId;
    }

    public Object getNode() { return node; }
    public Object getParent() { return parent; }
    public String getParentId() { return parentId; }
}
