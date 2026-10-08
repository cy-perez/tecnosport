#!/usr/bin/env python3
"""Base de productos conocidos: lo que costó investigar, guardado para la lista siguiente.

Hasta el 08/10/2026 cada lista se procesaba desde cero. El precio de mercado, la
descripción y los colores de un producto vivían en el productos.json de la
corrida en que se investigaron, y la lista siguiente —que trae casi los mismos
equipos— los volvía a pedir. Con la del 08/10/2026, 69 de 88 productos ya
estaban investigados.

La base guarda, por id definitivo (el de `equivalencias.json`):

- lo que se investiga una vez: título, precio de mercado con sus fuentes, nivel,
  notas y **fecha**, descripción, metadatos, colores oficiales y los supuestos
  que escribió quien investigó;
- lo que mueve cada lista: el último costo del proveedor y la última fecha en
  que el producto apareció.

Ganancia y margen no se guardan: dependen del costo del día y se recalculan.

Uso, al terminar el paso 5:

    python3 scripts/conocidos.py consolidar catalogo/productos.json

Sin `--escribir` solo dice qué cambiaría.
"""

import argparse
import json
import re
import sys
from copy import deepcopy
from datetime import date, timedelta
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from parsear_lista import id_de_titulo  # noqa: E402

BASE = Path(__file__).resolve().parent.parent / "referencias" / "conocidos.json"

# Decisión del negocio, 08/10/2026: un precio de mercado vale siete días. Pasado
# eso, el producto se vuelve a investigar aunque sea conocido.
VIGENCIA_PRECIO_DIAS = 7

# Lo que se investiga una vez y se copia tal cual.
INVESTIGADO = (
    "titulo", "categoria", "marca",
    "precio_mercado_cop", "nivel_precio", "fuentes_precio", "notas_precio",
    "descripcion", "meta_titulo", "meta_descripcion", "colores_oficiales",
)
PRECIO = ("precio_mercado_cop", "nivel_precio", "fuentes_precio", "notas_precio", "fecha_precio")
DINERO = ("precio_mercado_cop", "ultimo_costo_cop")

# Un supuesto que habla del precio o cita una línea es de esa lista, no del
# producto: «precio tomado del bloque PRECIOS DE VENTA (línea 530 …)» es falso en
# la lista siguiente. Se queda en la corrida y no entra a la base.
RE_SUPUESTO_DE_LA_LISTA = re.compile(r"l[ií]nea\s+\d|precio|\$", re.I)

# La sección de colores que redactar_fichas.py escribe en la descripción.
SECCIONES_DESPUES_DE_COLORES = ("## Contenido de la caja", "## Garantía")
RE_SECCION_COLORES = re.compile(r"## Colores\n\nDisponible en [^\n]*\.\n(?:\n|$)")


def con_colores(descripcion: str, colores) -> str:
    """La descripción con la sección de colores que corresponde a `colores`.

    La descripción lleva los colores escritos dentro; si cambian, la sección tiene
    que cambiar con ellos o se publica un color que no hay. Si la sección está,
    se reemplaza en su sitio; sin colores, se va. Si no estaba, entra antes de la
    primera sección que redactar_fichas.py escribe después de ella.
    """
    descripcion = descripcion or ""
    bloque = ("## Colores\n\nDisponible en " + ", ".join(colores) + ".\n\n") if colores else ""
    m = RE_SECCION_COLORES.search(descripcion)
    if m:
        if not descripcion[m.end():]:
            # Era la última sección: un solo salto al final, con o sin colores.
            antes = descripcion[:m.start()]
            return antes + bloque[:-1] if bloque else antes.rstrip("\n") + "\n"
        return descripcion[:m.start()] + bloque + descripcion[m.end():]
    if not bloque:
        return descripcion
    for siguiente in SECCIONES_DESPUES_DE_COLORES:
        i = descripcion.find(siguiente)
        if i >= 0:
            return descripcion[:i] + bloque + descripcion[i:]
    return descripcion.rstrip("\n") + "\n\n" + bloque[:-1]


def precio_vigente(entrada: dict, hoy: date) -> bool:
    if not entrada.get("precio_mercado_cop") or not entrada.get("fecha_precio"):
        return False
    edad = hoy - date.fromisoformat(entrada["fecha_precio"])
    return edad <= timedelta(days=VIGENCIA_PRECIO_DIAS)


