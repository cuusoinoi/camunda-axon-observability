package bank.account.domain;

import bank.account.domain.commands.DebitAccountCommand;
import bank.account.domain.events.AccountDebitedEvent;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;

import java.util.HashSet;
import java.util.Set;

@Aggregate
public class AccountAggregate {

  @AggregateIdentifier
  private String accountId;

  private long balance = 10_000; // demo
  private Set<String> processed = new HashSet<>();

  protected AccountAggregate() {}

  @CommandHandler
  public AccountAggregate(DebitAccountCommand cmd) {
    // Allow create-on-first-command (demo): aggregate created by first debit
    handle(cmd);
  }

  @CommandHandler
  public void handle(DebitAccountCommand cmd) {
    if (processed.contains(cmd.commandId)) return; // idempotent

    if (balance < cmd.amount) {
      throw new IllegalStateException("INSUFFICIENT_BALANCE");
    }

    AggregateLifecycle.apply(new AccountDebitedEvent(
        cmd.accountId, cmd.amount, cmd.commandId, cmd.correlationId, cmd.transferId
    ));
  }

  @EventSourcingHandler
  public void on(AccountDebitedEvent evt) {
    this.accountId = evt.accountId;
    this.balance -= evt.amount;
    this.processed.add(evt.commandId);
  }
}
