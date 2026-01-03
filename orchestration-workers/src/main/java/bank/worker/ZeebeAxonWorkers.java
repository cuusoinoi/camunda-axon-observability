package bank.worker;

import bank.account.domain.commands.DebitAccountCommand;
import bank.ledger.domain.commands.BookLedgerCommand;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.spring.client.annotation.JobWorker;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

/**
 * Zeebe workers that call Axon command side.
 *
 * Monitoring strategy (practical + "bank-grade"):
 * - Use the OpenTelemetry Java agent for auto-instrumentation + OTLP export.
 * - Propagate W3C trace context through Zeebe variables via a `traceparent` field.
 * - Add domain attributes: transfer.id, process.instance.key to make Tempo/TraceQL searches easy.
 */
@Component
public class ZeebeAxonWorkers {

  private static final String ATTR_TRANSFER_ID = "transfer.id";
  private static final String ATTR_PROCESS_INSTANCE_KEY = "process.instance.key";
  private static final String ATTR_ZEEBE_JOB_TYPE = "zeebe.job.type";

  private final CommandGateway commandGateway;
  private final Tracer tracer;

  public ZeebeAxonWorkers(CommandGateway commandGateway) {
    this.commandGateway = commandGateway;

    // With the Java agent, GlobalOpenTelemetry is provided by the agent.
    OpenTelemetry otel = GlobalOpenTelemetry.get();
    this.tracer = otel.getTracer("orchestration-workers");
  }

  @JobWorker(type = "orchestration.account.debit", autoComplete = true)
  public void debit(ActivatedJob job) {
    runWithTrace(job, () -> {
      String accountId = (String) job.getVariablesAsMap().get("accountId");
      Number amount = (Number) job.getVariablesAsMap().get("amount");
      String transferId = (String) job.getVariablesAsMap().get("transferId");

      // Use Zeebe job key as a deterministic command id (good for debugging / idempotency).
      String commandId = String.valueOf(job.getKey());
      String correlationId = String.valueOf(job.getProcessInstanceKey());

      commandGateway.sendAndWait(new DebitAccountCommand(
          accountId,
          amount == null ? 0L : amount.longValue(),
          commandId,
          correlationId,
          transferId
      ));
    });
  }

  @JobWorker(type = "orchestration.ledger.book", autoComplete = true)
  public void book(ActivatedJob job) {
    runWithTrace(job, () -> {
      String transferId = (String) job.getVariablesAsMap().get("transferId");
      String accountId = (String) job.getVariablesAsMap().get("accountId");
      Number amount = (Number) job.getVariablesAsMap().get("amount");

      String commandId = String.valueOf(job.getKey());
      String correlationId = String.valueOf(job.getProcessInstanceKey());

      commandGateway.sendAndWait(new BookLedgerCommand(
          transferId,
          accountId,
          amount == null ? 0L : amount.longValue(),
          commandId,
          correlationId
      ));
    });
  }

  private void runWithTrace(ActivatedJob job, Runnable work) {
    Map<String, Object> vars = job.getVariablesAsMap();
    String transferId = (String) vars.get("transferId");
    Object processInstanceKey = vars.get("processInstanceKey");

    Context parent = extractParentContext(vars);
    Span span = tracer.spanBuilder("zeebe.job")
        .setParent(parent)
        .setSpanKind(SpanKind.CONSUMER)
        .startSpan();

    // Domain attributes for super easy TraceQL filtering.
    if (transferId != null) span.setAttribute(ATTR_TRANSFER_ID, transferId);
    if (processInstanceKey != null) span.setAttribute(ATTR_PROCESS_INSTANCE_KEY, String.valueOf(processInstanceKey));
    if (job.getType() != null) span.setAttribute(ATTR_ZEEBE_JOB_TYPE, job.getType());

    try (Scope ignored = span.makeCurrent()) {
      work.run();
      span.setStatus(StatusCode.OK);
    } catch (Exception e) {
      span.recordException(e);
      span.setStatus(StatusCode.ERROR);
      throw e;
    } finally {
      span.end();
    }
  }

  private Context extractParentContext(Map<String, Object> vars) {
    Object tp = vars.get("traceparent");
    if (!(tp instanceof String traceparent) || traceparent.isBlank()) {
      return Context.current();
    }

    Map<String, String> carrier = Collections.singletonMap("traceparent", traceparent);
    return W3CTraceContextPropagator.getInstance().extract(Context.root(), carrier, TRACEPARENT_GETTER);
  }

  private static final TextMapGetter<Map<String, String>> TRACEPARENT_GETTER = new TextMapGetter<>() {
    @Override
    public Iterable<String> keys(Map<String, String> carrier) {
      return carrier.keySet();
    }

    @Override
    public String get(Map<String, String> carrier, String key) {
      return carrier.get(key);
    }
  };
}
