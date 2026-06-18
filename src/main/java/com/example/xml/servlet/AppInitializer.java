package com.example.xml.servlet;

import com.example.xml.parser.AltaParser;
import com.example.xml.parser.ConsultaParser;
import com.example.xml.parser.ModificacionParser;
import com.example.xml.parser.XmlParser;
import com.example.xml.router.ParserRouter;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Application lifecycle listener.
 *
 * <p>On startup:
 * <ul>
 *   <li>Builds the immutable {@link ParserRouter} and stores it in
 *       {@link ServletContext} for use by servlets.</li>
 *   <li>Creates the Virtual Thread {@link ExecutorService} used to handle
 *       requests concurrently without blocking platform threads.</li>
 * </ul>
 *
 * <p>On shutdown: closes the executor cleanly.
 */
@WebListener
public class AppInitializer implements ServletContextListener {

    private static final Logger log = LoggerFactory.getLogger(AppInitializer.class);

    /** ServletContext attribute key for the shared {@link ParserRouter}. */
    public static final String ROUTER_ATTR = "xmlParserRouter";

    /** ServletContext attribute key for the shared {@link ExecutorService}. */
    public static final String EXECUTOR_ATTR = "virtualThreadExecutor";

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        log.info("Iniciando xml-parser...");

        ServletContext ctx = sce.getServletContext();

        // ── Register all parsers here ─────────────────────────────────────────
        List<XmlParser<?>> parserList = List.of(
                new ConsultaParser(),
                new AltaParser(),
                new ModificacionParser()
                // Add new parsers here as the system grows
        );

        ParserRouter router = new ParserRouter(parserList);
        ctx.setAttribute(ROUTER_ATTR, router);

        // ── Virtual Thread executor (Java 21) ─────────────────────────────────
        // One virtual thread per task — no pool sizing needed.
        // Blocking I/O in virtual threads does NOT block carrier (platform) threads.
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        ctx.setAttribute(EXECUTOR_ATTR, executor);

        log.info("xml-parser iniciada correctamente.");
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        log.info("Deteniendo xml-parser...");

        ExecutorService executor = (ExecutorService)
                sce.getServletContext().getAttribute(EXECUTOR_ATTR);

        if (executor != null) {
            executor.shutdown();
        }

        log.info("xml-parser detenida.");
    }
}
