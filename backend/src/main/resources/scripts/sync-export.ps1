# ===== CONFIG =====
$Container = "pdt-dev"
$DbName    = "playerdata"

$LocalDir  = "C:\syncdb"
$DumpFile  = Join-Path $LocalDir "$DbName.dump"

$RemoteUser = "rodri"
$RemoteHost = "192.168.1.36"

# Carpeta remota en Windows (en el PC destino)
$RemoteDirWin = "C:\syncdb"

# Carpeta remota para scp (en Windows suele ir bien con C:/...)
$RemoteDirScp = "C:/syncdb"

# ===== WORK =====
New-Item -ItemType Directory -Force $LocalDir | Out-Null

Write-Host "1) Generando dump en $DumpFile ..."
docker exec $Container pg_dump -U postgres -Fc -d $DbName -f /tmp/playerdata.dump
docker cp ${Container}:/tmp/playerdata.dump $DumpFile
if ($LASTEXITCODE -ne 0) { throw "Fallo haciendo pg_dump" }

$ts = Get-Date -Format "yyyyMMdd_HHmmss"
$RemoteTemp = "$DbName.$ts.dump"

Write-Host "2) Subiendo dump temporal: $RemoteTemp ..."
scp $DumpFile "${RemoteUser}@${RemoteHost}:${RemoteDirScp}/$RemoteTemp"
if ($LASTEXITCODE -ne 0) { throw "Fallo enviando por scp" }

Write-Host "3) Pisando dump final en remoto (move /Y)..."
# Creamos carpeta si no existe y movemos/renombramos a playerdata.dump
ssh "${RemoteUser}@${RemoteHost}" "cmd /c if not exist $RemoteDirWin mkdir $RemoteDirWin && move /Y $RemoteDirWin\$RemoteTemp $RemoteDirWin\$DbName.dump"
if ($LASTEXITCODE -ne 0) { throw "Fallo moviendo/renombrando en remoto" }

Write-Host "4) Verificando remoto (dir)..."
ssh "${RemoteUser}@${RemoteHost}" "cmd /c dir $RemoteDirWin\$DbName.dump"

Write-Host "OK: export + envio completado."