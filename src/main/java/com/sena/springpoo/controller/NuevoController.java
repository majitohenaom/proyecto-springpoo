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
@Controller
@CrossOrigin(origins = "*")
public class NuevoController {

    // ── Logger ────────────────────────────────────────────────────
    private static final Logger log = LogManager.getLogger(NuevoController.class);

    @Autowired
    private ProductoRepository productoRepository;

    // ──────────────────────────────────────────────────────────────
    //  DTO interno
    // ──────────────────────────────────────────────────────────────
    static class ProductoRequest {
        private String nombre;
        private Double precio;
        private String categoria;

        public ProductoRequest() {}
        public String getNombre()            { return nombre; }
        public void   setNombre(String n)    { this.nombre = n; }
        public Double getPrecio()            { return precio; }
        public void   setPrecio(Double p)    { this.precio = p; }
        public String getCategoria()         { return categoria; }
        public void   setCategoria(String c) { this.categoria = c; }
    }

    // ──────────────────────────────────────────────────────────────
    //  Utilidad: idioma desde header Accept-Language
    // ──────────────────────────────────────────────────────────────
    private String idioma(String lang) {
        return (lang != null && lang.toLowerCase().startsWith("en")) ? "en" : "es";
    }
    private String msg(String es, String en, String lang) {
        return "en".equals(lang) ? en : es;
    }

