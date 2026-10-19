package com.fleetpilot.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void keeps_the_incoming_id_and_exposes_it_to_logs_during_the_request() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "3f2b8c1e9a7d4e60b1c2d3e4f5a6b7c8");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seenInMdc = new AtomicReference<>();

        filter.doFilter(request, response, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                seenInMdc.set(MDC.get(CorrelationIdFilter.MDC_KEY));
            }
        });

        assertThat(seenInMdc.get()).isEqualTo("3f2b8c1e9a7d4e60b1c2d3e4f5a6b7c8");
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("3f2b8c1e9a7d4e60b1c2d3e4f5a6b7c8");
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).as("MDC is cleaned after the request").isNull();
    }

    @Test
    void generates_an_id_when_none_is_sent() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest(), response, new MockFilterChain());

        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).matches("[0-9a-f-]{36}");
    }

    @Test
    void replaces_ids_that_could_inject_content_into_logs() {
        assertThat(CorrelationIdFilter.resolve("abc\nFAKE LOG LINE")).isNotEqualTo("abc\nFAKE LOG LINE").hasSize(36);
        assertThat(CorrelationIdFilter.resolve("short")).hasSize(36);
        assertThat(CorrelationIdFilter.resolve("a".repeat(65))).hasSize(36);
    }
}
