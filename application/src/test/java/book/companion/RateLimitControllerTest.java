package book.companion;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import book.companion.cache.RedisExamples;
import book.companion.security.SecurityConfig;
import book.companion.web.RateLimitController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RateLimitController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class RateLimitControllerTest {
  @Autowired MockMvc mvc;
  @MockitoBean RedisExamples redis;
  @MockitoBean JwtDecoder decoder;

  org.springframework.test.web.servlet.request.RequestPostProcessor reader() {
    return jwt().authorities(new SimpleGrantedAuthority("SCOPE_products:read"));
  }

  @Test
  void underLimitIsAllowed() throws Exception {
    when(redis.allow(anyString(), anyString(), anyInt(), any())).thenReturn(true);
    mvc.perform(post("/api/v1/limits/check").with(reader())).andExpect(status().isOk());
  }

  @Test
  void overLimitHasRetryContract() throws Exception {
    when(redis.allow(anyString(), anyString(), anyInt(), any())).thenReturn(false);
    mvc.perform(post("/api/v1/limits/check").with(reader()))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string("Retry-After", "1"))
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
  }

  @Test
  void unavailableRedisFailsClosed() throws Exception {
    when(redis.allow(anyString(), anyString(), anyInt(), any()))
        .thenThrow(
            new org.springframework.data.redis.RedisConnectionFailureException(
                "internal sentinel"));
    mvc.perform(post("/api/v1/limits/check").with(reader()))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.detail").value("Data service unavailable"));
  }
}
