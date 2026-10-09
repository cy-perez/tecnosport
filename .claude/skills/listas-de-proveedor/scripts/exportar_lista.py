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
from comparar_lista import colores_sugeridos  # noqa: E402
from parsear_lista import atributo_sim  # noqa: E402

# Lo que caben las columnas de la API (V92). Pasarse tumba la lista entera en una transacción,
# así que el que no cabe se queda fuera aquí y se dice.
LARGO_MAXIMO_DEL_ID = 60


def sim_de(producto) -> str | None:
    for a in producto.get("atributos") or []:
        canonica = atributo_sim(a.upper()) if a else None
        if canonica:
            return canonica
        if a == "eSIM":
            return "eSIM"
    return None


def sugeridos(producto) -> list:
    """Los colores que sugiere la lista, en nombres de la paleta.

    La comparación solo los calcula para un modelo que ya estaba en la base: uno nuevo
    no tiene paleta hasta el paso 4. Aquí la paleta ya existe, así que se calculan para
    los que llegaron sin ellos —que es justo el caso principal, el modelo nuevo—.
    """
    if producto.get("colores_sugeridos"):
        return list(producto["colores_sugeridos"])
    return colores_sugeridos(producto.get("colores_familia"), producto.get("colores_oficiales"))


def exportar(datos: dict) -> tuple[dict, list]:
    """Devuelve (lo que va a la API, los modelos que no se exportaron con su motivo)."""
    if not datos.get("fecha_lista"):
        raise ValueError("La lista no tiene fecha: sin ella la API no sabe si es más nueva que la última.")
    por_modelo = {}
    for p in datos["productos"]:
        if len(p["id"]) > LARGO_MAXIMO_DEL_ID:
            continue
        por_modelo.setdefault(p["id_modelo"], []).append(p)

    modelos = []
    fuera = [(p["titulo"], f"el id pasa de {LARGO_MAXIMO_DEL_ID} caracteres; acórtalo en las equivalencias")
             for p in datos["productos"] if len(p["id"]) > LARGO_MAXIMO_DEL_ID]
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
                "coloresSugeridos": sugeridos(p),
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
    try:
        lista, fuera = exportar(datos)
    except ValueError as error:
        sys.exit(str(error))
    Path(args.salida).write_text(json.dumps(lista, ensure_ascii=False, indent=2), encoding="utf-8", newline="")
    n = sum(len(m["configuraciones"]) for m in lista["modelos"])
    print(f"{len(lista['modelos'])} modelos y {n} configuraciones en {args.salida}")
    print(f"{len(lista['configuracionesDesaparecidas'])} configuraciones y "
          f"{len(lista['modelosDesaparecidos'])} modelos desaparecidos")
    for titulo, motivo in fuera:
        print(f"  no se exporta {titulo}: {motivo}")


if __name__ == "__main__":
    main()
