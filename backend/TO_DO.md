# V1
- arreglar percentiles: logica parcialmente correcta, no todos los jugadores muestran sus percentiles aunque la logica del front es correcta. Es un tema de BBDD/backend
- Actualizar CIL a la hora de hacer un update de players.
- Página de equipos con estadísticas clave de cada uno de ellos (diseño pendiente) e incluir referencia de foto en la bbdd.
- Carga de datos basicos desde Manual Data de indexal data.
- YAML de operaciones de postman.
- Unificar Metodo de CheckGoodCall.
- Actualización del git para mostrar que la fase 2 ha sido terminada.
- Mejorar filtros de busqueda para que busque por nombre + apellidos + nombre completo.
- Modulo de testing escalable y estable.
- Generar documentación y hacer un TFG sobre esto.

#V2 PLANNING
- Diseño de logica de usuario.
- Pagina de management general, que permita manejar ligas estudiadas.


# MEJORAS VARIAS
- unificar estilos de la pagina principal
- revisar modo noche para que sea mas legible y correcto con algunos cuadros embebidos mostrandose en blanco cuando deberían ser negros, y textos ilegibles al cambiarse a blanco.
- Capita de mantenibilidad: quitar endpoints del codigo, revisar posibles claves que se encuentren en el codigo y demás.
- Realizar peticiones de postman con un logado necesario para limpiar el corsConfig de endpoint que deberian estar protegidos.
- Añadir otra API como fuente de datos para añadir variabilidad y robustidad.
- Esquemas de flujos y diseño de arquitectura y clases.
- Tabla de guardado de operaciones realizadas (HIST) y sistema de logs.



# MODELO

	!!!!!!!!!!!AÑADIR COMPUTE_PERCENTILES.PY AL FLUJO DE ACTUALIZACION DE DATOS.
  Plan de acción por orden de impacto



  3. Más regularización para O/U y BTTS — después de fijar el leakage, si el gap persiste:
  - reg_alpha: 0.2 → 0.5, reg_lambda: 0.4 → 0.8
  - min_child_samples: 25 → 40
  - num_leaves: 31 → 25

- calibrar empate-> el empate suele tener un porcentaje bastante bajo, todos los fallos derivan en un empate como resultado.
