package com.collabflow.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Assigns every request a correlation id - reused from an incoming {@code X-Request-Id} header
 * if the caller (or an upstream reverse proxy) already set one, otherwise a fresh one - and
 * puts it in SLF4J's MDC for the lifetime of the request, so every log line written while
 * handling it (across every module and thread that logs synchronously within the request) can
 * be grep'd together. See {@code application.yml}'s {@code logging.pattern.console}, which
 * includes {@code %X{requestId}} - the actual reason this exists: without it, correlating "all
 * the log lines produced by this one failing request" across a real deployment's log volume
 * would mean guessing by timestamp proximity.
 *
 * <p>Registered as a plain {@link Filter} at {@link Ordered#HIGHEST_PRECEDENCE}, not as part of
 * Spring Security's filter chain, so the id is assigned - and appears in the response header
 * below - even for requests Spring Security itself rejects (a 401 on a bad token should still
 * be traceable back to specific server-side log lines).</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter implements Filter {

    public static final String HEADER_NAME = "X-Request-Id";
    private static final String MDC_KEY = "requestId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String incoming = httpRequest.getHeader(HEADER_NAME);
        String requestId = (incoming != null && !incoming.isBlank()) ? incoming : UUID.randomUUID().toString();

        httpResponse.setHeader(HEADER_NAME, requestId);
        MDC.put(MDC_KEY, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            // Tomcat reuses request-handling threads across requests (and, with virtual
            // threads, a thread pool's carrier threads are reused even more aggressively) - an
            // MDC value left behind here would leak into a completely unrelated later request's
            // logs on whatever thread happens to pick it up next.
            MDC.remove(MDC_KEY);
        }
    }
}
