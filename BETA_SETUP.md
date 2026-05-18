# PlayerDataTracking — Guía de instalación (Beta)

Guía paso a paso para instalar y ejecutar PlayerDataTracking en tu equipo.

---

## Contenido del paquete

```
playertracker/
├── app.jar                        ← Aplicación principal (backend + frontend)
├── start.bat                      ← Inicia todos los servicios
├── stop.bat                       ← Detiene todos los servicios
├── import-db.bat                  ← Importa la base de datos
├── application.properties.template ← Plantilla de configuración
├── data-api/
│   └── ml/
│       ├── predict_api.py         ← API de predicciones
│       ├── db_config.py
│       ├── models/                ← Modelos entrenados (archivos .pkl)
│       └── requirements.txt
├── sql/
│   └── playerdata_backup.sql      ← Copia de la base de datos
└── BETA_SETUP.md                  ← Este archivo
```

---

## Requisitos previos

### 1. Java 17

La aplicación principal requiere Java 17 o superior.

1. Descarga Java 17 desde: https://adoptium.net/
2. Durante la instalación, marca la opción **"Add to PATH"**
3. Verifica la instalación abriendo una terminal (`cmd`) y ejecutando:
   ```
   java -version
   ```
   Debe mostrar algo como `openjdk version "17.x.x"`.

---

### 2. Python 3.10 o superior

La API de predicciones requiere Python.

1. Descarga Python desde: https://www.python.org/downloads/
2. Durante la instalación, marca **"Add Python to PATH"** (importante)
3. Verifica la instalación:
   ```
   python --version
   ```

---

### 3. PostgreSQL 14 o superior

La base de datos de la aplicación.

1. Descarga PostgreSQL desde: https://www.postgresql.org/download/windows/
2. Durante la instalación:
   - Anota la contraseña que le pongas al usuario `postgres`
   - Deja el puerto en `5432` (valor por defecto)
   - **Instala también pgAdmin** si quieres una interfaz gráfica
3. Al finalizar la instalación, asegúrate de que el servicio PostgreSQL esté iniciado.
   - Puedes verlo en `Servicios de Windows` (busca "postgresql")

> **Nota:** Añade la carpeta `bin` de PostgreSQL al PATH para poder usar `psql` y `pg_dump` desde la terminal.
> Suele estar en: `C:\Program Files\PostgreSQL\17\bin\`
> 
> Cómo añadir al PATH:
> 1. Busca "Variables de entorno" en el menú de inicio
> 2. En "Variables del sistema", selecciona `Path` y haz clic en "Editar"
> 3. Añade la ruta de la carpeta `bin` de PostgreSQL

---

## Instalación paso a paso

### Paso 1 — Importar la base de datos

1. Abre una terminal (`cmd`) y navega a la carpeta del paquete:
   ```
   cd C:\ruta\a\playertracker
   ```
2. Ejecuta el script de importación:
   ```
   import-db.bat
   ```
3. Cuando te pida contraseña, introduce la contraseña del usuario `postgres` que configuraste durante la instalación de PostgreSQL.

El script creará automáticamente la base de datos `playerdata` e importará todos los datos.

> **Si falla:** Abre pgAdmin, crea manualmente una base de datos llamada `playerdata`, y luego importa el archivo `sql/playerdata_backup.sql` desde pgAdmin (clic derecho en la BD → Restore).

---

### Paso 2 — Configurar la aplicación

1. Copia el archivo `application.properties.template` y renómbralo a `application.properties`:
   ```
   copy application.properties.template application.properties
   ```
2. Abre `application.properties` con el Bloc de notas y ajusta estos valores:
   ```properties
   spring.datasource.password=TU_PASSWORD_AQUI
   ```
   Reemplaza `TU_PASSWORD_AQUI` con la contraseña de PostgreSQL que configuraste.

3. Guarda el archivo.

---

### Paso 3 — Iniciar la aplicación

Haz doble clic en **`start.bat`** o ejecútalo desde la terminal.

El script:
1. Verifica que Java y Python estén instalados
2. Verifica la conexión con PostgreSQL
3. Instala automáticamente las dependencias Python si no están
4. Inicia el backend (Spring Boot)
5. Inicia la API de predicciones (Python)
6. Abre automáticamente el navegador en `http://localhost:8080`

> La primera vez puede tardar 1-2 minutos en cargar mientras se inician todos los servicios.

---

### Paso 4 — Acceder a la aplicación

Una vez iniciada, accede desde el navegador:

```
http://localhost:8080
```

**Credenciales de acceso:**
- Usuario: (el que te haya facilitado el desarrollador)
- Contraseña: (la que te haya facilitado el desarrollador)

---

## Detener la aplicación

Para detener todos los servicios:
- Haz doble clic en **`stop.bat`**, o
- Cierra las ventanas de terminal que se abrieron al iniciar

---

## Solución de problemas frecuentes

### "Java no está instalado o no está en el PATH"
- Reinstala Java 17 marcando la opción de añadir al PATH
- Reinicia la terminal después de la instalación

### "No se puede conectar a PostgreSQL"
- Verifica que el servicio PostgreSQL esté iniciado (Servicios de Windows)
- Comprueba que la contraseña en `application.properties` es correcta
- Asegúrate de que la base de datos `playerdata` existe (ejecuta `import-db.bat`)

### La página web no carga
- Espera 1-2 minutos desde que ejecutaste `start.bat`
- Verifica que no haya otro programa usando el puerto 8080
- Revisa la ventana de terminal del backend por si hay errores en rojo

### "pip install falló" / errores de dependencias Python
- Actualiza pip: `python -m pip install --upgrade pip`
- Instala manualmente: `cd data-api\ml && pip install -r requirements.txt`
- Si hay problemas con `lightgbm`, descarga los Visual C++ Redistributables:
  https://learn.microsoft.com/en-us/cpp/windows/latest-supported-vc-redist

### Al recargar una página aparece un error 404
- Vuelve a `http://localhost:8080` y navega desde ahí
- Esto es un comportamiento conocido del SPA; la navegación interna funciona correctamente

### Los modelos de predicción no cargan
- Verifica que la carpeta `data-api/ml/models/` contiene archivos `.pkl`
- Revisa la ventana de terminal de la "Predict API" por mensajes de error

---

## Preguntas o problemas

Contacta al desarrollador indicando:
1. El mensaje de error exacto
2. En qué paso ocurrió el problema
3. Captura de pantalla si es posible

---

## Información técnica

| Componente       | Tecnología        | Puerto  |
|-----------------|-------------------|---------|
| Backend + Frontend | Spring Boot 3 / Angular 16 | 8080 |
| API de predicciones | Python / FastAPI | 8001 |
| Base de datos | PostgreSQL | 5432 |

La sesión de usuario se guarda en la base de datos PostgreSQL (no requiere Redis).
