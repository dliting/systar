package com.systar.monitor.drivers.websocket;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.systar.monitor.asset.type.ProbeType;
import com.systar.monitor.result.ResultDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * WsService.parseAndRoute routing contract: JSON top-level keys route to the
 * monitor registered under that key, numbers arrive as float, non-numbers as-is,
 * and malformed messages neither throw nor disturb registered monitors.
 */
@Timeout(value = 3, unit = TimeUnit.MINUTES)
class WsServiceTest {

    private WsService service;
    private WsProbe probe;

    @BeforeEach
    void setUp() {
        service = new WsService();
        service.setResultDispatcher(new ResultDispatcher());
        probe = new WsProbe();
        probe.init(new ProbeType("ws-probe"), 1, "ws-probe");
        service.registerMonitor("sensor.temp", probe);
    }

    @Nested
    @DisplayName("parseAndRoute - message routing")
    class Routing {

        @Test
        @DisplayName("numeric value routes to registered monitor as float")
        void routesNumberAsFloat() {
            service.parseAndRoute("{\"sensor.temp\": 22.5}");
            assertThat(probe.getValue()).isEqualTo(22.5f);
        }

        @Test
        @DisplayName("string value routes as-is")
        void routesStringAsIs() {
            service.parseAndRoute("{\"sensor.temp\": \"warm\"}");
            assertThat(probe.getValue()).isEqualTo("warm");
        }

        @Test
        @DisplayName("boolean value routes as-is")
        void routesBooleanAsIs() {
            service.parseAndRoute("{\"sensor.temp\": true}");
            assertThat(probe.getValue()).isEqualTo(Boolean.TRUE);
        }

        @Test
        @DisplayName("keys without a registered monitor are ignored")
        void ignoresUnknownKeys() {
            service.parseAndRoute("{\"other\": 1}");
            assertThat(probe.getValue()).isNull();
        }

        @Test
        @DisplayName("malformed JSON neither throws nor updates monitors")
        void malformedJsonIsSwallowed() {
            service.parseAndRoute("{\"sensor.temp\": 22.5}");
            assertThatCode(() -> service.parseAndRoute("{\"sensor.temp\": ")).doesNotThrowAnyException();
            assertThat(probe.getValue()).isEqualTo(22.5f);
        }

        @Test
        @DisplayName("non-object JSON payload is ignored")
        void nonObjectPayloadIsIgnored() {
            assertThatCode(() -> service.parseAndRoute("42")).doesNotThrowAnyException();
            assertThat(probe.getValue()).isNull();
        }

        @Test
        @DisplayName("non-object JSON payload logs a warning naming the payload")
        void nonObjectPayloadLogsWarning() {
            Logger logger = (Logger) LoggerFactory.getLogger(WsService.class);
            ListAppender<ILoggingEvent> appender = new ListAppender<>();
            appender.start();
            logger.addAppender(appender);
            try {
                service.parseAndRoute("42");
            } finally {
                logger.detachAppender(appender);
                appender.stop();
            }
            assertThat(appender.list)
                    .anySatisfy(event -> {
                        assertThat(event.getLevel()).isEqualTo(Level.WARN);
                        assertThat(event.getFormattedMessage()).contains("42");
                    });
        }
    }
}
