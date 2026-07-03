package com.sena.springpoo.repository; // Define el paquete repository; esta clase pertenece a la capa encargada de comunicarse con la base de datos.

import com.sena.springpoo.models.Usuario; // Importa el modelo Usuario; cada objeto Usuario representa una fila de la tabla usuarios.
import org.springframework.beans.factory.annotation.Autowired; // Importa @Autowired; Spring lo usa para inyectar dependencias automáticamente.
import org.springframework.jdbc.core.JdbcTemplate; // Importa JdbcTemplate; permite ejecutar SQL directo contra MySQL.
import org.springframework.jdbc.core.RowMapper; // Importa RowMapper; convierte cada fila SQL en un objeto Java.
import org.springframework.jdbc.support.GeneratedKeyHolder; // Importa GeneratedKeyHolder; guarda el ID autogenerado después de un INSERT.
import org.springframework.jdbc.support.KeyHolder; // Importa KeyHolder; interfaz usada para recuperar llaves generadas por la base de datos.
import org.springframework.stereotype.Repository; // Importa @Repository; marca esta clase como componente de persistencia.

import java.sql.PreparedStatement; // Importa PreparedStatement; permite construir SQL parametrizado seguro.
import java.sql.Statement; // Importa Statement; se usa aquí para indicar que se deben retornar llaves generadas.
import java.util.List; // Importa List; representa listas de usuarios obtenidas desde la base de datos.
import java.util.Optional; // Importa Optional; representa un resultado que puede existir o no.

/**
 * Repositorio encargado del acceso a datos de la entidad {@link Usuario}.
 * <p>
 * Esta clase se comunica directamente con MySQL usando {@link JdbcTemplate}.
 * No usa JPA ni Hibernate; por eso aquí aparecen las consultas SQL escritas manualmente.
 * </p>
 * <p>
 * Su responsabilidad es consultar, contar, buscar, insertar, actualizar y eliminar
 * usuarios de la tabla {@code usuarios}.
 * </p>
 */
@Repository // Spring registra esta clase como bean de persistencia; también traduce errores SQL a excepciones de Spring.
public class UsuarioRepository { // Declara la clase pública UsuarioRepository, usada por controladores como ControllerUsuarios y FileUploadController.

    @Autowired // Spring inyecta automáticamente JdbcTemplate configurado con los datos de application.properties.
    private JdbcTemplate jdbcTemplate; // Variable que ejecuta consultas SQL, updates e inserts contra MySQL.

    private final RowMapper<Usuario> rowMapper = (rs, rowNum) -> { // Define cómo convertir cada fila del ResultSet en un objeto Usuario.
        Usuario u = new Usuario(); // Crea un objeto Usuario vacío en memoria.
        u.setId(rs.getLong("id_usuario")); // Lee la columna id_usuario y la asigna al atributo id del usuario.
        u.setPrimerNombre(rs.getString("primer_nombre")); // Lee primer_nombre desde MySQL y lo guarda en primerNombre.
        u.setSegundoNombre(rs.getString("segundo_nombre")); // Lee segundo_nombre y lo guarda en segundoNombre.
        u.setPrimerApellido(rs.getString("primer_apellido")); // Lee primer_apellido y lo guarda en primerApellido.
        u.setSegundoApellido(rs.getString("segundo_apellido")); // Lee segundo_apellido y lo guarda en segundoApellido.
        u.setTipoDocumento(rs.getString("tipo_documento")); // Lee tipo_documento y lo guarda en tipoDocumento.
        u.setDocumento(rs.getString("documento")); // Lee documento y lo guarda en documento.
        u.setCelular(rs.getString("celular")); // Lee celular y lo guarda en celular.
        u.setGrupoFormacion(rs.getString("grupo_formacion")); // Lee grupo_formacion y lo guarda en grupoFormacion.
        u.setCorreoElectronico(rs.getString("correo_electronico")); // Lee correo_electronico y lo guarda en correoElectronico.
        u.setContrasena(rs.getString("contrasena")); // Lee contrasena, normalmente cifrada con BCrypt, y la guarda en contrasena.
        return u; // Retorna el objeto Usuario construido desde la fila actual.
    };

