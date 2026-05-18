package com.sovereign.connect.core.temporal.port;

import com.sovereign.connect.core.temporal.model.TemporalAct;

import java.time.Instant;

public interface TemporalActWritePort {
    void insertCreated(TemporalAct act);

    int cancelIfNonTerminal(String habitatId, String temporalActId, Instant now);

    int markFiredIfDueAndNonTerminal(String habitatId, String temporalActId, Instant now);

    int markMisfiredIfDueAndNonTerminal(String habitatId, String temporalActId, Instant cutoff, Instant now);
}
