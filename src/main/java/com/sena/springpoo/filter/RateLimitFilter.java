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
@Component // Registra este filtro como bean de Spring; Spring Boot lo agrega a la cadena de filtros web.
public class RateLimitFilter implements Filter { // Declara la clase RateLimitFilter; implementa Filter para interceptar peticiones HTTP.

    private static final Logger log = LogManager.getLogger(RateLimitFilter.class); // Crea un logger estático para registrar eventos del filtro.


    // Un bucket por IP — se limpia en producción con Caffeine/Redis
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>(); // Guarda un bucket por IP; cada bucket controla cuántas peticiones quedan disponibles.

    /**
     * Obtiene el bucket asociado a una dirección IP específica, creándolo si no existe.
     * <p>
     * Se configura un límite de 10 peticiones que se recargan cada minuto (1 minuto).
     * </p>
     *
     * @param ip La dirección IP del cliente remoto.
     * @return El objeto {@link Bucket} correspondiente a esa IP.
     */
    private Bucket obtenerBucket(String ip) { // Métdo privado que recibe una IP y retorna su Bucket.
        return buckets.computeIfAbsent(ip, k -> { // Busca el bucket por IP; si no existe, lo crea y lo guarda en el mapa.
            log.info(" Nuevo bucket creado para IP: {}", k); // Registra que se creó un bucket para una nueva IP.
            return Bucket.builder() // Inicia la construcción de un Bucket de Bucket4j.
                    .addLimit(Bandwidth.builder() // Agrega una regla de límite de ancho de banda o consumo
                            .capacity(10)   // Define la capacidad máxima del bucket en 10 tokens.
                            .refillGreedy(10, Duration.ofMinutes(1)) // recarga 10 por minuto
                            .build())
                    .build();
        });
    }

    /**
     * Intercepta peticiones HTTP y aplica rate limiting a rutas {@code /nuevo/**}.
     * <p>
     * Modificador: {@code public}, porque implementa el método de la interfaz {@link Filter}.
     * Tipo de retorno: {@code void}; no retorna un objeto, pero puede permitir o bloquear la petición.
     * </p>
     * <p>
     * Si la ruta no empieza por {@code /nuevo/}, la petición continúa sin consumir tokens.
     * Si la ruta sí empieza por {@code /nuevo/}, se consume 1 token del bucket de la IP.
     * Si no hay tokens disponibles, se responde directamente con HTTP 429.
     * </p>
     *
     * @param request petición recibida por el servidor.
     * @param response respuesta que se enviará al cliente.
     * @param chain cadena de filtros que permite continuar hacia el controlador.
     * @throws IOException si ocurre un error escribiendo la respuesta.
     * @throws ServletException si ocurre un error dentro del contenedor Servlet.
     */

        @Override // Indica que este método sobrescribe doFilter de la interfaz Filter.
        public void doFilter(ServletRequest request, ServletResponse response, // Métdo principal del filtro; no retorna valor porque es void.
                         FilterChain chain) throws IOException, ServletException { // Recibe la cadena de filtros y declara excepciones posibles.

            HttpServletRequest  req  = (HttpServletRequest)  request; // Convierte ServletRequest a HttpServletRequest para leer datos HTTP.
            HttpServletResponse resp = (HttpServletResponse) response;  // Convierte ServletResponse a HttpServletResponse para modificar estado, cabeceras y cuerpo.

        // Solo aplica a rutas /nuevo/**
        String uri = req.getRequestURI();  // Obtiene la URI solicitada, por ejemplo /nuevo/productos o /login.
            if (!uri.startsWith("/nuevo/")) { // Verifica si la ruta NO pertenece al módulo /nuevo.
                    chain.doFilter(request, response); // Si no pertenece a /nuevo, deja continuar la petición sin aplicar límite.
                return; // Termina este métdo para evitar seguir ejecutando la lógica del rate limit.
            }

        String ip     = req.getRemoteAddr(); // Obtiene la IP del cliente que hizo la petición.
        Bucket bucket = obtenerBucket(ip);  // Obtiene o crea el bucket asociado a esa IP.


            if (bucket.tryConsume(1)) { // Intenta consumir 1 token; retorna true si había token disponible.
                // ✅ Petición permitida — informa tokens restantes
            long restantes = bucket.getAvailableTokens(); // Obtiene cuántos tokens quedan después de consumir.
            resp.setHeader("X-RateLimit-Remaining", String.valueOf(restantes)); // Agrega cabecera HTTP indicando tokens restantes.
                log.debug("✅ Rate limit OK | IP={} | URI={} | tokens restantes={}", // Registra que la petición fue permitida.
                        ip, uri, restantes);
            chain.doFilter(request, response); // Permite que la petición continúe hacia otros filtros y luego al controlador.

            } else { // Si no había tokens disponibles.
                // 🚫 Límite superado — 429 Too Many Requests
            log.warn("🚫 Rate limit SUPERADO | IP={} | URI={} | método={}",
                    ip, uri, req.getMethod());  // Registra advertencia indicando que la IP superó el límite.

                resp.setStatus(HttpStatus.TOO_MANY_REQUESTS.value()); // Establece HTTP 429 Too Many Requests.
            resp.setContentType("application/json;charset=UTF-8"); // Indica que la respuesta será JSON en UTF-8.
                resp.setHeader("Retry-After", "60"); // Indica al cliente que debe esperar 60 segundos antes de intentar de nuevo.

                String lang = req.getHeader("Accept-Language"); // Lee el idioma preferido del cliente desde la cabecera HTTP.
                boolean enEs = lang == null || !lang.toLowerCase().startsWith("en"); // Define true si debe responder en español; false si debe responder en inglés.

                String body = String.format("""
                {
                  "timestamp": "%s",
                  "status": 429,
                  "error": "Too Many Requests",
                  "mensaje": "%s",
                  "detalle": "%s"
                }
                """, // Plantilla JSON que será enviada como respuesta de error.

                        new java.util.Date(),
                    enEs ? "Demasiadas peticiones" : "Too many requests",
                    enEs ? "Límite: 10 peticiones por minuto por IP. Intenta en 60 segundos."
                            : "Limit: 10 requests per minute per IP. Try again in 60 seconds." // Inserta mensaje según idioma.

                );

            resp.getWriter().write(body); // Escribe el JSON directamente en la respuesta HTTP; no continúa al controlador.

            }
    }
}