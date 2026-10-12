package com.arquitectura.session;

@FunctionalInterface
public interface Guard<C extends WorkflowContext> {
    boolean evaluate(C context);
}
