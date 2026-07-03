package com.sena.springpoo.controller;

import com.sena.springpoo.models.Producto;
import com.sena.springpoo.repository.ProductoRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.*;

/**
 * Controlador REST y Web para la gestión de Productos y vistas asociadas al "Nuevo" panel.
 * <p>
 *     Este controlador cumple dos funciones dentro de Springpoo:
 *  * mostrar vistas Thymeleaf como {@code prueba.html} y {@code formulario.html},
 *  * y exponer endpoints REST para consultar, crear, actualizar y eliminar productos.
 *  * </p>
 *  * <p>
 *  * No ejecuta SQL directamente; delega la persistencia a {@link ProductoRepository},
 *  * que trabaja con JdbcTemplate sobre la tabla {@code productos}.
 * ╔══════════════════════════════════════════════════════════════╗
 *  NuevoController
 * ╠══════════════════════════════════════════════════════════════╣
 *  GET    /nuevo/productos      @RequestParam
 *  GET    /nuevo/productos/{id} @PathVariable
 *  POST   /nuevo/productos      @RequestBody
 *  PUT    /nuevo/productos/{id} @ModelAttribute
 *  DELETE /nuevo/productos/{id} @PathVariable
 * ╠══════════════════════════════════════════════════════════════╣
 *  Errores HTTP manejados:
 *  200 OK           → operación exitosa
 *  201 Created      → producto creado
 *  400 Bad Request  → datos inválidos
 *  404 Not Found    → ID no existe
 *  500 Server Error → base de datos apagada u otro error
 * ╚══════════════════════════════════════════════════════════════╝
 * </p>
 */
@Controller // Registra esta clase como controlador Spring MVC; puede retornar vistas o respuestas JSON si el método usa @ResponseBody.
@CrossOrigin(origins = "*") // Permite peticiones desde cualquier origen; útil para pruebas con frontend, aunque en producción se recomienda restringirlo.
public class NuevoController { // Declara la clase pública NuevoController; Spring la detecta como bean durante el arranque.

    // ── Logger ────────────────────────────────────────────────────
    private static final Logger log = LogManager.getLogger(NuevoController.class); // Crea un logger estático para registrar acciones de este controlador.

    @Autowired // Spring inyecta automáticamente una instancia de ProductoRepository.
    private ProductoRepository productoRepository; // Variable que permite consultar, guardar, actualizar y eliminar productos en MySQL.

    /**

     // ──────────────────────────────────────────────────────────────
    //  DTO interno para recibir datos de productos desde JSON o formularios
     * <p>
     * DTO significa Data Transfer Object. Esta clase no representa directamente
     * una tabla, sino los datos que llegan desde una petición HTTP.
     * </p>
    // ──────────────────────────────────────────────────────────────
     */

    static class ProductoRequest { // Clase interna estática usada para transportar nombre, precio y categoría desde el cliente.
        private String nombre; // Variable String; almacena el nombre del producto recibido en la petición.
        private Double precio; // Variable Double; almacena el precio del producto y permite decimales
        private String categoria; // Variable String; almacena la categoría del producto.

        public ProductoRequest() {} // Constructor vacío; Spring lo necesita para crear el objeto antes de llenar sus campos
        public String getNombre()            { return nombre; } // Retorna el valor actual de nombre; puede retornar null si no llegó en la petición.
        public void   setNombre(String n)    { this.nombre = n; } // Recibe un String y lo asigna a nombre; no retorna nada porque es void.
        public Double getPrecio()            { return precio; } // Retorna el precio actual; puede retornar null si no llegó en la petición.
        public void   setPrecio(Double p)    { this.precio = p; } // Recibe un Double y lo asigna a precio; no retorna nada.
        public String getCategoria()         { return categoria; } // Retorna la categoría actual; puede retornar null si no llegó.
        public void   setCategoria(String c) { this.categoria = c; } // Recibe un String y lo asigna a categoria; no retorna nada.
    }

    /**
    // ──────────────────────────────────────────────────────────────
    //  Utilidad: idioma desde header Accept-Language: Detecta el idioma solicitado por el cliente.
     * @param lang valor de la cabecera Accept-Language.
     * @return "en" si el idioma empieza por en; en cualquier otro caso retorna "es".
    // ──────────────────────────────────────────────────────────────
     */

    private String idioma(String lang) { // Métdo privado auxiliar; recibe el idioma del navegador.
        return (lang != null && lang.toLowerCase().startsWith("en")) ? "en" : "es"; // Retorna "en" para inglés o "es" para español.
    }

