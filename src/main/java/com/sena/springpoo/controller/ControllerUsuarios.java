package com.sena.springpoo.controller; // Define el paquete del controlador; esta clase pertenece a la capa controller de Springpoo, encargada de recibir peticiones HTTP.

import com.sena.springpoo.models.Usuario; // Importa el modelo Usuario; representa en memoria un registro de la tabla usuarios.
import com.sena.springpoo.repository.UsuarioRepository; // Importa el repositorio JDBC que ejecuta consultas SQL sobre la tabla usuarios.
import org.apache.logging.log4j.LogManager; // Importa LogManager; permite crear un logger para registrar eventos del controlador.
import org.apache.logging.log4j.Logger; // Importa Logger; objeto usado para escribir mensajes en app.log, crud.log o errores.log según configuración.
import org.springframework.beans.factory.annotation.Autowired; // Importa @Autowired; Spring lo usa para inyectar dependencias automáticamente.
import org.springframework.dao.DataAccessException; // Importa excepción general de acceso a datos; sirve para detectar errores de base de datos.
import org.springframework.http.HttpStatus; // Importa códigos HTTP como 200, 201, 400, 404 y 500.
import org.springframework.http.ResponseEntity; // Importa ResponseEntity; permite retornar cuerpo JSON junto con estado HTTP.
import org.springframework.stereotype.Controller; // Importa @Controller; marca esta clase como controlador MVC de Spring.
import org.springframework.ui.Model; // Importa Model; permite enviar datos desde el controlador hacia vistas Thymeleaf.
import org.springframework.web.bind.annotation.*; // Importa anotaciones web como @GetMapping, @PostMapping, @RequestBody y @PathVariable.
import org.springframework.security.crypto.password.PasswordEncoder; // Importa PasswordEncoder; se usa para cifrar y verificar contraseñas.
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken; // Importa el token de autenticación que representa un usuario autenticado en Spring Security.
import org.springframework.security.core.context.SecurityContextHolder; // Importa el contenedor donde Spring Security guarda la autenticación actual.
import org.springframework.security.core.authority.SimpleGrantedAuthority; // Importa una autoridad o rol simple, por ejemplo ROLE_USER.
import org.springframework.security.core.userdetails.User; // Importa el usuario interno de Spring Security usado para construir la sesión autenticada.

import java.sql.SQLException; // Importa SQLException; permite reconocer errores relacionados con SQL o conexión a MySQL.
import java.util.*; // Importa utilidades como Map, List, Optional, Date, LinkedHashMap y Collections.


/**
 * Controlador principal para la gestión de usuarios y vistas web relacionadas.
 * <p>
 * Esta clase mezcla dos responsabilidades dentro del proyecto:
 * renderizar vistas Thymeleaf como login, registro y formulario, y exponer
 * endpoints REST que devuelven respuestas JSON para consultar, guardar,
 * actualizar y eliminar usuarios.
 * </p>
 * <p>
 * Trabaja con {@link UsuarioRepository}, que usa JdbcTemplate para comunicarse
 * con MySQL. Por eso este controlador no escribe SQL directamente, sino que
 * delega las operaciones de base de datos al repositorio.
 * </p>
 */


@Controller // Spring registra esta clase como controlador MVC; sus métodos pueden devolver vistas o respuestas HTTP.

@CrossOrigin(origins = "*") // Permite peticiones desde cualquier origen; útil para pruebas con frontend, aunque en producción conviene restringirlo.
public class ControllerUsuarios { // Declara la clase pública ControllerUsuarios; Spring la instancia como bean durante el arranque.

    private static final Logger log = LogManager.getLogger(ControllerUsuarios.class); // Crea un logger estático para registrar actividad de este controlador.

    @Autowired // Spring inyecta automáticamente una instancia de UsuarioRepository.
    private UsuarioRepository usuarioRepository; // Variable de tipo UsuarioRepository; permite consultar, guardar, actualizar y borrar usuarios en MySQL.
    
    @Autowired // Spring inyecta el PasswordEncoder definido en SecurityConfig.
    private PasswordEncoder passwordEncoder; // Variable usada para cifrar contraseñas con BCrypt y validar contraseñas durante el login.

    // ── VISTAS THYMELEAF ──────────────────────────────────────────

