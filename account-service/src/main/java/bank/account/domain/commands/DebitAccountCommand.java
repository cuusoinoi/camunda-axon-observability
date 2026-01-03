package bank.account.domain.commands;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

public class DebitAccountCommand {
  @TargetAggregateIdentifier
  public final String accountId;

  public final long amount;
  public final String commandId;
  public final String correlationId;
  public final String transferId;

  public DebitAccountCommand(String accountId, long amount, String commandId, String correlationId, String transferId) {
    this.accountId = accountId;
    this.amount = amount;
    this.commandId = commandId;
    this.correlationId = correlationId;
    this.transferId = transferId;
  }
}
