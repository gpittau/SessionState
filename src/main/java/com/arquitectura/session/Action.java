package com.arquitectura.session;

@FunctionalInterface
public interface Action<S, E, C extends WorkflowContext> {
    void execute(S source, S target, E event, C context);
}