    /**
     * Selecciona un mensaje según el idioma.
     *
     * @param es texto en español.
     * @param en texto en inglés.
     * @param lang idioma normalizado.
     * @return el texto en inglés si lang es "en"; de lo contrario retorna el texto en español.
     */

    private String msg(String es, String en, String lang) { // Métdo privado auxiliar para mensajes bilingües.
        return "en".equals(lang) ? en : es; // Retorna el mensaje en inglés o español según corresponda.
    }




    /**
     * Construye un cuerpo JSON estándar para errores.
     *
     * // ──────────────────────────────────────────────────────────────
     *     //  Utilidad: construir respuesta de error estándar
     *     // ──────────────────────────────────────────────────────────────
     *
     * @param status estado HTTP del error.
     * @param mensaje mensaje principal.
     * @param detalle explicación adicional.
     * @return Map con timestamp, status, error, mensaje y detalle.
     */


    private Map<String, Object> errorBody(HttpStatus status, String mensaje, String detalle) { // Método privado que retorna un Map para JSON de error.
        Map<String, Object> err = new LinkedHashMap<>(); // Crea un mapa ordenado en memoria para conservar el orden de claves.
        err.put("timestamp", new Date().toString()); // Agrega fecha y hora del error.
        err.put("status", status.value()); // Agrega el código numérico HTTP, por ejemplo 404 o 500.
        err.put("error", status.getReasonPhrase()); // Agrega la frase oficial del estado, como Not Found.
        err.put("mensaje", mensaje); // Agrega el mensaje visible para el usuario.
        err.put("detalle", detalle); // Agrega detalle técnico o recomendación.
        return err; // Retorna el mapa; luego ResponseEntity lo convierte en JSON.
    }

    // ──────────────────────────────────────────────────────────────
    //  Utilidad: detectar si el error es de conexión a MySQL
    // ──────────────────────────────────────────────────────────────

    /**
     * Detecta si una excepción corresponde a base de datos o conexión MySQL.
     *
     * @param e excepción capturada.
     * @return true si parece error JDBC, SQL o conexión; false si es otro tipo de error.
     */

    private boolean esErrorDeBaseDeDatos(Exception e) { // Métdo privado usado para personalizar errores 500 de MySQL.
        Throwable causa = e; // Variable Throwable; empieza con la excepción original.
        while (causa != null) { // Recorre todas las causas internas de la excepción.
            String msg = causa.getMessage(); // Obtiene el mensaje de la causa actual.
            if (msg != null && ( // Valida que el mensaje exista antes de buscar texto.
                    msg.contains("Communications link failure") || // Detecta pérdida de comunicación con MySQL.
                            msg.contains("Connection refused") || // Detecta conexión rechazada.
                            msg.contains("Unable to acquire JDBC") || // Detecta que no se pudo obtener conexión JDBC.
                            msg.contains("could not prepare statement") || // Detecta fallo preparando sentencia SQL.
                            msg.contains("Unable to open JDBC") || // Detecta que no se pudo abrir conexión JDBC.
                            msg.contains("No connection available") || // Detecta falta de conexiones disponibles.
                            msg.contains("Connection is closed") || // Detecta conexión cerrada.
                            causa instanceof SQLException || // Detecta excepción SQL.
                            causa instanceof java.net.ConnectException // Detecta excepción de conexión de red.
            )) return true; // Retorna true si encontró señal de error de base de datos.
            causa = causa.getCause(); // Avanza a la causa interna siguiente.
        }
        return e instanceof DataAccessException; // Retorna true si Spring clasificó el error como acceso a datos.
    }

    // ──────────────────────────────────────────────────────────────
    //  Utilidad: armar respuesta 500
    // ──────────────────────────────────────────────────────────────

    /**
     * Construye una respuesta HTTP 500.
     *
     * @param e excepción capturada.
     * @param idioma idioma normalizado, "es" o "en".
     * @return ResponseEntity con estado 500 y cuerpo JSON.
     */

