package com.sena.springpoo.controller;

import com.sena.springpoo.models.Producto;
import com.sena.springpoo.models.Usuario;
import com.sena.springpoo.repository.ProductoRepository;
import com.sena.springpoo.repository.UsuarioRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 *  FileUploadController — Carga masiva de archivos hacia la BD
 * ╠══════════════════════════════════════════════════════════════════╣
 *  POST  /upload/productos   → CSV/TXT con productos → tabla productos
 *  POST  /upload/usuarios    → CSV/TXT con usuarios  → tabla usuarios
 * ╠══════════════════════════════════════════════════════════════════╣
 *  Formato CSV esperado (primera fila = encabezado, se ignora):
 *
 *  Productos: nombre,precio,categoria
 *  Ejemplo:   Camiseta,25000.0,Ropa
 *
 *  Usuarios:  primerNombre,segundoNombre,primerApellido,segundoApellido,
 *             tipoDocumento,documento,celular,grupoFormacion,
 *             correoElectronico,contrasena
 *  Ejemplo:   Juan,Carlos,Pérez,López,CC,1234567890,3001234567,
 *             ADSO-2758,juan@correo.com,pass123
 * ╠══════════════════════════════════════════════════════════════════╣
 *  Respuestas HTTP:
 *  200 OK            → carga exitosa con resumen
 *  400 Bad Request   → archivo vacío, extensión inválida o fila con error
 *  500 Server Error  → fallo en base de datos
 * ╚══════════════════════════════════════════════════════════════════╝
 */
@RestController
@RequestMapping("/upload")
@CrossOrigin(origins = "*")
public class FileUploadController {

    private static final Logger log = LogManager.getLogger(FileUploadController.class);

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // ──────────────────────────────────────────────────────────────
    //  POST /upload/productos
    //  Recibe un archivo CSV y guarda cada fila como un Producto
    // ──────────────────────────────────────────────────────────────

