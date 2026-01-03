package bank.account.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AccountController {
  @GetMapping("/healthz")
  public String ok() { return "ok"; }
}
