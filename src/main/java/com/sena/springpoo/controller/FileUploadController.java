package com.sena.springpoo.controller; // Define el paquete del controlador; esta clase pertenece a la capa controller encargada de recibir peticiones HTTP.

import com.sena.springpoo.models.Producto; // Importa el modelo Producto, usado para representar en memoria una fila de la tabla productos.
import com.sena.springpoo.models.Usuario; // Importa el modelo Usuario, usado para representar en memoria una fila de la tabla usuarios.
import com.sena.springpoo.repository.ProductoRepository; // Importa el repositorio que ejecuta operaciones SQL sobre la tabla productos usando JdbcTemplate.
import com.sena.springpoo.repository.UsuarioRepository; // Importa el repositorio que ejecuta operaciones SQL sobre la tabla productos usando JdbcTemplate.
import org.apache.logging.log4j.LogManager; // Importa LogManager para crear el logger de esta clase.
import org.apache.logging.log4j.Logger; // Importa Logger para registrar información, advertencias y errores en los logs del sistema.
import org.springframework.beans.factory.annotation.Autowired; // Importa @Autowired para que Spring inyecte dependencias automáticamente.
import org.springframework.http.HttpStatus; // Importa códigos HTTP como OK, BAD_REQUEST e INTERNAL_SERVER_ERROR.
import org.springframework.http.ResponseEntity; // Importa ResponseEntity para retornar JSON junto con un estado HTTP.
import org.springframework.web.bind.annotation.*; // Importa anotaciones REST como @RestController, @PostMapping, @RequestParam y @CrossOrigin.
import org.springframework.web.multipart.MultipartFile; // Importa MultipartFile, tipo que representa un archivo subido desde un formulario.
import org.springframework.security.crypto.password.PasswordEncoder; // Importa PasswordEncoder para cifrar contraseñas antes de guardarlas.

import java.io.BufferedReader; // Importa BufferedReader para leer el archivo línea por línea de forma eficiente.
import java.io.InputStreamReader; // Importa InputStreamReader para convertir el flujo de bytes del archivo en texto.
import java.nio.charset.StandardCharsets; // Importa StandardCharsets para leer el archivo usando UTF-8.
import java.util.*; // Importa List, ArrayList, Map, LinkedHashMap, Optional y Date.

