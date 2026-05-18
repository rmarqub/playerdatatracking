@echo off
REM =============================================================
REM  Script para empaquetar la aplicacion para el beta tester.
REM  Ejecutar desde la raiz del proyecto en la maquina del dev.
REM  Requiere: Maven, Node/npm, Python, PostgreSQL activo.
REM =============================================================
setlocal EnableDelayedExpansion

echo ============================================================
echo  Empaquetando PlayerDataTracking para distribucion
echo ============================================================
echo.

set DIST_DIR=dist-beta
set FRONTEND_DIR=frontend\playerdatatrackingfront\playerdatatrackerfront
set BACKEND_DIR=backend
set DATAAPI_DIR=data-api\ml

REM --- Limpiar directorio de salida ---
if exist %DIST_DIR% (
    echo Limpiando directorio anterior...
    rmdir /s /q %DIST_DIR%
)
mkdir %DIST_DIR%
mkdir %DIST_DIR%\data-api\ml\models
mkdir %DIST_DIR%\sql

REM --- 1. Compilar frontend Angular ---
echo [1/4] Compilando frontend Angular...
cd %FRONTEND_DIR%
call npm install --silent
call npm run build -- --configuration production
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Fallo la compilacion del frontend.
    pause
    exit /b 1
)
cd ..\..\..

REM --- 2. Copiar frontend compilado al backend ---
echo [2/4] Copiando frontend al backend...
if exist %BACKEND_DIR%\src\main\resources\static (
    rmdir /s /q %BACKEND_DIR%\src\main\resources\static
)
mkdir %BACKEND_DIR%\src\main\resources\static
xcopy /s /q "%FRONTEND_DIR%\dist\playerdatatrackerfront\*" "%BACKEND_DIR%\src\main\resources\static\"

REM --- 3. Compilar backend Spring Boot (JAR con frontend incluido) ---
echo [3/4] Compilando backend Spring Boot...
cd %BACKEND_DIR%
call mvnw clean package -Dmaven.test.skip=true -q
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Fallo la compilacion del backend.
    pause
    exit /b 1
)
cd ..

REM --- 4. Copiar archivos al directorio de distribucion ---
echo [4/4] Copiando archivos...

REM JAR del backend
copy "%BACKEND_DIR%\target\playerdatatracking-0.1.0.jar" "%DIST_DIR%\app.jar" >nul

REM Plantilla de configuracion
copy "%BACKEND_DIR%\src\main\resources\application.properties.template" "%DIST_DIR%\application.properties.template" >nul

REM Scripts Python
copy "%DATAAPI_DIR%\predict_api.py" "%DIST_DIR%\data-api\ml\" >nul
copy "%DATAAPI_DIR%\feature_engineering.py" "%DIST_DIR%\data-api\ml\" >nul
copy "%DATAAPI_DIR%\compute_season_percentiles.py" "%DIST_DIR%\data-api\ml\" >nul
copy "%DATAAPI_DIR%\compute_player_percentiles.py" "%DIST_DIR%\data-api\ml\" >nul
copy "%DATAAPI_DIR%\train_model.py" "%DIST_DIR%\data-api\ml\" >nul
copy "%DATAAPI_DIR%\db_config.py" "%DIST_DIR%\data-api\ml\" >nul
copy "%DATAAPI_DIR%\requirements.txt" "%DIST_DIR%\data-api\ml\" >nul
copy "%DATAAPI_DIR%\.env.example" "%DIST_DIR%\data-api\ml\" >nul

REM Modelos entrenados
xcopy /s /q "%DATAAPI_DIR%\models\*" "%DIST_DIR%\data-api\ml\models\" >nul 2>&1

REM Scripts de arranque
copy "start.bat" "%DIST_DIR%\" >nul
copy "stop.bat" "%DIST_DIR%\" >nul
copy "import-db.bat" "%DIST_DIR%\" >nul
copy "BETA_SETUP.md" "%DIST_DIR%\" >nul

REM --- Exportar base de datos ---
echo Exportando base de datos...
set PGPASSWORD=admin
pg_dump -U postgres -d playerdata -F p -f "%DIST_DIR%\sql\playerdata_backup.sql"
if %ERRORLEVEL% neq 0 (
    echo [AVISO] No se pudo exportar la BD automaticamente.
    echo         Ejecuta export-db.bat manualmente y copia el .sql a %DIST_DIR%\sql\
)

echo.
echo ============================================================
echo  [OK] Paquete generado en: %DIST_DIR%\
echo.
echo  Contenido del paquete:
echo    app.jar                    - Backend + Frontend
echo    start.bat                  - Iniciar la aplicacion
echo    stop.bat                   - Detener la aplicacion
echo    import-db.bat              - Importar la base de datos
echo    application.properties.template
echo    data-api/ml/               - Scripts Python + modelos
echo    sql/playerdata_backup.sql  - Base de datos
echo    BETA_SETUP.md              - Guia de instalacion
echo.
echo  Comprime la carpeta %DIST_DIR% y enviasela al beta tester.
echo ============================================================
pause
