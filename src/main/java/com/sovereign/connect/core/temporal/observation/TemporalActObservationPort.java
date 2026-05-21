package com.sovereign.connect.core.temporal.observation;

import java.util.List;
import java.util.Optional;

public interface TemporalActObservationPort {
    List<TemporalActObservation> listActive(String habitatId);

    Optional<TemporalActObservation> findById(String habitatId, String temporalActId);

    List<TemporalActObservation> listTerminal(String habitatId, int maxResults);

    List<TemporalActObservation> listMisfired(String habitatId, int maxResults);
}
