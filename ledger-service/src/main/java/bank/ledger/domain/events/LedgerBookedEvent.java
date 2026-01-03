package bank.ledger.domain.events;

public class LedgerBookedEvent {
  public final String transferId;
  public final String accountId;
  public final long amount;
  public final String commandId;
  public final String correlationId;

  public LedgerBookedEvent(String transferId, String accountId, long amount, String commandId, String correlationId) {
    this.transferId = transferId;
    this.accountId = accountId;
    this.amount = amount;
    this.commandId = commandId;
    this.correlationId = correlationId;
  }
}