    /**
     * atiende la ruta raíz de la aplicación.
     * <p>
     * Redirige automáticamente a la página de inicio de sesión.
     * </p>
     *
     * @return Una cadena de redirección hacia {@code "/login"}. redirige al navegador hacia /login.
     */

    @GetMapping("/") // Mapea peticiones HTTP GET dirigidas a la raíz del sitio.
    public String index() // Méodo público sin parámetros; retorna String porque devuelve una vista o redirección
     {
        return "redirect:/login";  // Retorna una redirección; Spring no busca plantilla, sino que envía al navegador a login.
    }

    /**
     * Muestra la página de inicio de sesión (Login).
     *
     * @return El nombre lógico de la plantilla login.html.
     */
    @GetMapping("/login") // Mapea peticiones GET a /login.
    public String loginPage() // Méodo público sin parámetros; retorna el nombre de una vista Thymeleaf.
    {
        return "login"; // Spring busca templates/login.html y lo renderiza como página HTML.
    }



    /**
     * Muestra la página de registro de nuevos usuarios.
     *
     * @return El nombre  el nombre lógico de la plantilla registro.html. {@code "registro"}.
     */
    @GetMapping("/registro")// Mapea peticiones GET a /registro.
    public String registroPage() // Méodo público sin parámetros; devuelve una vista
     {
        return "registro"; // Spring renderiza templates/registro.html.
     }

    /**
     * Muestra la página principal o panel de formulario (Dashboard) protegido por Spring Security
     * <p>
     * Carga en el modelo la URL de la imagen principal a utilizar en el panel.
     * </p>
     *
     * @param model objeto usado para enviar datos desde Java hacia Thymeleaf.
     * @return el nombre lógico de la plantilla formulario.html.
     */
    @GetMapping("/formulario") // Mapea peticiones GET a /formulario; en SecurityConfig esta ruta requiere autenticación.
    public String formularioPage(Model model) { // Recibe Model; Spring lo crea y lo entrega automáticamente al métdo
        log.info("Vista principal solicitada: /formulario (SENA GDF)"); // Registra en logs que se abrió la vista principal.
        model.addAttribute("imagenUrl", "/usuarios/imagen"); // Agrega al modelo la clave imagenUrl; Thymeleaf puede usarla en el HTML.
        return "formulario"; // Retorna la plantilla formulario.html
    }

    // ── ENDPOINT IMAGEN ────────────────────────

    /**
     * Retorna los datos y la URL de la imagen principal mostrada en el panel.
     * <p>
     * Este endpoint es consumido vía AJAX/REST por el frontend para obtener 
     * el banner, título y descripción sin recargar la página.
     * </p>
     *
     * @param lang Idioma solicitado por el cliente (cabecera {@code Accept-Language})por defecto usa es.
     * @return Un objeto {@link ResponseEntity} que contiene un mapa JSON con {@code imagenUrl}, {@code titulo} y {@code descripcion}.
     */
    @ResponseBody // Indica que el retorno no es una vista, sino cuerpo HTTP JSON.
    @GetMapping("/usuarios/imagen") // Mapea peticiones GET a /usuarios/imagen.
    public ResponseEntity<Map<String, Object>> obtenerImagen( // Retorna ResponseEntity con un Map; Spring lo serializa como JSON.
                                                              @RequestHeader(value = "Accept-Language", defaultValue = "es") String lang) { // Recibe la cabecera Accept-Language; si no llega, usa "es".

        log.info("GET /usuarios/imagen — Enviando recurso visual"); // Registra que se está entregando información visual.
        Map<String, Object> respuesta = new LinkedHashMap<>(); // Crea un mapa ordenado en memoria para construir la respuesta JSON.
        // Imagen
        respuesta.put("imagenUrl", "https://images.unsplash.com/photo-1517245386807-bb43f82c33c4?w=800&q=80"); // Agrega la URL de la imagen al JSON.
        respuesta.put("titulo", "Plataforma SENA GDF"); // Agrega el título que el frontend puede mostrar.
        respuesta.put("descripcion", "Gestión de Formación Profesional Integral"); // Agrega la descripción del recurso visual.
        
        return ResponseEntity.ok(respuesta);  // Retorna HTTP 200 OK con el mapa como cuerpo JSON.

    }

