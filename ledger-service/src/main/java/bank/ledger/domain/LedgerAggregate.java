package bank.ledger.domain;

import bank.ledger.domain.commands.BookLedgerCommand;
import bank.ledger.domain.events.LedgerBookedEvent;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;

import java.util.HashSet;
import java.util.Set;

@Aggregate
public class LedgerAggregate {

  @AggregateIdentifier
  private String transferId;

  private Set<String> processed = new HashSet<>();

  protected LedgerAggregate() {}

  @CommandHandler
  public LedgerAggregate(BookLedgerCommand cmd) {
    handle(cmd);
  }

  @CommandHandler
  public void handle(BookLedgerCommand cmd) {
    if (processed.contains(cmd.commandId)) return; // idempotent
    AggregateLifecycle.apply(new LedgerBookedEvent(cmd.transferId, cmd.accountId, cmd.amount, cmd.commandId, cmd.correlationId));
  }

  @EventSourcingHandler
  public void on(LedgerBookedEvent evt) {
    this.transferId = evt.transferId;
    this.processed.add(evt.commandId);
  }
}
