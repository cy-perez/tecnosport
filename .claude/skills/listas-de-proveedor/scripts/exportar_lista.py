#!/usr/bin/env python3
"""La lista del día, lista para la API: lo que se importa como borradores de tecnología.

Uso, al terminar el paso 6:

    python3 scripts/exportar_lista.py catalogo/productos.json

Y luego, con la sesión del panel:

    node tools/importar-lista-tecnologia.mjs catalogo/5-api/lista-api.json --inventario \
        --proveedor <id> --escribir

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

Y solo las configuraciones con al menos un 5 % de margen sobre el costo (regla 18,
`MARGEN_MINIMO`): las demás van como desaparecidas, y se informan.
"""

import argparse
import hashlib
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from comparar_lista import colores_sugeridos  # noqa: E402
from parsear_lista import atributo_sim  # noqa: E402
import rutas  # noqa: E402
from margen import MARGEN_MINIMO, margen, se_ofrece  # noqa: E402

# Lo que caben las columnas de la API (V92). Pasarse tumba la lista entera en una transacción,
# así que el que no cabe se queda fuera aquí y se dice.
LARGO_MAXIMO_DEL_ID = 60

# La regla 18 (`margen.py`) se aplica aquí mandando como desaparecida la configuración que no
# llega al mínimo: es lo único que la retira, porque la importación no deduce nada de lo que
# falta en el archivo —sin eso seguiría a la venta con el costo de la lista anterior—. Si nunca
# se publicó, retirarla no hace nada.


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
    sin_margen, modelos_sin_margen = [], []
    for mid, productos in list(por_modelo.items()):
        bajos = [p for p in productos if not se_ofrece(p)]
        for p in bajos:
            sin_margen.append(p["id"])
            fuera.append((p["titulo"], f"margen de {margen(p) * 100:.1f} % frente al mercado, por debajo del "
                                       f"mínimo de {MARGEN_MINIMO * 100:.0f} %: deja de ofrecerse"))
        productos = [p for p in productos if p not in bajos]
        if productos:
            por_modelo[mid] = productos
        else:
            del por_modelo[mid]
            modelos_sin_margen.append(mid)

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
        "configuracionesDesaparecidas": [d["id"] for d in datos.get("desaparecidos") or []] + sin_margen,
        "modelosDesaparecidos": [d["id_modelo"] for d in datos.get("modelos_desaparecidos") or []]
                                + modelos_sin_margen,
    }, fuera


def huella(contenido: str) -> str:
    """La misma de `huellaDeArchivo` en tools/importar-lista-tecnologia.mjs: el SHA-256 del texto."""
    return hashlib.sha256(contenido.encode("utf-8")).hexdigest()


def choque_con_lo_que_entro(contenido: str, por_proponer: dict | None) -> str | None:
    """Por qué no se puede escribir esta exportación encima de la que ya entró, o None.

    El 15/10/2026 el inventario entró con una exportación y después se exportó otra
    encima: los borradores se negaban, con razón, y la que entró ya no estaba en
    ningún lado. Una lista de otra fecha es otra lista, y la misma exportación otra
    vez no cambia nada: las dos pasan.
    """
    if not por_proponer or not por_proponer.get("huellaLista"):
        return None
    if por_proponer.get("fechaLista") != json.loads(contenido).get("fechaLista"):
        return None
    if por_proponer["huellaLista"] == huella(contenido):
        return None
    return (f"El inventario de la lista del {por_proponer['fechaLista']} ya entró con otra exportación, "
            "y escribir esta encima deja a los borradores sin el contenido que el inventario vio. "
            "Si de verdad hay que corregir la lista, pasa --reemplazar-la-que-entro y vuelve a "
            "correr el inventario con el archivo nuevo: es otro archivo, así que la API lo acepta.")


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("productos")
    ap.add_argument("--salida", default=str(rutas.API / "lista-api.json"))
    ap.add_argument("--reemplazar-la-que-entro", action="store_true",
                    help="escribe aunque el inventario de esta lista ya haya entrado con otra exportación")
    args = ap.parse_args()

    datos = json.loads(Path(args.productos).read_text(encoding="utf-8"))
    if not datos.get("comparacion"):
        sys.exit("Esta corrida no pasó por comparar_lista.py: sin eso no se saben los desaparecidos.")
    try:
        lista, fuera = exportar(datos)
    except ValueError as error:
        sys.exit(str(error))
    contenido = json.dumps(lista, ensure_ascii=False, indent=2)
    ruta_por_proponer = Path(args.salida).parent / "por-proponer.json"
    por_proponer = (json.loads(ruta_por_proponer.read_text(encoding="utf-8"))
                    if ruta_por_proponer.exists() else None)
    choque = choque_con_lo_que_entro(contenido, por_proponer)
    if choque and not args.reemplazar_la_que_entro:
        sys.exit(choque)
    Path(args.salida).parent.mkdir(parents=True, exist_ok=True)
    Path(args.salida).write_text(contenido, encoding="utf-8", newline="")
    n = sum(len(m["configuraciones"]) for m in lista["modelos"])
    print(f"{len(lista['modelos'])} modelos y {n} configuraciones en {args.salida}")
    print(f"{len(lista['configuracionesDesaparecidas'])} configuraciones y "
          f"{len(lista['modelosDesaparecidos'])} modelos desaparecidos")
    for titulo, motivo in fuera:
        print(f"  no se exporta {titulo}: {motivo}")


if __name__ == "__main__":
    main()