    // ── ENDPOINTS REST  ─────────────────────────

    /**
     * Consulta el listado de usuarios de forma paginada o completa y con soporte de búsqueda.
     *
     * @param page    Número de página solicitado. puede ser null
     * @param size    Cantidad de registros por página. puede ser null
     * @param search  texto  de búsqueda (ID o Nombre).puede ser null
     * @param lang   Idioma solicitado.
     * @return Un {@link ResponseEntity} con la información de los usuarios ({@code total}, {@code usuarios}, {@code totalPages}, etc.) o un error 500 en caso de fallo con la bd.
     */
    @ResponseBody // El resultado se escribe como JSON y no como vista HTML.
    @GetMapping("/usuarios/lista")  // Mapea GET /usuarios/lista.
    public ResponseEntity<Map<String, Object>> listarUsuarios( // Métdo REST que retorna un ResponseEntity con estructura JSON.
                                                               @RequestParam(value = "page", required = false) Integer page, // Lee el parámetro page de la URL; si no existe queda null.

                                                               @RequestParam(value = "size", required = false) Integer size, // Lee el parámetro size de la URL; si no existe queda null.

                                                               @RequestParam(value = "search", required = false) String search, // Lee el parámetro search; puede buscar por ID o nombre.

                                                               @RequestHeader(value = "Accept-Language", defaultValue = "es") String lang) { // Lee idioma desde cabecera HTTP.
        
        log.info("REST: Consultando lista de usuarios (page={}, size={}, search={})", page, size, search); // Registra los filtros recibidos.
        try { // Inicia bloque controlado para capturar errores de repositorio o base de datos.
            Map<String, Object> res = new LinkedHashMap<>(); // Crea respuesta JSON ordenada.
            res.put("status", 200); // Agrega estado lógico 200 dentro del cuerpo JSON.
            res.put("totalGroups", usuarioRepository.countDistinctGroups()); // Consulta en MySQL cuántos grupos de formación distintos existen.

            if (page != null && size != null) { // Si llegan page y size, se trabaja con paginación.
                List<Usuario> lista = usuarioRepository.findPaginated(search, page, size); // Consulta usuarios paginados usando SQL LIMIT y OFFSET.
                int total = usuarioRepository.count(search); // Consulta en MySQL cuántos usuarios cumplen el filtro.
                int totalPages = (int) Math.ceil((double) total / size); // Calcula total de páginas redondeando hacia arriba

                res.put("total", total);  // Agrega el total de registros encontrados.
                res.put("usuarios", lista); // Agrega la lista de usuarios; Spring convierte cada Usuario a JSON.
                res.put("page", page); // Agrega la página actual.
                res.put("size", size); // Agrega el tamaño de página usado.
                res.put("totalPages", totalPages); // Agrega el número total de páginas.
            } else { // Si no hay page y size, devuelve todos los registros o todos los encontrados por búsqueda.
                List<Usuario> lista; // Declara una lista de Usuario que se llenará según exista búsqueda o no.
                if (search != null && !search.trim().isEmpty()) { // Verifica si hay texto real de búsqueda.
                    lista = usuarioRepository.findPaginated(search, 0, Integer.MAX_VALUE); // Busca todos los coincidientes usando página 0 y tamaño máximo.
                } else { // Si no hay búsqueda.
                    lista = usuarioRepository.findAll(); // Consulta todos los usuarios con SELECT * FROM usuarios.
                }
                res.put("total", lista.size()); // Agrega el total de usuarios devueltos.
                res.put("usuarios", lista); // Agrega la lista completa o filtrada.
            }
            return ResponseEntity.ok(res); // Retorna HTTP 200 OK con la respuesta JSON.
        } catch (Exception e) { // Captura cualquier fallo, por ejemplo conexión MySQL caída.
            return error500(e, lang); // Retorna una respuesta JSON de error 500 usando el méodo auxiliar.
        }
    }

