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

  Próximos pasos obligatorios

  # 1. Regenerar el dataset (tardará más por las nuevas queries de jugadores)
  python data-api/ml/feature_engineering.py

  # 2. Reentrenar los modelos
  python data-api/ml/train_model.py --test-seasons 2025

  # 3. Reiniciar la API
  uvicorn predict_api:app --host 0.0.0.0 --port 8001