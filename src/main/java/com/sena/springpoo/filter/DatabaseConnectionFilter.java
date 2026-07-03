package com.sena.springpoo.filter; // Define el paquete de esta clase; pertenece a la capa filter, encargada de interceptar peticiones antes de los controladores.

import jakarta.servlet.*; // Importa interfaces de Servlet como Filter, ServletRequest, ServletResponse, FilterChain, ServletException y RequestDispatcher.
import jakarta.servlet.http.*; // Importa clases HTTP como HttpServletRequest, necesarias para leer método y URI de la petición.
import org.apache.logging.log4j.LogManager; // Importa LogManager para crear el logger de esta clase.
import org.apache.logging.log4j.Logger; // Importa Logger para registrar errores o eventos en los logs del sistema.
import org.springframework.beans.factory.annotation.Autowired; // Importa @Autowired para que Spring inyecte dependencias automáticamente.
import org.springframework.jdbc.core.JdbcTemplate; // Importa JdbcTemplate, herramienta de Spring para ejecutar consultas SQL directas.
import org.springframework.stereotype.Component; // Importa @Component para registrar esta clase como bean administrado por Spring.

import java.io.IOException; // Importa IOException; puede ocurrir al reenviar la petición o continuar la cadena de filtros.

/**
 * Filtro HTTP que verifica si la base de datos está disponible antes de cargar vistas HTML.
 * <p>
 * Este filtro se ejecuta antes de que la petición llegue a los controladores.
 * Su objetivo es evitar que el usuario vea un error técnico cuando MySQL está apagado.
 * </p>
 * <p>
 * Para comprobar la conexión ejecuta {@code SELECT 1}, una consulta liviana que no modifica datos.
 * Si la consulta falla, la petición se reenvía internamente a {@code /error} para que
 * {@link com.sena.springpoo.controller.CustomErrorController} muestre una página amigable.
 * </p>
 */
@Component // Registra este filtro como bean de Spring; Spring Boot lo detecta y lo agrega a la cadena de filtros web.
public class DatabaseConnectionFilter implements Filter { // Declara la clase filtro; implementa Filter para interceptar peticiones HTTP.

    private static final Logger log = LogManager.getLogger(DatabaseConnectionFilter.class); // Crea un logger estático para registrar fallos de conexión a base de datos.

    @Autowired // Spring inyecta automáticamente JdbcTemplate usando la configuración de datasource de application.properties.
    private JdbcTemplate jdbcTemplate; // Variable usada para ejecutar una consulta SQL rápida contra MySQL.

    /**
     * Intercepta cada petición HTTP y decide si debe comprobar la conexión a la base de datos.
     * <p>
     * Modificador: {@code public}, porque implementa el método de la interfaz {@link Filter}.
     * Tipo de retorno: {@code void}, por lo tanto no retorna ningún valor.
     * </p>
     * <p>
     * Aunque no retorna un objeto, puede producir dos resultados:
     * continuar la petición con {@code chain.doFilter(...)} o reenviarla a {@code /error}
     * si la base de datos no responde.
     * </p>
     *
     * @param request petición entrante enviada por el navegador o cliente HTTP.
     * @param response respuesta que el servidor podrá devolver al cliente.
     * @param chain cadena de filtros que permite continuar hacia otros filtros y luego hacia el controlador.
     * @throws IOException si ocurre un problema de entrada/salida durante el forward o la continuación.
     * @throws ServletException si ocurre un error del contenedor Servlet.
     */
    @Override // Indica que este método sobrescribe doFilter de la interfaz Filter.
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) // Método principal del filtro; no retorna nada porque su tipo es void.
            throws IOException, ServletException { // Declara que puede lanzar IOException o ServletException.

        HttpServletRequest req = (HttpServletRequest) request; // Convierte ServletRequest a HttpServletRequest para poder leer método HTTP y URI.
        String method = req.getMethod(); // Obtiene el método HTTP de la petición, por ejemplo GET, POST, PUT o DELETE.
        String uri = req.getRequestURI(); // Obtiene la ruta solicitada, por ejemplo /login, /formulario o /nuevo/prueba.

        if ("GET".equalsIgnoreCase(method)) { // Solo revisa conexión para peticiones GET, normalmente usadas para navegar entre vistas.
            if (!uri.startsWith("/error") && // Evita revisar la ruta /error para no crear un bucle infinito de errores.
                    !uri.startsWith("/usuarios/") && // Evita endpoints REST de usuarios, porque esos manejan sus propios errores JSON.
                    !uri.startsWith("/nuevo/productos") && // Evita endpoints REST de productos, que ya manejan errores desde NuevoController.
                    !uri.startsWith("/nuevo/imagen") && // Evita endpoint de imagen del panel, porque no necesita validar vista completa.
                    !uri.startsWith("/upload/") && // Evita endpoints de carga de archivos.
                    !uri.contains(".") && // Evita archivos estáticos o rutas con extensión, como .css, .js, .png o .html.
                    !uri.startsWith("/favicon")) { // Evita la petición automática del favicon del navegador.

                try { // Intenta ejecutar una consulta mínima para comprobar si MySQL responde.
                    jdbcTemplate.execute("SELECT 1"); // Ejecuta SELECT 1; no retorna datos útiles al controlador, solo valida conexión activa.
                } catch (Exception e) { // Captura cualquier error de conexión, SQL o disponibilidad de base de datos.
                    log.error("Conexión a Base de Datos fallida al acceder a {}: {}", uri, e.getMessage()); // Registra la URI y el mensaje del fallo.

                    request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 500); // Guarda el código de error 500 para que /error lo pueda leer.
                    request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, e); // Guarda la excepción original para diagnóstico y logs.
                    request.setAttribute(RequestDispatcher.ERROR_MESSAGE, "La base de datos (MySQL) está apagada o no disponible."); // Guarda mensaje amigable para la vista.
                    request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, uri); // Guarda la ruta original donde ocurrió el problema.

                    req.getRequestDispatcher("/error").forward(request, response); // Reenvía internamente la petición al controlador global de errores.
                    return; // Detiene el filtro para que la petición no continúe hacia el controlador original.
                }
            }
        }

        chain.doFilter(request, response); // Continúa con la cadena de filtros y luego con el controlador si no hubo error.
    }
}