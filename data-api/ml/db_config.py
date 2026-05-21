"""
Configuración centralizada de la base de datos.
Lee las credenciales de variables de entorno con fallback a valores de desarrollo local.
Para distribución, crea un archivo .env en este directorio (copia .env.example) o define
las variables de entorno en el sistema.
"""

import os

try:
    from dotenv import load_dotenv
    import locale
    try:
        load_dotenv(encoding='utf-8')
    except UnicodeDecodeError:
        load_dotenv(encoding=locale.getpreferredencoding(False))
except ImportError:
    pass

DB_CONFIG = {
    "host":     os.getenv("DB_HOST", "localhost"),
    "port":     int(os.getenv("DB_PORT", "5432")),
    "dbname":   os.getenv("DB_NAME", "playerdata"),
    "user":     os.getenv("DB_USER", "postgres"),
    "password": os.getenv("DB_PASSWORD", "admin"),
}
