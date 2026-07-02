package com.sena.springpoo.models;

/**
 * Representa la entidad (modelo de dominio) de un Producto dentro de la plataforma SENA GDF.
 * <p>
 * Esta clase almacena los datos básicos de un producto, como su identificador único (ID),
 * nombre, precio y categoría, para ser manipulados en la base de datos a través 
 * de {@link com.sena.springpoo.repository.ProductoRepository}.
 * </p>
 */
public class Producto {

    private Long id;
    private String nombre;
    private Double precio;
    private String categoria;

    // ── Constructores ──
    
    /**
     * Constructor por defecto, requerido por frameworks como Spring y herramientas de serialización JSON.
     */
    public Producto() {}

    /**
     * Constructor para instanciar un nuevo Producto con todos sus datos principales (sin ID).
     *
     * @param nombre Nombre comercial o descriptivo del producto.
     * @param precio Valor monetario asignado.
     * @param categoria Clasificación o categoría a la que pertenece.
     */
    public Producto(String nombre, Double precio, String categoria) {
        this.nombre    = nombre;
        this.precio    = precio;
        this.categoria = categoria;
    }

    // ── Getters y Setters ──
    public Long   getId()                        { return id; }
    public void   setId(Long id)                 { this.id = id; }

    public String getNombre()                    { return nombre; }
    public void   setNombre(String nombre)       { this.nombre = nombre; }

    public Double getPrecio()                    { return precio; }
    public void   setPrecio(Double precio)       { this.precio = precio; }

    public String getCategoria()                 { return categoria; }
    public void   setCategoria(String categoria) { this.categoria = categoria; }
}