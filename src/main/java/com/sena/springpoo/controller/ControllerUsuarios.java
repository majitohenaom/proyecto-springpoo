package com.sena.springpoo.controller;

import com.sena.springpoo.models.Usuario;
import com.sena.springpoo.repository.UsuarioRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.sql.SQLException;
import java.util.*;


/**
 * Controlador principal para la gestión de Usuarios y las vistas web asociadas.
 * <p>
 * Provee tanto los endpoints que devuelven las vistas HTML procesadas por Thymeleaf
 * (login, registro, formulario, etc.) como una API REST completa para realizar 
 * operaciones CRUD (Crear, Leer, Actualizar, Borrar) sobre la entidad {@link Usuario}.
 * Además, incluye endpoints específicos para servir datos como imágenes de cabecera.
 * </p>
 */
@Controller
@CrossOrigin(origins = "*")
public class ControllerUsuarios {

    private static final Logger log = LogManager.getLogger(ControllerUsuarios.class);

    @Autowired
    private UsuarioRepository usuarioRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;

    // ── VISTAS THYMELEAF ──────────────────────────────────────────

    /**
     * Endpoint raíz de la aplicación.
     * <p>
     * Redirige automáticamente a la página de inicio de sesión.
     * </p>
     *
     * @return Una cadena de redirección hacia {@code "/login"}.
     */
    @GetMapping("/")
    public String index() {
        return "redirect:/login";
    }

    /**
     * Muestra la página de inicio de sesión (Login).
     *
     * @return El nombre de la plantilla Thymeleaf {@code "login"}.
     */
    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    

    /**
     * Muestra la página de registro de nuevos usuarios.
     *
     * @return El nombre de la plantilla Thymeleaf {@code "registro"}.
     */
    @GetMapping("/registro")
    public String registroPage() {
        return "registro";
    }

    /**
     * Muestra la página principal o panel de formulario (Dashboard).
     * <p>
     * Carga en el modelo la URL de la imagen principal a utilizar en el panel.
     * </p>
     *
     * @param model Objeto {@link Model} de Spring para inyectar datos en la vista.
     * @return El nombre de la plantilla Thymeleaf {@code "formulario"}.
     */
    @GetMapping("/formulario")
    public String formularioPage(Model model) {
        log.info("Vista principal solicitada: /formulario (SENA GDF)");
        model.addAttribute("imagenUrl", "/usuarios/imagen");
        return "formulario";
    }

    // ── ENDPOINT IMAGEN (Requerimiento 2.2) ────────────────────────

    /**
     * Retorna los datos y la URL de la imagen principal mostrada en el panel.
     * <p>
     * Este endpoint es consumido vía AJAX/REST por el frontend para obtener 
     * el banner, título y descripción sin recargar la página.
     * </p>
     *
     * @param lang Idioma solicitado por el cliente (cabecera {@code Accept-Language}).
     * @return Un objeto {@link ResponseEntity} que contiene un mapa JSON con {@code imagenUrl}, {@code titulo} y {@code descripcion}.
     */
    @ResponseBody
    @GetMapping("/usuarios/imagen")
    public ResponseEntity<Map<String, Object>> obtenerImagen(
            @RequestHeader(value = "Accept-Language", defaultValue = "es") String lang) {
        
        log.info("GET /usuarios/imagen — Enviando recurso visual");
        Map<String, Object> respuesta = new LinkedHashMap<>();
        // Imagen
        respuesta.put("imagenUrl", "https://images.unsplash.com/photo-1517245386807-bb43f82c33c4?w=800&q=80");
        respuesta.put("titulo", "Plataforma SENA GDF");
        respuesta.put("descripcion", "Gestión de Formación Profesional Integral");
        
        return ResponseEntity.ok(respuesta);
    }

    // ── ENDPOINTS REST  ─────────────────────────

