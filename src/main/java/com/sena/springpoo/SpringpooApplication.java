package com.sena.springpoo; // Define el paquete raíz del proyecto; desde aquí Spring Boot escanea subpaquetes como controller, repository, models, config y filter.

import org.apache.logging.log4j.LogManager; // Importa LogManager de Log4j2; sirve para crear el logger de esta clase.
import org.apache.logging.log4j.Logger; // Importa Logger; permite escribir mensajes en los archivos de logs configurados.
import org.springframework.boot.SpringApplication; // Importa SpringApplication; clase que arranca la aplicación Spring Boot.
import org.springframework.boot.autoconfigure.SpringBootApplication; // Importa @SpringBootApplication; anotación principal de arranque y configuración automática.
import jakarta.annotation.PreDestroy; // Importa @PreDestroy; permite ejecutar un método justo antes de destruir el bean o cerrar la aplicación.

/**
 * Clase principal que inicializa y ejecuta la aplicación Spring Boot.
 * <p>
 * Esta clase es el punto de entrada del proyecto Springpoo. Desde aquí se arranca
 * el servidor embebido, se crea el contexto de Spring, se cargan los controladores,
 * repositorios, filtros, configuración de seguridad, plantillas y propiedades.
 * </p>
 */
@SpringBootApplication // Activa configuración automática, escaneo de componentes y configuración Spring; equivale a combinar @Configuration, @EnableAutoConfiguration y @ComponentScan.
public class SpringpooApplication { // Declara la clase principal pública; Java necesita esta clase para iniciar la aplicación.

	private static final Logger log = LogManager.getLogger(SpringpooApplication.class); // Crea un logger estático para registrar arranque y apagado de la aplicación.

	/**
	 * Método principal de Java.
	 * <p>
	 * Modificadores: {@code public static}. Es público porque la JVM debe poder llamarlo,
	 * y es estático porque se ejecuta sin crear primero un objeto de la clase.
	 * </p>
	 * <p>
	 * Tipo de retorno: {@code void}; no retorna ningún valor.
	 * </p>
	 *
	 * @param args argumentos enviados por consola al ejecutar la aplicación.
	 */
	public static void main(String[] args) { // Método de entrada; recibe un arreglo de String y no retorna nada.
		SpringApplication.run(SpringpooApplication.class, args); // Arranca Spring Boot, crea el ApplicationContext, inicia Tomcat y carga beans del proyecto.
		log.info("Aplicación iniciada correctamente"); // Registra en logs que la aplicación arrancó bien.
	}

	/**
	 * Método ejecutado antes del cierre de la aplicación.
	 * <p>
	 * Tipo de retorno: {@code void}; no retorna ningún valor.
	 * Spring lo llama automáticamente por la anotación {@link PreDestroy}.
	 * </p>
	 */
	@PreDestroy // Indica que Spring debe ejecutar este método antes de destruir el bean al apagar la aplicación.
	public void onShutdown() { // Método público sin parámetros; se ejecuta durante el cierre del contexto de Spring.
		log.info("Aplicación cerrada correctamente"); // Registra en logs que la aplicación se cerró de forma controlada.
	}

}