package com.sovereign.connect.core.topology.port;

import com.sovereign.connect.core.topology.model.EndpointHealth;

public interface EndpointHealthWritePort {

    void saveEndpointHealth(String habitatId, String endpointId, EndpointHealth health);

    static EndpointHealthWritePort noOp() {
        return (habitatId, endpointId, health) -> {
        };
    }
}