    /**
     * Consulta el listado de usuarios de forma paginada o completa y con soporte de búsqueda.
     *
     * @param page   (Opcional) Número de página solicitado (base 0).
     * @param size   (Opcional) Cantidad de elementos por página.
     * @param search (Opcional) Término de búsqueda (ID o Nombre).
     * @param lang   Idioma solicitado.
     * @return Un {@link ResponseEntity} con la información de los usuarios ({@code total}, {@code usuarios}, {@code totalPages}, etc.) o un error 500 en caso de fallo.
     */
    @ResponseBody
    @GetMapping("/usuarios/lista")
    public ResponseEntity<Map<String, Object>> listarUsuarios(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "search", required = false) String search,
            @RequestHeader(value = "Accept-Language", defaultValue = "es") String lang) {
        
        log.info("REST: Consultando lista de usuarios (page={}, size={}, search={})", page, size, search);
        try {
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("status", 200);
            res.put("totalGroups", usuarioRepository.countDistinctGroups());

            if (page != null && size != null) {
                List<Usuario> lista = usuarioRepository.findPaginated(search, page, size);
                int total = usuarioRepository.count(search);
                int totalPages = (int) Math.ceil((double) total / size);

                res.put("total", total);
                res.put("usuarios", lista);
                res.put("page", page);
                res.put("size", size);
                res.put("totalPages", totalPages);
            } else {
                List<Usuario> lista;
                if (search != null && !search.trim().isEmpty()) {
                    lista = usuarioRepository.findPaginated(search, 0, Integer.MAX_VALUE);
                } else {
                    lista = usuarioRepository.findAll();
                }
                res.put("total", lista.size());
                res.put("usuarios", lista);
            }
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            return error500(e, lang);
        }
    }

    /**
     * Guarda un nuevo usuario en la base de datos de manera REST (usando JSON).
     * <p>
     * Este método valida que el nombre no esté vacío y cifra la contraseña con BCrypt 
     * antes de persistir la información.
     * </p>
     *
     * @param usuario El objeto {@link Usuario} deserializado del cuerpo de la petición.
     * @param lang    Idioma solicitado.
     * @return Una respuesta {@link ResponseEntity} informando el ID generado y el código de estado (201 Created o 400 Bad Request).
     */
    @ResponseBody
    @PostMapping("/usuarios/guardar")
    public ResponseEntity<Map<String, Object>> guardarRest(
            @RequestBody Usuario usuario,
            @RequestHeader(value = "Accept-Language", defaultValue = "es") String lang) {
        
        log.info("REST: Intentando guardar usuario documento={}", usuario.getDocumento());
        
        if (usuario.getPrimerNombre() == null || usuario.getPrimerNombre().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(errorBody(HttpStatus.BAD_REQUEST, "Nombre obligatorio", "El primer nombre no puede estar vacío"));
        }

        if (usuario.getContrasena() != null && !usuario.getContrasena().isBlank()) {
            usuario.setContrasena(passwordEncoder.encode(usuario.getContrasena()));
        }

        try {
            Usuario guardado = usuarioRepository.save(usuario);
            log.info("REST: Usuario guardado con éxito ID={}", guardado.getId());
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("status", 201);
            res.put("id", guardado.getId());
            res.put("mensaje", "Usuario registrado correctamente");
            return ResponseEntity.status(HttpStatus.CREATED).body(res);
        } catch (Exception e) {
            return error500(e, lang);
        }
    }

    /**
     * Elimina un usuario de la base de datos por su ID (REST).
     *
     * @param id El identificador único del usuario a eliminar.
     * @return Una respuesta {@link ResponseEntity} indicando éxito (200 OK) o si no se encontró (404 Not Found).
     */
    @ResponseBody
    @DeleteMapping("/usuarios/borrar/{id}")
    public ResponseEntity<Map<String, Object>> eliminarRest(@PathVariable Long id) {
        log.info("REST: Eliminando usuario ID={}", id);
        try {
            if (!usuarioRepository.existsById(id)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(errorBody(HttpStatus.NOT_FOUND, "No encontrado", "El ID no existe"));
            }
            usuarioRepository.deleteById(id);
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("status", 200);
            res.put("mensaje", "Usuario eliminado");
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            return error500(e, "es");
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
     * @return Una respuesta {@link ResponseEntity} con el mensaje de éxito (200 OK) o error si no existe (404 Not Found).
     */
    @ResponseBody
    @PutMapping("/usuarios/actualizar/{id}")
    public ResponseEntity<Map<String, Object>> actualizarRest(
            @PathVariable Long id,
            @RequestBody Usuario usuarioActualizado,
            @RequestHeader(value = "Accept-Language", defaultValue = "es") String lang) {
        
        log.info("REST: Actualizando usuario ID={}", id);
        try {
            Optional<Usuario> optional = usuarioRepository.findById(id);
            if (optional.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(errorBody(HttpStatus.NOT_FOUND, "No encontrado", "El ID de usuario no existe"));
            }
            Usuario existente = optional.get();

            if (usuarioActualizado.getPrimerNombre() != null && !usuarioActualizado.getPrimerNombre().isBlank())
                existente.setPrimerNombre(usuarioActualizado.getPrimerNombre());
            if (usuarioActualizado.getPrimerApellido() != null && !usuarioActualizado.getPrimerApellido().isBlank())
                existente.setPrimerApellido(usuarioActualizado.getPrimerApellido());
            if (usuarioActualizado.getDocumento() != null && !usuarioActualizado.getDocumento().isBlank())
                existente.setDocumento(usuarioActualizado.getDocumento());
            if (usuarioActualizado.getCorreoElectronico() != null && !usuarioActualizado.getCorreoElectronico().isBlank())
                existente.setCorreoElectronico(usuarioActualizado.getCorreoElectronico());
            
            if (usuarioActualizado.getContrasena() != null && !usuarioActualizado.getContrasena().isBlank()) {
                existente.setContrasena(passwordEncoder.encode(usuarioActualizado.getContrasena()));
            }

            usuarioRepository.save(existente);

            Map<String, Object> res = new LinkedHashMap<>();
            res.put("status", 200);
            res.put("mensaje", "Usuario actualizado correctamente");
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            return error500(e, lang);
        }
    }

    // ── LOGICA DE LOGIN  (Registro/Login) ──

    // LOGIN — se usa passwordEncoder.matches() con fallback a texto plano para mayor compatibilidad
    /**
     * Procesa la autenticación (Login) desde el formulario tradicional.
     * <p>
     * Busca al usuario por su tipo y número de documento. Verifica la contraseña
     * usando BCrypt; en caso de que falle, realiza un intento de fallback comparando
     * en texto plano (útil para contraseñas sin cifrar en la BD).
     * Si las credenciales son válidas, establece el contexto de Spring Security 
     * y redirige al panel principal ({@code /nuevo/prueba}).
     * </p>
     *
     * @param tipoDocumento Tipo de documento seleccionado (ej. CC, TI).
     * @param documento Número de documento del usuario.
     * @param contrasena Contraseña ingresada.
     * @param model Objeto {@link Model} para inyectar errores en caso de fallo.
     * @return Redirección al panel en caso de éxito, o la vista de login con mensaje de error en caso de fallo.
     */
@PostMapping("/ingresar")
public String login(@RequestParam String tipoDocumento,
                    @RequestParam String documento,
                    @RequestParam String contrasena,
                    Model model) {

    log.info("Intento de login: docs={} / {}", tipoDocumento, documento);

    // Busca el usuario solo por tipo y número de documento
    Optional<Usuario> userOpt = usuarioRepository
            .findByTipoDocumentoAndDocumento(tipoDocumento, documento);

    if (userOpt.isPresent()) {
        Usuario user = userOpt.get();
        boolean passwordValida = false;

        try {
            // Intentar verificar con BCrypt
            passwordValida = passwordEncoder.matches(contrasena, user.getContrasena());
        } catch (Exception e) {
            log.warn("Error al validar con BCrypt, probando texto plano para el documento {}: {}", documento, e.getMessage());
        }

        // Fallback: Si no coincide con BCrypt, comparar en texto plano directo
        if (!passwordValida && contrasena.equals(user.getContrasena())) {
            passwordValida = true;
        }

        if (passwordValida) {
            log.info("Login exitoso para: {}", documento);

            // Registrar al usuario en el contexto de Spring Security para evitar redirecciones al login
            User securityUser = new User(
                user.getDocumento(),
                user.getContrasena() != null ? user.getContrasena() : "",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
            );

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                securityUser, null, securityUser.getAuthorities()
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);

            return "redirect:/nuevo/prueba";
        }
    }

    log.warn("Login fallido para: {}", documento);
    model.addAttribute("error", "Credenciales incorrectas");
    return "login";
}

/**
 * Guarda un usuario enviado a través del formulario de registro tradicional de Thymeleaf.
 * <p>
 * Este método cifra la contraseña si no está vacía y redirige nuevamente al login 
 * tras crear exitosamente la cuenta.
 * </p>
 *
 * @param usuario Objeto {@link Usuario} poblado con los campos del formulario.
 * @return Cadena de redirección hacia {@code "/login"}.
 */
@PostMapping("/guardar")
public String guardarThymeleaf(@ModelAttribute Usuario usuario) {
    if (usuario.getContrasena() != null && !usuario.getContrasena().isBlank()) {
        usuario.setContrasena(passwordEncoder.encode(usuario.getContrasena()));
    }
    usuarioRepository.save(usuario);
    return "redirect:/login";
}

    // ── UTILIDADES DE ERROR (Requerimiento 1.2 y 2.1) ──────────────

    private Map<String, Object> errorBody(HttpStatus status, String mensaje, String detalle) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("timestamp", new Date().toString());
        err.put("status", status.value());
        err.put("error", status.getReasonPhrase());
        err.put("mensaje", mensaje);
        err.put("detalle", detalle);
        return err;
    }

    private ResponseEntity<Map<String, Object>> error500(Exception e, String lang) {
        log.error("❌ Error 500 detectado: {}", e.getMessage(), e);
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        String msg = "Error interno del servidor";
        String det = e.getMessage();

        if (esErrorDeBaseDeDatos(e)) {
            msg = "Base de datos no disponible";
            det = "MySQL está apagado o la conexión fue rechazada. Inicia MySQL en XAMPP.";
        }

        return ResponseEntity.status(status).body(errorBody(status, msg, det));
    }

    private boolean esErrorDeBaseDeDatos(Exception e) {
        Throwable causa = e;
        while (causa != null) {
            if (causa instanceof SQLException || causa instanceof java.net.ConnectException 
                || causa.getMessage().contains("link failure") || causa.getMessage().contains("JDBC")) return true;
            causa = causa.getCause();
        }
        return e instanceof DataAccessException;
    }
}