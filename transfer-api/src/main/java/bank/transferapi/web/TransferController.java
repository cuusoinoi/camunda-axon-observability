package bank.transferapi.web;

import bank.transferapi.IdempotencyStore;
import bank.transferapi.dto.CreateTransferRequest;
import bank.transferapi.dto.TransferResponse;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
public class TransferController {

  private final ZeebeClient zeebeClient;
  private final IdempotencyStore idem;
  private final Tracer tracer;

  public TransferController(ZeebeClient zeebeClient, IdempotencyStore idem) {
    this.zeebeClient = zeebeClient;
    this.idem = idem;
    OpenTelemetry otel = GlobalOpenTelemetry.get();
    this.tracer = otel.getTracer("transfer-api");
  }

  @GetMapping("/healthz")
  public String ok() { return "ok"; }

  @PostMapping("/transfers")
  public ResponseEntity<TransferResponse> create(
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody CreateTransferRequest req
  ) {
    // If key provided and already processed -> return same response (202 Accepted)
    if (idempotencyKey != null && !idempotencyKey.isBlank()) {
      Optional<TransferResponse> cached = idem.get(idempotencyKey);
      if (cached.isPresent()) {
        return ResponseEntity.accepted().body(cached.get());
      }
    }

    String transferId = "T-" + UUID.randomUUID();
    Span span = tracer.spanBuilder("transfer.create")
        .setSpanKind(SpanKind.SERVER)
        .setAttribute("transfer.id", transferId)
        .setAttribute("account.id", req.accountId())
        .setAttribute("amount", req.amount())
        .startSpan();

    try {
      try (Scope scope = span.makeCurrent()) {
        // inject W3C trace context into process variables so workers can continue the trace
        Map<String, Object> vars = new java.util.HashMap<>();
        vars.put("transferId", transferId);
        vars.put("accountId", req.accountId());
        vars.put("amount", req.amount());
        W3CTraceContextPropagator.getInstance().inject(
            io.opentelemetry.context.Context.current(),
            vars,
            (TextMapSetter<Map<String, Object>>) (carrier, key, value) -> carrier.put(key, value)
        );

        ProcessInstanceEvent instance = zeebeClient.newCreateInstanceCommand()
            .bpmnProcessId("MoneyTransferProcess")
            .latestVersion()
            .variables(vars)
            .send()
            .join();

        TransferResponse resp = new TransferResponse(transferId, instance.getProcessInstanceKey());

        // correlation attributes for TraceQL
        span.setAttribute("process.instance.key", instance.getProcessInstanceKey());

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
          idem.put(idempotencyKey, resp);
        }

        return ResponseEntity.accepted().body(resp);
      }
    } finally {
      span.end();
    }
  }
}