    /**
     * Guarda un nuevo usuario en la base de datos de manera REST (usando JSON).
     * <p>
     * Este méodo valida que el nombre no esté vacío y cifra la contraseña con BCrypt
     * antes de persistir la información.
     * </p>
     *
     * @param usuario El objeto {@link Usuario} creado por Spring a partir del cuerpo JSON.
     * @param lang    Idioma solicitado.
     * @return Una respuesta {@link ResponseEntity} TTP 201 si guarda, HTTP 400 si falta el primer nombre o HTTP 500 si falla el servidor.
     */
    @ResponseBody // Devuelve JSON en el cuerpo de la respuesta.
    @PostMapping("/usuarios/guardar") // Mapea POST /usuarios/guardar.
    public ResponseEntity<Map<String, Object>> guardarRest( // Méodo REST para crear usuario desde JSON.

                                                            @RequestBody Usuario usuario, // Spring deserializa el JSON recibido y llena un objeto Usuario.

                                                            @RequestHeader(value = "Accept-Language", defaultValue = "es") String lang) { // Lee la cabecera de idioma.
        
        log.info("REST: Intentando guardar usuario documento={}", usuario.getDocumento()); // Registra el documento que se intenta guardar.
        
        if (usuario.getPrimerNombre() == null || usuario.getPrimerNombre().isBlank()) { // Valida que el primer nombre no sea null ni vacío.

            return ResponseEntity.status(HttpStatus.BAD_REQUEST) // Prepara una respuesta HTTP 400 Bad Request.
                    .body(errorBody(HttpStatus.BAD_REQUEST, "Nombre obligatorio", "El primer nombre no puede estar vacío")); // Retorna JSON de error.
        }

        if (usuario.getContrasena() != null && !usuario.getContrasena().isBlank()) { // Si llegó contraseña, valida que tenga contenido.
            usuario.setContrasena(passwordEncoder.encode(usuario.getContrasena())); // Cifra la contraseña con BCrypt antes de guardarla.
        }

        try { // Intenta guardar el usuario en base de datos.
            Usuario guardado = usuarioRepository.save(usuario); // Ejecuta INSERT o UPDATE desde UsuarioRepository; si es nuevo asigna ID.
            log.info("REST: Usuario guardado con éxito ID={}", guardado.getId()); // Registra el ID generado o actualizado.
            Map<String, Object> res = new LinkedHashMap<>(); // Crea mapa de respuesta.
            res.put("status", 201); // Agrega estado lógico 201.
            res.put("id", guardado.getId()); // Agrega el ID del usuario guardado.
            res.put("mensaje", "Usuario registrado correctamente"); // Agrega mensaje de éxito.
            return ResponseEntity.status(HttpStatus.CREATED).body(res);// Retorna HTTP 201
        } catch (Exception e) { // Captura errores de SQL, conexión o validaciones internas.
            return error500(e, lang); // Devuelve error 500 estandarizado.
        }
    }

    /**
     * Elimina un usuario de la base de datos por su ID (REST).
     *
     * @param id El identificador único del usuario a eliminar de la tabla usuarios, columna id_usuario.
     * @return Una respuesta {@link ResponseEntity} indicando éxito (HTTP 200) o si no se encontró (404 Not Found) o HTTP 500 si falla..
     */
    @ResponseBody // Devuelve JSON.
    @DeleteMapping("/usuarios/borrar/{id}") // Mapea DELETE /usuarios/borrar/{id}; el ID llega en la URL.
    public ResponseEntity<Map<String, Object>> eliminarRest(@PathVariable Long id) { // Recibe id desde la ruta y retorna JSON.
        log.info("REST: Eliminando usuario ID={}", id); // Registra el intento de eliminación.

        try { // Inicia bloque para capturar errores.
            if (!usuarioRepository.existsById(id)) { // Consulta en MySQL si existe un usuario con ese ID.
                return ResponseEntity.status(HttpStatus.NOT_FOUND) // Si no existe, prepara HTTP 404.
                        .body(errorBody(HttpStatus.NOT_FOUND, "No encontrado", "El ID no existe")); // Retorna cuerpo JSON de error.
            }
            usuarioRepository.deleteById(id); // Ejecuta DELETE FROM usuarios WHERE id_usuario = ?.
            Map<String, Object> res = new LinkedHashMap<>(); // Crea mapa de respuesta exitosa.
            res.put("status", 200); // Agrega estado lógico 200.
            res.put("mensaje", "Usuario eliminado"); // Agrega mensaje de confirmación.
            return ResponseEntity.ok(res); // Retorna HTTP 200 OK.
        } catch (Exception e) { // Captura fallos de base de datos.
            return error500(e, "es"); // Retorna error 500 en español.
        }
    }

