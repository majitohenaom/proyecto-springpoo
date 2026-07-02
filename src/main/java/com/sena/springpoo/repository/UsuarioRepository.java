    package com.sena.springpoo.repository;
    
    import com.sena.springpoo.models.Usuario;
    import org.springframework.beans.factory.annotation.Autowired;
    import org.springframework.jdbc.core.JdbcTemplate;
    import org.springframework.jdbc.core.RowMapper;
    import org.springframework.jdbc.support.GeneratedKeyHolder;
    import org.springframework.jdbc.support.KeyHolder;
    import org.springframework.stereotype.Repository;
    
    import java.sql.PreparedStatement;
    import java.sql.Statement;
    import java.util.List;
    import java.util.Optional;
    
    /**
     * Capa de acceso a datos para la entidad {@link Usuario}.
     * <p>
     * Se comunica con la base de datos MySQL usando {@link JdbcTemplate}. 
     * Implementa lógica robusta para la paginación, búsqueda (exacta por ID o parcial por nombres completos) 
     * y operaciones de guardado y validación de credenciales.
     * </p>
     */
    @Repository
    public class UsuarioRepository {
    
        @Autowired
        private JdbcTemplate jdbcTemplate;
    
        private final RowMapper<Usuario> rowMapper = (rs, rowNum) -> {
            Usuario u = new Usuario();
            u.setId(rs.getLong("id_usuario"));
            u.setPrimerNombre(rs.getString("primer_nombre"));
            u.setSegundoNombre(rs.getString("segundo_nombre"));
            u.setPrimerApellido(rs.getString("primer_apellido"));
            u.setSegundoApellido(rs.getString("segundo_apellido"));
            u.setTipoDocumento(rs.getString("tipo_documento"));
            u.setDocumento(rs.getString("documento"));
            u.setCelular(rs.getString("celular"));
            u.setGrupoFormacion(rs.getString("grupo_formacion"));
            u.setCorreoElectronico(rs.getString("correo_electronico"));
            u.setContrasena(rs.getString("contrasena"));
            return u;
        };

        public List<Usuario> findAll() {
            return jdbcTemplate.query("SELECT * FROM usuarios", rowMapper);
        }

        /**
         * Retorna una lista paginada de usuarios basándose en un criterio de búsqueda opcional.
         * <p>
         * Si el término de búsqueda es puramente numérico, intenta filtrar de forma exacta
         * por {@code id_usuario}. De lo contrario (o si es un texto), busca de manera parcial 
         * concatenando los nombres y apellidos.
         * </p>
         *
         * @param search Texto ingresado por el usuario en la barra de búsqueda (puede ser nulo o vacío).
         * @param page Número de página actual (índice 0).
         * @param size Cantidad de registros requeridos por página.
         * @return Una lista de {@link Usuario} que cumplen con el criterio y la paginación.
         */
        public List<Usuario> findPaginated(String search, int page, int size) {
            int offset = page * size;
            if (search == null || search.trim().isEmpty()) {
                return jdbcTemplate.query("SELECT * FROM usuarios LIMIT ? OFFSET ?", rowMapper, size, offset);
            } else {
                String trimmed = search.trim();
                if (trimmed.matches("\\d+")) {
                    try {
                        long idSearch = Long.parseLong(trimmed);
                        String sql = "SELECT * FROM usuarios WHERE id_usuario = ? LIMIT ? OFFSET ?";
                        return jdbcTemplate.query(sql, rowMapper, idSearch, size, offset);
                    } catch (NumberFormatException e) {
                        // Fallback to name search in case number exceeds long limits
                    }
                }
                String searchPattern = "%" + trimmed.toLowerCase() + "%";
                String sql = "SELECT * FROM usuarios WHERE " +
                             "LOWER(CONCAT(primer_nombre, ' ', COALESCE(segundo_nombre, ''), ' ', primer_apellido, ' ', COALESCE(segundo_apellido, ''))) LIKE ? " +
                             "LIMIT ? OFFSET ?";
                return jdbcTemplate.query(sql, rowMapper, searchPattern, size, offset);
            }
        }

        public int count(String search) {
            if (search == null || search.trim().isEmpty()) {
                Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM usuarios", Integer.class);
                return count != null ? count : 0;
            } else {
                String trimmed = search.trim();
                if (trimmed.matches("\\d+")) {
                    try {
                        long idSearch = Long.parseLong(trimmed);
                        String sql = "SELECT count(*) FROM usuarios WHERE id_usuario = ?";
                        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, idSearch);
                        return count != null ? count : 0;
                    } catch (NumberFormatException e) {
                        // Fallback to name search in case number exceeds long limits
                    }
                }
                String searchPattern = "%" + trimmed.toLowerCase() + "%";
                String sql = "SELECT count(*) FROM usuarios WHERE " +
                             "LOWER(CONCAT(primer_nombre, ' ', COALESCE(segundo_nombre, ''), ' ', primer_apellido, ' ', COALESCE(segundo_apellido, ''))) LIKE ?";
                Integer count = jdbcTemplate.queryForObject(sql, Integer.class, searchPattern);
                return count != null ? count : 0;
            }
        }

        public Optional<Usuario> findById(Long id) {
            List<Usuario> results = jdbcTemplate.query("SELECT * FROM usuarios WHERE id_usuario = ?", rowMapper, id);
            return results.stream().findFirst();
        }

        public Optional<Usuario> findByTipoDocumentoAndDocumento(String tipoDocumento, String documento) {
            List<Usuario> results = jdbcTemplate.query(
                "SELECT * FROM usuarios WHERE tipo_documento = ? AND documento = ?",
                rowMapper, tipoDocumento, documento
            );
            return results.stream().findFirst();
        }
    
        public Optional<Usuario> findByTipoDocumentoAndDocumentoAndContrasena(String tipoDocumento, String documento, String contrasena) {
            List<Usuario> results = jdbcTemplate.query(
                "SELECT * FROM usuarios WHERE tipo_documento = ? AND documento = ? AND contrasena = ?",
                rowMapper, tipoDocumento, documento, contrasena
            );
            return results.stream().findFirst();
        }
    
        public boolean existsById(Long id) {
            Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM usuarios WHERE id_usuario = ?", Integer.class, id);
            return count != null && count > 0;
        }
    
        public int countDistinctGroups() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(DISTINCT grupo_formacion) FROM usuarios WHERE grupo_formacion IS NOT NULL AND grupo_formacion <> ''", Integer.class);
        return count != null ? count : 0;
    }

    public void deleteById(Long id) {
            jdbcTemplate.update("DELETE FROM usuarios WHERE id_usuario = ?", id);
        }
    
        /**
         * Inserta o actualiza la información de un usuario en la tabla {@code usuarios}.
         *
         * @param usuario El objeto usuario con los datos a persistir.
         * @return El usuario con su ID ya configurado por la base de datos (si fue una inserción).
         */
        public Usuario save(Usuario usuario) {
            if (usuario.getId() == null) {
                KeyHolder keyHolder = new GeneratedKeyHolder();
                jdbcTemplate.update(connection -> {
                    PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO usuarios (primer_nombre, segundo_nombre, primer_apellido, segundo_apellido, tipo_documento, documento, celular, grupo_formacion, correo_electronico, contrasena) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS
                    );
                    ps.setString(1, usuario.getPrimerNombre());
                    ps.setString(2, usuario.getSegundoNombre());
                    ps.setString(3, usuario.getPrimerApellido());
                    ps.setString(4, usuario.getSegundoApellido());
                    ps.setString(5, usuario.getTipoDocumento());
                    ps.setString(6, usuario.getDocumento());
                    ps.setString(7, usuario.getCelular());
                    ps.setString(8, usuario.getGrupoFormacion());
                    ps.setString(9, usuario.getCorreoElectronico());
                    ps.setString(10, usuario.getContrasena());
                    return ps;
                }, keyHolder);
    
                Number key = keyHolder.getKey();
                if (key != null) {
                    usuario.setId(key.longValue());
                }
            } else {
                jdbcTemplate.update(
                    "UPDATE usuarios SET primer_nombre = ?, segundo_nombre = ?, primer_apellido = ?, segundo_apellido = ?, tipo_documento = ?, documento = ?, celular = ?, grupo_formacion = ?, correo_electronico = ?, contrasena = ? WHERE id_usuario = ?",
                    usuario.getPrimerNombre(),
                    usuario.getSegundoNombre(),
                    usuario.getPrimerApellido(),
                    usuario.getSegundoApellido(),
                    usuario.getTipoDocumento(),
                    usuario.getDocumento(),
                    usuario.getCelular(),
                    usuario.getGrupoFormacion(),
                    usuario.getCorreoElectronico(),
                    usuario.getContrasena(),
                    usuario.getId()
                );
            }
            return usuario;
        }
    }