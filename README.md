# SENA GDF — Plataforma de Gestión de Formación Profesional

Este repositorio contiene la plataforma **SENA GDF**, una aplicación empresarial web desarrollada en **Java 17** y **Spring Boot** utilizando arquitectura **MVC** clásica de alto rendimiento, persistencia directa mediante **JDBC (JdbcTemplate)**, autenticación segura con **Spring Security**, motor de plantillas **Thymeleaf**, limitación de peticiones por tasa con **Bucket4j** y auditoría avanzada mediante **Log4j2**.

---

## 📋 Tabla de Contenidos
1. [Arquitectura del Sistema](#1-arquitectura-del-sistema)
2. [Estructura del Proyecto](#2-estructura-del-proyecto)
3. [Descripción de Componentes Java](#3-descripción-de-componentes-java)
4. [Estructura de la Base de Datos](#4-estructura-de-la-base-de-datos)
5. [Endpoints y Vistas del Sistema](#5-endpoints-y-vistas-del-sistema)
6. [Carga Masiva de Archivos](#6-carga-masiva-de-archivos)
7. [Filtros de Seguridad y Control de Tasa (Rate Limit)](#7-filtros-de-seguridad-y-control-de-tasa-rate-limit)
8. [Configuración y Monitoreo de Logs (Log4j2)](#8-configuración-y-monitoreo-de-logs-log4j2)
9. [Instrucciones de Instalación y Ejecución](#9-instrucciones-de-instalación-y-ejecución)

---

## 1. Arquitectura del Sistema

La aplicación sigue el patrón arquitectónico **Model-View-Controller (MVC)** combinado con servicios REST:
- **Modelo:** Representado por clases Java limpias (`Producto` y `Usuario`) mapeando los datos relacionales de la base de datos.
- **Vista:** Plantillas HTML con Thymeleaf procesadas dinámicamente en el lado del servidor y enriquecidas con Javascript moderno en el lado del cliente (fetch API asíncrono, Drag & Drop).
- **Controlador:** Clases con anotaciones de Spring (`@Controller` y `@RestController`) que gestionan la navegación, interceptan peticiones HTTP y retornan tanto vistas web como formatos estructurados (JSON).
- **Acceso a Datos:** En lugar de utilizar JPA/Hibernate, este proyecto implementa consultas SQL nativas directas mediante **JdbcTemplate** de Spring, optimizando los tiempos de respuesta y el control sobre la base de datos MySQL.

---

## 2. Estructura del Proyecto

La organización del código fuente en la carpeta `src/main` es la siguiente:

```text
├── java/com/sena/springpoo
│   ├── config/
│   │   └── SecurityConfig.java            # Configuración de Spring Security (BCrypt, Login)
│   ├── controller/
│   │   ├── ControllerUsuarios.java         # Vistas de login/registro y API REST de usuarios
│   │   ├── CustomErrorController.java      # Manejo global de excepciones (404, 403, 500)
│   │   ├── FileUploadController.java       # Procesamiento y validación de carga masiva CSV
│   │   └── NuevoController.java            # Vistas y API REST para gestión de productos
│   ├── filter/
│   │   ├── DatabaseConnectionFilter.java  # Interceptor que valida disponibilidad de MySQL
│   │   └── RateLimitFilter.java            # Limitación de peticiones por IP (Bucket4j)
│   ├── models/
│   │   ├── Producto.java                  # Modelo de datos de Producto
│   │   └── Usuario.java                   # Modelo de datos de Usuario
│   ├── repository/
│   │   ├── ProductoRepository.java        # Consultas JDBC nativas sobre tabla 'productos'
│   │   └── UsuarioRepository.java         # Consultas JDBC nativas sobre tabla 'usuarios'
│   └── SpringpooApplication.java          # Clase de inicio y ciclo de vida de la aplicación
│
└── resources/
    ├── templates/                         # Vistas renderizadas por Thymeleaf
    │   ├── 500.html                       # Error 500 (Base de datos desconectada)
    │   ├── error.html                     # Error genérico (404, 403)
    │   ├── formulario.html                # Panel de gestión simplificado (ES/EN)
    │   ├── login.html                     # Inicio de sesión de usuarios
    │   ├── prueba.html                    # Dashboard interactivo con CRUD y Drag & Drop
    │   └── registro.html                  # Registro de nuevos aprendices
    ├── application.properties             # Parámetros de configuración del framework
    ├── log4j2.xml                         # Políticas y appenders de la auditoría de logs
    └── schema.sql                         # Inicialización automática de tablas en MySQL
```

---

## 3. Descripción de Componentes Java

Todos los archivos Java han sido completamente comentados con bloques **Javadoc** detallando sus tipos de retorno, parámetros y comportamiento técnico.

### Controladores (`com.sena.springpoo.controller`)
- **`ControllerUsuarios`:** Maneja las rutas principales de navegación Thymeleaf (login, registro y redirección). También actúa como un controlador híbrido exponiendo endpoints de consulta AJAX, eliminación de usuarios y renderización asíncrona de recursos como imágenes del sistema.
- **`NuevoController`:** Gestiona el flujo y la API CRUD de productos (Crear, Editar, Consultar por ID o Nombre y Eliminar) con soporte de mensajería multi-idioma (Español/Inglés) según las cabeceras `Accept-Language`.
- **`FileUploadController`:** Se encarga de procesar flujos de subida de archivos mediante multipart. Contiene métodos robustos para parsear líneas CSV, ignorar cabeceras, encriptar contraseñas al vuelo e insertar de forma transaccional productos o usuarios.
- **`CustomErrorController`:** Intercepta cualquier excepción a nivel de contenedor de servlet. Posee lógica para detectar fallos específicos de infraestructura (como MySQL apagado en XAMPP) y redirigir dinámicamente con alertas descriptivas.

### Repositorios (`com.sena.springpoo.repository`)
- **`UsuarioRepository`:** Ejecuta consultas nativas de inserción, actualización, eliminación e inicio de sesión sobre la base de datos mediante sentencias SQL preparadas (`PreparedStatement`). Implementa mapeadores de filas (`RowMapper`) para transformar conjuntos de resultados SQL a entidades POJO `Usuario`.
- **`ProductoRepository`:** Ejecuta lógica equivalente para la tabla de productos, implementando búsquedas optimizadas por rango de caracteres de nombre o ID.

### Filtros e Interceptores (`com.sena.springpoo.filter`)
- **`DatabaseConnectionFilter`:** Filtro web que comprueba antes de procesar cualquier solicitud HTTP si la base de datos MySQL está activa. En caso de detectar fallos de enlace (refused connection), interrumpe el ciclo e invoca un error 500 simulado para prevenir cuelgues del servidor.
- **`RateLimitFilter`:** Filtro web para seguridad perimetral de la API. Asigna un depósito (Bucket) único a cada dirección IP del cliente, otorgando un máximo de 10 peticiones por minuto. Si se excede la tasa, bloquea inmediatamente la petición respondiendo con código HTTP `429 Too Many Requests`.

---

## 4. Estructura de la Base de Datos

El motor relacional utiliza la base de datos `springpoo` en MySQL. Las tablas se inicializan automáticamente con la estructura definida en `src/main/resources/schema.sql`:

### Tabla: `productos`
*   `id`: `BIGINT AUTO_INCREMENT` (Clave primaria).
*   `nombre`: `VARCHAR(100) NOT NULL` (Nombre del producto).
*   `precio`: `DOUBLE NOT NULL` (Valor comercial).
*   `categoria`: `VARCHAR(80)` (Grupo).

### Tabla: `usuarios`
*   `id_usuario`: `BIGINT AUTO_INCREMENT` (Clave primaria).
*   `primer_nombre`: `VARCHAR(255)` (Obligatorio).
*   `segundo_nombre`: `VARCHAR(255)` (Opcional).
*   `primer_apellido`: `VARCHAR(255)` (Obligatorio).
*   `segundo_apellido`: `VARCHAR(255)` (Opcional).
*   `tipo_documento`: `VARCHAR(255)` (CC, TI, CE, PA).
*   `documento`: `VARCHAR(255)` (Usado para login).
*   `celular`: `VARCHAR(255)`.
*   `grupo_formacion`: `VARCHAR(255)` (Código de ficha).
*   `correo_electronico`: `VARCHAR(255)`.
*   `contrasena`: `VARCHAR(255)` (Clave encriptada).

---

## 5. Endpoints y Vistas del Sistema

### Rutas de Navegación (HTML Thymeleaf)
-   `GET /login`: Muestra el inicio de sesión.
-   `GET /registro`: Muestra el formulario para autoregistro de usuarios.
-   `GET /formulario`: Panel simplificado que lista y crea usuarios mediante peticiones AJAX, con traducción en caliente ES/EN.
-   `GET /prueba.html` (o endpoint mapeado en controlador): Dashboard interactivo completo que integra carga masiva por Drag & Drop, paginación dinámica, búsqueda parametrizada y modal para editar perfiles.

### API REST de Usuarios y Productos
-   `GET /usuarios/lista`: Retorna JSON con la lista de usuarios.
-   `POST /usuarios/guardar`: Registra/Inserta un nuevo usuario (espera JSON).
-   `DELETE /usuarios/borrar/{id}`: Elimina físicamente un usuario de la base de datos.
-   `GET /nuevo/productos`: Obtiene productos paginados y filtrados.
-   `POST /nuevo/productos`: Registra un producto.
-   `PUT /nuevo/productos/{id}`: Modifica un producto existente.
-   `DELETE /nuevo/productos/{id}`: Elimina un producto.

---

## 6. Carga Masiva de Archivos

La plataforma permite poblar las tablas de forma masiva a través de archivos **CSV** o **TXT** mediante los siguientes endpoints controlados por `FileUploadController`:

-   **Usuarios:** `POST /upload/usuarios`
-   **Productos:** `POST /upload/productos`

### Formatos Esperados
Los archivos deben contener una cabecera en la primera línea (que es descartada automáticamente). Los valores pueden estar delimitados por **comas (`,`)** o **puntos y comas (`;`)**.

*Ejemplo de archivo de usuarios (`usuarios.csv`):*
```csv
primerNombre,segundoNombre,primerApellido,segundoApellido,tipoDocumento,documento,celular,grupoFormacion,correoElectronico,contrasena
Juan,Carlos,Pérez,López,CC,1000123456,3001234567,ADSO-2758,juan.perez@sena.edu.co,claveSegura123
María,,Gómez,Ruiz,TI,1000987654,3109876543,ADSO-2758,maria.gomez@sena.edu.co,claveSegura456
```

---

## 7. Filtros de Seguridad y Control de Tasa (Rate Limit)

### Seguridad (`SecurityConfig`)
La aplicación cuenta con seguridad perimetral de accesos y cifrado:
1.  **Cifrado BCrypt:** Todas las contraseñas se encriptan utilizando un algoritmo hash de fuerza de trabajo 10 antes de guardarse en MySQL.
2.  **Autorización:** Se configuran permisos públicos para rutas estáticas, pantallas de login y registro. Se requiere autenticación para ingresar a `/formulario` o interactuar con el Dashboard de pruebas.

### Rate Limiting (`RateLimitFilter`)
El sistema protege la infraestructura del abuso de peticiones mediante el algoritmo de token-bucket implementado con **Bucket4j**:
-   **Límite:** 10 tokens (peticiones) por minuto.
-   **Recarga:** Se repone a una tasa de 10 tokens cada 60 segundos.
-   **Acción:** Si un cliente excede este límite, la petición es interceptada respondiendo con estado `429 Too Many Requests` y no llega a consumir recursos de base de datos ni procesamiento de Spring Boot.

---

## 8. Configuración y Monitoreo de Logs (Log4j2)

Toda actividad crítica y diagnóstico de errores es registrada mediante **Log4j2**, estructurado bajo el archivo `src/main/resources/log4j2.xml`. Los registros se dividen de la siguiente manera:

1.  **`logs/app.log` (Consola y Archivo General):** Guarda el registro cronológico del arranque del servidor, logs de nivel INFO en adelante generados por Spring y dependencias externas.
2.  **`logs/crud.log` (Auditoría CRUD):** Registra las operaciones de base de datos llevadas a cabo por los controladores (ej: inserciones, actualizaciones y borrados), facilitando el rastreo de cambios.
3.  **`logs/errores.log` (Errores del Sistema):** Registra **únicamente** trazas de errores de nivel `ERROR` o `FATAL`, tales como excepciones no controladas o pérdidas de enlace con la base de datos MySQL, permitiendo un diagnóstico rápido y directo.

---

## 9. Instrucciones de Instalación y Ejecución

### Requisitos Previos
*   **Java JDK 17** instalado en el sistema.
*   **Maven** (opcional, el proyecto incluye el ejecutable `mvnw` local).
*   **XAMPP** o un servidor **MySQL** local activo ejecutándose en el puerto estándar `3306`.
*   Tener creada la base de datos `springpoo` (o dejar que la cadena de conexión la cree automáticamente).

### Pasos para Ejecutar
1.  Asegúrate de que el puerto `3306` de tu MySQL esté activo en XAMPP.
2.  Ejecuta la aplicación haciendo doble clic sobre el script por lotes:
    ```bash
    iniciar.bat
    ```
    *Este script autodetectará la versión JDK 17 local y lanzará el comando de Spring Boot.*
3.  Abre un navegador e ingresa a:
    ```text
    http://localhost:8080/login
    ```
4.  Si deseas probar en caliente el flujo del sistema o simular que el motor relacional se ha apagado, presiona el botón "⚠️ Simular Error 500" en el login o apaga MySQL directamente en tu panel de control de XAMPP.
