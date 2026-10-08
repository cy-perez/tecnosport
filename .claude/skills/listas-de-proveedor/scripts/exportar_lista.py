#!/usr/bin/env python3
"""La lista del día, lista para la API: lo que se importa como borradores de tecnología.

Uso, al terminar el paso 6:

    python3 scripts/exportar_lista.py catalogo/productos.json --salida catalogo/lista-api.json

Y luego, con la sesión del panel:

    node tools/importar-lista-tecnologia.mjs catalogo/lista-api.json --proveedor <id> --escribir

La API recibe, por modelo, lo que la skill ya decidió —título, descripción, paleta
oficial— y, por configuración, la memoria, la SIM, el costo del proveedor, el
precio de mercado y los colores que sugieren los emojis de la lista. Con eso:

- un modelo que el sitio no tiene entra como **borrador de tecnología**, para que
  en el panel se elijan los colores que de verdad hay, el precio de venta de cada
  configuración, la categoría y la marca;
- uno que ya está publicado se **renueva**: costo del día y existencia repuesta en
  cada variante que vino (decisión del 08/10/2026: 2 unidades por variante);
- las configuraciones y los modelos **desaparecidos** dejan de ofrecerse.

Solo se exportan los modelos terminados (con descripción): uno a medio investigar
no puede convertirse en un producto. Los demás se informan.
"""

import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from parsear_lista import atributo_sim  # noqa: E402


def sim_de(producto) -> str | None:
    for a in producto.get("atributos") or []:
        canonica = atributo_sim(a.upper()) if a else None
        if canonica:
            return canonica
        if a == "eSIM":
            return "eSIM"
    return None


def exportar(datos: dict) -> tuple[dict, list]:
    """Devuelve (lo que va a la API, los modelos que no se exportaron con su motivo)."""
    por_modelo = {}
    for p in datos["productos"]:
        por_modelo.setdefault(p["id_modelo"], []).append(p)

    modelos, fuera = [], []
    for mid, productos in por_modelo.items():
        m = productos[0]
        if not m.get("descripcion"):
            fuera.append((m["titulo_modelo"], "sin descripción: falta terminar el paso 4"))
            continue
        modelos.append({
            "idModelo": mid,
            "titulo": m["titulo_modelo"],
            "marca": m.get("marca"),
            "categoria": m.get("categoria"),
            "descripcion": m["descripcion"],
            "metaDescripcion": m.get("meta_descripcion"),
            "paleta": list(m.get("colores_oficiales") or []),
            "configuraciones": [{
                "sku": p["id"],
                "titulo": p["titulo"],
                "ram": p.get("ram"),
                "almacenamiento": p.get("almacenamiento"),
                "sim": sim_de(p),
                "costoProveedor": p["precio_proveedor_cop"],
                "precioMercado": p.get("precio_mercado_cop"),
                "coloresSugeridos": list(p.get("colores_sugeridos") or []),
            } for p in productos],
        })

    return {
        "fechaLista": datos.get("fecha_lista"),
        "bloques": list(datos.get("bloques") or []),
        "modelos": modelos,
        "configuracionesDesaparecidas": [d["id"] for d in datos.get("desaparecidos") or []],
        "modelosDesaparecidos": [d["id_modelo"] for d in datos.get("modelos_desaparecidos") or []],
    }, fuera


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("productos")
    ap.add_argument("--salida", default="catalogo/lista-api.json")
    args = ap.parse_args()

    datos = json.loads(Path(args.productos).read_text(encoding="utf-8"))
    if not datos.get("comparacion"):
        sys.exit("Esta corrida no pasó por comparar_lista.py: sin eso no se saben los desaparecidos.")
    lista, fuera = exportar(datos)
    Path(args.salida).write_text(json.dumps(lista, ensure_ascii=False, indent=2), encoding="utf-8", newline="")
    n = sum(len(m["configuraciones"]) for m in lista["modelos"])
    print(f"{len(lista['modelos'])} modelos y {n} configuraciones en {args.salida}")
    print(f"{len(lista['configuracionesDesaparecidas'])} configuraciones y "
          f"{len(lista['modelosDesaparecidos'])} modelos desaparecidos")
    for titulo, motivo in fuera:
        print(f"  no se exporta {titulo}: {motivo}")


if __name__ == "__main__":
    main()
