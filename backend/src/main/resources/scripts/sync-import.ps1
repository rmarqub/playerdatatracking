$Container = "pdt-dev"
$DbName    = "playerdata"
$DumpFile  = "C:\syncdb\playerdata.dump"

if (!(Test-Path $DumpFile)) { throw "No existe el dump: $DumpFile" }

Write-Host "1) Copiando dump al contenedor..."
docker cp $DumpFile "${Container}:/tmp/${DbName}.dump"
if ($LASTEXITCODE -ne 0) { throw "Fallo docker cp" }

Write-Host "2) Re-creando base de datos..."
docker exec $Container psql -U postgres -d postgres -c "DROP DATABASE IF EXISTS $DbName;"
docker exec $Container psql -U postgres -d postgres -c "CREATE DATABASE $DbName;"

Write-Host "3) Restaurando dump..."
docker exec $Container pg_restore -U postgres -d $DbName "/tmp/${DbName}.dump"
if ($LASTEXITCODE -ne 0) { throw "Fallo pg_restore" }

Write-Host "OK: BD restaurada."