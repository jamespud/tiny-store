package com.github.spud.tinystore.inventory.interfaces.filter;

import com.github.spud.tinystore.interfaces.aspect.LogConstant;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InventoryLoggingFilter Tests")
class InventoryLoggingFilterTest {

    private final InventoryLoggingFilter filter = new InventoryLoggingFilter();

    @Test
    @DisplayName("usesHeaderTraceId_whenPresent_andWritesResponseHeader")
    void usesHeaderTraceId_whenPresent_andWritesResponseHeader() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(LogConstant.HEADER_TRACE_ID, "trace-123");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = (rq, rs) -> { /* no-op */ };

        filter.doFilter(req, res, chain);

        assertThat(res.getHeader(LogConstant.HEADER_TRACE_ID)).isEqualTo("trace-123");
        assertThat(MDC.get(LogConstant.MDC_LOG_ID)).isNull(); // cleared after
    }

    @Test
    @DisplayName("generatesTraceId_whenHeaderAbsent")
    void generatesTraceId_whenHeaderAbsent() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = (rq, rs) -> { /* no-op */ };

        filter.doFilter(req, res, chain);

        String generated = res.getHeader(LogConstant.HEADER_TRACE_ID);
        assertThat(generated).isNotBlank();
        assertThat(generated).isNotEqualTo("trace-123");
        assertThat(MDC.get(LogConstant.MDC_LOG_ID)).isNull(); // cleared
    }

    @Test
    @DisplayName("populatesMdc_duringChain_andClearsAfter")
    void populatesMdc_duringChain_andClearsAfter() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(LogConstant.HEADER_TRACE_ID, "trace-xyz");
        MockHttpServletResponse res = new MockHttpServletResponse();
        String[] seenInChain = new String[1];
        FilterChain chain = (rq, rs) -> seenInChain[0] = MDC.get(LogConstant.MDC_LOG_ID);

        filter.doFilter(req, res, chain);

        assertThat(seenInChain[0]).isEqualTo("trace-xyz"); // MDC populated during chain
        assertThat(MDC.get(LogConstant.MDC_LOG_ID)).isNull(); // cleared after
    }
}
