"""Las decisiones de la persona sobre una lista, escritas una vez y aplicadas en cada corrida.

`catalogo/1-lista/decisiones.json`, junto a la lista, porque son de esa lista y no del
catálogo —lo que vale para todas las listas va en `referencias/equivalencias.json` o en
`excluidos.json`—. Las aplica `comparar_lista.py` antes de comparar, sobre la copia de lo
que dejó el parser, así que una decisión nueva entra con solo volver a correr la comparación.

El 22/10/2026 la lista trajo el iPhone 18 Pro 256GB y el 17 Pro Max 256GB sin precio, la
persona decidió incluirlos con el costo de la lista anterior, y no había dónde decirlo: se
escribió un script suelto en `catalogo/1-lista/`, que hubo que correr a mano entre el
parser y la comparación cada vez que se rehízo la corrida.

Formato:

    {
      "decisiones": [
        {
          "id_lista": "apple-iphone-18-pro-256gb",
          "costo_cop": 4300000,
          "titulo": "Apple iPhone 18 Pro 256GB eSIM",
          "atributos": ["eSIM"],
          "motivo": "la lista no trae precio; se incluye con el costo de la lista del 15/10/2026",
          "fecha": "2026-10-22"
        }
      ]
    }

- `id_lista`: el id que dio el parser (el `id_lista` de un producto o de un descartado).
- `costo_cop`: el costo que decidió la persona. Si el producto estaba descartado por no
  traer precio, vuelve a los productos.
- `titulo`: el título definitivo, si cambia (el id y el modelo se recalculan de él).
- `atributos`: los que se suman, como la SIM que la línea no dice.
- `motivo` y `fecha`, obligatorios: quedan en `supuestos`, como cualquier decisión.
"""

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from parsear_lista import id_de_titulo, titulo_de_modelo  # noqa: E402

OBLIGATORIOS = ("id_lista", "motivo", "fecha")


def cargar(ruta) -> list:
    ruta = Path(ruta)
    if not ruta.is_file():
        return []
    decisiones = json.loads(ruta.read_text(encoding="utf-8")).get("decisiones") or []
    errores = [f"{d.get('id_lista') or '(sin id_lista)'}: faltan {', '.join(c for c in OBLIGATORIOS if not d.get(c))}"
               for d in decisiones if any(not d.get(c) for c in OBLIGATORIOS)]
    if errores:
        raise ValueError("decisiones.json no es válido:\n- " + "\n- ".join(errores))
    return decisiones


def aplicar(datos: dict, decisiones: list) -> list:
    """Aplica las decisiones en su sitio y devuelve los avisos (las que no encontraron su producto)."""
    avisos = []
    for d in decisiones:
        clave = d["id_lista"]
        en_productos = [p for p in datos["productos"] if clave in (p.get("id_lista"), p.get("id"))]
        en_descartados = [x for x in datos.get("descartados") or [] if clave in (x.get("id_lista"), x.get("id"))]
        if not en_productos and not en_descartados:
            avisos.append(f"{clave}: no está en la lista de hoy; la decisión no se aplicó")
            continue
        for x in en_descartados:
            if d.get("costo_cop") is None:
                avisos.append(f"{clave}: está descartado ({x.get('motivo')}) y la decisión no le da costo")
                continue
            datos["descartados"].remove(x)
            x = {k: v for k, v in x.items() if k not in ("motivo", "falta_informacion", "resolver")}
            datos["productos"].append(x)
            en_productos.append(x)
        for p in en_productos:
            if d.get("costo_cop") is not None:
                p["precio_proveedor_cop"] = int(d["costo_cop"])
            if d.get("titulo"):
                p["titulo"] = d["titulo"]
                p["id"] = id_de_titulo(d["titulo"])
                p["titulo_modelo"] = titulo_de_modelo(d["titulo"])
                p["id_modelo"] = id_de_titulo(p["titulo_modelo"])
            for a in d.get("atributos") or []:
                if a not in (p.get("atributos") or []):
                    p["atributos"] = list(p.get("atributos") or []) + [a]
            nota = f"decisión de la persona ({'/'.join(reversed(d['fecha'].split('-')))}): {d['motivo']}"
            p["supuestos"] = list(dict.fromkeys((p.get("supuestos") or []) + [nota]))
    return avisos
