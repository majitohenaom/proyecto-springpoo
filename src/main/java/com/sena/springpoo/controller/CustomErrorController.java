package com.sena.springpoo.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.Date;

/**
 * Controlador global para la captura y manejo de errores HTTP y excepciones del sistema.
 * <p>
 * Implementa la interfaz {@link ErrorController} de Spring Boot para interceptar
 * cualquier error (como 404, 403, 500) y mostrar una página de error amigable,
 * además de registrar los detalles en los logs del sistema.
 * </p>
 */
@Controller
public class CustomErrorController implements ErrorController {

    private static final Logger log = LogManager.getLogger(CustomErrorController.class);

    /**
     * Endpoint de prueba para forzar y simular un error 500 (Error interno del servidor).
     * 
     * @return No retorna una vista, lanza directamente una {@link RuntimeException}.
     */
    @GetMapping("/generar-error-500")
    public String generarError500() {
        throw new RuntimeException("Error de conexión a base de datos simulado (Communications link failure / Connection refused).");
    }

    /**
     * Intercepta las solicitudes dirigidas a "/error" y procesa la excepción o el código de estado.
     * <p>
     * Este método extrae el código HTTP, la URI solicitada, y la excepción subyacente.
     * Dependiendo del tipo de error (por ejemplo, si la base de datos está caída o si la ruta no existe),
     * genera un mensaje personalizado y lo añade al modelo para ser mostrado en las plantillas 
     * Thymeleaf de error ("500" o "error"). Además, registra el incidente en los logs.
     * </p>
     *
     * @param request El objeto {@link HttpServletRequest} que contiene los atributos del error despachado.
     * @param model El objeto {@link Model} utilizado para pasar los detalles del error a la vista.
     * @return El nombre de la plantilla Thymeleaf a renderizar ({@code "500"} si es error del servidor, {@code "error"} para el resto).
     */
    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Model model) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        Object exception = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        Object message = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
        Object requestUri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);

        int statusCode = 500;
        if (status != null) {
            try {
                statusCode = Integer.parseInt(status.toString());
            } catch (NumberFormatException e) {
                // Mantener el valor por defecto
            }
        }

        HttpStatus httpStatus = HttpStatus.resolve(statusCode);
        String errorTitle = (httpStatus != null) ? httpStatus.getReasonPhrase() : "Error Detectado";
        
        boolean bdApagada = false;
        if (exception != null) {
            Throwable causa = (Throwable) exception;
            while (causa != null) {
                String msg = causa.getMessage();
                if (msg != null && (
                        msg.contains("Communications link failure") ||
                        msg.contains("Connection refused") ||
                        msg.contains("JDBC") ||
                        msg.contains("SQL") ||
                        causa instanceof java.sql.SQLException ||
                        causa instanceof java.net.ConnectException
                )) {
                    bdApagada = true;
                    break;
                }
                causa = causa.getCause();
            }
        }

        String errorMsg = "";
        if (statusCode == 404) {
            errorMsg = "Lo sentimos, el recurso que buscas no está disponible o ha ocurrido un problema técnico en la plataforma SENA GDF.";
        } else if (statusCode == 403) {
            errorMsg = "Acceso denegado. No tienes permisos para acceder a esta sección de la plataforma SENA GDF.";
        } else if (bdApagada) {
            errorMsg = "No se ha podido establecer la conexión con el servidor de la base de datos (MySQL). Asegúrate de que MySQL esté activo en XAMPP.";
        } else if (message != null && !message.toString().isEmpty()) {
            errorMsg = message.toString();
        } else {
            errorMsg = "Ha ocurrido un problema interno en el servidor de la plataforma SENA GDF.";
        }

        // Registrar error en los logs
        if (statusCode >= 400) {
            if (exception != null) {
                log.error("Error {} detectado en ruta: {} - Mensaje: {}", statusCode, requestUri, errorMsg, (Throwable) exception);
            } else {
                log.error("Error {} detectado en ruta: {} - Mensaje: {}", statusCode, requestUri, errorMsg);
            }
        }

        model.addAttribute("status", statusCode);
        model.addAttribute("error", errorTitle);
        model.addAttribute("message", errorMsg);
        model.addAttribute("bdApagada", bdApagada);
        model.addAttribute("path", requestUri != null ? requestUri.toString() : "");
        model.addAttribute("timestamp", new Date().toString());

        if (statusCode == 500) {
            return "500";
        }
        return "error";
    }
}