    /**
     * Actualiza los datos de un usuario existente (REST).
     * <p>
     * Se busca el usuario por ID y se actualizan únicamente los campos que no vengan nulos 
     * o vacíos en el objeto recibido. Si se envía una contraseña, esta se cifra antes de guardarse.
     * </p>
     *
     * @param id El identificador único del usuario a actualizar.
     * @param usuarioActualizado Objeto con los nuevos datos a aplicar.
     * @param lang Idioma solicitado.
     * @return Una respuesta {@link ResponseEntity} con el mensaje de éxito (200 OK) o error si no existe (404 Not Found) o HTTP 500 si falla.
     */
    @ResponseBody // Devuelve respuesta JSON.
    @PutMapping("/usuarios/actualizar/{id}") // Mapea PUT /usuarios/actualizar/{id}.
    public ResponseEntity<Map<String, Object>> actualizarRest( // Méodo REST de actualización.

                                                               @PathVariable Long id, // Extrae el ID desde la URL.

            @RequestBody Usuario usuarioActualizado, // Recibe los nuevos datos desde el cuerpo JSON.

                                                               @RequestHeader(value = "Accept-Language", defaultValue = "es") String lang) { // Recibe idioma desde cabecera.

        log.info("REST: Actualizando usuario ID={}", id); // Registra el ID a actualizar.
        try { // Inicia bloque con manejo de errores.
            Optional<Usuario> optional = usuarioRepository.findById(id); // Busca usuario en MySQL por id_usuario.
            if (optional.isEmpty()) { // Si el Optional no contiene usuario, no existe en la base de datos.
                return ResponseEntity.status(HttpStatus.NOT_FOUND) // Prepara HTTP 404.
                        .body(errorBody(HttpStatus.NOT_FOUND, "No encontrado", "El ID de usuario no existe")); // Retorna JSON de error.
            }
            Usuario existente = optional.get(); // Extrae el usuario encontrado para modificarlo en memoria.

            if (usuarioActualizado.getPrimerNombre() != null && !usuarioActualizado.getPrimerNombre().isBlank()) // Valida si llegó un primer nombre útil.
                existente.setPrimerNombre(usuarioActualizado.getPrimerNombre()); // Actualiza en memoria el primer nombre.
            if (usuarioActualizado.getPrimerApellido() != null && !usuarioActualizado.getPrimerApellido().isBlank()) // Valida si llegó primer apellido.
                existente.setPrimerApellido(usuarioActualizado.getPrimerApellido()); // Actualiza en memoria el primer apellido.
            if (usuarioActualizado.getDocumento() != null && !usuarioActualizado.getDocumento().isBlank()) // Valida si llegó documento.
                existente.setDocumento(usuarioActualizado.getDocumento()); // Actualiza en memoria el documento.
            if (usuarioActualizado.getCorreoElectronico() != null && !usuarioActualizado.getCorreoElectronico().isBlank()) // Valida si llegó correo.
                existente.setCorreoElectronico(usuarioActualizado.getCorreoElectronico()); // Actualiza en memoria el correo electrónico.
            if (usuarioActualizado.getContrasena() != null && !usuarioActualizado.getContrasena().isBlank()) { // Si llegó contraseña nueva
                existente.setContrasena(passwordEncoder.encode(usuarioActualizado.getContrasena()));  // La cifra con BCrypt antes de guardarla.
            }

            usuarioRepository.save(existente); // Como el usuario tiene ID, el repositorio ejecuta UPDATE en la tabla usuarios.

            Map<String, Object> res = new LinkedHashMap<>(); // Crea respuesta JSON.
            res.put("status", 200); // Agrega estado lógico 200.
            res.put("mensaje", "Usuario actualizado correctamente"); // Agrega mensaje de éxito.
            return ResponseEntity.ok(res); // Retorna HTTP 200 OK
        } catch (Exception e) { // Captura errores de base de datos o ejecución.
            return error500(e, lang); // Retorna JSON de error 500
        }
    }

