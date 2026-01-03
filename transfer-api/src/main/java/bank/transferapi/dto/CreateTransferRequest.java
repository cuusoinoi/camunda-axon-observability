package bank.transferapi.dto;

public record CreateTransferRequest(String accountId, long amount) {}
