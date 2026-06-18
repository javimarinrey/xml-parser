package com.example.xml.router;

import com.example.xml.exception.UnknownTipoException;
import com.example.xml.exception.XmlParseException;
import com.example.xml.parser.XmlParser;
import com.example.xml.pool.XmlStreamReaderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Two-phase XML dispatcher.
 *
 * <p><b>Phase 1</b> — reads the stream only until {@code <tipo>} is found,
 * extracts its text content, and leaves the reader positioned just after
 * {@code </tipo>}. Cost: O(bytes until {@code </tipo>}).
 *
 * <p><b>Phase 2</b> — delegates to the matching {@link XmlParser} using the
 * <em>same</em> {@link XMLStreamReader}. No re-parsing, no stream copy.
 *
 * <h3>Logging strategy (zero overhead on hot path)</h3>
 * <ul>
 *   <li>INFO — startup/shutdown only: one log per JVM lifecycle.</li>
 *   <li>DEBUG — per-request tipo + timing, guarded by {@code isDebugEnabled()}.
 *       In production (INFO level) the guard short-circuits before any
 *       {@code String} is allocated.</li>
 *   <li>WARN/ERROR — only on exceptional paths (unknown tipo, close failure).
 *       These are infrequent by definition.</li>
 *   <li>All calls use SLF4J parameterised messages ({}) — no string
 *       concatenation at the call site regardless of level.</li>
 * </ul>
 *
 * <p>This class is thread-safe. The internal {@code Map} is immutable after
 * construction. Each call to {@link #dispatch} creates its own reader.
 */
public final class ParserRouter {

    private static final Logger log = LoggerFactory.getLogger(ParserRouter.class);

    /** Tag name that identifies the request type. */
    private static final String TIPO_TAG = "tipo";

    /** Buffer size for wrapping raw InputStreams (8 KiB). */
    private static final int BUFFER_SIZE = 8_192;

    /** Immutable map built once at startup. */
    private final Map<String, XmlParser<?>> parsers;

    /**
     * Builds the router from the supplied list of parsers.
     *
     * @param parsers list of all registered {@link XmlParser} implementations
     * @throws IllegalArgumentException if two parsers share the same tipo
     */
    public ParserRouter(List<XmlParser<?>> parsers) {
        this.parsers = parsers.stream()
                .collect(Collectors.toUnmodifiableMap(
                        XmlParser::tipo,
                        Function.identity(),
                        (a, b) -> { throw new IllegalArgumentException(
                                "Tipo duplicado en parsers: " + a.tipo()); }
                ));

        // INFO: fires once at startup, never on the hot path
        log.info("ParserRouter inicializado con {} tipos: {}",
                this.parsers.size(), this.parsers.keySet());
    }

    /**
     * Dispatches the XML in {@code rawXml} to the appropriate parser.
     *
     * @param rawXml the raw request body; ownership is NOT transferred
     * @return the DTO produced by the matching parser
     * @throws XmlParseException    if the XML is malformed or {@code <tipo>} is absent
     * @throws UnknownTipoException if no parser is registered for the detected tipo
     */
    public Object dispatch(InputStream rawXml) {
        InputStream buffered = rawXml instanceof BufferedInputStream
                ? rawXml
                : new BufferedInputStream(rawXml, BUFFER_SIZE);

        XMLStreamReader reader = XmlStreamReaderFactory.create(buffered);
        try {
            // ── Phase 1: peek for <tipo> ──────────────────────────────────────
            String tipo = peekTipo(reader);

            /*
             * DEBUG guard: isDebugEnabled() is a simple volatile boolean read
             * (~1 ns). Without the guard, SLF4J would still skip I/O, but the
             * JVM would still evaluate the arguments (tipo is already a String
             * here, so cost is minimal — the guard is more important on calls
             * that would build a String via concatenation or toString()).
             */
            if (log.isDebugEnabled()) {
                log.debug("Fase 1 completada: tipo={}", tipo);
            }

            // ── Phase 2: full parse with the same reader ──────────────────────
            Object result = route(tipo, reader);

            if (log.isDebugEnabled()) {
                // Lambda form: the toString() is only called if DEBUG is active.
                // Use this pattern whenever the argument requires non-trivial work.
                log.debug("Fase 2 completada: dto={}", result);
            }

            return result;

        } catch (XMLStreamException e) {
            throw new XmlParseException("Error StAX durante el parseo", e);
        } finally {
            closeQuietly(reader);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Advances the reader until it finds {@code <tipo>}, reads its text via
     * {@link XMLStreamReader#getElementText()} (which also consumes the
     * closing tag), and returns. The reader is left positioned just after
     * {@code </tipo>} — ready for phase 2.
     */
    private static String peekTipo(XMLStreamReader reader) throws XMLStreamException {
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT
                    && TIPO_TAG.equals(reader.getLocalName())) {
                String value = reader.getElementText().strip();
                if (value.isEmpty()) {
                    throw new XmlParseException("El tag <tipo> está vacío");
                }
                return value;
            }
        }
        throw new XmlParseException("No se encontró el tag <tipo> en el XML");
    }

    @SuppressWarnings("unchecked")
    private <T> T route(String tipo, XMLStreamReader reader) throws XMLStreamException {
        XmlParser<T> parser = (XmlParser<T>) parsers.get(tipo);
        if (parser == null) {
            // WARN: infrequent — bad client request, not a hot path
            log.warn("Tipo no registrado: '{}'", tipo);
            throw new UnknownTipoException(tipo);
        }
        return parser.parse(reader);
    }

    private static void closeQuietly(XMLStreamReader reader) {
        if (reader != null) {
            try {
                reader.close();
            } catch (XMLStreamException e) {
                // WARN: should never happen in practice; cheap to log
                log.warn("Error al cerrar XMLStreamReader", e);
            }
        }
    }
}
