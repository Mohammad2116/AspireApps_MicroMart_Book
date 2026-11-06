package book.companion.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
  @Bean
  SecurityFilterChain api(HttpSecurity http, com.fasterxml.jackson.databind.ObjectMapper json)
      throws Exception {
    var errors = new ProblemSecurityHandler(json);
    return http.csrf(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers("/actuator/health", "/actuator/health/**")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/products/**",
                        "/api/orders/**",
                        "/api/v1/products/**",
                        "/api/v1/orders/**")
                    .hasAuthority("SCOPE_products:read")
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/products/**",
                        "/api/orders/**",
                        "/api/v1/products/**",
                        "/api/v1/orders/**")
                    .hasAuthority("SCOPE_products:write")
                    .requestMatchers("/actuator/prometheus")
                    .hasAuthority("SCOPE_ops:read")
                    .requestMatchers(
                        org.springframework.http.HttpMethod.POST, "/api/v1/limits/check")
                    .hasAuthority("SCOPE_products:read")
                    .anyRequest()
                    .denyAll())
        .exceptionHandling(e -> e.authenticationEntryPoint(errors).accessDeniedHandler(errors))
        .oauth2ResourceServer(
            o -> o.jwt(j -> {}).authenticationEntryPoint(errors).accessDeniedHandler(errors))
        .build();
  }

  @Bean
  @Profile("!test")
  public JwtDecoder decoder(
      @Value("${app.security.issuer}") String issuer,
      @Value("${app.security.audience}") String audience) {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withIssuerLocation(issuer)
            .jwsAlgorithm(org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256)
            .build();
    decoder.setJwtValidator(validators(issuer, audience));
    return decoder;
  }

  public static OAuth2TokenValidator<Jwt> validators(String issuer, String audience) {
    OAuth2TokenValidator<Jwt> contract =
        jwt ->
            jwt.getAudience() != null
                    && jwt.getAudience().contains(audience)
                    && jwt.getSubject() != null
                    && !jwt.getSubject().isBlank()
                    && jwt.getExpiresAt() != null
                    && jwt.getSubject().length() <= 128
                    && (jwt.getClaim("tenant") == null
                        || (jwt.getClaim("tenant") instanceof String t
                            && !t.isBlank()
                            && t.length() <= 64))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Required claims are missing", null));
    return new DelegatingOAuth2TokenValidator<>(
        JwtValidators.createDefaultWithIssuer(issuer), contract);
  }
}
