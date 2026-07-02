package com.sena.springpoo;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import jakarta.annotation.PreDestroy;

/**
 * Clase principal que inicializa y ejecuta la aplicación Spring Boot.
 * <p>
 * Contiene el método {@code main} estándar de Java y se encarga de 
 * arrancar el contexto de Spring, inicializar las configuraciones, 
 * y exponer el ciclo de vida básico de la aplicación.
 * </p>
 */
@SpringBootApplication
public class SpringpooApplication {

	private static final Logger log = LogManager.getLogger(SpringpooApplication.class);

	/**
	 * Método de entrada principal de la aplicación Java.
	 * 
	 * @param args Argumentos de la línea de comandos pasados al ejecutar el programa.
	 */
	public static void main(String[] args) {
		SpringApplication.run(SpringpooApplication.class, args);
		log.info("✅ Aplicación iniciada correctamente");
	}

	/**
	 * Método que se ejecuta automáticamente justo antes de que la aplicación 
	 * se cierre o destruya el contexto de Spring.
	 * <p>
	 * Útil para registrar el apagado seguro de la aplicación o liberar recursos.
	 * </p>
	 */
	@PreDestroy
	public void onShutdown() {
		log.info("🛑 Aplicación cerrada correctamente");
	}

}