    // ──────────────────────────────────────────────────────────────
    //  Utilidad: construir respuesta de error estándar
    // ──────────────────────────────────────────────────────────────
    private Map<String, Object> errorBody(HttpStatus status, String mensaje, String detalle) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("timestamp", new Date().toString());
        err.put("status",    status.value());
        err.put("error",     status.getReasonPhrase());
        err.put("mensaje",   mensaje);
        err.put("detalle",   detalle);
        return err;
    }

    // ──────────────────────────────────────────────────────────────
    //  Utilidad: detectar si el error es de conexión a MySQL
    // ──────────────────────────────────────────────────────────────
    private boolean esErrorDeBaseDeDatos(Exception e) {
        Throwable causa = e;
        while (causa != null) {
            String msg = causa.getMessage();
            if (msg != null && (
                    msg.contains("Communications link failure") ||
                            msg.contains("Connection refused")         ||
                            msg.contains("Unable to acquire JDBC")     ||
                            msg.contains("could not prepare statement")||
                            msg.contains("Unable to open JDBC")        ||
                            msg.contains("No connection available")    ||
                            msg.contains("Connection is closed")       ||
                            causa instanceof SQLException              ||
                            causa instanceof java.net.ConnectException
            )) return true;
            causa = causa.getCause();
        }
        return e instanceof DataAccessException;
    }

    // ──────────────────────────────────────────────────────────────
    //  Utilidad: armar respuesta 500
    // ──────────────────────────────────────────────────────────────
    private ResponseEntity<Map<String, Object>> error500(Exception e, String idioma) {
        if (esErrorDeBaseDeDatos(e)) {
            log.error("❌ Base de datos no disponible — {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorBody(
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            msg("⚠ Base de datos no disponible",
                                    "⚠ Database not available", idioma),
                            msg("MySQL está apagado o no se puede conectar. " +
                                            "Inicia MySQL en XAMPP e intenta de nuevo.",
                                    "MySQL is off or unreachable. " +
                                            "Start MySQL in XAMPP and try again.", idioma)
                    ));
        }
        log.error("❌ Error interno del servidor — {}", e.getMessage(), e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorBody(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        msg("Error interno del servidor",
                                "Internal server error", idioma),
                        e.getMessage() != null ? e.getMessage() : "Unknown error"
                ));
    }

    // ══════════════════════════════════════════════════════════════
    //  VISTAS THYMELEAF
    // ══════════════════════════════════════════════════════════════

    /**
     * Retorna la vista principal de pruebas para el panel (Usuarios SENA GDF).
     *
     * @param model Modelo para inyectar la URL de la imagen y el título en la vista.
     * @return El nombre de la plantilla Thymeleaf {@code "prueba"}.
     */
    @GetMapping("/nuevo/prueba")
    public String mostrarProductos(Model model) {
        log.info("Vista solicitada: /nuevo/prueba (Usuarios SENA GDF)");
        model.addAttribute("imagenUrl",
                "https://images.unsplash.com/photo-1522071820081-009f0129c71c?w=800&q=80");
        model.addAttribute("imagenTitulo", "SENA GDF");
        return "prueba";
    }

    /**
     * Retorna la vista del formulario genérico del nuevo panel.
     *
     * @return El nombre de la plantilla Thymeleaf {@code "formulario"}.
     */
    @GetMapping("/nuevo/formulario")
    public String mostrarFormulario() {
        log.info("Vista solicitada: /nuevo/formulario");
        return "formulario";
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
    @ResponseBody
    @GetMapping("/nuevo/imagen")
    public ResponseEntity<Map<String, Object>> obtenerImagen(
            @RequestHeader(value = "Accept-Language", defaultValue = "es") String lang) {

        log.info("GET /nuevo/imagen solicitado");
        String idioma = idioma(lang);

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("imagenUrl",   "https://images.unsplash.com/photo-1522071820081-009f0129c71c?w=800&q=80");
        respuesta.put("titulo",      msg("SENA GDF - Gestión de Usuarios", "SENA GDF - User Management", idioma));
        respuesta.put("descripcion", msg("Gestión integral de perfiles de formación",
                "Comprehensive management of training profiles", idioma));

        log.info("GET /nuevo/imagen respondido correctamente");
        return ResponseEntity.ok(respuesta);
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
    @ResponseBody
    @GetMapping("/nuevo/productos")
    public ResponseEntity<Map<String, Object>> consultar(
            @RequestParam(value = "categoria", required = false) String categoria,
            @RequestParam(value = "precioMax", required = false) Double precioMax,
            @RequestHeader(value = "Accept-Language", defaultValue = "es") String lang) {

        String idioma = idioma(lang);
        log.info("GET /nuevo/productos | categoria={} | precioMax={}", categoria, precioMax);

        try {
            List<Producto> resultado;

            if (categoria != null && precioMax != null) {
                log.debug("Filtro: categoria={} AND precioMax={}", categoria, precioMax);
                resultado = productoRepository.findByCategoriaAndPrecioLessThanEqual(categoria, precioMax);
            } else if (categoria != null) {
                log.debug("Filtro: categoria={}", categoria);
                resultado = productoRepository.findByCategoria(categoria);
            } else if (precioMax != null) {
                log.debug("Filtro: precioMax={}", precioMax);
                resultado = productoRepository.findByPrecioLessThanEqual(precioMax);
            } else {
                log.debug("Sin filtros — consultando todos los productos");
                resultado = productoRepository.findAll();
            }

            log.info("GET /nuevo/productos exitoso — {} productos encontrados", resultado.size());

            Map<String, Object> respuesta = new LinkedHashMap<>();
            respuesta.put("status",    200);
            respuesta.put("mensaje",   msg("Consulta realizada con éxito",
                    "Query executed successfully", idioma));
            respuesta.put("total",     resultado.size());
            respuesta.put("productos", resultado);

            return ResponseEntity.ok(respuesta);

        } catch (Exception e) {
            log.error("Error en GET /nuevo/productos | categoria={} | precioMax={}", categoria, precioMax, e);
            return error500(e, idioma);
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
    @ResponseBody
    @GetMapping("/nuevo/productos/{id}")
    public ResponseEntity<Map<String, Object>> buscarPorId(
            @PathVariable Long id,
            @RequestHeader(value = "Accept-Language", defaultValue = "es") String lang) {

        String idioma = idioma(lang);
        log.info("GET /nuevo/productos/{}", id);

        try {
            Optional<Producto> optional = productoRepository.findById(id);

            if (optional.isEmpty()) {
                log.warn("GET /nuevo/productos/{} — producto no encontrado", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(errorBody(HttpStatus.NOT_FOUND,
                                msg("Producto no encontrado con ID: " + id,
                                        "Product not found with ID: "    + id, idioma),
                                msg("Verifica el ID e intenta de nuevo",
                                        "Check the ID and try again", idioma)));
            }

            Producto producto = optional.get();
            log.info("GET /nuevo/productos/{} exitoso — nombre={}", id, producto.getNombre());

            Map<String, Object> respuesta = new LinkedHashMap<>();
            respuesta.put("status",   200);
            respuesta.put("mensaje",  msg("Producto encontrado",
                    "Product found", idioma));
            respuesta.put("producto", producto);

            return ResponseEntity.ok(respuesta);

        } catch (Exception e) {
            log.error("Error en GET /nuevo/productos/{}", id, e);
            return error500(e, idioma);
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
    @ResponseBody
    @PostMapping("/nuevo/productos")
    public ResponseEntity<Map<String, Object>> crear(
            @RequestBody ProductoRequest body,
            @RequestHeader(value = "Accept-Language", defaultValue = "es") String lang) {

        String idioma = idioma(lang);
        log.info("POST /nuevo/productos | nombre={} | precio={} | categoria={}",
                body.getNombre(), body.getPrecio(), body.getCategoria());

        // 400 — nombre vacío
        if (body.getNombre() == null || body.getNombre().isBlank()) {
            log.warn("POST /nuevo/productos — validación fallida: nombre vacío o nulo");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(errorBody(HttpStatus.BAD_REQUEST,
                            msg("El campo nombre es obligatorio",
                                    "Field nombre is required", idioma),
                            msg("Envía el campo 'nombre' en el JSON",
                                    "Send 'nombre' field in the JSON", idioma)));
        }

        // 400 — precio inválido
        if (body.getPrecio() == null || body.getPrecio() <= 0) {
            log.warn("POST /nuevo/productos — validación fallida: precio inválido={}", body.getPrecio());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(errorBody(HttpStatus.BAD_REQUEST,
                            msg("El precio debe ser mayor a 0",
                                    "Price must be greater than 0", idioma),
                            msg("Envía un precio válido mayor a $0",
                                    "Send a valid price greater than $0", idioma)));
        }

        try {
            Producto nuevo    = new Producto(body.getNombre(), body.getPrecio(), body.getCategoria());
            Producto guardado = productoRepository.save(nuevo);

            log.info("POST /nuevo/productos exitoso — producto creado | id={} | nombre={}",
                    guardado.getId(), guardado.getNombre());

            Map<String, Object> respuesta = new LinkedHashMap<>();
            respuesta.put("status",   201);
            respuesta.put("mensaje",  msg("Producto guardado en base de datos",
                    "Product saved to database", idioma));
            respuesta.put("id",       guardado.getId());
            respuesta.put("producto", guardado);

            return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);

        } catch (Exception e) {
            log.error("Error en POST /nuevo/productos | nombre={} | precio={}",
                    body.getNombre(), body.getPrecio(), e);
            return error500(e, idioma);
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
    @ResponseBody
    @PutMapping("/nuevo/productos/{id}")
    public ResponseEntity<Map<String, Object>> modificar(
            @PathVariable Long id,
            @ModelAttribute ProductoRequest cambios,
            @RequestHeader(value = "Accept-Language", defaultValue = "es") String lang) {

        String idioma = idioma(lang);
        log.info("PUT /nuevo/productos/{} | nombre={} | precio={} | categoria={}",
                id, cambios.getNombre(), cambios.getPrecio(), cambios.getCategoria());

        try {
            // 404 — ID no existe
            Optional<Producto> optional = productoRepository.findById(id);
            if (optional.isEmpty()) {
                log.warn("PUT /nuevo/productos/{} — producto no encontrado", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(errorBody(HttpStatus.NOT_FOUND,
                                msg("Producto no encontrado con ID: " + id,
                                        "Product not found with ID: "    + id, idioma),
                                msg("Verifica el ID e intenta de nuevo",
                                        "Check the ID and try again", idioma)));
            }

            Producto existente = optional.get();
            log.debug("PUT /nuevo/productos/{} — datos actuales: nombre={} | precio={} | categoria={}",
                    id, existente.getNombre(), existente.getPrecio(), existente.getCategoria());

            if (cambios.getNombre()    != null && !cambios.getNombre().isBlank())
                existente.setNombre(cambios.getNombre());
            if (cambios.getPrecio()    != null && cambios.getPrecio() > 0)
                existente.setPrecio(cambios.getPrecio());
            if (cambios.getCategoria() != null && !cambios.getCategoria().isBlank())
                existente.setCategoria(cambios.getCategoria());

            Producto actualizado = productoRepository.save(existente);

            log.info("PUT /nuevo/productos/{} exitoso — nombre={} | precio={} | categoria={}",
                    id, actualizado.getNombre(), actualizado.getPrecio(), actualizado.getCategoria());

            Map<String, Object> respuesta = new LinkedHashMap<>();
            respuesta.put("status",   200);
            respuesta.put("mensaje",  msg("Producto actualizado en base de datos",
                    "Product updated in database", idioma));
            respuesta.put("producto", actualizado);

            return ResponseEntity.ok(respuesta);

        } catch (Exception e) {
            log.error("Error en PUT /nuevo/productos/{}", id, e);
            return error500(e, idioma);
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

            productoRepository.deleteById(id);

            log.info("DELETE /nuevo/productos/{} exitoso — producto eliminado", id);

            Map<String, Object> respuesta = new LinkedHashMap<>();
            respuesta.put("status",  200);
            respuesta.put("mensaje", msg("Producto eliminado de la base de datos",
                    "Product deleted from database", idioma));
            respuesta.put("id",      id);

            return ResponseEntity.ok(respuesta);

        } catch (Exception e) {
            log.error("Error en DELETE /nuevo/productos/{}", id, e);
            return error500(e, idioma);
        }
    }
}