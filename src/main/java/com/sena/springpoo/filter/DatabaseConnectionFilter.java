package com.sena.springpoo.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Filtro HTTP para interceptar y verificar la conectividad de la base de datos 
 * en cada petición de navegación hacia las vistas HTML de la aplicación.
 * <p>
 * Este filtro evita que el usuario vea un error no controlado si el servicio de MySQL 
 * está apagado (ej. en XAMPP). Ejecuta una consulta rápida ("SELECT 1") antes de 
 * cargar la vista; si falla, delega la petición al {@code /error} controlador 
 * generando un amigable error 500.
 * </p>
 */
@Component
public class DatabaseConnectionFilter implements Filter {

    private static final Logger log = LogManager.getLogger(DatabaseConnectionFilter.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Intercepta la petición HTTP para comprobar el estado de la base de datos.
     * <p>
     * Se filtran las peticiones para ejecutar la comprobación únicamente en los 
     * métodos GET dirigidos a las rutas de navegación (ignorando API REST, carga de archivos,
     * recursos estáticos y páginas de error).
     * </p>
     *
     * @param request El objeto {@link ServletRequest} con los datos de la petición cliente.
     * @param response El objeto {@link ServletResponse} para enviar la respuesta.
     * @param chain El objeto {@link FilterChain} que permite continuar con la cadena de filtros.
     * @throws IOException Si ocurre un error de entrada/salida.
     * @throws ServletException Si ocurre un error a nivel de Servlet.
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        String method = req.getMethod();
        String uri = req.getRequestURI();

        // Solo verificar en peticiones GET de navegación (vistas HTML)
        if ("GET".equalsIgnoreCase(method)) {
            // Evitar recursos estáticos, endpoints REST de datos y la propia ruta de error
            if (!uri.startsWith("/error") &&
                    !uri.startsWith("/usuarios/") &&
                    !uri.startsWith("/nuevo/productos") &&
                    !uri.startsWith("/nuevo/imagen") &&
                    !uri.startsWith("/upload/") &&
                    !uri.contains(".") &&
                    !uri.startsWith("/favicon")) {

                try {
                    // Ejecuta una consulta rápida de prueba
                    jdbcTemplate.execute("SELECT 1");
                } catch (Exception e) {
                    log.error("🔌 Conexión a Base de Datos fallida al acceder a {}: {}", uri, e.getMessage());
                    
                    // Configurar atributos de error para redirigir a la vista de error 500
                    request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 500);
                    request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, e);
                    request.setAttribute(RequestDispatcher.ERROR_MESSAGE, "La base de datos (MySQL) está apagada o no disponible.");
                    request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, uri);

                    // Redirigir internamente al manejador de errores
                    req.getRequestDispatcher("/error").forward(request, response);
                    return;
                }
            }
        }

        chain.doFilter(request, response);
    }
}
