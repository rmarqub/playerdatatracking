@echo off
REM Exporta la base de datos playerdata a un archivo SQL.
REM Requiere que pg_dump este en el PATH (viene con la instalacion de PostgreSQL).

setlocal
set FECHA=%DATE:~6,4%-%DATE:~3,2%-%DATE:~0,2%
set OUTPUT=sql\playerdata_backup_%FECHA%.sql

if not exist sql mkdir sql

echo Exportando base de datos 'playerdata' a %OUTPUT%...
echo (Se pedira la contrasena de PostgreSQL si no esta en PGPASSWORD)

set PGPASSWORD=admin
pg_dump -U postgres -d playerdata -F p -f %OUTPUT%

if %ERRORLEVEL% equ 0 (
    echo.
    echo [OK] Base de datos exportada en: %OUTPUT%
    echo      Incluye este archivo en el paquete para el beta tester.
) else (
    echo [ERROR] Fallo la exportacion. Asegurate de que pg_dump esta en el PATH.
    echo         Suele estar en: C:\Program Files\PostgreSQL\<version>\bin\
)

pause
