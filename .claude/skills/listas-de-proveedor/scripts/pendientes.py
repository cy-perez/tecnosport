"""Qué le falta investigar a cada producto en el paso 4.

`comparar_lista.py` escribe en cada producto un campo `pendiente` con las tareas
que le quedan; los scripts del paso 4 le preguntan aquí antes de procesarlo:

- `precio`: cosechar y decidir el precio de mercado (precios.py, asignar_precios.py)
- `ficha`: buscar la ficha técnica oficial (icecat_local.py)
- `descripcion`: redactar descripción y metadatos (redactar_fichas.py)
- `completar`: la descripción heredada de la base existe, pero le falta la ficha técnica o el
  contenido de la caja; redactar_fichas.py inserta solo esas secciones, sin reescribir la prosa
  (22/10/2026: 20 de los 56 borradores de esa lista iban así al panel)

Un producto conocido con el precio vigente y la descripción completa no tiene nada
pendiente; uno con el precio vencido, solo el precio; uno nuevo, todo. **Sin el
campo —porque no se corrió la comparación— se procesa todo**, como antes de que
existiera; `completar` no, porque sin comparación la descripción se redacta entera.
"""

TAREAS = ("precio", "ficha", "descripcion")
COMPLETAR = "completar"


def necesita(producto: dict, tarea: str) -> bool:
    if tarea == COMPLETAR:
        return COMPLETAR in (producto.get("pendiente") or [])
    if tarea not in TAREAS:
        raise ValueError(f"tarea desconocida: {tarea}")
    pendiente = producto.get("pendiente")
    return pendiente is None or tarea in pendiente
