package com.caseware.pendingupdates;

import com.caseware.pendingupdates.model.ChangeRecord;
import com.caseware.pendingupdates.model.EngagementRef;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class InMemorySink implements PendingUpdateSink {

    private final Set<String> keys = Collections.synchronizedSet(new HashSet<>());

    @Override
    public boolean markPending(EngagementRef engagement, ChangeRecord change, String region) {
        String key = engagement.firmId() + "#" + engagement.engagementId()
                + "#" + change.changeId();
        return keys.add(key);
    }

    public int size() {
        return keys.size();
    }
}