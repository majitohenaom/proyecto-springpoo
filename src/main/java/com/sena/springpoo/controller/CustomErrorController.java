package com.sena.springpoo.controller; // Define el paquete donde está este controlador; pertenece a la capa controller del proyecto Springpoo.

import jakarta.servlet.RequestDispatcher; // Importa RequestDispatcher; permite leer atributos estándar que el servidor guarda cuando ocurre un error HTTP.
import jakarta.servlet.http.HttpServletRequest; // Importa HttpServletRequest; representa la petición HTTP y permite obtener datos del error, ruta y atributos internos.
import org.springframework.boot.web.servlet.error.ErrorController; // Importa ErrorController; interfaz usada por Spring Boot para personalizar el manejo global de errores.
import org.springframework.http.HttpStatus; // Importa HttpStatus; permite trabajar con códigos HTTP como 404, 403 y 500.
import org.springframework.stereotype.Controller; // Importa @Controller; marca la clase como controlador MVC que retorna vistas Thymeleaf.
import org.springframework.ui.Model; // Importa Model; permite enviar datos desde Java hacia la vista HTML.
import org.springframework.web.bind.annotation.GetMapping; // Importa @GetMapping; sirve para mapear peticiones HTTP GET.
import org.springframework.web.bind.annotation.RequestMapping; // Importa @RequestMapping; sirve para mapear una ruta HTTP general.

import org.apache.logging.log4j.LogManager; // Importa LogManager; permite crear un logger para esta clase.
import org.apache.logging.log4j.Logger; // Importa Logger; permite registrar errores y mensajes en los archivos de logs.
import java.util.Date; // Importa Date; se usa para generar la fecha y hora del error.

/**
 * Controlador global encargado de manejar errores HTTP y excepciones del sistema.
 * <p>
 * Esta clase intercepta errores como 404, 403 y 500 mediante la ruta especial
 * {@code /error}, que Spring Boot usa cuando ocurre un fallo durante una petición.
 * </p>
 * <p>
 * Su función dentro de Springpoo es mostrar páginas de error amigables usando
 * Thymeleaf, enviar información del error al modelo y registrar el problema
 * en los logs configurados con Log4j2.
 * </p>
 */
@Controller // Registra esta clase como controlador de Spring MVC; sus métodos pueden retornar nombres de plantillas HTML.
public class CustomErrorController implements ErrorController { // Declara el controlador y usa ErrorController para integrarse con el sistema de errores de Spring Boot.

    private static final Logger log = LogManager.getLogger(CustomErrorController.class); // Crea un logger estático para registrar errores detectados por este controlador.

    /**
     * Endpoint de prueba que fuerza un error 500.
     * <p>
     * Modificador: {@code public}. Retorno declarado: {@code String}, aunque en la práctica
     * no alcanza a retornar una vista porque lanza una excepción.
     * </p>
     *
     * @return no retorna normalmente; siempre lanza RuntimeException.
     */
    @GetMapping("/generar-error-500") // Mapea peticiones GET a /generar-error-500; se usa para probar la página de error 500.
    public String generarError500() { // Método público sin parámetros; declarado como String porque normalmente un controlador retorna una vista.
        throw new RuntimeException("Error de conexión a base de datos simulado (Communications link failure / Connection refused)."); // Lanza una excepción manual para que Spring redirija el flujo hacia /error.
    }

