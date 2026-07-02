package com.sena.springpoo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Clase de configuración principal de Spring Security.
 * <p>
 * Se encarga de establecer las políticas de autenticación, la protección 
 * de rutas (endpoints), desactivar o configurar protección contra CSRF 
 * y proveer componentes criptográficos como el {@link PasswordEncoder}.
 * </p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Define el bean del codificador de contraseñas (PasswordEncoder).
     * <p>
     * Este componente es inyectado en los controladores o servicios 
     * para cifrar las contraseñas antes de guardarlas en la base de datos 
     * y para verificar que la contraseña en texto plano introducida coincida 
     * con el hash almacenado mediante el algoritmo BCrypt.
     * </p>
     *
     * @return Una instancia de {@link BCryptPasswordEncoder}.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Configura el filtro de seguridad (SecurityFilterChain) que procesa las peticiones HTTP.
     * <p>
     * En esta configuración:
     * <ul>
     *   <li>Se deshabilita CSRF para simplificar las llamadas REST y peticiones web sin token adicional.</li>
     *   <li>Se define que la ruta {@code /formulario} requiere estar autenticado (login previo).</li>
     *   <li>El resto de rutas son públicas por defecto ({@code permitAll()}), facilitando el acceso a vistas públicas y errores 404.</li>
     *   <li>Se habilita el login por formulario ({@code formLogin}) en la ruta {@code /login}, redirigiendo a {@code /nuevo/prueba} en caso de éxito.</li>
     *   <li>Se habilita la funcionalidad de cerrar sesión ({@code logout}), redirigiendo a {@code /login} al terminar.</li>
     * </ul>
     * </p>
     *
     * @param http El objeto {@link HttpSecurity} proveído por el contexto para configurar la seguridad web.
     * @return La cadena de filtros de seguridad configurada.
     * @throws Exception Si ocurre un error al construir la configuración de seguridad.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                // Ruta protegida que sí requiere estar autenticado
                .requestMatchers("/formulario").authenticated()
                // Cualquier otra ruta es pública (para permitir 404 en rutas incorrectas)
                .anyRequest().permitAll()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/nuevo/prueba", true)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/login")
                .permitAll()
            );

        return http.build();
    }
}