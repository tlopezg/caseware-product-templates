package com.caseware.pendingupdates;

import com.caseware.pendingupdates.model.FailedEvaluation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class InMemoryDlq implements DeadLetterQueue {

    private final List<FailedEvaluation> failures =
            Collections.synchronizedList(new ArrayList<>());

    @Override
    public void send(FailedEvaluation failed, Throwable cause) {
        failures.add(failed);
    }

    public int size() {
        return failures.size();
    }

    public List<FailedEvaluation> failures() {
        return new ArrayList<>(failures);
    }
}