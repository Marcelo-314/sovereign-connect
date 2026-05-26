package com.sovereign.connect.core.northbound;

import com.sovereign.connect.core.temporal.application.TemporalActApplicationPort;
import com.sovereign.connect.core.temporal.engine.TemporalEngineHealth;
import com.sovereign.connect.core.temporal.observation.TemporalActObservationPort;
import com.sovereign.connect.core.topology.query.CoreSnapshotQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import java.time.Clock;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:sqlite:target/northbound-context.sqlite"
})
class NorthboundFacadeSpringContextTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void scCoreNorthboundFacadeBeanExistsAndUsesDefaultImplementation() {
        ScCoreNorthboundFacade facade = context.getBean(ScCoreNorthboundFacade.class);

        assertThat(facade).isInstanceOf(DefaultScCoreNorthboundFacade.class);
        assertThat(context.getBeansOfType(ScCoreNorthboundFacade.class)).hasSize(1);
    }

    @Test
    void requiredCollaboratorsResolveFromSpringContext() {
        assertThat(context.getBean(CoreSnapshotQueryService.class)).isNotNull();
        assertThat(context.getBean(TemporalActApplicationPort.class)).isNotNull();
        assertThat(context.getBean(TemporalActObservationPort.class)).isNotNull();
        assertThat(context.getBean(TemporalEngineHealth.class)).isNotNull();
        assertThat(context.getBean(Clock.class)).isNotNull();
    }

    @Test
    void noHttpControllerBeanIsIntroduced() {
        Map<String, Object> controllerBeans = ((ListableBeanFactory) context).getBeansWithAnnotation(
            org.springframework.stereotype.Controller.class
        );

        assertThat(controllerBeans).isEmpty();
    }

    @Test
    void getNorthboundDiagnosticsIsAccessibleThroughFacadeBean() {
        ScCoreNorthboundFacade facade = context.getBean(ScCoreNorthboundFacade.class);

        var response = facade.getNorthboundDiagnostics("habitat-001");

        assertThat(response.status()).isEqualTo(ScNorthboundStatus.OK);
        assertThat(response.payload().migrationReadiness().status()).isEqualTo("UNKNOWN_PENDING_NORMALIZATION");
    }
}
