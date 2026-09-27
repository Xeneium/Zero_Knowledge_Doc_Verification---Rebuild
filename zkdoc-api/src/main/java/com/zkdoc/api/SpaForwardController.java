package com.zkdoc.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Forwards known root-level client routes to React while leaving /api and static files untouched. */
@Controller
public class SpaForwardController {
    @GetMapping({
            "/",
            "/verify",
            "/credentials",
            "/verification",
            "/predicate-verification",
            "/audit-logs",
            "/administration"
    })
    public String forwardClientRoute() {
        return "forward:/index.html";
    }
}
