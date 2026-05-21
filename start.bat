@echo off

REM --- Auto-relanzar dentro de cmd /k para que la ventana nunca se cierre sola ---
if not defined _WRAPPED (
    set _WRAPPED=1
    cmd /k "%~f0"
    exit /b
)

setlocal EnableDelayedExpansion
title PlayerDataTracking - Iniciando...

echo ============================================================
echo  PlayerDataTracking - Iniciando la aplicacion
echo ============================================================
echo.

REM --- Verificar Java 17+ ---
java -version >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Java no esta instalado o no esta en el PATH.
    echo         Descarga Java 17 desde: https://adoptium.net/
    pause & exit /b 1
)
for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do set JAVA_VER=%%v
set JAVA_VER=!JAVA_VER:"=!
for /f "tokens=1 delims=." %%m in ("!JAVA_VER!") do set JAVA_MAJOR=%%m
if !JAVA_MAJOR! LSS 17 (
    echo [ERROR] Se requiere Java 17 o superior. Version actual: !JAVA_VER!
    echo         Descarga Java 17 desde: https://adoptium.net/
    pause & exit /b 1
)
echo [OK] Java !JAVA_VER! detectado.

REM --- Verificar Python ---
python --version >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Python no esta instalado o no esta en el PATH.
    echo         Descarga Python desde: https://www.python.org/downloads/
    pause & exit /b 1
)
echo [OK] Python detectado.

REM --- Verificar que el JAR existe ---
if not exist "%~dp0app.jar" (
    echo [ERROR] No se encuentra app.jar en: %~dp0
    pause & exit /b 1
)

REM --- Verificar application.properties ---
if not exist "%~dp0application.properties" (
    echo Creando application.properties desde la plantilla...
    copy "%~dp0application.properties.template" "%~dp0application.properties" >nul
    echo.
    echo [IMPORTANTE] Edita application.properties con tu contrasena de PostgreSQL
    echo              y vuelve a ejecutar este script.
    notepad "%~dp0application.properties"
    pause & exit /b 1
)

REM --- Instalar dependencias Python si no estan ---
echo Verificando dependencias Python...
python -c "import fastapi, uvicorn, psycopg2, lightgbm, dotenv" >nul 2>&1
if not errorlevel 1 goto PY_OK
echo Instalando dependencias Python (puede tardar unos minutos)...
pip install -r "%~dp0data-api\ml\requirements.txt"
if not errorlevel 1 goto PY_OK
echo [AVISO] Dependencias Python incompletas. Las APIs de prediccion y valoracion no estaran disponibles.
set PYTHON_OK=0
goto PY_DONE
:PY_OK
set PYTHON_OK=1
echo [OK] Dependencias Python listas.
:PY_DONE

REM --- Iniciar la API Python de prediccion ---
if !PYTHON_OK! equ 0 goto SKIP_PREDICT
echo Iniciando API de prediccion (Python)...
pushd "%~dp0data-api\ml"
start "Predict API" cmd /k python -m uvicorn predict_api:app --host 127.0.0.1 --port 8001
popd
goto AFTER_PREDICT
:SKIP_PREDICT
echo [AVISO] Saltando inicio de API de prediccion.
:AFTER_PREDICT

REM --- Iniciar la API Python de valoracion ---
if !PYTHON_OK! equ 0 goto SKIP_VALUATION
echo Iniciando API de valoracion (Python)...
pushd "%~dp0data-api\ml"
start "Valuation API" cmd /k python -m uvicorn valuation_api:app --host 127.0.0.1 --port 8002
popd
goto AFTER_VALUATION
:SKIP_VALUATION
echo [AVISO] Saltando inicio de API de valoracion.
:AFTER_VALUATION

REM --- Iniciar el backend Spring Boot ---
echo Iniciando backend (Spring Boot)...
start "Backend PlayerTracker" cmd /k java -jar "%~dp0app.jar" --spring.config.location="%~dp0application.properties"

REM --- Esperar hasta 90 segundos a que el backend arranque ---
echo Esperando a que el backend arranque (puede tardar hasta 90 segundos)...
set INTENTOS=0
:WAIT_LOOP
    timeout /t 4 /nobreak >nul
    curl -s --connect-timeout 2 --max-time 3 "http://localhost:8080" >nul 2>&1
    if %ERRORLEVEL% equ 0 goto BACKEND_LISTO
    set /a INTENTOS+=1
    if !INTENTOS! LSS 22 (
        set /a PROGRESO=!INTENTOS!*4
        echo   Esperando... !PROGRESO! segundos
        goto WAIT_LOOP
    )
    echo [AVISO] El backend tarda mas de lo esperado. Intentando abrir el navegador igualmente...
    goto ABRIR_NAVEGADOR

:BACKEND_LISTO
echo [OK] Backend iniciado correctamente.

:ABRIR_NAVEGADOR
timeout /t 2 /nobreak >nul
echo Abriendo la aplicacion en el navegador...
start "" "http://localhost:8080"

echo.
echo ============================================================
echo  Aplicacion iniciada.
echo  URL: http://localhost:8080
echo.
echo  Para detenerla: ejecuta stop.bat o cierra las
echo  ventanas de terminal minimizadas en la barra de tareas.
echo ============================================================
pause