    /**
     * Consulta todos los usuarios registrados.
     *
     * @return una lista {@link List} con todos los objetos {@link Usuario}; si no hay registros, retorna una lista vacía.
     */
    public List<Usuario> findAll() { // Método público; no recibe parámetros y retorna List<Usuario>.
        return jdbcTemplate.query("SELECT * FROM usuarios", rowMapper); // Ejecuta SELECT * FROM usuarios y convierte cada fila con rowMapper.
    }

    /**
     * Consulta usuarios con paginación y búsqueda opcional.
     *
     * @param search texto de búsqueda; puede ser null, vacío, un ID numérico o parte de un nombre.
     * @param page número de página empezando desde 0.
     * @param size cantidad de registros por página.
     * @return lista de usuarios que cumplen la búsqueda y la paginación; puede retornar lista vacía.
     */
    public List<Usuario> findPaginated(String search, int page, int size) { // Retorna List<Usuario> paginada.
        int offset = page * size; // Calcula desde qué registro empezar; por ejemplo page 2 y size 10 produce offset 20.
        if (search == null || search.trim().isEmpty()) { // Si no hay texto de búsqueda.
            return jdbcTemplate.query("SELECT * FROM usuarios LIMIT ? OFFSET ?", rowMapper, size, offset); // Retorna usuarios paginados usando LIMIT y OFFSET.
        } else { // Si sí hay búsqueda.
            String trimmed = search.trim(); // Elimina espacios al inicio y al final del texto buscado.
            if (trimmed.matches("\\d+")) { // Verifica si la búsqueda contiene solo números.
                try { // Intenta convertir el texto numérico a long.
                    long idSearch = Long.parseLong(trimmed); // Convierte el texto a número para buscar por id_usuario exacto.
                    String sql = "SELECT * FROM usuarios WHERE id_usuario = ? LIMIT ? OFFSET ?"; // SQL para buscar por ID exacto con paginación.
                    return jdbcTemplate.query(sql, rowMapper, idSearch, size, offset); // Retorna lista con el usuario encontrado o lista vacía.
                } catch (NumberFormatException e) { // Captura si el número es demasiado grande para long.
                    // Si falla la conversión, continúa hacia búsqueda por nombre.
                }
            }
            String searchPattern = "%" + trimmed.toLowerCase() + "%"; // Construye patrón LIKE para buscar coincidencias parciales.
            String sql = "SELECT * FROM usuarios WHERE " + // Inicia SQL de búsqueda textual.
                    "LOWER(CONCAT(primer_nombre, ' ', COALESCE(segundo_nombre, ''), ' ', primer_apellido, ' ', COALESCE(segundo_apellido, ''))) LIKE ? " + // Busca en nombre completo en minúsculas.
                    "LIMIT ? OFFSET ?"; // Aplica paginación.
            return jdbcTemplate.query(sql, rowMapper, searchPattern, size, offset); // Retorna usuarios cuyo nombre completo coincida parcialmente.
        }
    }

