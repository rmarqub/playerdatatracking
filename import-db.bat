@echo off
REM Importa la base de datos desde un archivo SQL.
REM Uso: import-db.bat [ruta_al_archivo.sql]
REM Si no se pasa argumento, busca el primer .sql en la carpeta sql\

setlocal

if "%~1"=="" (
    for %%f in (sql\*.sql) do (
        set SQL_FILE=%%f
        goto FOUND
    )
    echo [ERROR] No se encontro ningun archivo .sql en la carpeta sql\
    echo         Uso: import-db.bat ruta\al\archivo.sql
    pause
    exit /b 1
) else (
    set SQL_FILE=%~1
)

:FOUND
if not exist "%SQL_FILE%" (
    echo [ERROR] No se encuentra el archivo: %SQL_FILE%
    pause
    exit /b 1
)

echo ============================================================
echo  Importando base de datos desde: %SQL_FILE%
echo ============================================================
echo.
echo ADVERTENCIA: Esto borrara y recreara la base de datos 'playerdata'.
echo Presiona Ctrl+C para cancelar o...
pause

set PGPASSWORD=admin

REM Crear la base de datos si no existe
psql -U postgres -c "SELECT 1 FROM pg_database WHERE datname='playerdata';" | findstr /c:"1 row" >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo Creando base de datos 'playerdata'...
    psql -U postgres -c "CREATE DATABASE playerdata ENCODING='UTF8';"
)

REM Importar el dump
echo Importando datos (puede tardar varios minutos)...
psql -U postgres -d playerdata -f "%SQL_FILE%"

if %ERRORLEVEL% equ 0 (
    echo.
    echo [OK] Base de datos importada correctamente.
) else (
    echo [ERROR] Ocurrio un error durante la importacion.
    echo         Revisa que PostgreSQL este ejecutandose y que tengas permisos.
)

pause
