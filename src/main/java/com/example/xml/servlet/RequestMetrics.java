package com.example.xml.servlet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lightweight latency tracker for XML request processing.
 *
 * <p>Uses {@link System#nanoTime()} (monotonic clock, no OS syscall on modern
 * JVMs) to measure wall-clock time between phases without allocating objects
 * on the hot path beyond this record itself.
 *
 * <p>Metrics are written to the dedicated {@code metrics} logger, which routes
 * to a separate file appender — keeping latency data out of the main log and
 * making it trivial to analyse with standard Unix tools.
 *
 * <h3>Why a dedicated logger and not a counter?</h3>
 * Counters (Micrometer, Dropwizard) are better for dashboards. This approach
 * is intentionally zero-dependency and produces a text file that ops teams can
 * {@code grep | awk} without any extra infrastructure.
 *
 * <h3>Performance notes</h3>
 * <ul>
 *   <li>{@code System.nanoTime()} costs ~20 ns on x86-64; negligible vs parsing.</li>
 *   <li>The log event is enqueued on the Disruptor ring buffer in the calling
 *       thread and written to disk asynchronously — no I/O on the request thread.</li>
 *   <li>When the {@code metrics} logger is at WARN or above, the
 *       {@code log.isInfoEnabled()} guard prevents even the string formatting.</li>
 * </ul>
 */
public final class RequestMetrics {

    /** Routes to MetricsFile appender defined in log4j2.xml. */
    private static final Logger METRICS = LoggerFactory.getLogger("metrics");

    private final long startNanos;
    private long phase1Nanos;

    private RequestMetrics() {
        this.startNanos = System.nanoTime();
    }

    /** Start the stopwatch. Call once at the beginning of {@code doPost}. */
    public static RequestMetrics start() {
        return new RequestMetrics();
    }

    /** Record the end of phase 1 (right after {@code <tipo>} is extracted). */
    public void markPhase1Done() {
        this.phase1Nanos = System.nanoTime();
    }

    /**
     * Logs the full timing breakdown to the {@code metrics} logger.
     *
     * <p>Output format (TSV-friendly for awk):
     * <pre>
     *   METRICS rid=abc tipo=CONSULTA phase1_us=42 total_us=310 status=OK
     * </pre>
     *
     * @param requestId correlation ID from MDC
     * @param tipo      value extracted from {@code <tipo>}, or {@code "UNKNOWN"}
     * @param status    {@code "OK"} or {@code "ERROR"}
     */
    public void log(String requestId, String tipo, String status) {
        if (!METRICS.isInfoEnabled()) {
            return; // guard: skip string building if metrics logger is disabled
        }

        long endNanos     = System.nanoTime();
        long phase1Micros = phase1Nanos > 0 ? (phase1Nanos - startNanos) / 1_000 : -1;
        long totalMicros  = (endNanos - startNanos) / 1_000;

        // SLF4J parameterised message → no String concatenation at call site
        METRICS.info("METRICS rid={} tipo={} phase1_us={} total_us={} status={}",
                requestId, tipo, phase1Micros, totalMicros, status);
    }
}
