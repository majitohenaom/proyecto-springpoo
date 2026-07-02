package com.sena.springpoo.filter;

import io.github.bucket4j.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.apache.logging.log4j.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 *  RateLimitFilter
 * ╠══════════════════════════════════════════════════════════════╣
 *  Limita a cada IP a 10 peticiones por minuto.
 *  Aplica a TODOS los endpoints /nuevo/**
 *  HTTP 429 Too Many Requests si se supera el límite.
 * ╚══════════════════════════════════════════════════════════════╝
 */
@Component
public class RateLimitFilter implements Filter {

    private static final Logger log = LogManager.getLogger(RateLimitFilter.class);

    // Un bucket por IP — se limpia en producción con Caffeine/Redis
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * Obtiene el bucket asociado a una dirección IP específica, creándolo si no existe.
     * <p>
     * Se configura un límite de 10 peticiones que se recargan cada minuto (1 minuto).
     * </p>
     *
     * @param ip La dirección IP del cliente remoto.
     * @return El objeto {@link Bucket} correspondiente a esa IP.
     */
    private Bucket obtenerBucket(String ip) {
        return buckets.computeIfAbsent(ip, k -> {
            log.info("🪣 Nuevo bucket creado para IP: {}", k);
            return Bucket.builder()
                    .addLimit(Bandwidth.builder()
                            .capacity(10)                        // máximo 10 tokens
                            .refillGreedy(10, Duration.ofMinutes(1)) // recarga 10 por minuto
                            .build())
                    .build();
        });
    }

    /**
     * Intercepta las peticiones HTTP y aplica el algoritmo de rate limiting (Bucket4j).
     * <p>
     * Verifica si la IP del cliente aún cuenta con tokens disponibles en su bucket.
     * Si los tiene, consume 1 token y permite continuar. Si los agota, rechaza la petición 
     * devolviendo un estado HTTP 429 (Too Many Requests) junto con un mensaje JSON explicativo.
     * Solo aplica para los endpoints que comiencen por {@code /nuevo/}.
     * </p>
     *
     * @param request La petición entrante (HTTP).
     * @param response La respuesta saliente (HTTP).
     * @param chain Cadena de filtros para continuar la ejecución si es permitido.
     * @throws IOException Si ocurre un error de I/O en el proceso de escritura de la respuesta.
     * @throws ServletException Si ocurre un error procesando el filtro en el contenedor de Servlets.
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {

        HttpServletRequest  req  = (HttpServletRequest)  request;
        HttpServletResponse resp = (HttpServletResponse) response;

        // Solo aplica a rutas /nuevo/**
        String uri = req.getRequestURI();
        if (!uri.startsWith("/nuevo/")) {
            chain.doFilter(request, response);
            return;
        }

        String ip     = req.getRemoteAddr();
        Bucket bucket = obtenerBucket(ip);

        if (bucket.tryConsume(1)) {
            // ✅ Petición permitida — informa tokens restantes
            long restantes = bucket.getAvailableTokens();
            resp.setHeader("X-RateLimit-Remaining", String.valueOf(restantes));
            log.debug("✅ Rate limit OK | IP={} | URI={} | tokens restantes={}",
                    ip, uri, restantes);
            chain.doFilter(request, response);

        } else {
            // 🚫 Límite superado — 429 Too Many Requests
            log.warn("🚫 Rate limit SUPERADO | IP={} | URI={} | método={}",
                    ip, uri, req.getMethod());

            resp.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            resp.setContentType("application/json;charset=UTF-8");
            resp.setHeader("Retry-After", "60");

            String lang = req.getHeader("Accept-Language");
            boolean enEs = lang == null || !lang.toLowerCase().startsWith("en");

            String body = String.format("""
                {
                  "timestamp": "%s",
                  "status": 429,
                  "error": "Too Many Requests",
                  "mensaje": "%s",
                  "detalle": "%s"
                }
                """,
                    new java.util.Date(),
                    enEs ? "Demasiadas peticiones" : "Too many requests",
                    enEs ? "Límite: 10 peticiones por minuto por IP. Intenta en 60 segundos."
                            : "Limit: 10 requests per minute per IP. Try again in 60 seconds."
            );

            resp.getWriter().write(body);
        }
    }
}