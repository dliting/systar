package com.systar.server.controller;

import com.systar.monitor.server.MonitorEngine;
import com.systar.server.security.TokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.TimeUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins the engine web registration end to end: the closed engine's
 * {@code ScheduledTaskController} must be reachable through the real
 * {@code DispatcherServlet} mapping, not merely exist as a bean. The engine
 * split moved the controller behind {@code @Import} without a stereotype, and
 * since Spring 6.1 {@code RequestMappingHandlerMapping.isHandler} only honors
 * {@code @Controller} — a {@code @RequestMapping}-only bean is silently never
 * mapped (the bean shows in {@code /actuator/beans}, the endpoint 404s).
 * Pure unit tests of the controller class cannot see this; only a request
 * through the real mapping can.
 * <p>
 * The endpoint is asserted to be mapped <em>iff</em> the engine is present:
 * 200 in the full form, 404 in the open form (engine absent). Asserting both
 * sides keeps the test meaningful in either build without assumption skips.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Timeout(value = 3, unit = TimeUnit.MINUTES)
@DisplayName("Engine scheduled-task API mapped through real DispatcherServlet iff engine present")
class ScheduledTaskApiMappingTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ApplicationContext ctx;

    @Test
    @DisplayName("GET /api/monitor/scheduled-tasks: 200 with engine, 404 without")
    void scheduledTasksMappedIffEnginePresent() throws Exception {
        boolean enginePresent = ctx.getBeanNamesForType(MonitorEngine.class).length > 0;
        String jwt = tokenService.generateToken(1L, "admin", "*");
        if (enginePresent) {
            mvc.perform(get("/api/monitor/scheduled-tasks")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.code").value(0));
        } else {
            mvc.perform(get("/api/monitor/scheduled-tasks")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt))
               .andExpect(status().isNotFound());
        }
    }
}
