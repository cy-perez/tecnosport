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
DINERO = ("precio_mercado_cop", "ultimo_costo_cop")


def precio_vigente(entrada: dict, hoy: date) -> bool:
    if not entrada.get("precio_mercado_cop") or not entrada.get("fecha_precio"):
        return False
    edad = hoy - date.fromisoformat(entrada["fecha_precio"])
    return edad <= timedelta(days=VIGENCIA_PRECIO_DIAS)


def consolidar(base: dict, datos: dict, hoy: date):
    """Devuelve una base nueva con los productos terminados de esta lista, y un resumen.

    Terminado es con descripción: es lo último que se escribe en el paso 4. Lo
    demás queda fuera y se informa, para que no se pierda en silencio.

    La fecha del precio es `hoy` solo si el precio se investigó en esta corrida.
    Si `comparar_lista` lo heredó de la base, el producto trae su `fecha_precio`
    y se conserva: rejuvenecerlo haría que un precio no venciera nunca.
    """
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
        de_la_lista = set(p.get("supuestos_lista") or [])
        entrada["supuestos_investigacion"] = [s for s in p.get("supuestos") or [] if s not in de_la_lista]
        # El mensaje de la lista en que suele venir: es lo que permite decir que
        # desapareció. Un producto que hoy llegó solo en un aviso no lo pierde.
        entrada["bloques"] = p.get("bloques") or (anterior or {}).get("bloques") or []
        entrada["ultimo_costo_cop"] = p.get("precio_proveedor_cop")
        entrada["visto_por_ultima_vez"] = fecha_lista
        entrada["fecha_alta"] = anterior["fecha_alta"] if anterior else fecha_lista
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
        if not isinstance(e.get("bloques"), list):
            errores.append(f"{clave}: bloques tiene que ser una lista")
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
