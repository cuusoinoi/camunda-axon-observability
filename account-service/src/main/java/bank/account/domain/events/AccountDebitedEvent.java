package bank.account.domain.events;

public class AccountDebitedEvent {
  public final String accountId;
  public final long amount;
  public final String commandId;
  public final String correlationId;
  public final String transferId;

  public AccountDebitedEvent(String accountId, long amount, String commandId, String correlationId, String transferId) {
    this.accountId = accountId;
    this.amount = amount;
    this.commandId = commandId;
    this.correlationId = correlationId;
    this.transferId = transferId;
  }
}
