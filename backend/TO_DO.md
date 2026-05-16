# V1
- GPT: Actualizar CIL a la hora de hacer un update de players/club.
- SONNET: Carga de datos basicos desde Manual Data de indexal data.
- GPT: YAML de operaciones de postman.
- HAIKU: Esquemas de flujos y diseño de arquitectura y clases.
- --: Actualización del git para mostrar que la fase 2 ha sido terminada.
- SONNET: Realizar peticiones de postman con un logado necesario para limpiar el corsConfig de endpoint que deberian estar protegidos.
- HAIKU: Generar documentación y hacer un TFG sobre esto.

# V2 PLANNING
- SONNET: Modulo de testing escalable y estable.
- SONNET: filtro de busqueda de jugadores por percentiles.



# PERCENTILES
- SONNET: revisar inserción de player_percentiles en /updatePlayerData
- SONNET/HAIKU: cambiar uso en predict-api y GetContextualMatchPrediction de player_season_percentiles por player_percentiles para poder eliminar la tabla, no tiene sentido hacerlo asi.
- SONNET: percentiles por posición, ajustable desde el front para mostrarlo de forma correcta
- HAIKU: añadir en el front capacidad para mostrar percentiles de una liga concreta en /comparePlayers, actualmente bien almacenada en bbdd.
- HAIKU: en comparativa de percentiles, añadir boton de percentiles por posicion y eliminar la comparativa por liga.

# MODELO
- SONNET/HAIKU: predict-api y GetContextualMatchPrediction utilizan percentiles_season y no percentiles_player, hay que corregir esto.

 Resumen de prioridad de implementación

  ┌─────────────────────────────────────────┬────────────────────────────────────────────────────────────┬────────────────────────────────────────────────┬────────┐
  │                 Mejora                  │                          Esfuerzo                          │                Impacto esperado                │ Riesgo │
  ├─────────────────────────────────────────┼────────────────────────────────────────────────────────────┼────────────────────────────────────────────────┼────────┤
  │ Over 0.5/1.5/3.5                        │ Muy bajo — solo añadir targets a train_model.py            │ Alto — targets ya definidos                    │ Mínimo │
  ├─────────────────────────────────────────┼────────────────────────────────────────────────────────────┼────────────────────────────────────────────────┼────────┤
  │ BTTS/OU con pinning + rolling features  │ Medio — cambios en feature_engineering.py y train_model.py │ Alto en calibración                            │ Bajo   │
  ├─────────────────────────────────────────┼────────────────────────────────────────────────────────────┼────────────────────────────────────────────────┼────────┤
  │ Córners (Poisson regressor)             │ Medio — nuevo modelo + nueva query en predict_api.py       │ Moderado — inherentemente difícil              │ Bajo   │
  ├─────────────────────────────────────────┼────────────────────────────────────────────────────────────┼────────────────────────────────────────────────┼────────┤
  │ Goleadores probable (Poisson analítico) │ Medio — nueva función en predict_api.py, sin modelo        │ Moderado — limitado por ausencia de alineación │ Bajo   │
  └─────────────────────────────────────────┴────────────────────────────────────────────────────────────┴────────────────────────────────────────────────┴────────┘

# MUNDIAL / TEMPORADA 2026
- SONNET: agregar compatibilidad entre paises y clubes. Gestionar la ingesta de datos del mundial.
- HAIKU: revisar funcionalidad para la temporada 2026.

# PAGINA DE CLUBES
- SONNET: Página de equipos con estadísticas clave de cada uno de ellos (diseño pendiente) e incluir referencia de foto en la bbdd.
- SONNET: Agregar funcionalidad de standings.




# MEJORAS VARIAS
- SONNET: Capita de mantenibilidad: quitar endpoints del codigo, revisar posibles claves que se encuentren en el codigo y demás.
- --: Añadir otra API como fuente de datos para añadir variabilidad y robustidad.
- SONNET: Tabla de guardado de operaciones realizadas (HIST) y sistema de logs.
- SONNET: Unificar Metodo de CheckGoodCall.


# INDEXED PLAYER