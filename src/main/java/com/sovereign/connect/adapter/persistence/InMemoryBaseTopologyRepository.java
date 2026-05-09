package com.sovereign.connect.adapter.persistence;

import com.sovereign.connect.core.topology.model.HabitatBaseTopology;
import com.sovereign.connect.core.topology.model.TopologyVersion;
import com.sovereign.connect.core.topology.port.BaseTopologyRepository;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Repository
public class InMemoryBaseTopologyRepository implements BaseTopologyRepository {

    private final ConcurrentMap<String, HabitatBaseTopology> topologiesByHabitatId = new ConcurrentHashMap<>();

    @Override
    public void save(HabitatBaseTopology topology) {
        Objects.requireNonNull(topology, "topology is required");
        topologiesByHabitatId.put(topology.habitatId(), topology);
    }

    @Override
    public Optional<HabitatBaseTopology> findByHabitatId(String habitatId) {
        Objects.requireNonNull(habitatId, "habitatId is required");
        return Optional.ofNullable(topologiesByHabitatId.get(habitatId));
    }

    @Override
    public Optional<TopologyVersion> findCurrentVersion(String habitatId) {
        return findByHabitatId(habitatId).map(HabitatBaseTopology::topologyVersion);
    }
}