def consolidar(base: dict, datos: dict, hoy: date):
    """Devuelve una base nueva con los productos terminados de esta lista, y un resumen.

    Terminado es con descripción: es lo último que se escribe en el paso 4. Lo
    demás queda fuera y se informa, para que no se pierda en silencio.

    La fecha del precio es la de la consulta, que asignar_precios.py escribe en
    el producto, o la que heredó de la base. `hoy` es solo el último recurso, para
    un precio que llegó sin fecha: consolidar días después no puede rejuvenecerlo.

    Se niega a correr sin la comparación: sin `supuestos_lista` no hay cómo
    separar los supuestos del parser de los de la investigación.
    """
    if not datos.get("comparacion"):
        raise ValueError("Esta corrida no pasó por comparar_lista.py: sin eso no se pueden separar "
                         "los supuestos del parser de los de la investigación. Córrelo antes del paso 4.")
    nueva = deepcopy(base)
    resumen = {"altas": 0, "actualizados": 0, "sin_terminar": []}
    fecha_lista = datos.get("fecha_lista") or hoy.isoformat()

    for p in datos["productos"]:
        if not p.get("descripcion"):
            resumen["sin_terminar"].append(p["id"])
            continue
        anterior = nueva.get(p["id"])
        entrada = {campo: deepcopy(p.get(campo)) for campo in INVESTIGADO}
        entrada["fecha_precio"] = (
            (p.get("fecha_precio") or hoy.isoformat()) if p.get("precio_mercado_cop") else None)
        if not p.get("precio_mercado_cop") and anterior and anterior.get("precio_mercado_cop"):
            # El paso 4 no encontró precio: se conserva el anterior, con su fecha,
            # como referencia. Ya está vencido, así que la lista siguiente lo pide.
            for campo in PRECIO:
                entrada[campo] = deepcopy(anterior.get(campo))
        entrada["descripcion"] = con_colores(entrada["descripcion"], entrada["colores_oficiales"])

        de_la_lista = set(p.get("supuestos_lista") or [])
        investigados = [s for s in p.get("supuestos") or [] if s not in de_la_lista]
        entrada["supuestos_colores"] = [s for s in investigados if s.startswith("colores")]
        entrada["supuestos_investigacion"] = [
            s for s in investigados if not s.startswith("colores") and not RE_SUPUESTO_DE_LA_LISTA.search(s)]
        # Los colores que marcaba la lista: los oficiales solo se heredan mientras
        # la lista marque los mismos (ver comparar_lista.py).
        entrada["colores_de_la_lista"] = list(p.get("colores_familia") or [])

        # El mensaje de la lista en que suele venir: es lo que permite decir que
        # desapareció. Un producto que hoy llegó solo en un aviso no lo pierde.
        entrada["bloques"] = p.get("bloques") or (anterior or {}).get("bloques") or []
        entrada["ultimo_costo_cop"] = p.get("precio_proveedor_cop")
        entrada["visto_por_ultima_vez"] = fecha_lista
        entrada["fecha_alta"] = anterior["fecha_alta"] if anterior else fecha_lista
        if anterior and fecha_lista < anterior["visto_por_ultima_vez"]:
            # Una lista más vieja que la última vista —se volvió a consolidar para
            # corregir una descripción— no hace retroceder lo que mueve la lista.
            for campo in ("bloques", "ultimo_costo_cop", "visto_por_ultima_vez", "colores_de_la_lista"):
                entrada[campo] = deepcopy(anterior.get(campo))
        nueva[p["id"]] = entrada
        resumen["actualizados" if anterior else "altas"] += 1
    return nueva, resumen


def validar(base: dict) -> dict:
    """Se niega a cargar una base que daría por bueno un dato que no lo es."""
    errores = []
    for clave, e in base.items():
        if not e.get("titulo") or id_de_titulo(e["titulo"]) != clave:
            errores.append(f"{clave}: no corresponde al título «{e.get('titulo')}»")
        for campo in DINERO:
            valor = e.get(campo)
            if valor is not None and (not isinstance(valor, int) or isinstance(valor, bool)):
                errores.append(f"{clave}: {campo} tiene que ser pesos enteros, no {valor!r}")
        if e.get("precio_mercado_cop"):
            if not e.get("fecha_precio"):
                errores.append(f"{clave}: precio de mercado sin fecha_precio; no se sabría si venció")
            if not e.get("fuentes_precio"):
                errores.append(f"{clave}: precio de mercado sin fuentes; no se podría defender")
        for campo in ("bloques", "colores_de_la_lista", "supuestos_colores", "supuestos_investigacion"):
            if not isinstance(e.get(campo), list):
                errores.append(f"{clave}: {campo} tiene que ser una lista")
        for campo in ("visto_por_ultima_vez", "fecha_alta"):
            if not e.get(campo):
                errores.append(f"{clave}: falta {campo}")
    if errores:
        raise ValueError("conocidos.json no es válido:\n- " + "\n- ".join(errores))
    return base


def cargar(ruta=BASE) -> dict:
    ruta = Path(ruta)
    if not ruta.exists():
        return {}
    return validar(json.loads(ruta.read_text(encoding="utf-8"))["productos"])


def guardar(base: dict, ruta=BASE):
    """Ordenado y con LF, para que el diff de un commit sea el de los productos que cambiaron."""
    validar(base)
    documento = {
        "_leeme": [
            "Productos ya investigados, por id definitivo. Lo escribe `scripts/conocidos.py consolidar`;",
            "no se edita a mano. El precio de mercado vence a los "
            f"{VIGENCIA_PRECIO_DIAS} días de su fecha_precio.",
        ],
        "productos": dict(sorted(base.items())),
    }
    Path(ruta).write_text(json.dumps(documento, ensure_ascii=False, indent=2) + "\n",
                          encoding="utf-8", newline="")


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    sub = ap.add_subparsers(dest="orden", required=True)
    c = sub.add_parser("consolidar", help="guarda en la base los productos terminados de una corrida")
    c.add_argument("productos", help="productos.json al terminar el paso 5")
    c.add_argument("--base", default=str(BASE))
    c.add_argument("--fecha", default=date.today().isoformat(),
                   help="fecha de la investigación (por omisión, hoy)")
    c.add_argument("--escribir", action="store_true", help="sin esto, solo informa")
    args = ap.parse_args()

    datos = json.loads(Path(args.productos).read_text(encoding="utf-8"))
    base, resumen = consolidar(cargar(args.base), datos, date.fromisoformat(args.fecha))
    print(f"{resumen['altas']} altas · {resumen['actualizados']} actualizados · "
          f"{len(resumen['sin_terminar'])} sin terminar (sin descripción)")
    for pid in resumen["sin_terminar"]:
        print(f"  sin terminar: {pid}")
    if args.escribir:
        guardar(base, args.base)
        print(f"Escrito {args.base}")
    else:
        print("Simulación: agrega --escribir para guardar.")


if __name__ == "__main__":
    main()
