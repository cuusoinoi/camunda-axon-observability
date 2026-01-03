package bank.transferapi.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Auth placeholder: requires X-API-Key header.
 * Production: OAuth2/JWT/mTLS.
 */
@Component
public class AuthFilter extends OncePerRequestFilter {

  @Value("${security.apiKeyHeader:X-API-Key}")
  private String header;

  @Value("${security.apiKeyValue:demo-key}")
  private String value;

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return path.startsWith("/actuator") || path.equals("/healthz");
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String provided = request.getHeader(header);
    if (provided == null || !provided.equals(value)) {
      response.setStatus(401);
      response.getWriter().write("Unauthorized");
      return;
    }
    filterChain.doFilter(request, response);
  }
}