    private ResponseEntity<Map<String, Object>> error500(Exception e, String idioma) { // Métdo privado que retorna una respuesta JSON de error 500.
        if (esErrorDeBaseDeDatos(e)) { // Verifica si la excepción se relaciona con MySQL o JDBC.
            log.error("❌ Base de datos no disponible — {}", e.getMessage()); // Registra error de base de datos.
            return ResponseEntity // Inicia construcción de ResponseEntity.
                    .status(HttpStatus.INTERNAL_SERVER_ERROR) // Define HTTP 500 Internal Server Error.
                    .body(errorBody( // Define el cuerpo JSON usando errorBody.
                            HttpStatus.INTERNAL_SERVER_ERROR, // Estado incluido dentro del JSON.
                            msg("⚠ Base de datos no disponible",
                                    "⚠ Database not available", idioma), // Mensaje según idioma.
                            msg("MySQL está apagado o no se puede conectar. " +
                                            "Inicia MySQL en XAMPP e intenta de nuevo.",
                                    "MySQL is off or unreachable. " +
                                            "Start MySQL in XAMPP and try again.", idioma) // Detalle según idioma.
                    )); // Retorna ResponseEntity 500 con JSON.
        }
        log.error("❌ Error interno del servidor — {}", e.getMessage(), e); // Registra error interno con traza completa.
        return ResponseEntity // Inicia construcción de respuesta.
                .status(HttpStatus.INTERNAL_SERVER_ERROR) // Define HTTP 500.
                .body(errorBody( // Crea cuerpo JSON.
                        HttpStatus.INTERNAL_SERVER_ERROR, // Estado incluido en el cuerpo.
                        msg("Error interno del servidor",
                                "Internal server error", idioma),
                        e.getMessage() != null ? e.getMessage() : "Unknown error" // Retorna detalle de excepción o texto por defecto.
                )); // Retorna ResponseEntity con error 500.
    }

    // ══════════════════════════════════════════════════════════════
    //  VISTAS THYMELEAF
    // ══════════════════════════════════════════════════════════════

    /**
     * Muestra la vista principal del panel nuevo.
     *
     * @param model modelo usado para enviar datos a Thymeleaf.
     * @return el nombre de la plantilla {@code prueba.html}.
     */
    @GetMapping("/nuevo/prueba") // Mapea peticiones GET a /nuevo/prueba.
    public String mostrarProductos(Model model) { // Método público; recibe Model y retorna String con nombre de vista.
        log.info("Vista solicitada: /nuevo/prueba (Usuarios SENA GDF)"); // Registra que se solicitó la vista.
        model.addAttribute("imagenUrl",
                "https://images.unsplash.com/photo-1522071820081-009f0129c71c?w=800&q=80"); // Envía a la vista una URL de imagen.
        model.addAttribute("imagenTitulo", "SENA GDF"); // Envía a la vista el título de imagen.
        return "prueba"; // Retorna "prueba"; Spring renderiza templates/prueba.html.
    }

    /**
     * Muestra el formulario genérico.
     *
     * @return el nombre de la plantilla {@code formulario.html}.
     */
    @GetMapping("/nuevo/formulario") // Mapea peticiones GET a /nuevo/formulario.
    public String mostrarFormulario() { // Método público sin parámetros; retorna String.
        log.info("Vista solicitada: /nuevo/formulario"); // Registra que se solicitó la vista formulario.
        return "formulario"; // Retorna "formulario"; Spring renderiza templates/formulario.html.
    }

    // ══════════════════════════════════════════════════════════════
    //  GET /nuevo/imagen
    // ══════════════════════════════════════════════════════════════
    
    /**
     * Proveedor de datos para la imagen decorativa del panel nuevo.
     * <p>
     * Se adapta al idioma enviado en la cabecera HTTP.
     * </p>
     *
     * @param lang Cabecera {@code Accept-Language}.
     * @return Un mapa JSON con la URL de la imagen, título y descripción.
     */
    @ResponseBody // Indica que el retorno no es vista HTML, sino cuerpo JSON.
    @GetMapping("/nuevo/imagen") // Mapea peticiones GET a /nuevo/imagen.
    public ResponseEntity<Map<String, Object>> obtenerImagen( // Método público que retorna ResponseEntity con Map convertido a JSON.
                                                              @RequestHeader(value = "Accept-Language", defaultValue = "es") String lang) { // Recibe idioma desde cabecera; por defecto "es".

        log.info("GET /nuevo/imagen solicitado"); // Registra solicitud del endpoint.
        String idioma = idioma(lang); // Normaliza idioma a "es" o "en".

        Map<String, Object> respuesta = new LinkedHashMap<>(); // Crea mapa ordenado para respuesta JSON.
        respuesta.put("imagenUrl", "https://images.unsplash.com/photo-1522071820081-009f0129c71c?w=800&q=80"); // Agrega URL de imagen.
        respuesta.put("titulo", msg("SENA GDF - Gestión de Usuarios", "SENA GDF - User Management", idioma)); // Agrega título según idioma.
        respuesta.put("descripcion", msg("Gestión integral de perfiles de formación",
                "Comprehensive management of training profiles", idioma)); // Agrega descripción según idioma.

        log.info("GET /nuevo/imagen respondido correctamente"); // Registra respuesta correcta.
        return ResponseEntity.ok(respuesta); // Retorna HTTP 200 OK con el mapa como JSON.
    }

