# V1
- GPT: Actualizar CIL a la hora de hacer un update de players/club.
- SONNET: Carga de datos basicos desde Manual Data de indexal data.
- GPT: YAML de operaciones de postman.
- HAIKU: Esquemas de flujos y diseño de arquitectura y clases.
- --: Actualización del git para mostrar que la fase 2 ha sido terminada.
- SONNET: Realizar peticiones de postman con un logado necesario para limpiar el corsConfig de endpoint que deberian estar protegidos.
- HAIKU: Generar documentación y hacer un TFG sobre esto.

# V2 PLANNING
- HAIKU: Diseño de logica de usuario.
- SONNET: Pagina de management general, que permita manejar ligas estudiadas.
- SONNET: Modulo de testing escalable y estable.
- SONNET: filtro de busqueda de jugadores por percentiles.



# PERCENTILES
- SONNET: revisar inserción de player_percentiles en /updatePlayerData
- SONNET/HAIKU: cambiar uso en predict-api y GetContextualMatchPrediction de player_season_percentiles por player_percentiles para poder eliminar la tabla, no tiene sentido hacerlo asi.
- SONNET: percentiles por posición, ajustable desde el front para mostrarlo de forma correcta
- HAIKU: añadir en el front capacidad para mostrar percentiles de una liga concreta en /comparePlayers, actualmente bien almacenada en bbdd.
- HAIKU: en comparativa de percentiles, añadir boton de percentiles por posicion y eliminar la comparativa por liga.

# MODELO
- HAIKU: Aparentemente se regenera el analisis a partir del modelo basico sin tener en cuenta lo contextual, hay que preguntarle a Claude para que lo revise.
- SONNET: Features con ponderacion ajustable


Conclusión: el problema de draw recall no es de features ni de class_weight — es estructural. La solución correcta no es reentrenar, es ajustar el threshold de decisión.

  Lo que v3 sí mejoró

  1. league_season_draw_rate entró en top 12 de importancia para 1X2 — la nueva feature funciona
  2. total_season_goal_rate es #2 para BTTS — la feature multiplicativa de goles funciona
  3. OUC overfitting gap: el mejor valor de las tres versiones (0.072)
  4. Draw recall duplicado: 1.9% → 3.7% (señal de que las features de draw sí discriminan algo más)

  Por qué BTTS empeoró el gap

  Las nuevas features de cruce (total_season_goal_rate, goal_threat_product) tienen más poder expresivo pero también más riesgo de overfitting. El modelo aprende patrones más específicos de cada liga/equipo en train que no generalizan igual. Test AUC subió igual (+0.0002), pero
  el train AUC creció más.

  El elefante en la habitación: league_id

  En los tres modelos y todos los targets, league_id es #1 por un margen enorme. Todas las league_*_rate (draw_rate, home_win_rate, avg_goals) deberían estar capturando lo mismo que el ID crudo — pero el modelo los ignora en favor de memorizar el ID directamente. Esto infla el
  overfitting.

  ---
  Qué hacer ahora

  Para empates (acción inmediata): ejecutar threshold_optimization_v2.py apuntando al modelo v3 con --objective draw_f1. Un threshold en torno a 0.22-0.25 recuperará recall sin sacrificar la calibración honesta que v3 tiene.

  Para OUC/BTTS a medio plazo: hay dos opciones con trade-offs distintos:

● User answered Claude's questions:
  ⎿  · ¿Por qué línea quieres continuar para OUC y BTTS? → Threshold optimization de empates

● El modelo lgbm_1x2.pkl que acaba de guardar v3 es el que usa threshold_optimization_v2.py por defecto. Puedes ejecutarlo directamente con dos objetivos distintos para comparar:

  # Objetivo balanceado (accuracy + macro F1 — más conservador con el empate)
  python .\threshold_optimization_v2.py --test-seasons 2025 --objective balanced

  # Objetivo draw F1 puro (maximiza precisión×recall del empate)
  python .\threshold_optimization_v2.py --test-seasons 2025 --objective draw_f1

  Qué buscar en el output:

  La tabla mostrará cómo evoluciona el recall de empates a medida que bajas el threshold. Con calibración honesta (~23.7% media en v3), el punto óptimo estará probablemente en el rango 0.22–0.28:

  - Threshold bajo (ej. 0.18): recall alto pero precision baja → predice demasiados empates
  - Threshold óptimo: el sweet spot donde F1 de empate se maximiza sin destruir accuracy global
  - Threshold 0.5 (default argmax): recall 3.7% que ya vemos

  El objetivo draw_f1 te da el threshold más agresivo con empates. El balanced te da el threshold que mejor coexiste con accuracy general. Ejecuta los dos y compara el threshold recomendado — si coinciden, ese es el número definitivo; si difieren, decides según si priorizas
  accuracy global o identificar empates.

- SONNET/OPUS: calibrar empate-> el empate suele tener un porcentaje bastante bajo, todos los fallos derivan en un empate como resultado.
- SONNET: mas estadisticas al cargar la pagina: corners, posibles goleadores, posibles jugadores destacados.
- SONNET: con O/U y BTTS, ademas de regularizarlo y mejorarlo, se deben cargar estadísticas para probabilidades de +1gol, 0 goles, Qué equipo marcará, etc.
- SONNET/HAIKU: predict-api y GetContextualMatchPrediction utilizan percentiles_season y no percentiles_player, hay que corregir esto.

# MUNDIAL / TEMPORADA 2026
- SONNET: agregar compatibilidad entre paises y clubes. Gestionar la ingesta de datos del mundial.
- HAIKU: revisar funcionalidad para la temporada 2026.

# PAGINA DE CLUBES
- SONNET: Página de equipos con estadísticas clave de cada uno de ellos (diseño pendiente) e incluir referencia de foto en la bbdd.
- SONNET: Agregar funcionalidad de standings.


# INDEXED PLAYER
- GPT: rating y % de pases acertados se calcula como la media de todas las competiciones (incompatible si se tiene en cuenta que en champions o supercopa se juegan menos partidos que en una liga regular).


# MEJORAS VARIAS
- GPT/HAIKU: Separar live fixtures en dos subcuadros: studied leagues y el resto
- HAIKU: unificar estilos de la pagina principal
- SONNET: Capita de mantenibilidad: quitar endpoints del codigo, revisar posibles claves que se encuentren en el codigo y demás.
- --: Añadir otra API como fuente de datos para añadir variabilidad y robustidad.
- SONNET: Tabla de guardado de operaciones realizadas (HIST) y sistema de logs.
- SONNET: Unificar Metodo de CheckGoodCall.