    /**
     * Maneja la ruta global {@code /error}.
     * <p>
     * Spring Boot envía aquí las peticiones que fallaron por errores HTTP o excepciones.
     * El método lee el estado, la excepción, el mensaje y la ruta original, construye
     * un mensaje entendible y decide qué plantilla mostrar.
     * </p>
     *
     * @param request petición HTTP que contiene atributos internos del error.
     * @param model modelo usado para enviar datos a las plantillas error.html o 500.html.
     * @return {@code "500"} si el error es 500; {@code "error"} para otros errores.
     */
    @RequestMapping("/error") // Mapea cualquier petición dirigida a /error, sin limitarse a GET o POST.
    public String handleError(HttpServletRequest request, Model model) { // Método principal de manejo de errores; recibe la petición y el modelo de vista.
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE); // Obtiene el código HTTP del error, por ejemplo 404, 403 o 500.
        Object exception = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION); // Obtiene la excepción original si el error fue causado por una excepción Java.
        Object message = request.getAttribute(RequestDispatcher.ERROR_MESSAGE); // Obtiene el mensaje asociado al error, si existe.
        Object requestUri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI); // Obtiene la ruta original donde ocurrió el error.

        int statusCode = 500; // Declara el código de estado por defecto como 500; se usa si no se puede leer otro código.
        if (status != null) { // Verifica si Spring o el servidor dejaron un código de estado en la petición.
            try { // Intenta convertir el estado recibido a número entero.
                statusCode = Integer.parseInt(status.toString()); // Convierte el atributo status a int; ejemplo: "404" pasa a 404.
            } catch (NumberFormatException e) { // Captura error si el estado no puede convertirse a número.
                // Mantiene statusCode en 500 porque no se pudo interpretar el código recibido.
            }
        }

        HttpStatus httpStatus = HttpStatus.resolve(statusCode); // Convierte el número HTTP en un objeto HttpStatus si Spring lo reconoce.
        String errorTitle = (httpStatus != null) ? httpStatus.getReasonPhrase() : "Error Detectado"; // Define el título del error; usa texto oficial si existe.

        boolean bdApagada = false; // Variable booleana que indica si el error parece causado por MySQL apagado o conexión fallida.
        if (exception != null) { // Verifica si existe una excepción asociada al error.
            Throwable causa = (Throwable) exception; // Convierte el objeto exception a Throwable para recorrer sus causas internas.
            while (causa != null) { // Recorre la cadena de excepciones hasta que no haya más causas.
                String msg = causa.getMessage(); // Obtiene el mensaje de la causa actual.
                if (msg != null && ( // Verifica que el mensaje no sea null antes de buscar textos dentro.
                        msg.contains("Communications link failure") || // Detecta error típico de conexión JDBC perdida.
                                msg.contains("Connection refused") || // Detecta cuando MySQL rechaza la conexión.
                                msg.contains("JDBC") || // Detecta errores relacionados con JDBC.
                                msg.contains("SQL") || // Detecta errores relacionados con SQL.
                                causa instanceof java.sql.SQLException || // Detecta excepciones SQL directamente.
                                causa instanceof java.net.ConnectException // Detecta errores de conexión de red.
                )) { // Si cualquiera de esas condiciones se cumple, se considera error de base de datos.
                    bdApagada = true; // Marca que probablemente MySQL está apagado o inaccesible.
                    break; // Detiene el recorrido porque ya se identificó la causa.
                }
                causa = causa.getCause(); // Avanza a la causa interna siguiente de la excepción.
            }
        }

        String errorMsg = ""; // Declara el mensaje que se mostrará en la vista de error.
        if (statusCode == 404) { // Si el código es 404, significa recurso o ruta no encontrada.
            errorMsg = "Lo sentimos, el recurso que buscas no está disponible o ha ocurrido un problema técnico en la plataforma SENA GDF."; // Mensaje amigable para error 404.
        } else if (statusCode == 403) { // Si el código es 403, significa acceso denegado.
            errorMsg = "Acceso denegado. No tienes permisos para acceder a esta sección de la plataforma SENA GDF."; // Mensaje amigable para falta de permisos.
        } else if (bdApagada) { // Si se detectó fallo de conexión con base de datos.
            errorMsg = "No se ha podido establecer la conexión con el servidor de la base de datos (MySQL). Asegúrate de que MySQL esté activo en XAMPP."; // Mensaje específico para MySQL apagado.
        } else if (message != null && !message.toString().isEmpty()) { // Si existe un mensaje de error proporcionado por Spring o el servidor.
            errorMsg = message.toString(); // Usa ese mensaje como detalle principal.
        } else { // Si no hay un caso específico ni mensaje disponible.
            errorMsg = "Ha ocurrido un problema interno en el servidor de la plataforma SENA GDF."; // Mensaje genérico para errores internos.
        }

        if (statusCode >= 400) { // Solo registra como error los estados HTTP 400 o superiores.
            if (exception != null) { // Si existe excepción, se registra junto con su traza completa.
                log.error("Error {} detectado en ruta: {} - Mensaje: {}", statusCode, requestUri, errorMsg, (Throwable) exception); // Escribe en logs el código, ruta, mensaje y excepción.
            } else { // Si no existe excepción, registra solo los datos disponibles.
                log.error("Error {} detectado en ruta: {} - Mensaje: {}", statusCode, requestUri, errorMsg); // Escribe en logs el código, ruta y mensaje.
            }
        }

        model.addAttribute("status", statusCode); // Envía a Thymeleaf el código HTTP para mostrarlo en la página.
        model.addAttribute("error", errorTitle); // Envía a Thymeleaf el título del error.
        model.addAttribute("message", errorMsg); // Envía a Thymeleaf el mensaje amigable construido.
        model.addAttribute("bdApagada", bdApagada); // Envía a Thymeleaf si el problema fue detectado como fallo de base de datos.
        model.addAttribute("path", requestUri != null ? requestUri.toString() : ""); // Envía la ruta donde ocurrió el error o cadena vacía si no existe.
        model.addAttribute("timestamp", new Date().toString()); // Envía la fecha y hora del error como texto.

        if (statusCode == 500) { // Si el error es interno del servidor.
            return "500"; // Retorna la plantilla templates/500.html.
        }
        return "error"; // Retorna la plantilla templates/error.html para errores diferentes de 500.
    }
}
