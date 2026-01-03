package bank.ledger.domain.commands;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

public class BookLedgerCommand {
  @TargetAggregateIdentifier
  public final String transferId;

  public final String accountId;
  public final long amount;
  public final String commandId;
  public final String correlationId;

  public BookLedgerCommand(String transferId, String accountId, long amount, String commandId, String correlationId) {
    this.transferId = transferId;
    this.accountId = accountId;
    this.amount = amount;
    this.commandId = commandId;
    this.correlationId = correlationId;
  }
}
