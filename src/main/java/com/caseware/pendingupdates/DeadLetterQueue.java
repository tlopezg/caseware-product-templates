package com.caseware.pendingupdates;

import com.caseware.pendingupdates.model.FailedEvaluation;

public interface DeadLetterQueue {
    void send(FailedEvaluation failed, Throwable cause);
}