    /**
     * Endpoint para cargar productos desde un archivo CSV.
     *
     * @param archivo Archivo CSV con columnas: nombre, precio, categoria
     * @return Resumen de registros insertados y filas con error
     */
    @PostMapping("/productos")
    public ResponseEntity<Map<String, Object>> cargarProductos(
            @RequestParam("archivo") MultipartFile archivo) {

        log.info("Iniciando carga de productos desde archivo: {}", archivo.getOriginalFilename());

        // Validar que el archivo no esté vacío
        ResponseEntity<Map<String, Object>> validacion = validarArchivo(archivo);
        if (validacion != null) return validacion;

        List<Producto> insertados   = new ArrayList<>();
        List<String>   errores      = new ArrayList<>();
        int            filaActual   = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(archivo.getInputStream(), StandardCharsets.UTF_8))) {

            String linea;
            boolean esPrimeraFila = true;

            while ((linea = reader.readLine()) != null) {
                filaActual++;

                // Saltar encabezado
                if (esPrimeraFila) {
                    esPrimeraFila = false;
                    log.debug("Encabezado ignorado: {}", linea);
                    continue;
                }

                // Saltar líneas vacías
                if (linea.isBlank()) continue;

                String[] columnas = linea.split(",", -1);

                // Validar cantidad de columnas
                if (columnas.length < 3) {
                    String msg = "Fila " + filaActual + ": se esperaban 3 columnas (nombre,precio,categoria), se encontraron " + columnas.length;
                    errores.add(msg);
                    log.warn(msg);
                    continue;
                }

                try {
                    String nombre    = columnas[0].trim();
                    Double precio    = Double.parseDouble(columnas[1].trim());
                    String categoria = columnas[2].trim();

                    // Validar campo nombre
                    if (nombre.isEmpty()) {
                        errores.add("Fila " + filaActual + ": el campo 'nombre' está vacío");
                        continue;
                    }

                    Producto p = new Producto(nombre, precio, categoria);
                    productoRepository.save(p);
                    insertados.add(p);
                    log.debug("Producto guardado: {} | {} | {}", nombre, precio, categoria);

                } catch (NumberFormatException e) {
                    String msg = "Fila " + filaActual + ": 'precio' no es un número válido → '" + columnas[1].trim() + "'";
                    errores.add(msg);
                    log.warn(msg);
                }
            }

        } catch (Exception e) {
            log.error("Error leyendo el archivo de productos: {}", e.getMessage(), e);
            return respuestaError(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Error procesando el archivo", e.getMessage());
        }

        log.info("Carga de productos finalizada. Insertados: {}, Errores: {}", insertados.size(), errores.size());
        return respuestaExito(insertados.size(), errores, "productos");
    }

    // ──────────────────────────────────────────────────────────────
    //  POST /upload/usuarios
    //  Recibe un archivo CSV y guarda cada fila como un Usuario
    // ──────────────────────────────────────────────────────────────

    /**
     * Endpoint para cargar usuarios desde un archivo CSV.
     *
     * @param archivo Archivo CSV con columnas: primerNombre, segundoNombre,
     *                primerApellido, segundoApellido, tipoDocumento, documento,
     *                celular, grupoFormacion, correoElectronico, contrasena
     * @return Resumen de registros insertados y filas con error
     */
    @PostMapping("/usuarios")
    public ResponseEntity<Map<String, Object>> cargarUsuarios(
            @RequestParam("archivo") MultipartFile archivo) {

        log.info("Iniciando carga de usuarios desde archivo: {}", archivo.getOriginalFilename());

        ResponseEntity<Map<String, Object>> validacion = validarArchivo(archivo);
        if (validacion != null) return validacion;

        int          totalInsertados = 0;
        int          totalActualizados = 0;
        List<String> errores         = new ArrayList<>();
        int          filaActual      = 0;
        String       separador       = ",";

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(archivo.getInputStream(), StandardCharsets.UTF_8))) {

            String  linea;
            boolean esPrimeraFila = true;

            while ((linea = reader.readLine()) != null) {
                filaActual++;

                if (esPrimeraFila) {
                    esPrimeraFila = false;
                    // Auto-detectar delimitador
                    if (linea.contains(";")) {
                        separador = ";";
                    }
                    log.debug("Encabezado ignorado: {}. Separador detectado: '{}'", linea, separador);
                    continue;
                }

                if (linea.isBlank()) continue;

                String[] columnas = linea.split(separador, -1);

                // Se requieren al menos 10 columnas
                if (columnas.length < 10) {
                    String msg = "Fila " + filaActual + ": se esperaban 10 columnas, se encontraron " + columnas.length;
                    errores.add(msg);
                    log.warn(msg);
                    continue;
                }

                try {
                    String primerNombre      = columnas[0].trim();
                    String segundoNombre     = columnas[1].trim();
                    String primerApellido    = columnas[2].trim();
                    String segundoApellido   = columnas[3].trim();
                    String tipoDocumento     = columnas[4].trim();
                    String documento         = columnas[5].trim();
                    String celular           = columnas[6].trim();
                    String grupoFormacion    = columnas[7].trim();
                    String correoElectronico = columnas[8].trim();
                    String rawPassword       = columnas[9].trim();

                    // Validar campos obligatorios
                    if (documento.isEmpty() || correoElectronico.isEmpty() || primerNombre.isEmpty() || tipoDocumento.isEmpty()) {
                        errores.add("Fila " + filaActual + ": 'primerNombre', 'tipoDocumento', 'documento' y 'correoElectronico' son obligatorios");
                        continue;
                    }

                    // Buscar si el usuario ya existe
                    Optional<Usuario> existenteOpt = usuarioRepository.findByTipoDocumentoAndDocumento(tipoDocumento, documento);
                    Usuario u;
                    boolean esActualizacion = false;

                    if (existenteOpt.isPresent()) {
                        u = existenteOpt.get();
                        esActualizacion = true;
                        log.debug("Fila " + filaActual + ": detectado usuario existente para actualizar (Documento: " + documento + ")");
                    } else {
                        u = new Usuario();
                        u.setTipoDocumento(tipoDocumento);
                        u.setDocumento(documento);
                    }

                    u.setPrimerNombre(primerNombre);
                    u.setSegundoNombre(segundoNombre);
                    u.setPrimerApellido(primerApellido);
                    u.setSegundoApellido(segundoApellido);
                    u.setCelular(celular);
                    u.setGrupoFormacion(grupoFormacion);
                    u.setCorreoElectronico(correoElectronico);

                    if (!rawPassword.isEmpty()) {
                        u.setContrasena(passwordEncoder.encode(rawPassword));
                    } else if (!esActualizacion) {
                        u.setContrasena(""); // Contraseña vacía para nuevo
                    }

                    usuarioRepository.save(u);

                    if (esActualizacion) {
                        totalActualizados++;
                    } else {
                        totalInsertados++;
                    }
                    log.debug("Usuario {} guardado con éxito.", primerNombre);

                } catch (Exception e) {
                    String msg = "Fila " + filaActual + ": error al guardar → " + e.getMessage();
                    errores.add(msg);
                    log.warn(msg);
                }
            }

        } catch (Exception e) {
            log.error("Error leyendo el archivo de usuarios: {}", e.getMessage(), e);
            return respuestaError(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Error procesando el archivo", e.getMessage());
        }

        log.info("Carga de usuarios finalizada. Insertados: {}, Actualizados: {}, Errores: {}", totalInsertados, totalActualizados, errores.size());
        return respuestaExitoUsuarios(totalInsertados, totalActualizados, errores);
    }

    /**
     * Construye la respuesta HTTP exitosa para la carga de usuarios.
     * <p>
     * Devuelve un resumen con la cantidad de registros insertados, actualizados,
     * y los errores encontrados.
     * </p>
     *
     * @param insertados Número de usuarios nuevos creados.
     * @param actualizados Número de usuarios existentes actualizados.
     * @param errores Lista de cadenas con los errores encontrados por fila.
     * @return Una respuesta {@link ResponseEntity} con estado 200 OK y el cuerpo en formato JSON.
     */
    private ResponseEntity<Map<String, Object>> respuestaExitoUsuarios(
            int insertados, int actualizados, List<String> errores) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp",     new Date().toString());
        body.put("entidad",       "usuarios");
        body.put("insertados",    insertados);
        body.put("actualizados",  actualizados);
        body.put("errores",       errores.size());

        if (!errores.isEmpty()) {
            body.put("detalleErrores", errores);
        }

        body.put("mensaje", String.format(
            "Carga finalizada con éxito. %d registros insertados, %d actualizados, %d con errores.",
            insertados, actualizados, errores.size()
        ));

        return ResponseEntity.ok(body);
    }

    // ──────────────────────────────────────────────────────────────
    //  Utilidades privadas
    // ──────────────────────────────────────────────────────────────

    /**
     * Valida que el archivo subido no sea nulo, no esté vacío y tenga una extensión permitida (CSV o TXT).
     *
     * @param archivo El archivo {@link MultipartFile} recibido en la petición.
     * @return {@code null} si el archivo es válido; de lo contrario, retorna un {@link ResponseEntity} con error 400 Bad Request.
     */
    private ResponseEntity<Map<String, Object>> validarArchivo(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            log.warn("Se recibió un archivo vacío o nulo");
            return respuestaError(HttpStatus.BAD_REQUEST,
                    "El archivo está vacío o no fue enviado", null);
        }

        String nombreArchivo = archivo.getOriginalFilename();
        if (nombreArchivo == null ||
            (!nombreArchivo.toLowerCase().endsWith(".csv") &&
             !nombreArchivo.toLowerCase().endsWith(".txt"))) {
            log.warn("Extensión de archivo no permitida: {}", nombreArchivo);
            return respuestaError(HttpStatus.BAD_REQUEST,
                    "Solo se permiten archivos .csv o .txt", "Archivo recibido: " + nombreArchivo);
        }

        return null; // Archivo válido
    }

    /**
     * Construye la respuesta HTTP exitosa estándar con el resumen de la carga (para entidades como productos).
     *
     * @param insertados Número de registros guardados correctamente.
     * @param errores Lista con los detalles de los errores encontrados.
     * @param entidad Nombre de la entidad procesada (ej. "productos").
     * @return Un {@link ResponseEntity} con código 200 OK y el mapa JSON descriptivo.
     */
    private ResponseEntity<Map<String, Object>> respuestaExito(
            int insertados, List<String> errores, String entidad) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp",  new Date().toString());
        body.put("entidad",    entidad);
        body.put("insertados", insertados);
        body.put("errores",    errores.size());

        if (!errores.isEmpty()) {
            body.put("detalleErrores", errores);
        }

        body.put("mensaje", insertados + " registro(s) de " + entidad + " cargados correctamente.");

        HttpStatus status = errores.isEmpty() ? HttpStatus.OK : HttpStatus.OK;
        return ResponseEntity.status(status).body(body);
    }

    /**
     * Construye una respuesta HTTP de error estandarizada en formato JSON.
     *
     * @param status Código de estado HTTP a devolver (ej. HttpStatus.BAD_REQUEST).
     * @param mensaje Mensaje principal del error.
     * @param detalle Información adicional o técnica sobre el error (puede ser {@code null}).
     * @return Un {@link ResponseEntity} con el código de error y el mapa JSON descriptivo.
     */
    private ResponseEntity<Map<String, Object>> respuestaError(
            HttpStatus status, String mensaje, String detalle) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", new Date().toString());
        body.put("status",    status.value());
        body.put("error",     status.getReasonPhrase());
        body.put("mensaje",   mensaje);
        if (detalle != null) body.put("detalle", detalle);

        return ResponseEntity.status(status).body(body);
    }
}