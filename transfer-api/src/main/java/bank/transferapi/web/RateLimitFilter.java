package bank.transferapi.web;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory rate limiter per API key.
 * Production: distributed limiter (Redis) and per-client plans.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

  @Value("${rateLimit.capacity:20}")
  private long capacity;

  @Value("${rateLimit.refillTokens:20}")
  private long refillTokens;

  @Value("${rateLimit.refillSeconds:1}")
  private long refillSeconds;

  private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return path.startsWith("/actuator") || path.equals("/healthz");
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String key = request.getHeader("X-API-Key");
    if (key == null) key = "anonymous";

    Bucket bucket = buckets.computeIfAbsent(key, k -> {
      Bandwidth limit = Bandwidth.classic(capacity, Refill.intervally(refillTokens, Duration.ofSeconds(refillSeconds)));
      return Bucket.builder().addLimit(limit).build();
    });

    if (bucket.tryConsume(1)) {
      filterChain.doFilter(request, response);
    } else {
      response.setStatus(429);
      response.getWriter().write("Too Many Requests");
    }
  }
}
