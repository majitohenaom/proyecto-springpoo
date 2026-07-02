@echo off
REM ──────────────────────────────────────────────────────────────────
REM  SCRIPT DE INICIALIZACIÓN - PLATAFORMA SENA GDF (SPRINGPOO)
REM ──────────────────────────────────────────────────────────────────
REM Este script configura temporalmente las variables de entorno necesarias
REM para compilar y ejecutar la aplicación usando el JDK 17 y Maven Wrapper.
REM Evita tener que configurar variables de entorno permanentes en el sistema.

REM 1. Configuración de la ruta absoluta del JDK 17 (Microsoft OpenJDK recomendado)
set JAVA_HOME=C:\Users\Aprendiz\.jdks\ms-17.0.19

REM 2. Agregar la carpeta 'bin' del JDK al principio del PATH de Windows para usar java y javac correctos
set PATH=%JAVA_HOME%\bin;%PATH%

echo ==========================================
echo        SENA GDF - SPRINGPOO RUNNER
echo ==========================================
echo Configurando JAVA_HOME en: %JAVA_HOME%
echo.
echo Iniciando aplicacion Spring Boot en puerto 8080...
echo (Asegurate de tener MySQL activo en XAMPP con la base de datos creada)
echo.

REM 3. Ejecución del comando de inicio de Spring Boot mediante Maven Wrapper (mvnw)
call mvnw spring-boot:run

REM 4. Mantiene la consola abierta al finalizar o ante fallas para poder inspeccionar los logs de error
pause
