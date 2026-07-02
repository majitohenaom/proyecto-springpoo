package com.sena.springpoo.repository;

import com.sena.springpoo.models.Producto;
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
 * Capa de persistencia para la entidad {@link Producto}.
 * <p>
 * Este repositorio utiliza {@link JdbcTemplate} de Spring para ejecutar sentencias SQL
 * puras sobre la tabla {@code productos} en MySQL. Contiene operaciones CRUD completas
 * y consultas de búsqueda especializadas.
 * </p>
 */
@Repository
public class ProductoRepository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final RowMapper<Producto> rowMapper = (rs, rowNum) -> {
        Producto p = new Producto();
        p.setId(rs.getLong("id"));
        p.setNombre(rs.getString("nombre"));
        p.setPrecio(rs.getDouble("precio"));
        p.setCategoria(rs.getString("categoria"));
        return p;
    };

    /**
     * Obtiene todos los productos registrados en la base de datos.
     *
     * @return Una lista completa de objetos {@link Producto}.
     */
    public List<Producto> findAll() {
        return jdbcTemplate.query("SELECT * FROM productos", rowMapper);
    }

    public Optional<Producto> findById(Long id) {
        List<Producto> results = jdbcTemplate.query("SELECT * FROM productos WHERE id = ?", rowMapper, id);
        return results.stream().findFirst();
    }

    public List<Producto> findByCategoria(String categoria) {
        return jdbcTemplate.query("SELECT * FROM productos WHERE categoria = ?", rowMapper, categoria);
    }

    public List<Producto> findByPrecioLessThanEqual(Double precioMax) {
        return jdbcTemplate.query("SELECT * FROM productos WHERE precio <= ?", rowMapper, precioMax);
    }

    public List<Producto> findByCategoriaAndPrecioLessThanEqual(String categoria, Double precioMax) {
        return jdbcTemplate.query("SELECT * FROM productos WHERE categoria = ? AND precio <= ?", rowMapper, categoria, precioMax);
    }

    public boolean existsById(Long id) {
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM productos WHERE id = ?", Integer.class, id);
        return count != null && count > 0;
    }

    /**
     * Elimina un producto permanentemente de la base de datos.
     *
     * @param id Identificador numérico único del producto a eliminar.
     */
    public void deleteById(Long id) {
        jdbcTemplate.update("DELETE FROM productos WHERE id = ?", id);
    }

    /**
     * Guarda o actualiza un producto en la base de datos.
     * <p>
     * Si el producto carece de ID (es nulo), se asume que es nuevo y se inserta (INSERT), 
     * recuperando la llave generada automáticamente. Si ya tiene ID, se actualiza (UPDATE).
     * </p>
     *
     * @param producto Objeto a persistir.
     * @return El mismo objeto con su ID asignado o actualizado.
     */
    public Producto save(Producto producto) {
        if (producto.getId() == null) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO productos (nombre, precio, categoria) VALUES (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
                );
                ps.setString(1, producto.getNombre());
                ps.setDouble(2, producto.getPrecio());
                ps.setString(3, producto.getCategoria());
                return ps;
            }, keyHolder);

            Number key = keyHolder.getKey();
            if (key != null) {
                producto.setId(key.longValue());
            }
        } else {
            jdbcTemplate.update(
                "UPDATE productos SET nombre = ?, precio = ?, categoria = ? WHERE id = ?",
                producto.getNombre(),
                producto.getPrecio(),
                producto.getCategoria(),
                producto.getId()
            );
        }
        return producto;
    }
}