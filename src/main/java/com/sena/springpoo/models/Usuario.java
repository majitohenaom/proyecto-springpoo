package com.sena.springpoo.models;

/**
 * Representa la entidad (modelo de dominio) de un Usuario o Aprendiz dentro de la plataforma SENA GDF.
 * <p>
 * Contiene toda la información personal, de contacto, y de autenticación necesaria
 * para gestionar el acceso y los registros en el sistema.
 * Es utilizado directamente por {@link com.sena.springpoo.repository.UsuarioRepository} 
 * para mapear las filas de la base de datos a objetos Java.
 * </p>
 */
public class Usuario {

    private Long id;
    private String primerNombre;
    private String segundoNombre;
    private String primerApellido;
    private String segundoApellido;
    private String tipoDocumento;
    private String documento;
    private String celular;
    private String grupoFormacion;
    private String correoElectronico;
    private String contrasena;

    /**
     * Constructor por defecto, requerido por herramientas de persistencia y serialización JSON.
     */
    public Usuario() {}

    // Getters y Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPrimerNombre() { return primerNombre; }
    public void setPrimerNombre(String primerNombre) { this.primerNombre = primerNombre; }

    public String getSegundoNombre() { return segundoNombre; }
    public void setSegundoNombre(String segundoNombre) { this.segundoNombre = segundoNombre; }

    public String getPrimerApellido() { return primerApellido; }
    public void setPrimerApellido(String primerApellido) { this.primerApellido = primerApellido; }

    public String getSegundoApellido() { return segundoApellido; }
    public void setSegundoApellido(String segundoApellido) { this.segundoApellido = segundoApellido; }

    public String getTipoDocumento() { return tipoDocumento; }
    public void setTipoDocumento(String tipoDocumento) { this.tipoDocumento = tipoDocumento; }

    public String getDocumento() { return documento; }
    public void setDocumento(String documento) { this.documento = documento; }

    public String getCelular() { return celular; }
    public void setCelular(String celular) { this.celular = celular; }

    public String getGrupoFormacion() { return grupoFormacion; }
    public void setGrupoFormacion(String grupoFormacion) { this.grupoFormacion = grupoFormacion; }

    public String getCorreoElectronico() { return correoElectronico; }
    public void setCorreoElectronico(String correoElectronico) { this.correoElectronico = correoElectronico; }

    public String getContrasena() { return contrasena; }
    public void setContrasena(String contrasena) { this.contrasena = contrasena; }
}