    /**
     * Cuenta usuarios según un criterio de búsqueda.
     *
     * @param search texto usado para contar; puede ser null, vacío, ID o nombre.
     * @return cantidad de usuarios encontrados; retorna 0 si no hay coincidencias o si la consulta devuelve null.
     */
    public int count(String search) { // Método público que retorna int.
        if (search == null || search.trim().isEmpty()) { // Si no hay búsqueda.
            Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM usuarios", Integer.class); // Ejecuta conteo total de usuarios.
            return count != null ? count : 0; // Retorna el conteo si existe; si es null retorna 0.
        } else { // Si hay búsqueda.
            String trimmed = search.trim(); // Limpia espacios del texto.
            if (trimmed.matches("\\d+")) { // Si parece un ID numérico.
                try { // Intenta convertir a long.
                    long idSearch = Long.parseLong(trimmed); // Convierte el texto a número.
                    String sql = "SELECT count(*) FROM usuarios WHERE id_usuario = ?"; // SQL para contar por ID exacto.
                    Integer count = jdbcTemplate.queryForObject(sql, Integer.class, idSearch); // Ejecuta conteo con parámetro.
                    return count != null ? count : 0; // Retorna cantidad encontrada o 0.
                } catch (NumberFormatException e) { // Captura números demasiado grandes.
                    // Si falla, pasa a búsqueda textual por nombre.
                }
            }
            String searchPattern = "%" + trimmed.toLowerCase() + "%"; // Crea patrón LIKE para búsqueda por nombre.
            String sql = "SELECT count(*) FROM usuarios WHERE " + // Inicia SQL de conteo textual.
                    "LOWER(CONCAT(primer_nombre, ' ', COALESCE(segundo_nombre, ''), ' ', primer_apellido, ' ', COALESCE(segundo_apellido, ''))) LIKE ?"; // Cuenta coincidencias en nombre completo.
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, searchPattern); // Ejecuta consulta de conteo.
            return count != null ? count : 0; // Retorna el número encontrado o 0.
        }
    }

    /**
     * Busca un usuario por su ID.
     *
     * @param id valor de la columna id_usuario.
     * @return Optional con el Usuario si existe; Optional.empty() si no existe.
     */
    public Optional<Usuario> findById(Long id) { // Método público; recibe Long y retorna Optional<Usuario>.
        List<Usuario> results = jdbcTemplate.query("SELECT * FROM usuarios WHERE id_usuario = ?", rowMapper, id); // Ejecuta SELECT filtrando por id_usuario.
        return results.stream().findFirst(); // Retorna el primer resultado como Optional o vacío si la lista no tiene elementos.
    }

    /**
     * Busca un usuario por tipo de documento y número de documento.
     *
     * @param tipoDocumento tipo de documento, por ejemplo CC, TI o CE.
     * @param documento número de documento del usuario.
     * @return Optional con el Usuario si existe; Optional.empty() si no se encuentra.
     */
    public Optional<Usuario> findByTipoDocumentoAndDocumento(String tipoDocumento, String documento) { // Retorna un Optional<Usuario>.
        List<Usuario> results = jdbcTemplate.query( // Ejecuta consulta y guarda resultados en una lista.
                "SELECT * FROM usuarios WHERE tipo_documento = ? AND documento = ?", // SQL con dos condiciones.
                rowMapper, tipoDocumento, documento // Mapea resultados y envía parámetros seguros.
        );
        return results.stream().findFirst(); // Retorna el primer usuario encontrado o Optional.empty().
    }

    /**
     * Busca un usuario por tipo, documento y contraseña exacta.
     *
     * @param tipoDocumento tipo de documento.
     * @param documento número de documento.
     * @param contrasena contraseña a comparar exactamente.
     * @return Optional con el Usuario si coinciden los tres campos; Optional.empty() si no hay coincidencia.
     */
    public Optional<Usuario> findByTipoDocumentoAndDocumentoAndContrasena(String tipoDocumento, String documento, String contrasena) { // Retorna Optional<Usuario>.
        List<Usuario> results = jdbcTemplate.query( // Ejecuta consulta SQL.
                "SELECT * FROM usuarios WHERE tipo_documento = ? AND documento = ? AND contrasena = ?", // SQL que compara los tres campos.
                rowMapper, tipoDocumento, documento, contrasena // Envía parámetros a la consulta preparada.
        );
        return results.stream().findFirst(); // Retorna primer resultado o vacío.
    }

    /**
     * Verifica si existe un usuario por ID.
     *
     * @param id identificador de usuario.
     * @return true si existe al menos un registro con ese ID; false si no existe.
     */
    public boolean existsById(Long id) { // Método público que retorna boolean.
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM usuarios WHERE id_usuario = ?", Integer.class, id); // Cuenta usuarios con ese ID.
        return count != null && count > 0; // Retorna true si el conteo no es null y es mayor que cero.
    }

    /**
     * Cuenta cuántos grupos de formación distintos existen.
     *
     * @return número de grupos únicos no nulos y no vacíos; retorna 0 si la consulta devuelve null.
     */
    public int countDistinctGroups() { // Método público que retorna int.
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(DISTINCT grupo_formacion) FROM usuarios WHERE grupo_formacion IS NOT NULL AND grupo_formacion <> ''", Integer.class); // Cuenta grupos diferentes en la tabla usuarios.
        return count != null ? count : 0; // Retorna el conteo o 0 si llega null.
    }

    /**
     * Elimina un usuario por ID.
     *
     * @param id identificador del usuario que se desea borrar.
     */
    public void deleteById(Long id) { // Método público void; no retorna valor.
        jdbcTemplate.update("DELETE FROM usuarios WHERE id_usuario = ?", id); // Ejecuta DELETE en MySQL para eliminar el usuario con ese ID.
    }

    /**
     * Inserta o actualiza un usuario.
     * <p>
     * Si {@code usuario.getId()} es null, hace INSERT.
     * Si {@code usuario.getId()} tiene valor, hace UPDATE.
     * </p>
     *
     * @param usuario objeto Usuario con los datos a persistir.
     * @return el mismo Usuario recibido, con ID asignado si fue insertado.
     */
    public Usuario save(Usuario usuario) { // Método público que retorna Usuario.
        if (usuario.getId() == null) { // Si el usuario no tiene ID, se considera nuevo.
            KeyHolder keyHolder = new GeneratedKeyHolder(); // Crea contenedor para capturar el ID autogenerado por MySQL.
            jdbcTemplate.update(connection -> { // Ejecuta un update personalizado usando la conexión JDBC.
                PreparedStatement ps = connection.prepareStatement( // Prepara sentencia SQL parametrizada.
                        "INSERT INTO usuarios (primer_nombre, segundo_nombre, primer_apellido, segundo_apellido, tipo_documento, documento, celular, grupo_formacion, correo_electronico, contrasena) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", // SQL INSERT para crear usuario.
                        Statement.RETURN_GENERATED_KEYS // Indica que MySQL debe devolver la llave generada.
                );
                ps.setString(1, usuario.getPrimerNombre()); // Asigna primer_nombre al parámetro 1.
                ps.setString(2, usuario.getSegundoNombre()); // Asigna segundo_nombre al parámetro 2.
                ps.setString(3, usuario.getPrimerApellido()); // Asigna primer_apellido al parámetro 3.
                ps.setString(4, usuario.getSegundoApellido()); // Asigna segundo_apellido al parámetro 4.
                ps.setString(5, usuario.getTipoDocumento()); // Asigna tipo_documento al parámetro 5.
                ps.setString(6, usuario.getDocumento()); // Asigna documento al parámetro 6.
                ps.setString(7, usuario.getCelular()); // Asigna celular al parámetro 7.
                ps.setString(8, usuario.getGrupoFormacion()); // Asigna grupo_formacion al parámetro 8.
                ps.setString(9, usuario.getCorreoElectronico()); // Asigna correo_electronico al parámetro 9.
                ps.setString(10, usuario.getContrasena()); // Asigna contrasena al parámetro 10.
                return ps; // Retorna el PreparedStatement listo para que JdbcTemplate lo ejecute.
            }, keyHolder); // Ejecuta el INSERT y guarda la llave generada en keyHolder.

            Number key = keyHolder.getKey(); // Obtiene el ID generado por MySQL.
            if (key != null) { // Verifica si MySQL devolvió una llave.
                usuario.setId(key.longValue()); // Asigna el ID generado al objeto Usuario en memoria.
            }
        } else { // Si el usuario ya tiene ID, se actualiza el registro existente.
            jdbcTemplate.update( // Ejecuta UPDATE sobre la tabla usuarios.
                    "UPDATE usuarios SET primer_nombre = ?, segundo_nombre = ?, primer_apellido = ?, segundo_apellido = ?, tipo_documento = ?, documento = ?, celular = ?, grupo_formacion = ?, correo_electronico = ?, contrasena = ? WHERE id_usuario = ?", // SQL de actualización.
                    usuario.getPrimerNombre(), // Valor para primer_nombre.
                    usuario.getSegundoNombre(), // Valor para segundo_nombre.
                    usuario.getPrimerApellido(), // Valor para primer_apellido.
                    usuario.getSegundoApellido(), // Valor para segundo_apellido.
                    usuario.getTipoDocumento(), // Valor para tipo_documento.
                    usuario.getDocumento(), // Valor para documento.
                    usuario.getCelular(), // Valor para celular.
                    usuario.getGrupoFormacion(), // Valor para grupo_formacion.
                    usuario.getCorreoElectronico(), // Valor para correo_electronico.
                    usuario.getContrasena(), // Valor para contrasena.
                    usuario.getId() // ID usado en WHERE id_usuario = ?.
            );
        }
        return usuario; // Retorna el usuario insertado o actualizado.
    }
}