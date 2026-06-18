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

import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Single-entry-point servlet for all XML requests.
 *
 * <p>Accepts POST requests at {@code /api/xml} with {@code Content-Type: application/xml}.
 * Delegates to {@link ParserRouter} for two-phase StAX parsing.
 *
 * <p>Each request is submitted to the Virtual Thread executor so that
 * blocking inside the parser never stalls a platform thread.
 *
 * <p><b>Threading model:</b>
 * <ol>
 *   <li>Tomcat platform thread calls {@link #doPost}.</li>
 *   <li>{@code doPost} submits work to the VT executor and blocks on
 *       {@link Future#get()} — which is cheap because Tomcat's NIO connector
 *       does not hold a platform thread during the wait.</li>
 *   <li>A virtual thread runs the actual XML parsing.</li>
 * </ol>
 *
 * <p>If you use Tomcat with its Virtual Thread connector (Tomcat 11+), you
 * can skip the executor entirely and call the router directly in {@code doPost}.
 */
@WebServlet(name = "XmlDispatcherServlet", urlPatterns = "/api/xml")
public class XmlDispatcherServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(XmlDispatcherServlet.class);

    private static final String CONTENT_TYPE_XML = "application/xml";
    private static final int    MAX_BODY_BYTES    = 1_048_576; // 1 MiB guard

    private ParserRouter    router;
    private ExecutorService executor;

    @Override
    public void init() {
        router   = (ParserRouter)    getServletContext().getAttribute(AppInitializer.ROUTER_ATTR);
        executor = (ExecutorService) getServletContext().getAttribute(AppInitializer.EXECUTOR_ATTR);
    }

    // ── POST /api/xml ──────────────────────────────────────────────────────────

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        // Basic content-type guard
        String contentType = req.getContentType();
        if (contentType == null || !contentType.startsWith(CONTENT_TYPE_XML)) {
            sendError(resp, HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE,
                    "Content-Type debe ser application/xml");
            return;
        }

        // Size guard (prevent OOM on huge payloads)
        if (req.getContentLength() > MAX_BODY_BYTES) {
            sendError(resp, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    "Cuerpo de la petición demasiado grande (máx 1 MiB)");
            return;
        }

        try {
            // Submit to VT executor — parse on a virtual thread
            Future<Object> future = executor.submit(
                    () -> router.dispatch(req.getInputStream())
            );

            Object result = future.get(); // blocks until VT completes

            log.debug("Petición procesada: tipo={}", result.getClass().getSimpleName());

            sendResult(resp, result);

        } catch (java.util.concurrent.ExecutionException ee) {
            handleCause(resp, ee.getCause());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            sendError(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Petición interrumpida");
        }
    }

    // ── Error handling ─────────────────────────────────────────────────────────

    private void handleCause(HttpServletResponse resp, Throwable cause) throws IOException {
        if (cause instanceof XmlParseException xpe) {
            log.warn("XML malformado: {}", xpe.getMessage());
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, xpe.getMessage());

        } else if (cause instanceof UnknownTipoException ute) {
            log.warn("Tipo desconocido: {}", ute.getTipo());
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, ute.getMessage());

        } else {
            log.error("Error inesperado procesando XML", cause);
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Error interno del servidor");
        }
    }

    // ── Response helpers ───────────────────────────────────────────────────────

    /**
     * Writes a minimal XML acknowledgement with the DTO's {@code toString()}.
     * In a real project this would delegate to a marshaller or template engine.
     */
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

    /** Minimal XML escaping for dynamic content in responses. */
    private static String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&",  "&amp;")
                .replace("<",  "&lt;")
                .replace(">",  "&gt;")
                .replace("\"", "&quot;")
                .replace("'",  "&apos;");
    }
}
