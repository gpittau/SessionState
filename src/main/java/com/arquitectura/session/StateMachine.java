package com.arquitectura.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class StateMachine<S, E, C extends WorkflowContext> {
    private final Map<S, Map<E, List<Transition<S, E, C>>>> transitionTable;

    private StateMachine(Map<S, Map<E, List<Transition<S, E, C>>>> transitionTable) {
        this.transitionTable = transitionTable;
    }

    public S fire(S currentState, E event, C context) {
        Map<E, List<Transition<S, E, C>>> availableEvents =
                transitionTable.getOrDefault(currentState, Collections.emptyMap());
        List<Transition<S, E, C>> possibleTransitions =
                availableEvents.getOrDefault(event, Collections.emptyList());

        for (Transition<S, E, C> transition : possibleTransitions) {
            if (transition.guard.map(guard -> guard.evaluate(context)).orElse(true)) {
                transition.action.ifPresent(
                        action -> action.execute(currentState, transition.target, event, context));
                return transition.target;
            }
        }
        throw new IllegalStateException(
                "Transición inválida o guards fallidos para [" + currentState + "] + [" + event + "]");
    }

    public static <S, E, C extends WorkflowContext> Builder<S, E, C> builder() {
        return new Builder<>();
    }

    public static class Builder<S, E, C extends WorkflowContext> {
        private final Map<S, Map<E, List<Transition<S, E, C>>>> table = new HashMap<>();

        public TransitionBuilder<S, E, C> from(S source) {
            return new TransitionBuilder<>(this, source);
        }

        private void addTransition(Transition<S, E, C> transition) {
            table.computeIfAbsent(transition.source, ignored -> new HashMap<>())
                    .computeIfAbsent(transition.event, ignored -> new ArrayList<>())
                    .add(transition);
        }

        public StateMachine<S, E, C> build() {
            return new StateMachine<>(table);
        }
    }

    public static class TransitionBuilder<S, E, C extends WorkflowContext> {
        private final Builder<S, E, C> parent;
        private final S source;
        private E event;
        private S target;
        private Guard<C> guard;
        private Action<S, E, C> action;

        private TransitionBuilder(Builder<S, E, C> parent, S source) {
            this.parent = parent;
            this.source = source;
        }

        public TransitionBuilder<S, E, C> on(E event) {
            this.event = event;
            return this;
        }

        public TransitionBuilder<S, E, C> to(S target) {
            this.target = target;
            return this;
        }

        public TransitionBuilder<S, E, C> guard(Guard<C> guard) {
            this.guard = guard;
            return this;
        }

        public TransitionBuilder<S, E, C> action(Action<S, E, C> action) {
            this.action = action;
            return this;
        }

        public Builder<S, E, C> and() {
            Objects.requireNonNull(event, "event");
            Objects.requireNonNull(target, "target");
            parent.addTransition(new Transition<>(source, target, event, guard, action));
            return parent;
        }
    }

    private static class Transition<S, E, C extends WorkflowContext> {
        private final S source;
        private final S target;
        private final E event;
        private final Optional<Guard<C>> guard;
        private final Optional<Action<S, E, C>> action;

        private Transition(S source, S target, E event, Guard<C> guard, Action<S, E, C> action) {
            this.source = source;
            this.target = target;
            this.event = event;
            this.guard = Optional.ofNullable(guard);
            this.action = Optional.ofNullable(action);
        }
    }
}
