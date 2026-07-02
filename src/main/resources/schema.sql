-- ──────────────────────────────────────────────────────────────────
--  ESQUEMA DE BASE DE DATOS - PLATAFORMA SENA GDF
-- ──────────────────────────────────────────────────────────────────
-- Este archivo es ejecutado automáticamente por Spring Boot al arrancar
-- si la propiedad spring.sql.init.mode está configurada como 'always'.
-- Define las tablas de persistencia en la base de datos MySQL 'springpoo'.

-- 1. TABLA: productos
-- Almacena los productos gestionados en el inventario/formulario.
CREATE TABLE IF NOT EXISTS productos (
    -- Identificador único autoincrementable de cada producto
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    
    -- Nombre o descripción corta del producto
    nombre VARCHAR(100) NOT NULL,
    
    -- Precio unitario comercial del producto
    precio DOUBLE NOT NULL,
    
    -- Categoría o grupo al que pertenece (ej: Tecnología, Ropa, etc.)
    categoria VARCHAR(80)
);

-- 2. TABLA: usuarios
-- Almacena la información de aprendices y usuarios registrados en la plataforma.
CREATE TABLE IF NOT EXISTS usuarios (
    -- Identificador interno único y autoincrementable del usuario
    id_usuario BIGINT AUTO_INCREMENT PRIMARY KEY,
    
    -- Primer nombre del usuario (obligatorio en el formulario)
    primer_nombre VARCHAR(255),
    
    -- Segundo nombre (opcional)
    segundo_nombre VARCHAR(255),
    
    -- Primer apellido (obligatorio)
    primer_apellido VARCHAR(255),
    
    -- Segundo apellido (opcional)
    segundo_apellido VARCHAR(255),
    
    -- Tipo de documento de identidad (ej: CC, TI, CE, PA)
    tipo_documento VARCHAR(255),
    
    -- Número de documento de identidad (usado como login junto al tipo)
    documento VARCHAR(255),
    
    -- Número de contacto o celular
    celular VARCHAR(255),
    
    -- Ficha o grupo de formación académica SENA (ej: ADSO-2758)
    grupo_formacion VARCHAR(255),
    
    -- Correo electrónico institucional o personal
    correo_electronico VARCHAR(255),
    
    -- Contraseña encriptada mediante BCrypt en la capa de seguridad
    contrasena VARCHAR(255)
);
