"""Qué le falta investigar a cada producto en el paso 4.

`comparar_lista.py` escribe en cada producto un campo `pendiente` con las tareas
que le quedan; los scripts del paso 4 le preguntan aquí antes de procesarlo:

- `precio`: cosechar y decidir el precio de mercado (precios.py, asignar_precios.py)
- `ficha`: buscar la ficha técnica oficial (icecat_local.py)
- `descripcion`: redactar descripción y metadatos (redactar_fichas.py)

Un producto conocido con el precio vigente no tiene nada pendiente; uno con el
precio vencido, solo el precio; uno nuevo, todo. **Sin el campo —porque no se
corrió la comparación— se procesa todo**, como antes de que existiera.
"""

TAREAS = ("precio", "ficha", "descripcion")


def necesita(producto: dict, tarea: str) -> bool:
    if tarea not in TAREAS:
        raise ValueError(f"tarea desconocida: {tarea}")
    pendiente = producto.get("pendiente")
    return pendiente is None or tarea in pendiente
