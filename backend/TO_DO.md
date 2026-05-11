# V1
- Actualizar CIL a la hora de hacer un update de players/club.
- Carga de datos basicos desde Manual Data de indexal data.
- YAML de operaciones de postman.
- Esquemas de flujos y diseño de arquitectura y clases.
- Actualización del git para mostrar que la fase 2 ha sido terminada.
- Mejorar filtros de busqueda para que busque por nombre + apellidos + nombre completo.
- Realizar peticiones de postman con un logado necesario para limpiar el corsConfig de endpoint que deberian estar protegidos.
- Generar documentación y hacer un TFG sobre esto.

# V2 PLANNING
- Diseño de logica de usuario.
- Pagina de management general, que permita manejar ligas estudiadas.
- Modulo de testing escalable y estable.
- filtro de busqueda dejugadores por percentiles.



# PERCENTILES
- revisar inserción de player_percentiles en /updatePlayerData
- arreglar bug de muerte al cargar estadisticas y percentiles en el mismo workflow
- Comprobar que la logica de percentiles para el front y percentiles para el modelo es correcta y está separada.
- percentiles por posición, ajustable desde el front para mostrarlo de forma correcta
- añadir en el front capacidad para mostrar percentiles de una liga concreta, actualmente bien almacenada en bbdd.
- en comparativa de percentiles, añadir boton de percentiles por posicion y eliminar filtro por liga.

# MODELO
- al regenerar el analisis contextual segun la aplicacion del modelo base hay que revisar dos cosas: 1o, que actualice unicamente los analisis contextuales de partidos que estén por jugar. 2o,


  3. Más regularización para O/U y BTTS — después de fijar el leakage, si el gap persiste:
  - reg_alpha: 0.2 → 0.5, reg_lambda: 0.4 → 0.8
  - min_child_samples: 25 → 40
  - num_leaves: 31 → 25

- calibrar empate-> el empate suele tener un porcentaje bastante bajo, todos los fallos derivan en un empate como resultado.
- mas estadisticas al cargar la pagina: corners, posibles goleadores, posibles jugadores destacados.
- con O/U y BTTS, ademas de regularizarlo y mejorarlo, se deben cargar estadísticas para probabilidades de +1gol, 0 goles, Qué equipo marcará, etc.

# MUNDIAL / TEMPORADA 2026
- agregar compatibilidad entre paises y clubes. Gestionar la ingesta de datos del mundial.
- revisar funcionalidad para la temporada 2026.

# PAGINA DE CLUBES
- Página de equipos con estadísticas clave de cada uno de ellos (diseño pendiente) e incluir referencia de foto en la bbdd.
- Agregar funcionalidad de standings.


# INDEXED PLAYER
- rating y % de pases acertados se calcula como la media de todas las competiciones (incompatible si se tiene en cuenta que en champions o supercopa se juegan menos partidos que en una liga regular).


# MEJORAS VARIAS
- Separar live fixtures en dos subcuadros: studied leagues y el resto
- revisar modo noche para que sea mas legible y correcto con algunos cuadros embebidos mostrandose en blanco cuando deberían ser negros, y textos ilegibles al cambiarse a blanco.
- unificar estilos de la pagina principal
- Capita de mantenibilidad: quitar endpoints del codigo, revisar posibles claves que se encuentren en el codigo y demás.
- Añadir otra API como fuente de datos para añadir variabilidad y robustidad.
- Tabla de guardado de operaciones realizadas (HIST) y sistema de logs.
- Unificar Metodo de CheckGoodCall.