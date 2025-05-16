package io.github.douira.glsl_transformer.ast.node.type.specifier;

import io.github.douira.glsl_transformer.ast.node.abstract_node.InnerASTNode;
import io.github.douira.glsl_transformer.ast.query.Root;
import io.github.douira.glsl_transformer.ast.traversal.*;

public class PointerSpecifier extends InnerASTNode {
    private final int depth;

    public PointerSpecifier(int depth) {
        this.depth = depth;
    }

    public int getDepth() {
        return depth;
    }

    @Override
    public <R> R accept(ASTVisitor<R> visitor) {
        return visitor.visitPointerSpecifier(this);
    }

    @Override
    public void enterNode(ASTListener listener) {
        listener.enterPointerSpecifier(this);
    }

    @Override
    public void exitNode(ASTListener listener) {
        listener.exitPointerSpecifier(this);
    }

    @Override
    public PointerSpecifier clone() {
        return new PointerSpecifier(depth);
    }

    @Override
    public PointerSpecifier cloneInto(Root root) {
        return (PointerSpecifier) super.cloneInto(root);
    }
}