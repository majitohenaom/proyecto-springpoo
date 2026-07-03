package com.sena.springpoo.config; // Define el paquete donde se encuentra esta clase; pertenece a la capa de configuración del proyecto Springpoo.

import org.springframework.context.annotation.Bean; // Importa @Bean, anotación usada para registrar objetos dentro del contenedor de Spring.
import org.springframework.context.annotation.Configuration; // Importa @Configuration, anotación que marca esta clase como una clase de configuración de Spring.
import org.springframework.security.config.annotation.web.builders.HttpSecurity; // Importa HttpSecurity, clase que permite configurar reglas de seguridad HTTP.
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity; // Importa @EnableWebSecurity, anotación que activa la seguridad web de Spring Security.
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;// Importa BCryptPasswordEncoder, implementación que cifra contraseñas usando BCrypt.
import org.springframework.security.crypto.password.PasswordEncoder; // Importa PasswordEncoder, interfaz general para codificar y validar contraseñas.
import org.springframework.security.web.SecurityFilterChain; // Importa SecurityFilterChain, objeto que representa la cadena de filtros de seguridad.


/**
 * Clase de configuración principal de Spring Security dentro del proyecto Springpoo.
 * <p>
 * Esta clase pertenece a la capa de configuración porque no controla vistas,
 * no guarda datos y no representa una entidad; su responsabilidad es definir
 * cómo se comporta la seguridad de la aplicación.
 * </p>
 * <p>
 * En este proyecto se usa para:
 * </p>
 * <ul>
 *   <li>Registrar un {@link PasswordEncoder} basado en BCrypt para cifrar contraseñas.</li>
 *   <li>Configurar qué rutas requieren autenticación y cuáles son públicas.</li>
 *   <li>Definir la página personalizada de login.</li>
 *   <li>Definir a dónde se redirige al usuario después de iniciar sesión correctamente.</li>
 *   <li>Configurar el cierre de sesión.</li>
 * </ul>
 */

@Configuration // Le indica a Spring que esta clase contiene métodos @Bean; Spring la lee al iniciar la aplicación y registra sus objetos en memoria.
@EnableWebSecurity // Activa la configuración de seguridad web; permite que Spring Security inserte sus filtros en el flujo de peticiones HTTP.
public class SecurityConfig { // Declara la clase SecurityConfig; es pública para que Spring pueda detectarla e instanciarla durante el arranque.


    /**
     * Registra el codificador de contraseñas de la aplicación.
     * <p>
     * Modificador de acceso: {@code public}, porque Spring necesita poder invocar
     * este método desde el contexto de aplicación.
     * </p>
     * <p>
     * Tipo de retorno: {@link PasswordEncoder}. Es una interfaz de Spring Security
     * que define operaciones para cifrar contraseñas y comparar texto plano contra
     * contraseñas cifradas.
     * </p>
     * <p>
     * Valor retornado: una nueva instancia de {@link BCryptPasswordEncoder}.
     * BCrypt genera un hash seguro con salt interno, por eso dos contraseñas iguales
     * pueden producir hashes diferentes.
     * </p>
     * <p>
     * En el proyecto Springpoo, este bean es usado por controladores como
     * {@code ControllerUsuarios} y {@code FileUploadController} para cifrar
     * contraseñas antes de guardarlas en la tabla {@code usuarios}.
     * </p>
     *
     * @return un objeto {@link PasswordEncoder} implementado por {@link BCryptPasswordEncoder}.
     */

    @Bean // Registra el objeto retornado como bean de Spring; después puede inyectarse con @Autowired en otras clases.
    public PasswordEncoder passwordEncoder() { // Mtodo público llamado passwordEncoder; no recibe parámetros y retorna un PasswordEncoder.
        return new BCryptPasswordEncoder(); // Crea en memoria y retorna un codificador BCrypt para cifrar y verificar contraseñas.

    }

    /**
     * Configura la cadena de filtros de seguridad que Spring Security aplicará
     * a las peticiones HTTP del proyecto Springpoo.
     * <p>
     * Modificador de acceso: {@code public}, porque Spring debe poder ejecutar
     * este método al construir el contexto.
     * </p>
     * <p>
     * Tipo de retorno: {@link SecurityFilterChain}. Representa el conjunto de filtros
     * que revisan autenticación, autorización, login, logout y otras reglas antes
     * de que una petición llegue a los controladores.
     * </p>
     * <p>
     * Parámetro recibido:
     * {@code HttpSecurity http}, objeto creado por Spring Security que permite
     * configurar reglas de seguridad mediante una API fluida.
     * </p>
     * <p>
     * Este método puede lanzar {@link Exception} si ocurre un error al construir
     * la configuración de seguridad.
     * </p>
     *
     * @param http objeto de configuración HTTP proporcionado por Spring Security.
     * @return la cadena de filtros de seguridad ya configurada.
     * @throws Exception si Spring Security no puede construir correctamente los filtros.
     */

    @Bean // Registra la SecurityFilterChain como bean; Spring Security la usará para proteger las rutas web.
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception { // Define el métdo que configura seguridad; recibe HttpSecurity y retorna SecurityFilterChain.
        http
            .csrf(csrf -> csrf.disable()) // Desactiva CSRF; permite enviar formularios y peticiones REST sin token CSRF adicional.
                .authorizeHttpRequests(auth -> auth // Inicia la configuración de autorización de rutas HTTP.

                // Ruta protegida que sí requiere estar autenticado
                .requestMatchers("/formulario").authenticated() // Exige que la ruta /formulario solo sea accesible para usuarios autenticados.

                // Cualquier otra ruta es pública (para permitir 404 en rutas incorrectas)
                .anyRequest().permitAll() // Permite todas las demás rutas sin autenticación, como /login, /registro, /guardar y endpoints públicos.
            )

            .formLogin(form -> form // Configura el inicio de sesión basado en formulario HTML.
                    .loginPage("/login")  // Indica que la página personalizada de login está en la ruta /login.
                .defaultSuccessUrl("/nuevo/prueba", true) // Después de iniciar sesión correctamente, redirige siempre a /nuevo/prueba.
                    .permitAll() // Permite que cualquier usuario pueda acceder al formulario de login.
            )

            .logout(logout -> logout // Configura el cierre de sesión manejado por Spring Security.
                .logoutSuccessUrl("/login") // Después de cerrar sesión, redirige al usuario nuevamente a /login.
                .permitAll() // Permite que cualquier usuario pueda acceder al proceso de logout.
            );

        return http.build(); // Construye y retorna la cadena final de filtros que Spring Security usará en memoria.
    }
}