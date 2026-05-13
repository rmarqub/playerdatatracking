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
- SONNET: percentiles por posición, ajustable desde el front para mostrarlo de forma correcta
- HAIKU: añadir en el front capacidad para mostrar percentiles de una liga concreta en /comparePlayers, actualmente bien almacenada en bbdd.
- HAIKU: en comparativa de percentiles, añadir boton de percentiles por posicion y eliminar la comparativa por liga.

# MODELO
- HAIKU: Aparentemente se regenera el analisis a partir del modelo basico sin tener en cuenta lo contextual, hay que preguntarle a Claude para que lo revise.


  3. Más regularización para O/U y BTTS — después de fijar el leakage, si el gap persiste:
  - reg_alpha: 0.2 → 0.5, reg_lambda: 0.4 → 0.8
  - min_child_samples: 25 → 40
  - num_leaves: 31 → 25

- SONNET/OPUS: calibrar empate-> el empate suele tener un porcentaje bastante bajo, todos los fallos derivan en un empate como resultado.
- SONNET: mas estadisticas al cargar la pagina: corners, posibles goleadores, posibles jugadores destacados.
- SONNET: con O/U y BTTS, ademas de regularizarlo y mejorarlo, se deben cargar estadísticas para probabilidades de +1gol, 0 goles, Qué equipo marcará, etc.

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