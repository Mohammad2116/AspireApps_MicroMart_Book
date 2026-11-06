package book.companion;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import book.companion.inventory.ProductService;
import book.companion.security.SecurityConfig;
import book.companion.web.ProductController;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class ProductControllerTest {
  @Autowired MockMvc mvc;
  @MockitoBean ProductService service;
  @MockitoBean JwtDecoder decoder;

  org.springframework.test.web.servlet.request.RequestPostProcessor reader() {
    return jwt()
        .jwt(j -> j.subject("alice"))
        .authorities(new SimpleGrantedAuthority("SCOPE_products:read"));
  }

  org.springframework.test.web.servlet.request.RequestPostProcessor writer() {
    return jwt()
        .jwt(j -> j.subject("alice"))
        .authorities(new SimpleGrantedAuthority("SCOPE_products:write"));
  }

  @Test
  void unauthenticatedIs401() throws Exception {
    mvc.perform(get("/api/products")).andExpect(status().isUnauthorized());
  }

  @Test
  void wrongScopeIs403() throws Exception {
    mvc.perform(post("/api/products").with(reader()).contentType("application/json").content("{}"))
        .andExpect(status().isForbidden());
    verifyNoInteractions(service);
  }

  @Test
  void invalidNameIs400() throws Exception {
    mvc.perform(
            post("/api/products")
                .with(writer())
                .contentType("application/json")
                .content("{\"name\":\" \",\"price\":12,\"stock\":2}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400));
    verifyNoInteractions(service);
  }

  @Test
  void rejectsFractionalCents() throws Exception {
    mvc.perform(
            post("/api/products")
                .with(writer())
                .contentType("application/json")
                .content("{\"name\":\"book\",\"price\":1.001,\"stock\":2}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createdReturnsLocationAndDto() throws Exception {
    var id = UUID.randomUUID();
    when(service.create(eq("legacy#alice"), eq("Book"), any(), eq(2)))
        .thenReturn(new ProductService.View(id, "Book", new BigDecimal("12.50"), 2, 0));
    mvc.perform(
            post("/api/products")
                .with(writer())
                .contentType("application/json")
                .content("{\"name\":\"Book\",\"price\":12.50,\"stock\":2}"))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/products/" + id))
        .andExpect(jsonPath("$.owner").doesNotExist());
  }

  @Test
  void missingIsProblem404() throws Exception {
    var id = UUID.randomUUID();
    when(service.get("legacy#alice", id)).thenThrow(new ProductService.NotFound());
    mvc.perform(get("/api/products/" + id).with(reader()))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
  }

  @Test
  void boundedPagination() throws Exception {
    mvc.perform(get("/api/products?size=101").with(reader())).andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }

  @Test
  void malformedId() throws Exception {
    mvc.perform(get("/api/products/not-a-uuid").with(reader())).andExpect(status().isBadRequest());
  }

  @Test
  void malformedJson() throws Exception {
    mvc.perform(post("/api/products").with(writer()).contentType("application/json").content("{"))
        .andExpect(status().isBadRequest());
  }
}
