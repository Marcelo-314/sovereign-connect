package com.sovereign.connect.core.temporal.port;

import com.sovereign.connect.core.temporal.model.TemporalAct;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TemporalActReadPort {
    Optional<TemporalAct> findById(String habitatId, String temporalActId);

    List<TemporalAct> listActive(String habitatId);

    List<TemporalAct> findDue(String habitatId, Instant now);

    List<TemporalAct> findDue(String habitatId, Instant now, int maxRows);

    List<TemporalAct> findNonTerminalDueBefore(String habitatId, Instant cutoff);

    List<TemporalAct> findNonTerminalDueBefore(String habitatId, Instant cutoff, int maxRows);

    List<TemporalAct> listTerminal(String habitatId);

    List<TemporalAct> listTerminal(String habitatId, int maxResults);

    List<TemporalAct> listMisfired(String habitatId);

    List<TemporalAct> listMisfired(String habitatId, int maxResults);
}