/** FUNCIONALIDAD
 * Este controlador permite subir archivos con datos de productos o usuarios
 * Cada fila válida se convierte en un objeto Java y luego se guarda en MySQL
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
@RestController // Marca la clase como controlador REST; todos sus métodos retornan datos directamente en el cuerpo HTTP, normalmente JSON.
@RequestMapping("/upload") // Define el prefijo común de rutas; todos los endpoints de esta clase empiezan con /upload.
@CrossOrigin(origins = "*") // Permite peticiones desde cualquier origen; útil en pruebas, aunque en producción conviene restringirlo.
public class FileUploadController { // Declara la clase pública que Spring detecta y registra como bean controlador.

    private static final Logger log = LogManager.getLogger(FileUploadController.class); // Crea el logger de esta clase para registrar eventos del proceso de carga.

    @Autowired // Spring inyecta automáticamente el repositorio de productos.
    private ProductoRepository productoRepository; // Variable que permite guardar productos en la tabla productos.

    @Autowired // Spring inyecta automáticamente el repositorio de usuarios.
    private UsuarioRepository usuarioRepository; // Variable que permite buscar, insertar o actualizar usuarios en la tabla usuarios.

    @Autowired // Spring inyecta automáticamente el codificador definido en SecurityConfig.
    private PasswordEncoder passwordEncoder; // Variable usada para cifrar contraseñas de usuarios con BCrypt.

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
    @PostMapping("/productos") // Mapea peticiones POST a /upload/productos.
    public ResponseEntity<Map<String, Object>> cargarProductos( // Método REST que retorna un ResponseEntity con un Map convertido a JSON.
                                                                @RequestParam("archivo") MultipartFile archivo) { // Recibe el archivo enviado en el campo multipart llamado archivo.

        log.info("Iniciando carga de productos desde archivo: {}", archivo.getOriginalFilename()); // Registra el nombre original del archivo recibido.

        ResponseEntity<Map<String, Object>> validacion = validarArchivo(archivo); // Valida si el archivo existe, no está vacío y tiene extensión .csv o .txt.
        if (validacion != null) return validacion; // Si la validación falla, retorna inmediatamente el error 400.

        List<Producto> insertados = new ArrayList<>(); // Lista en memoria donde se guardan los productos insertados correctamente.
        List<String> errores = new ArrayList<>(); // Lista en memoria donde se guardan mensajes de error por fila.
        int filaActual = 0; // Contador de filas; permite indicar en qué línea del archivo ocurrió un error.

        try (BufferedReader reader = new BufferedReader( // Abre un lector de texto que se cerrará automáticamente al terminar.
                new InputStreamReader(archivo.getInputStream(), StandardCharsets.UTF_8))) { // Convierte el archivo subido a texto usando UTF-8.

            String linea; // Variable que almacenará temporalmente cada línea leída del archivo.
            boolean esPrimeraFila = true; // Controla si la línea actual es el encabezado del CSV.

            while ((linea = reader.readLine()) != null) { // Lee el archivo línea por línea hasta llegar al final.

                filaActual++; // Incrementa el número de fila procesada.

                // Saltar encabezado
                if (esPrimeraFila) { // Verifica si se está leyendo la primera fila.
                    esPrimeraFila = false; // Marca que ya se procesó la primera fila.
                    log.debug("Encabezado ignorado: {}", linea); // Registra el encabezado ignorado.
                    continue; // Salta al siguiente ciclo sin procesar esta fila como producto.
                }

                // Saltar líneas vacías
                if (linea.isBlank()) continue; // Si la línea está vacía, la ignora y continúa con la siguiente.

                String[] columnas = linea.split(",", -1); // Divide la línea por comas; -1 conserva columnas vacías

                // Validar cantidad de columnas
                if (columnas.length < 3) { // Valida que existan al menos tres columnas: nombre, precio y categoria.
                    String msg = "Fila " + filaActual + ": se esperaban 3 columnas (nombre,precio,categoria), se encontraron " + columnas.length; // Construye mensaje de error específico.
                    errores.add(msg); // Agrega el error a la lista de errores.
                    log.warn(msg); // Registra la advertencia en logs.
                    continue; // Omite esta fila y sigue con la siguiente.
                }

                try { // Intenta convertir la fila en un Producto y guardarlo.
                    String nombre = columnas[0].trim(); // Obtiene el nombre desde la primera columna y elimina espacios alrededor.
                    Double precio = Double.parseDouble(columnas[1].trim()); // Convierte la segunda columna a Double; puede lanzar NumberFormatException.
                    String categoria = columnas[2].trim(); // Obtiene la categoría desde la tercera columna.

                    // Validar campo nombre
                    if (nombre.isEmpty()) { // Valida que el nombre no esté vacío.
                        errores.add("Fila " + filaActual + ": el campo 'nombre' está vacío"); // Guarda error si falta nombre.
                        continue; // No guarda esta fila y pasa a la siguiente.
                    }

                    Producto p = new Producto(nombre, precio, categoria); // Crea un objeto Producto en memoria con los datos leídos.
                    productoRepository.save(p); // Guarda el producto en MySQL; ejecuta INSERT INTO productos y asigna ID generado.
                    insertados.add(p); // Agrega el producto a la lista de insertados.
                    log.debug("Producto guardado: {} | {} | {}", nombre, precio, categoria); // Registra detalle del producto guardado.

                } catch (NumberFormatException e) { // Captura error cuando el precio no puede convertirse a número.
                    String msg = "Fila " + filaActual + ": 'precio' no es un número válido -> '" + columnas[1].trim() + "'"; // Construye mensaje específico para precio inválido.
                    errores.add(msg); // Agrega el error a la lista.
                    log.warn(msg); // Registra la advertencia.

                }
            }

        } catch (Exception e) { // Captura errores al leer el archivo o acceder a su contenido.
            log.error("Error leyendo el archivo de productos: {}", e.getMessage(), e); // Registra el error con traza completa.
            return respuestaError(HttpStatus.INTERNAL_SERVER_ERROR, // Retorna estado HTTP 500.
                    "Error procesando el archivo", e.getMessage()); // Devuelve mensaje JSON con detalle técnico.
        }

        log.info("Carga de productos finalizada. Insertados: {}, Errores: {}", insertados.size(), errores.size()); // Registra resumen final.
        return respuestaExito(insertados.size(), errores, "productos"); // Retorna JSON con resumen de productos cargados.
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
    @PostMapping("/usuarios") // Mapea peticiones POST a /upload/usuarios.
    public ResponseEntity<Map<String, Object>> cargarUsuarios( // MéTdo REST para carga masiva de usuarios.
            @RequestParam("archivo") MultipartFile archivo) { // Recibe el archivo enviado en el campo multipart llamado archivo.

        log.info("Iniciando carga de usuarios desde archivo: {}", archivo.getOriginalFilename()); // Registra el nombre del archivo recibido.

        ResponseEntity<Map<String, Object>> validacion = validarArchivo(archivo); // Valida archivo nulo, vacío o extensión inválida.
        if (validacion != null) return validacion; // Si hay error de validación, retorna HTTP 400.

        int totalInsertados = 0; // Contador de usuarios nuevos insertados en la base de datos.
        int totalActualizados = 0; // Contador de usuarios existentes actualizados.
        List<String> errores = new ArrayList<>(); // Lista donde se almacenan errores por fila.
        int filaActual = 0; // Contador de filas procesadas.
        String separador = ","; // Separador por defecto para el CSV; puede cambiar a punto y coma.

        try (BufferedReader reader = new BufferedReader( // Crea lector de texto con cierre automático.
                new InputStreamReader(archivo.getInputStream(), StandardCharsets.UTF_8))) { // Lee el archivo como UTF-8.

            String  linea; // Variable temporal que contiene cada línea leída.
            boolean esPrimeraFila = true; // Indica si la línea actual corresponde al encabezado.

            while ((linea = reader.readLine()) != null) { // Lee cada línea hasta finalizar el archivo.
                filaActual++; // Aumenta el contador de fila.

                if (esPrimeraFila) { // Si es la primera fila, se trata como encabezado.
                    esPrimeraFila = false; // Marca que el encabezado ya fue leído.
                    if (linea.contains(";")) { // Detecta si el archivo usa punto y coma como separador.
                        separador = ";"; // Cambia el separador a punto y coma.
                    }
                    log.debug("Encabezado ignorado: {}. Separador detectado: '{}'", linea, separador); // Registra encabezado y separador.
                    continue; // Salta el encabezado y continúa con los datos.
                }

                if (linea.isBlank()) continue; // Ignora líneas vacías.

                String[] columnas = linea.split(separador, -1); // Divide la línea usando el separador detectado y conserva campos vacíos.

                // Se requieren al menos 10 columnas
                if (columnas.length < 10) { // Valida que existan las 10 columnas requeridas para Usuario.
                    String msg = "Fila " + filaActual + ": se esperaban 10 columnas, se encontraron " + columnas.length; // Crea mensaje de error.
                    errores.add(msg); // Guarda el error.
                    log.warn(msg); // Registra advertencia.
                    continue; // Omite esta fila.

            }

                try { // Intenta convertir la fila en Usuario y guardar o actualizar.
                    String primerNombre = columnas[0].trim(); // Lee primerNombre desde columna 0.
                    String segundoNombre = columnas[1].trim(); // Lee segundoNombre desde columna 1.
                    String primerApellido = columnas[2].trim(); // Lee primerApellido desde columna 2.
                    String segundoApellido = columnas[3].trim(); // Lee segundoApellido desde columna 3.
                    String tipoDocumento = columnas[4].trim(); // Lee tipoDocumento desde columna 4.
                    String documento = columnas[5].trim(); // Lee documento desde columna 5.
                    String celular = columnas[6].trim(); // Lee celular desde columna 6.
                    String grupoFormacion = columnas[7].trim(); // Lee grupoFormacion desde columna 7.
                    String correoElectronico = columnas[8].trim(); // Lee correoElectronico desde columna 8.
                    String rawPassword = columnas[9].trim(); // Lee contraseña en texto plano desde columna 9.

                    // Validar campos obligatorios
                    if (documento.isEmpty() || correoElectronico.isEmpty() || primerNombre.isEmpty() || tipoDocumento.isEmpty()) { // Valida campos obligatorios.
                        errores.add("Fila " + filaActual + ": 'primerNombre', 'tipoDocumento', 'documento' y 'correoElectronico' son obligatorios"); // Guarda error de campos faltantes.
                        continue; // Omite la fila incompleta.
                    }

                    // Buscar si el usuario ya existe
                    Optional<Usuario> existenteOpt = usuarioRepository.findByTipoDocumentoAndDocumento(tipoDocumento, documento); // Busca en MySQL si ya existe un usuario con ese tipo y número de documento.
                    Usuario u; // Variable que representará al usuario a guardar.
                    boolean esActualizacion = false; // Indica si se hará UPDATE en vez de INSERT.

                    if (existenteOpt.isPresent()) { // Si el usuario ya existe en la base de datos.
                        u = existenteOpt.get(); // Usa el usuario existente recuperado desde MySQL.
                        esActualizacion = true; // Marca que la operación será actualización.
                        log.debug("Fila " + filaActual + ": detectado usuario existente para actualizar (Documento: " + documento + ")"); // Registra detección de usuario existente.
                    } else { // Si el usuario no existe.
                        u = new Usuario(); // Crea un nuevo objeto Usuario en memoria.
                        u.setTipoDocumento(tipoDocumento); // Asigna tipo de documento al usuario nuevo.
                        u.setDocumento(documento); // Asigna número de documento al usuario nuevo.
                    }

                    u.setPrimerNombre(primerNombre); // Actualiza o asigna primer nombre.
                    u.setSegundoNombre(segundoNombre); // Actualiza o asigna segundo nombre.
                    u.setPrimerApellido(primerApellido); // Actualiza o asigna primer apellido.
                    u.setSegundoApellido(segundoApellido); // Actualiza o asigna segundo apellido.
                    u.setCelular(celular); // Actualiza o asigna celular.
                    u.setGrupoFormacion(grupoFormacion); // Actualiza o asigna grupo de formación.
                    u.setCorreoElectronico(correoElectronico); // Actualiza o asigna correo electrónico.

                    if (!rawPassword.isEmpty()) { // Si el archivo trae contraseña.
                        u.setContrasena(passwordEncoder.encode(rawPassword)); // Cifra la contraseña con BCrypt antes de guardarla.
                    } else if (!esActualizacion) { // Si es usuario nuevo y no llegó contraseña.
                        u.setContrasena(""); // Guarda contraseña vacía para usuario nuevo sin contraseña.

                    }

                    usuarioRepository.save(u); // Guarda en MySQL; hace INSERT si no tiene ID o UPDATE si ya tiene ID.

                    if (esActualizacion) { // Si el usuario ya existía.
                        totalActualizados++;// Incrementa contador de actualizados.
                    } else { // Si el usuario era nuevo.
                        totalInsertados++; // Incrementa contador de insertados.
                    }
                    log.debug("Usuario {} guardado con éxito.", primerNombre);// Registra usuario procesado correctamente.

                } catch (Exception e) { // Captura errores de conversión, validación o base de datos en una fila específica.
                    String msg = "Fila " + filaActual + ": error al guardar → " + e.getMessage(); // Construye mensaje de error por fila.
                    errores.add(msg); // Guarda el error en la lista.
                    log.warn(msg); // Registra advertencia sin detener toda la carga.
                }
            }

        } catch (Exception e) { // Captura errores generales al leer el archivo.
            log.error("Error leyendo el archivo de usuarios: {}", e.getMessage(), e); // Registra error completo.
            return respuestaError(HttpStatus.INTERNAL_SERVER_ERROR, // Retorna HTTP 500.
                    "Error procesando el archivo", e.getMessage()); // Devuelve JSON con detalle del fallo.
        }

        log.info("Carga de usuarios finalizada. Insertados: {}, Actualizados: {}, Errores: {}", totalInsertados, totalActualizados, errores.size()); // Registra resumen final.
        return respuestaExitoUsuarios(totalInsertados, totalActualizados, errores); // Retorna resumen JSON específico para usuarios.
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
    private ResponseEntity<Map<String, Object>> respuestaExitoUsuarios( // Método privado auxiliar usado solo dentro de esta clase.
                                                                        int insertados, int actualizados, List<String> errores) { // Recibe contadores y lista de errores.

        Map<String, Object> body = new LinkedHashMap<>(); // Crea mapa ordenado que será convertido a JSON.
        body.put("timestamp", new Date().toString()); // Agrega fecha y hora de la respuesta.
        body.put("entidad", "usuarios"); // Indica que la entidad procesada fue usuarios.
        body.put("insertados", insertados); // Agrega número de usuarios insertados.
        body.put("actualizados", actualizados); // Agrega número de usuarios actualizados.
        body.put("errores", errores.size()); // Agrega cantidad de errores encontrados.

        if (!errores.isEmpty()) { // Si hubo errores en alguna fila.
            body.put("detalleErrores", errores); // Agrega la lista completa de errores al JSON.
        }

        body.put("mensaje", String.format( // Agrega mensaje final usando formato.
                "Carga finalizada con éxito. %d registros insertados, %d actualizados, %d con errores.", // Plantilla del mensaje.
                insertados, actualizados, errores.size() // Valores que reemplazan los %d.
        ));

        return ResponseEntity.ok(body); // Retorna HTTP 200 OK con el resumen.
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
    private ResponseEntity<Map<String, Object>> validarArchivo(MultipartFile archivo) { // Método privado que centraliza validaciones del archivo.
        if (archivo == null || archivo.isEmpty()) { // Valida si el archivo no llegó o llegó vacío.
            log.warn("Se recibió un archivo vacío o nulo"); // Registra advertencia.
            return respuestaError(HttpStatus.BAD_REQUEST, // Retorna HTTP 400.
                    "El archivo está vacío o no fue enviado", null); // Mensaje de error sin detalle adicional.
        }

        String nombreArchivo = archivo.getOriginalFilename(); // Obtiene el nombre original del archivo subido.
        if (nombreArchivo == null || // Valida si no existe nombre.
                (!nombreArchivo.toLowerCase().endsWith(".csv") && // Valida si no termina en .csv.
                        !nombreArchivo.toLowerCase().endsWith(".txt"))) { // Valida si no termina en .txt.
            log.warn("Extensión de archivo no permitida: {}", nombreArchivo); // Registra extensión inválida.
            return respuestaError(HttpStatus.BAD_REQUEST, // Retorna HTTP 400.
                    "Solo se permiten archivos .csv o .txt", "Archivo recibido: " + nombreArchivo); // Explica la extensión permitida.
        }

        return null; // Retorna null para indicar que el archivo es válido
    }

    /**
     * Construye la respuesta HTTP exitosa estándar con el resumen de la carga (para entidades como productos).
     *
     * @param insertados Número de registros guardados correctamente.
     * @param errores Lista con los detalles de los errores encontrados.
     * @param entidad Nombre de la entidad procesada (ej. "productos").
     * @return Un {@link ResponseEntity} con código 200 OK y el mapa JSON descriptivo.
     */
    private ResponseEntity<Map<String, Object>> respuestaExito( // Método privado para construir respuesta de éxito genérica.
                                                                int insertados, List<String> errores, String entidad) { // Recibe cantidad, errores y nombre de entidad.

        Map<String, Object> body = new LinkedHashMap<>(); // Crea mapa ordenado para respuesta JSON.
        body.put("timestamp", new Date().toString()); // Agrega fecha y hora.
        body.put("entidad", entidad); // Agrega nombre de la entidad procesada.
        body.put("insertados", insertados); // Agrega número de registros insertados.
        body.put("errores", errores.size()); // Agrega cantidad de errores.

        if (!errores.isEmpty()) { // Si hubo errores.
            body.put("detalleErrores", errores); // Agrega detalles de errores al JSON.
        }

        body.put("mensaje", insertados + " registro(s) de " + entidad + " cargados correctamente."); // Agrega mensaje resumen.

        HttpStatus status = errores.isEmpty() ? HttpStatus.OK : HttpStatus.OK; // Define estado HTTP; actualmente siempre queda 200 OK.
        return ResponseEntity.status(status).body(body); // Retorna el estado y el cuerpo JSON
    }

    /**
     * Construye una respuesta HTTP de error estandarizada en formato JSON.
     *
     * @param status Código de estado HTTP a devolver (ej. HttpStatus.BAD_REQUEST).
     * @param mensaje Mensaje principal del error.
     * @param detalle Información adicional o técnica sobre el error (puede ser {@code null}).
     * @return Un {@link ResponseEntity} con el código de error y el mapa JSON descriptivo.
     */
    private ResponseEntity<Map<String, Object>> respuestaError( // Método privado para estandarizar errores.
                                                                HttpStatus status, String mensaje, String detalle) { // Recibe estado HTTP, mensaje y detalle.

        Map<String, Object> body = new LinkedHashMap<>(); // Crea mapa ordenado para JSON.
        body.put("timestamp", new Date().toString()); // Agrega fecha y hora del error.
        body.put("status", status.value()); // Agrega número HTTP, por ejemplo 400 o 500.
        body.put("error", status.getReasonPhrase()); // Agrega texto del estado, por ejemplo Bad Request.
        body.put("mensaje", mensaje); // Agrega mensaje principal.
        if (detalle != null) body.put("detalle", detalle); // Agrega detalle solo si no es null.

        return ResponseEntity.status(status).body(body); // Retorna la respuesta HTTP con su JSON.
    }
}