    // ══════════════════════════════════════════════════════════════
    //  GET /nuevo/productos — @RequestParam
    //  200 OK  |  500 Base de datos apagada
    // ══════════════════════════════════════════════════════════════
    
    /**
     * Consulta y filtra el catálogo de productos registrados.
     * <p>
     * Soporta filtrado opcional por categoría y precio máximo. Si no se envían
     * parámetros, devuelve todos los productos.
     * </p>
     *
     * @param categoria Filtro exacto por categoría (opcional).
     * @param precioMax Filtro de precio máximo permitido (opcional).
     * @param lang Idioma solicitado para los mensajes de respuesta.
     * @return Una lista JSON con los productos encontrados y el total.
     */
    @ResponseBody // Indica que este método retorna JSON.
    @GetMapping("/nuevo/productos") // Mapea GET /nuevo/productos.
    public ResponseEntity<Map<String, Object>> consultar( // Métdo público de consulta de productos.
                                                          @RequestParam(value = "categoria", required = false) String categoria, // Recibe parámetro opcional categoria.
                                                          @RequestParam(value = "precioMax", required = false) Double precioMax, // Recibe parámetro opcional precioMax.
                                                          @RequestHeader(value = "Accept-Language", defaultValue = "es") String lang) { // Recibe idioma desde cabecera.

        String idioma = idioma(lang); // Convierte idioma recibido a "es" o "en".
        log.info("GET /nuevo/productos | categoria={} | precioMax={}", categoria, precioMax); // Registra filtros recibidos

        try { // Inicia bloque para capturar errores de base de datos.
            List<Producto> resultado; // Variable que almacenará la lista de productos consultados.

            if (categoria != null && precioMax != null) { // Si llegaron ambos filtros.
                log.debug("Filtro: categoria={} AND precioMax={}", categoria, precioMax); // Registra filtro compuesto.
                resultado = productoRepository.findByCategoriaAndPrecioLessThanEqual(categoria, precioMax); // Ejecuta SELECT por categoría y precio <= precioMax.
            } else if (categoria != null) { // Si solo llegó categoría.
                log.debug("Filtro: categoria={}", categoria); // Registra filtro por categoría.
                resultado = productoRepository.findByCategoria(categoria); // Ejecuta SELECT * FROM productos WHERE categoria = ?.
            } else if (precioMax != null) {// Si solo llegó precio máximo.
                log.debug("Filtro: precioMax={}", precioMax); // Registra filtro por precio.
                resultado = productoRepository.findByPrecioLessThanEqual(precioMax); // Ejecuta SELECT * FROM productos WHERE precio <= ?.
            } else { // Si no llegó ningún filtro.
                log.debug("Sin filtros — consultando todos los productos"); // Registra consulta sin filtros.
                resultado = productoRepository.findAll(); // Ejecuta SELECT * FROM productos
            }

            log.info("GET /nuevo/productos exitoso — {} productos encontrados", resultado.size()); // Registra cantidad encontrada.

            Map<String, Object> respuesta = new LinkedHashMap<>(); // Agrega estado lógico 200 al cuerpo.
            respuesta.put("status",    200); // Agrega estado lógico 200 al cuerpo
            respuesta.put("mensaje",   msg("Consulta realizada con éxito",
                    "Query executed successfully", idioma)); // mensaje según idioma.
            respuesta.put("total",     resultado.size()); // Agrega cantidad de productos retornados.
            respuesta.put("productos", resultado); // Agrega lista de productos; Spring la serializa como JSON

            return ResponseEntity.ok(respuesta); // Retorna HTTP 200 OK con JSON de productos.

        } catch (Exception e) { // Captura cualquier error, normalmente de base de datos.
            log.error("Error en GET /nuevo/productos | categoria={} | precioMax={}", categoria, precioMax, e); // Registra error.
            return error500(e, idioma); // Retorna ResponseEntity con HTTP 500 y JSON de error.
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  GET /nuevo/productos/{id} — @PathVariable
    //  200 OK  |  404 Not Found  |  500 BD apagada
    // ══════════════════════════════════════════════════════════════
    
    /**
     * Busca y retorna los detalles de un producto específico según su ID.
     *
     * @param id El identificador único del producto a buscar.
     * @param lang Idioma solicitado para los mensajes.
     * @return El producto encontrado (200 OK) o un error (404 Not Found) si no existe.
     */
    @ResponseBody // Indica que retorna JSON.
    @GetMapping("/nuevo/productos/{id}") // Mapea GET /nuevo/productos/{id}.
    public ResponseEntity<Map<String, Object>> buscarPorId( // Método público que retorna ResponseEntity JSON.
                                                            @PathVariable Long id, // Recibe el ID desde la URL.
                                                            @RequestHeader(value = "Accept-Language", defaultValue = "es") String lang) { // Recibe idioma.

        String idioma = idioma(lang); // Normaliza idioma.
        log.info("GET /nuevo/productos/{}", id); // Registra ID consultado

        try { // Inicia bloque de manejo de errores.
            Optional<Producto> optional = productoRepository.findById(id); // Busca producto en MySQL por ID; puede retornar vacío.

            if (optional.isEmpty()) { // Si no encontró producto
                log.warn("GET /nuevo/productos/{} — producto no encontrado", id); // Registra advertencia
                return ResponseEntity.status(HttpStatus.NOT_FOUND) //Retorna HTTP 404
                        .body(errorBody(HttpStatus.NOT_FOUND,
                                msg("Producto no encontrado con ID: " + id,
                                        "Product not found with ID: "    + id, idioma),
                                msg("Verifica el ID e intenta de nuevo",
                                        "Check the ID and try again", idioma)));
            }

            Producto producto = optional.get(); // Extrae el Producto encontrado.
            log.info("GET /nuevo/productos/{} exitoso — nombre={}", id, producto.getNombre()); // Registra nombre del producto.

            Map<String, Object> respuesta = new LinkedHashMap<>(); // Crea mapa de respuesta.
            respuesta.put("status",   200); // Agrega estado lógico 200.
            respuesta.put("mensaje",  msg("Producto encontrado",
                    "Product found", idioma));
            respuesta.put("producto", producto); // Agrega el producto encontrado al JSON.

            return ResponseEntity.ok(respuesta); // Retorna HTTP 200 OK con producto.

        } catch (Exception e) { // Captura errores de SQL, conexión o ejecución
            log.error("Error en GET /nuevo/productos/{}", id, e); // Registra error.
            return error500(e, idioma);  // Retorna HTTP 500 con JSON de error.
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  POST /nuevo/productos — @RequestBody
    //  201 Created  |  400 Bad Request  |  500 BD apagada
    // ══════════════════════════════════════════════════════════════
    
    /**
     * Crea un nuevo producto y lo almacena en la base de datos.
     * <p>
     * Espera recibir los datos del producto en formato JSON en el cuerpo de la petición.
     * Valida que el nombre no esté vacío y que el precio sea mayor a cero.
     * </p>
     *
     * @param body Datos del producto a crear ({@link ProductoRequest}).
     * @param lang Idioma solicitado para mensajes de validación y éxito.
     * @return Información del producto creado y su ID generado, con estado 201 Created.
     */
    @ResponseBody // Indica que retorna JSON.
    @PostMapping("/nuevo/productos") // Mapea POST /nuevo/productos.
    public ResponseEntity<Map<String, Object>> crear( // Métdo público para crear productos.
                                                      @RequestBody ProductoRequest body, // Recibe JSON y Spring lo convierte en ProductoRequest.
                                                      @RequestHeader(value = "Accept-Language", defaultValue = "es") String lang) { // Recibe idioma. {

        String idioma = idioma(lang); // Normaliza idioma.
        log.info("POST /nuevo/productos | nombre={} | precio={} | categoria={}",
                body.getNombre(), body.getPrecio(), body.getCategoria()); // // Registra datos recibidos.

        // 400 — nombre vacío
        if (body.getNombre() == null || body.getNombre().isBlank()) { // Valida que nombre no sea null ni vacío.
            log.warn("POST /nuevo/productos — validación fallida: nombre vacío o nulo"); // Registra validación fallida.
            return ResponseEntity.status(HttpStatus.BAD_REQUEST) // Retorna HTTP 400.
                    .body(errorBody(HttpStatus.BAD_REQUEST,
                            msg("El campo nombre es obligatorio",
                                    "Field nombre is required", idioma),
                            msg("Envía el campo 'nombre' en el JSON",
                                    "Send 'nombre' field in the JSON", idioma)));
        }

        // 400 — precio inválido
        if (body.getPrecio() == null || body.getPrecio() <= 0) { // Valida que precio exista y sea mayor a cero.
            log.warn("POST /nuevo/productos — validación fallida: precio inválido={}", body.getPrecio()); // Registra precio inválido.
            return ResponseEntity.status(HttpStatus.BAD_REQUEST) // Retorna HTTP 400
                    .body(errorBody(HttpStatus.BAD_REQUEST,
                            msg("El precio debe ser mayor a 0",
                                    "Price must be greater than 0", idioma),
                            msg("Envía un precio válido mayor a $0",
                                    "Send a valid price greater than $0", idioma))); //Retorna JSON de error
        }

        try { // Intenta crear y guardar el producto.
            Producto nuevo    = new Producto(body.getNombre(), body.getPrecio(), body.getCategoria()); // Crea objeto Producto en memoria sin ID.
            Producto guardado = productoRepository.save(nuevo); // Guarda en MySQL; como ID es null, ejecuta INSERT y asigna ID generado.


            log.info("POST /nuevo/productos exitoso - producto creado | id={} | nombre={}",
                    guardado.getId(), guardado.getNombre()); // Registra producto creado.

            Map<String, Object> respuesta = new LinkedHashMap<>();
            respuesta.put("status",   201);
            respuesta.put("mensaje",  msg("Producto guardado en base de datos",
                    "Product saved to database", idioma));
            respuesta.put("id",       guardado.getId());
            respuesta.put("producto", guardado); // Agrega objeto producto completo

            return ResponseEntity.status(HttpStatus.CREATED).body(respuesta); // Retorna HTTP 201 Created con JSON del producto.

        } catch (Exception e) { // Captura errores de base de datos o ejecución
            log.error("Error en POST /nuevo/productos | nombre={} | precio={}", // Registra error.
                    body.getNombre(), body.getPrecio(), e);
            return error500(e, idioma); // Retorna HTTP 500 con JSON de error.

        }
    }

    // ══════════════════════════════════════════════════════════════
    //  PUT /nuevo/productos/{id} — @ModelAttribute + @PathVariable
    //  200 OK  |  404 Not Found  |  500 BD apagada
    // ══════════════════════════════════════════════════════════════
    
    /**
     * Modifica parcialmente o totalmente un producto existente.
     * <p>
     * Los datos pueden ser enviados a través de un formulario (x-www-form-urlencoded).
     * Solo se actualizarán los campos que se envíen de manera válida.
     * </p>
     *
     * @param id El identificador único del producto a actualizar.
     * @param cambios Objeto con los nuevos valores.
     * @param lang Idioma solicitado.
     * @return El producto actualizado con estado 200 OK.
     */
    @ResponseBody // Indica que retorna JSON.
    @PutMapping("/nuevo/productos/{id}") // Mapea PUT /nuevo/productos/{id}.
    public ResponseEntity<Map<String, Object>> modificar( // Métdo público para modificar productos.
                                                          @PathVariable Long id, // Recibe ID desde la URL.
                                                          @ModelAttribute ProductoRequest cambios, // Recibe datos desde formulario x-www-form-urlencoded o multipart.
                                                          @RequestHeader(value = "Accept-Language", defaultValue = "es") String lang) { // Recibe idioma.

        String idioma = idioma(lang); // Normaliza idioma.
        log.info("PUT /nuevo/productos/{} | nombre={} | precio={} | categoria={}",
                id, cambios.getNombre(), cambios.getPrecio(), cambios.getCategoria()); // Registra datos recibidos.

        try { // Intenta buscar y actualizar.

            // 404 — ID no existe
            Optional<Producto> optional = productoRepository.findById(id);// Busca producto en MySQL por ID.
            if (optional.isEmpty()) { // Si no existe.
                log.warn("PUT /nuevo/productos/{} — producto no encontrado", id); // Registra advertencia
                return ResponseEntity.status(HttpStatus.NOT_FOUND) //Retorna HTTP 404.
                        .body(errorBody(HttpStatus.NOT_FOUND,
                                msg("Producto no encontrado con ID: " + id,
                                        "Product not found with ID: "    + id, idioma),
                                msg("Verifica el ID e intenta de nuevo",
                                        "Check the ID and try again", idioma)));
            }

            Producto existente = optional.get(); // Extrae producto existente.
            log.debug("PUT /nuevo/productos/{} - datos actuales: nombre={} | precio={} | categoria={}",
                    id, existente.getNombre(), existente.getPrecio(), existente.getCategoria()); // Registra datos actuales.

            if (cambios.getNombre() != null && !cambios.getNombre().isBlank()) // Si llegó nombre válido.
                existente.setNombre(cambios.getNombre()); // Actualiza nombre en memoria; no retorna nada.
            if (cambios.getPrecio() != null && cambios.getPrecio() > 0) // Si llegó precio válido.
                existente.setPrecio(cambios.getPrecio()); // Actualiza precio en memoria; no retorna nada.
            if (cambios.getCategoria() != null && !cambios.getCategoria().isBlank()) // Si llegó categoría válida.
                existente.setCategoria(cambios.getCategoria()); // Actualiza categoría en memoria; no retorna nada.

            Producto actualizado = productoRepository.save(existente); // Guarda cambios; como tiene ID, ejecuta UPDATE en la tabla productos.

            log.info("PUT /nuevo/productos/{} exitoso - nombre={} | precio={} | categoria={}",
                    id, actualizado.getNombre(), actualizado.getPrecio(), actualizado.getCategoria()); // Registra actualización correcta.

            Map<String, Object> respuesta = new LinkedHashMap<>(); // Crea mapa de respuesta JSON.
            respuesta.put("status",   200); // Agrega estado lógico 200.
            respuesta.put("mensaje",  msg("Producto actualizado en base de datos",
                    "Product updated in database", idioma));
            respuesta.put("producto", actualizado); // Agrega producto actualizado al JSON.


            return ResponseEntity.ok(respuesta); // Retorna HTTP 200 OK con producto actualizado.

        } catch (Exception e) { // Captura errores.
            log.error("Error en PUT /nuevo/productos/{}", id, e); // Registra error.
            return error500(e, idioma); // Retorna HTTP 500 con JSON de error.
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  DELETE /nuevo/productos/{id} — @PathVariable
    //  200 OK  |  404 Not Found  |  500 BD apagada
    // ══════════════════════════════════════════════════════════════
    
    /**
     * Elimina permanentemente un producto de la base de datos dado su ID.
     *
     * @param id El ID del producto a borrar.
     * @param lang Idioma solicitado.
     * @return Confirmación de eliminación (200 OK) o error si el ID no existe (404 Not Found).
     */
    @ResponseBody
    @DeleteMapping("/nuevo/productos/{id}")
    public ResponseEntity<Map<String, Object>> eliminar(
            @PathVariable Long id,
            @RequestHeader(value = "Accept-Language", defaultValue = "es") String lang) {

        String idioma = idioma(lang);
        log.info("DELETE /nuevo/productos/{}", id);

        try {
            // 404 — ID no existe
            if (!productoRepository.existsById(id)) {
                log.warn("DELETE /nuevo/productos/{} — producto no encontrado", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(errorBody(HttpStatus.NOT_FOUND,
                                msg("No se encontró el producto con ID: " + id,
                                        "Product not found with ID: "         + id, idioma),
                                msg("Verifica el ID e intenta de nuevo",
                                        "Check the ID and try again", idioma)));
            }

            productoRepository.deleteById(id); // Ejecuta DELETE FROM productos WHERE id = ? en MySQL.

            log.info("DELETE /nuevo/productos/{} exitoso — producto eliminado", id); // Registra eliminación exitosa.

            Map<String, Object> respuesta = new LinkedHashMap<>();
            respuesta.put("status",  200);
            respuesta.put("mensaje", msg("Producto eliminado de la base de datos",
                    "Product deleted from database", idioma));
            respuesta.put("id",      id);

            return ResponseEntity.ok(respuesta); // Retorna HTTP 200 OK con JSON de confirmación

        } catch (Exception e) { // Captura errores de base de datos o ejecución.
            log.error("Error en DELETE /nuevo/productos/{}", id, e); // Registra error.
            return error500(e, idioma); // Retorna HTTP 500 con JSON de error.
        }
    }
}