package com.sovereign.connect.adapter.northbound.http;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:sqlite:target/http-context.sqlite"
})
@AutoConfigureMockMvc
class ScNorthboundHttpSpringContextTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoadsWithHttpControllers() {
        assertThat(context.getBean(ScNorthboundTopologyHttpController.class)).isNotNull();
        assertThat(context.getBean(ScNorthboundTemporalHttpController.class)).isNotNull();
    }

    @Test
    void topologyControllerBeanExistsAndIsProperlyTyped() {
        assertThat(context.getBean(ScNorthboundTopologyHttpController.class))
            .isInstanceOf(ScNorthboundTopologyHttpController.class);
    }

    @Test
    void temporalControllerBeanExistsAndIsProperlyTyped() {
        assertThat(context.getBean(ScNorthboundTemporalHttpController.class))
            .isInstanceOf(ScNorthboundTemporalHttpController.class);
    }

    @Test
    void httpControllerBeansAreOnlyInHttpAdapterPackage() {
        Map<String, Object> controllerBeans =
            ((ListableBeanFactory) context).getBeansWithAnnotation(
                org.springframework.stereotype.Controller.class);
        for (Object bean : controllerBeans.values()) {
            if (!bean.getClass().getPackageName().startsWith("com.sovereign.connect")) {
                continue;
            }
            assertThat(bean.getClass().getPackageName())
                .as("Controller bean %s must be in adapter.northbound.http",
                    bean.getClass().getSimpleName())
                .startsWith("com.sovereign.connect.adapter.northbound.http");
        }
    }

    @Test
    void openApiEndpointResolvesToNonEmptyDocument() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        assertThat(body).isNotBlank();
        assertThat(body).contains("\"openapi\"");
    }
}
