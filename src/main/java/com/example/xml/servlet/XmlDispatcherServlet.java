package com.example.xml.servlet;

import com.example.xml.exception.UnknownTipoException;
import com.example.xml.exception.XmlParseException;
import com.example.xml.router.ParserRouter;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Single-entry-point servlet for all XML requests.
 *
 * <h3>Logging strategy</h3>
 *
 * <p><b>MDC (Mapped Diagnostic Context)</b> — a thread-local map that Log4j2
 * injects into every log message from that thread. We store a {@code requestId}
 * so that all log lines for a single request can be correlated with a simple
 * {@code grep rid=<id>}, without passing the ID through every method call.
 *
 * <p>Key MDC fields set per request:
 * <ul>
 *   <li>{@code requestId} — short random ID (first 8 chars of UUID, enough for correlation)</li>
 *   <li>{@code tipo} — set after phase 1; available on all subsequent log lines</li>
 * </ul>
 *
 * <p><b>MDC + Virtual Threads</b> — SLF4J 2.x copies the MDC map when a
 * virtual thread is created, so the {@code requestId} is visible inside the
 * VT executor task without extra work.
 *
 * <p><b>Cleanup</b> — MDC is always cleared in a {@code finally} block. Tomcat
 * reuses platform threads; a stale MDC from a previous request would corrupt
 * subsequent log lines.
 *
 * <p><b>Metrics</b> — {@link RequestMetrics} captures {@code System.nanoTime()}
 * at entry and after phase 1, then logs the breakdown to the dedicated
 * {@code metrics} logger (separate file, async). No impact on response latency.
 */
@WebServlet(name = "XmlDispatcherServlet", urlPatterns = "/api/xml")
public class XmlDispatcherServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(XmlDispatcherServlet.class);

    private static final String CONTENT_TYPE_XML = "application/xml";
    private static final int    MAX_BODY_BYTES    = 1_048_576; // 1 MiB

    /** MDC key injected into every log line via the pattern [rid=%X{requestId}]. */
    static final String MDC_REQUEST_ID = "requestId";
    /** MDC key set after phase 1 so subsequent lines carry the tipo. */
    static final String MDC_TIPO       = "tipo";

    private ParserRouter    router;
    private ExecutorService executor;

    @Override
    public void init() {
        router   = (ParserRouter)    getServletContext().getAttribute(AppInitializer.ROUTER_ATTR);
        executor = (ExecutorService) getServletContext().getAttribute(AppInitializer.EXECUTOR_ATTR);
        log.info("XmlDispatcherServlet inicializado");
    }

    // ── POST /api/xml ──────────────────────────────────────────────────────────

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        // ── MDC setup ─────────────────────────────────────────────────────────
        // Short ID: 8 hex chars are enough for per-request correlation in logs.
        // UUID.randomUUID() uses SecureRandom; for ultra-high throughput consider
        // ThreadLocalRandom or a counter instead.
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put(MDC_REQUEST_ID, requestId);

        // Start latency stopwatch (nanoTime read — ~20 ns, no syscall)
        RequestMetrics metrics = RequestMetrics.start();
        String  tipo   = "UNKNOWN";
        String  status = "OK";

        try {
            // ── Input validation ─────────────────────────────────────────────
            String contentType = req.getContentType();
            if (contentType == null || !contentType.startsWith(CONTENT_TYPE_XML)) {
                log.warn("Content-Type inválido: '{}'", contentType);
                sendError(resp, HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE,
                        "Content-Type debe ser application/xml");
                status = "ERROR";
                return;
            }

            if (req.getContentLength() > MAX_BODY_BYTES) {
                log.warn("Payload demasiado grande: {} bytes", req.getContentLength());
                sendError(resp, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                        "Cuerpo de la petición demasiado grande (máx 1 MiB)");
                status = "ERROR";
                return;
            }

            if (log.isDebugEnabled()) {
                log.debug("Petición recibida: contentType={} contentLength={}",
                        contentType, req.getContentLength());
            }

            // ── Dispatch to virtual thread ────────────────────────────────────
            /*
             * SLF4J 2.x inherits MDC into the child thread automatically when
             * using InheritableThreadLocal (default since SLF4J 2.0.9).
             * The requestId and tipo set here will appear in VT log lines too.
             */
            final String finalRequestId = requestId;
            Future<Object> future = executor.submit(() -> {
                // MDC is inherited — no need to re-set requestId here
                return router.dispatch(req.getInputStream());
            });

            Object result = future.get();

            // Enrich MDC with tipo for any log lines after this point
            tipo = result.getClass().getSimpleName()
                         .replace("Request", ""); // e.g. "ConsultaRequest" → "CONSULTA"
            MDC.put(MDC_TIPO, tipo);

            metrics.markPhase1Done(); // best-effort marker (phase split is in router)

            if (log.isDebugEnabled()) {
                log.debug("Petición completada: dto={}", result);
            }

            sendResult(resp, result);

        } catch (java.util.concurrent.ExecutionException ee) {
            status = "ERROR";
            handleCause(resp, ee.getCause());

        } catch (InterruptedException ie) {
            status = "ERROR";
            Thread.currentThread().interrupt();
            log.error("Petición interrumpida");
            sendError(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Petición interrumpida");

        } finally {
            // ── Log metrics and clear MDC ─────────────────────────────────────
            /*
             * ALWAYS clear MDC in finally. Tomcat reuses platform threads;
             * leftover MDC values from this request would appear in the next
             * request handled by the same thread.
             */
            metrics.log(requestId, tipo, status);
            MDC.remove(MDC_REQUEST_ID);
            MDC.remove(MDC_TIPO);
        }
    }

    // ── Error handling ─────────────────────────────────────────────────────────

    private void handleCause(HttpServletResponse resp, Throwable cause) throws IOException {
        if (cause instanceof XmlParseException xpe) {
            // WARN: bad client XML — frequent enough to be cheap
            log.warn("XML malformado: {}", xpe.getMessage());
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, xpe.getMessage());

        } else if (cause instanceof UnknownTipoException ute) {
            log.warn("Tipo desconocido: '{}'", ute.getTipo());
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, ute.getMessage());

        } else {
            // ERROR: unexpected — log full stack trace; infrequent by definition
            log.error("Error inesperado procesando XML", cause);
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Error interno del servidor");
        }
    }

    // ── Response helpers ───────────────────────────────────────────────────────

    private void sendResult(HttpServletResponse resp, Object result) throws IOException {
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setContentType("application/xml;charset=UTF-8");

        try (PrintWriter out = resp.getWriter()) {
            out.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            out.println("<respuesta>");
            out.println("  <estado>OK</estado>");
            out.printf("  <tipo>%s</tipo>%n", result.getClass().getSimpleName());
            out.printf("  <datos>%s</datos>%n", escapeXml(result.toString()));
            out.println("</respuesta>");
        }
    }

    private void sendError(HttpServletResponse resp, int status, String message)
            throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/xml;charset=UTF-8");

        try (PrintWriter out = resp.getWriter()) {
            out.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            out.println("<respuesta>");
            out.println("  <estado>ERROR</estado>");
            out.printf("  <mensaje>%s</mensaje>%n", escapeXml(message));
            out.println("</respuesta>");
        }
    }

    private static String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&",  "&amp;")
                .replace("<",  "&lt;")
                .replace(">",  "&gt;")
                .replace("\"", "&quot;")
                .replace("'",  "&apos;");
    }
}