    // ── LOGICA DE LOGIN  (Registro/Login) ──
    /**
     * Procesa la autenticación (Login) desde el formulario HTML.
     * <p>
     * Busca al usuario por su tipo y número de documento. Verifica la contraseña
     * usando BCrypt; en caso de que falle, realiza un intento de fallback comparando
     * en texto plano (útil para contraseñas sin cifrar en la BD).
     * Si las credenciales son válidas, establece el contexto de Spring Security 
     * y redirige al panel principal ({@code /nuevo/prueba}).
     * </p>
     *
     * @param tipoDocumento Tipo de documento seleccionado (ej. CC, TI). desde login.html.
     * @param documento Número de documento del usuario.
     * @param contrasena Contraseña ingresada.
     * @param model Objeto {@link Model} para enviar mensajes de error a la vista login.html en caso de fallo.
     * @return Redirección al panel  /nuevo/prueba en caso de éxito, o la vista de login con mensaje de error en caso de fallo.
     */
@PostMapping("/ingresar") // Mapea POST /ingresar, ruta usada por el formulario de login.
public String login(@RequestParam String tipoDocumento,// Recibe tipoDocumento desde el formulario.
                    @RequestParam String documento, // Recibe documento desde el formulario.
                    @RequestParam String contrasena,// Recibe contraseña en texto plano desde el formulario.
                    Model model) { // Recibe Model para devolver errores a Thymeleaf

    log.info("Intento de login: docs={} / {}", tipoDocumento, documento);// Registra intento de login sin mostrar la contraseña.

    // Busca el usuario solo por tipo y número de documento
    Optional<Usuario> userOpt = usuarioRepository //puede contener o no un Usuario encontrado.
            .findByTipoDocumentoAndDocumento(tipoDocumento, documento);// Ejecuta SELECT por tipo_documento y documento.

    if (userOpt.isPresent()) { // Verifica si la consulta encontró un usuario.
        Usuario user = userOpt.get(); // Extrae el usuario desde Optional y lo guarda en memoria.
        boolean passwordValida = false; // Variable booleana; empieza en false y cambia a true si la contraseña coincide.

        try { // Intenta validar usando BCrypt.
            passwordValida = passwordEncoder.matches(contrasena, user.getContrasena()); // Compara texto plano contra hash guardado.
        } catch (Exception e) { // Captura error si la contraseña guardada no tiene formato BCrypt válido.
            log.warn("Error al validar con BCrypt, probando texto plano para el documento {}: {}", documento, e.getMessage()); // Registra advertencia.
        }

        // Fallback: Si no coincide con BCrypt, comparar en texto plano directo
        if (!passwordValida && contrasena.equals(user.getContrasena())) { // Fallback: compara texto plano si BCrypt falló.
            passwordValida = true; // Marca la contraseña como válida por compatibilidad con datos antiguos.
        }

        if (passwordValida) {  // Si la contraseña fue validada por BCrypt o por fallback.
            log.info("Login exitoso para: {}", documento); // Registra login correcto.

            // Registrar al usuario en el contexto de Spring Security para evitar redirecciones al login
            User securityUser = new User( // Crea un usuario interno de Spring Security para la sesión.
                user.getDocumento(), // Usa el documento como username.
                    user.getContrasena() != null ? user.getContrasena() : "", // Usa la contraseña cifrada o cadena vacía si es null.
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")) // Asigna el rol ROLE_USER.
            );

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken( // Crea token de autenticación.
                securityUser, null, securityUser.getAuthorities() // Guarda usuario, credenciales nulas y autoridades del usuario.
            );

            SecurityContextHolder.getContext().setAuthentication(authentication); // Guarda la autenticación en el contexto de Spring Security.

            return "redirect:/nuevo/prueba"; // Redirige al panel principal después de iniciar sesión.

        }
    }

    log.warn("Login fallido para: {}", documento); // Registra login incorrecto.
    model.addAttribute("error", "Credenciales incorrectas"); // Envía mensaje de error a login.html
    return "login"; // Retorna nuevamente la vista login.html.
}

/**
 * Guarda un usuario enviado a través del formulario de registro tradicional de Thymeleaf.
 * <p>
 * Este métdo cifra la contraseña si no está vacía y redirige nuevamente al login
 * tras crear exitosamente la cuenta.
 * </p>
 *
 * @param usuario Objeto {@link Usuario} llenado automáticamente con los campos del formulario.
 * @return redirección a /login después de guardar. {@code "/login"}.
 */
@PostMapping("/guardar") // Mapea POST /guardar, usado por el formulario de registro.
public String guardarThymeleaf(@ModelAttribute Usuario usuario) { // Spring llena Usuario con los campos enviados por formulario.
    if (usuario.getContrasena() != null && !usuario.getContrasena().isBlank()) { // Valida si la contraseña existe y no está vacía.
        usuario.setContrasena(passwordEncoder.encode(usuario.getContrasena())); // Cifra la contraseña antes de guardarla.
    }
    usuarioRepository.save(usuario); // Guarda el usuario en MySQL mediante INSERT o UPDATE.
    return "redirect:/login"; // Redirige al login después del registro.
}

