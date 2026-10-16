package com.zkdoc.api.demo;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Public REST controller exposing pre-minted synthetic demonstration accounts and Bearer tokens
 * when demo mode is explicitly enabled.
 * <p>
 * Strictly excluded and returns 404 in production environments where demo seeding is disabled.
 */
@RestController
@RequestMapping("/api/public/demo-tokens")
@ConditionalOnProperty(name = "zkdoc.demo.seed-enabled", havingValue = "true", matchIfMissing = false)
public class DemoTokensController {

    private final DemoTokenService demoTokenService;
    private final boolean demoEnabled;
    private final Environment environment;

    @Autowired
    public DemoTokensController(
            DemoTokenService demoTokenService,
            Environment environment,
            @Value("${zkdoc.demo.seed-enabled:false}") boolean demoEnabled) {
        this.demoTokenService = demoTokenService;
        this.environment = environment;
        this.demoEnabled = demoEnabled;
    }

    @GetMapping
    public ResponseEntity<DemoAccountsResponse> getDemoTokens() {
        if (!demoEnabled || isProductionEnvironment()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Demo accounts are disabled in this environment");
        }

        List<DemoTokenService.DemoAccountDetails> accounts = demoTokenService.getAllDemoAccounts();
        return ResponseEntity.ok(new DemoAccountsResponse(
                true,
                DemoCryptoKeyConstants.DEMO_JWT_ISSUER,
                DemoCryptoKeyConstants.DEMO_JWT_AUDIENCE,
                DemoCryptoKeyConstants.DEMO_JWT_KEY_ID,
                accounts
        ));
    }

    private boolean isProductionEnvironment() {
        if (environment == null) {
            return false;
        }
        return environment.acceptsProfiles(Profiles.of("prod", "production"));
    }

    public record DemoAccountsResponse(
            boolean demoEnabled,
            String issuer,
            String audience,
            String keyId,
            List<DemoTokenService.DemoAccountDetails> accounts) {}
}
