# V1
- Actualizar CIL a la hora de hacer un update de players.
- Página de equipos con estadísticas clave de cada uno de ellos (diseño pendiente) e incluir referencia de foto en la bbdd.
- Dias de descanso de los jugadores (hoy - ultimo dia jugado)
- Carga de datos basicos desde Manual Data de indexal data.
- Actualizar scripts de creacion de tablas.
- YAML de operaciones de postman.
- Unificar Metodo de CheckGoodCall.
- Actualización del git para mostrar que la fase 2 ha sido terminada.
- Mejorar filtros de busqueda para que busque por nombre + apellidos + nombre completo.
- Too Many Request Exception Handler mejorado y añadir tiempo de espera de 180ms en todas las operaciones que manden external calls.
- Modulo de testing escalable y estable.
- Generar documentación y hacer un TFG sobre esto.

#V2 PLANNING
- Calcular ausencia de jugadores clave
- Página de predictibilidad en los partidos que estén por jugar.
- Modelo predictivo: Gradient Boosting (XGBoost/LightGBM), Capa interpretativa con Style clustering y SHAP values
- Diseño de logica de usuario.
- Pagina de management general.


# MEJORAS VARIAS
- Jugar con la variable restUpdate para añadir modularidad a las operaciones de update de los diferentes indexalData como está hecho en update clubs.
- Capita de mantenibilidad: quitar endpoints del codigo, revisar posibles claves que se encuentren en el codigo y demás.
- Realizar peticiones de postman con un logado necesario para limpiar el corsConfig de endpoint que deberian estar protegidos.
- Añadir otra API como fuente de datos para añadir variabilidad y robustidad.
- Esquemas de flujos y diseño de arquitectura y clases.
- Tabla de guardado de operaciones realizadas (HIST) y sistema de logs.



#implementacion

Orden de implementación que recomendaría

  1. Primero el SQL de diagnóstico — confirmar que tienes suficientes partidos con stats completos
  2. Feature engineering en Python (script que lee de PostgreSQL y genera CSV/DataFrame de entrenamiento)
  3. Entrenamiento + validación del modelo LightGBM con time-split
  4. FastAPI endpoint /predict en data-api
  5. Integración Spring Boot — operación GetMatchPrediction + endpoint
  6. Tabla fixture_contextual_analysis + operación SaveContextualAnalysis
  7. Fórmula de combinación (logit blend) en Spring Boot
  8. Frontend — formulario + visualización de resultados combinados