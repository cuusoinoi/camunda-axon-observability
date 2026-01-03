package bank.ledger.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LedgerController {
  @GetMapping("/healthz")
  public String ok() { return "ok"; }
}
