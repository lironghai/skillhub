package com.iflytek.skillhub.observability.tracing;

import io.micrometer.tracing.CurrentTraceContext.Scope;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import org.slf4j.MDC;

/**
 * Keeps trace correlation fields aligned with the active OpenTelemetry scope.
 */
final class MdcOtelCurrentTraceContext extends OtelCurrentTraceContext {

    private static final String TRACE_ID = "traceId";
    private static final String SPAN_ID = "spanId";

    @Override
    public Scope newScope(TraceContext context) {
        MdcSnapshot previous = MdcSnapshot.capture();
        Scope delegate = super.newScope(context);
        publish(context);
        return () -> closeAndRestore(delegate, previous);
    }

    @Override
    public Scope maybeScope(TraceContext context) {
        if (context != null) {
            return newScope(context);
        }
        MdcSnapshot previous = MdcSnapshot.capture();
        Scope delegate = super.maybeScope(null);
        clearCorrelation();
        return () -> closeAndRestore(delegate, previous);
    }

    private static void publish(TraceContext context) {
        if (context == null) {
            clearCorrelation();
            return;
        }
        MDC.put(TRACE_ID, context.traceId());
        MDC.put(SPAN_ID, context.spanId());
    }

    private static void closeAndRestore(Scope delegate, MdcSnapshot previous) {
        try {
            delegate.close();
        } finally {
            previous.restore();
        }
    }

    private static void clearCorrelation() {
        MDC.remove(TRACE_ID);
        MDC.remove(SPAN_ID);
    }

    private record MdcSnapshot(String traceId, String spanId) {

        static MdcSnapshot capture() {
            return new MdcSnapshot(MDC.get(TRACE_ID), MDC.get(SPAN_ID));
        }

        void restore() {
            restoreValue(TRACE_ID, traceId);
            restoreValue(SPAN_ID, spanId);
        }

        private static void restoreValue(String key, String value) {
            if (value == null) {
                MDC.remove(key);
            } else {
                MDC.put(key, value);
            }
        }
    }
}
