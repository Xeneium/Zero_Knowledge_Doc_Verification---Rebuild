package com.zkdoc.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
final class FrontendUrlLogger {
    private static final Logger LOGGER = LoggerFactory.getLogger(FrontendUrlLogger.class);

    private final Environment environment;

    FrontendUrlLogger(Environment environment) {
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    void logFrontendUrl(ApplicationReadyEvent event) {
        if (!(event.getApplicationContext() instanceof WebServerApplicationContext applicationContext)) {
            return;
        }

        int port = applicationContext.getWebServer().getPort();
        String scheme = environment.getProperty("server.ssl.enabled", Boolean.class, false) ? "https" : "http";
        String contextPath = normalizeContextPath(environment.getProperty("server.servlet.context-path", ""));

        LOGGER.info("Frontend URL: {}://localhost:{}{}/", scheme, port, contextPath);
    }

    private static String normalizeContextPath(String contextPath) {
        if (contextPath == null || contextPath.isBlank() || "/".equals(contextPath)) {
            return "";
        }
        String withLeadingSlash = contextPath.startsWith("/") ? contextPath : "/" + contextPath;
        return withLeadingSlash.endsWith("/")
                ? withLeadingSlash.substring(0, withLeadingSlash.length() - 1)
                : withLeadingSlash;
    }
}