    // ── UTILIDADES DE ERROR
    //  @param status estado HTTP del error.
    //   * @param mensaje mensaje principal para el usuario.
    //   * @param detalle detalle técnico o descriptivo del error.
    //   * @return Map con timestamp, status, error, mensaje y detalle.
    //   */
    //──────────────

    private Map<String, Object> errorBody(HttpStatus status, String mensaje, String detalle) { // Méodo privado auxiliar; solo se usa dentro del controlador.
        Map<String, Object> err = new LinkedHashMap<>(); // Crea mapa ordenado para respuesta JSON.
        err.put("timestamp", new Date().toString()); // Agrega fecha y hora del error.
        err.put("status", status.value()); // Agrega número HTTP, por ejemplo 400, 404 o 500.
        err.put("error", status.getReasonPhrase());// Agrega texto oficial del estado, por ejemplo Bad Request.
        err.put("mensaje", mensaje); // Agrega mensaje entendible para el usuario.
        err.put("detalle", detalle); // Agrega detalle técnico o explicación.
        return err; // Retorna el mapa para usarlo como cuerpo de ResponseEntity.
    }


    /**
     * Construye una respuesta HTTP 500.
     *
     * @param e excepción capturada.
     * @param lang idioma solicitado; actualmente no cambia el mensaje.
     * @return ResponseEntity con estado 500 y cuerpo JSON.
     */

    private ResponseEntity<Map<String, Object>> error500(Exception e, String lang) { // Métdo privado que centraliza errores internos.
        log.error("❌ Error 500 detectado: {}", e.getMessage(), e); // Registra el error completo en logs.
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR; // Variable con estado HTTP 500.
        String msg = "Error interno del servidor"; // Mensaje inicial por defecto.
        String det = e.getMessage(); // Detalle inicial tomado de la excepción.

        if (esErrorDeBaseDeDatos(e)) { // Verifica si la excepción parece relacionada con MySQL o JDBC.
            msg = "Base de datos no disponible"; // Cambia el mensaje principal si falló la base de datos.
            det = "MySQL está apagado o la conexión fue rechazada. Inicia MySQL en XAMPP."; // Da una explicación más clara.
        }

        return ResponseEntity.status(status).body(errorBody(status, msg, det));// Retorna HTTP 500 con cuerpo JSON estándar.
    }
    /**
     * Detecta si una excepción corresponde a un fallo de base de datos.
     *
     * @param e excepción capturada durante una operación del controlador.
     * @return true si parece error SQL/JDBC/conexión; false si es otro tipo de error.
     */

    private boolean esErrorDeBaseDeDatos(Exception e) { // Métdo privado usado para personalizar errores de MySQL.
        Throwable causa = e; // Variable que empieza con la excepción original y recorrerá sus causas internas.
        while (causa != null) { // Recorre la cadena de causas hasta llegar a null.
            if (causa instanceof SQLException || causa instanceof java.net.ConnectException 
                || causa.getMessage().contains("link failure") || causa.getMessage().contains("JDBC")) return true; // Retorna true si detecta SQL, conexión o mensajes JDBC.
            causa = causa.getCause(); // Avanza a la causa interna de la excepción.
        }
        return e instanceof DataAccessException; // Retorna true si la excepción principal pertenece a errores de acceso a datos de Spring.
    }
}