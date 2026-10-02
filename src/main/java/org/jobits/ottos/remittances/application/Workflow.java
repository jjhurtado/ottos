package org.jobits.ottos.remittances.application;

import org.jobits.ottos.remittances.domain.RemittanceStatus;
import org.jobits.ottos.remittances.domain.RemittanceStatusRepository;
import org.jobits.ottos.remittances.domain.RemittanceTransition;
import org.jobits.ottos.remittances.domain.RemittanceTransitionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/** Reads the statuses and transitions configured in the database. */
@Component
public class Workflow {

    private final RemittanceStatusRepository statuses;
    private final RemittanceTransitionRepository transitions;

    Workflow(RemittanceStatusRepository statuses, RemittanceTransitionRepository transitions) {
        this.statuses = statuses;
        this.transitions = transitions;
    }

    @Transactional(readOnly = true)
    RemittanceStatus initial() {
        List<RemittanceStatus> initial = statuses.findByInitialTrue();
        if (initial.size() != 1) {
            throw new IllegalStateException("remittance_statuses must have exactly one initial status, found " + initial.size());
        }
        return initial.get(0);
    }

    @Transactional(readOnly = true)
    RemittanceStatus status(String code) {
        return statuses.findById(code)
                .orElseThrow(() -> new IllegalStateException("Unknown remittance status " + code));
    }

    @Transactional(readOnly = true)
    List<String> finalCodes() {
        return statuses.findByFinalStatusTrue().stream().map(RemittanceStatus::getCode).toList();
    }

    @Transactional(readOnly = true)
    Optional<RemittanceTransition> transition(String from, String to) {
        return transitions.findById(new RemittanceTransition.Key(from, to));
    }

    /** The allowed transition from a status whose target matches, preferring the earliest target in the workflow. */
    @Transactional(readOnly = true)
    Optional<RemittanceTransition> firstTransition(String from, Predicate<RemittanceStatus> target) {
        return transitions.findByKeyFromStatus(from).stream()
                .map(t -> new Candidate(t, status(t.getToStatus())))
                .filter(c -> target.test(c.target()))
                .min(Comparator.comparingInt(c -> c.target().getPosition()))
                .map(Candidate::transition);
    }

    @Transactional(readOnly = true)
    public WorkflowView describe() {
        return new WorkflowView(
                statuses.findAllByOrderByPosition().stream()
                        .map(s -> new StatusView(s.getCode(), s.getName(), s.getPosition(), s.isInitial(),
                                s.isRequiresCourier(), s.isFinalStatus()))
                        .toList(),
                transitions.findAll().stream()
                        .map(t -> new TransitionView(t.getFromStatus(), t.getToStatus(), t.getPermissionCode()))
                        .toList());
    }

    private record Candidate(RemittanceTransition transition, RemittanceStatus target) {
    }

    public record WorkflowView(List<StatusView> statuses, List<TransitionView> transitions) {
    }

    public record StatusView(String code, String name, int position, boolean initial, boolean requiresCourier,
                             boolean isFinal) {
    }

    public record TransitionView(String from, String to, String permission) {
    }
}
