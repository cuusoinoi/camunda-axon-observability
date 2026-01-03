package bank.transferapi;

import bank.transferapi.dto.TransferResponse;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Demo in-memory idempotency store.
 * Production: Redis/DB with TTL + unique constraint.
 */
@Component
public class IdempotencyStore {

  private static class Entry {
    final TransferResponse resp;
    final Instant createdAt;
    Entry(TransferResponse resp) { this.resp = resp; this.createdAt = Instant.now(); }
  }

  private final Map<String, Entry> store = new ConcurrentHashMap<>();

  public Optional<TransferResponse> get(String key) {
    Entry e = store.get(key);
    return e == null ? Optional.empty() : Optional.of(e.resp);
  }

  public void put(String key, TransferResponse resp) {
    store.putIfAbsent(key, new Entry(resp));
  }
}
