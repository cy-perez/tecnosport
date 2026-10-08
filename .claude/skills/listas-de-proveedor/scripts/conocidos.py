#!/usr/bin/env python3
"""Base de productos conocidos: lo que costó investigar, guardado para la lista siguiente.

Hasta el 08/10/2026 cada lista se procesaba desde cero. El precio de mercado, la
descripción y los colores de un producto vivían en el productos.json de la
corrida en que se investigaron, y la lista siguiente —que trae casi los mismos
equipos— los volvía a pedir.

La base tiene dos partes, porque lo que se investiga no es de la misma unidad:

- **modelos** (por id del modelo, `parsear_lista.titulo_de_modelo`): lo que es del
  equipo y no cambia con la memoria —título, categoría, marca, descripción,
  metadatos, la paleta oficial de colores, los supuestos de la investigación, de
  dónde salió la ficha y las fotos—. Se investiga una vez por modelo: el Galaxy
  A57 de 256 GB y el de 512 GB comparten ficha.
- **configuraciones** (por id del SKU): lo que es de cada combinación de memoria y
  SIM —el precio de mercado con sus fuentes y su **fecha**, que el mercado paga
  distinto el de 256 y el de 512— y lo que mueve cada lista: el último costo, los
  colores que marcó, el mensaje en que vino y la última vez que se vio.

Ganancia y margen no se guardan: dependen del costo del día y se recalculan.

La descripción del modelo no lleva colores ni memoria: las dos son variantes que
el cliente elige al comprar (decisión del 08/10/2026), no texto de la ficha.

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
# eso, la configuración se vuelve a investigar aunque sea conocida.
VIGENCIA_PRECIO_DIAS = 7

PRECIO = ("precio_mercado_cop", "nivel_precio", "fuentes_precio", "notas_precio", "fecha_precio")
DINERO = ("precio_mercado_cop", "ultimo_costo_cop")

# Un supuesto que habla del precio o cita una línea es de esa lista, no del
# producto: «precio tomado del bloque PRECIOS DE VENTA (línea 530 …)» es falso en
# la lista siguiente. Se queda en la corrida y no entra a la base.
RE_SUPUESTO_DE_LA_LISTA = re.compile(r"l[ií]nea\s+\d|precio|\$", re.I)

# Lo que una descripción por SKU traía y una descripción de modelo no puede
# llevar: la sección de colores, la fila de memoria y las notas de la RAM
# virtual y de la SIM, que cambian de una configuración a otra.
RE_SECCION_COLORES = re.compile(r"## Colores\n\nDisponible en [^\n]*\.\n(?:\n|$)")
RE_FILA_MEMORIA = re.compile(r"^\| Memoria \|[^\n]*\n", re.M)
RE_NOTAS_DE_CONFIGURACION = re.compile(
    r"^- (?:La memoria RAM física es de [^\n]*|Admite una SIM física y una eSIM\.|"
    r"Este equipo funciona con eSIM: no tiene bandeja para SIM física\.)\n", re.M)


def descripcion_de_modelo(descripcion: str) -> str:
    """La descripción sin lo que depende de la configuración o del color."""
    d = descripcion or ""
    d = RE_SECCION_COLORES.sub("", d)
    d = RE_FILA_MEMORIA.sub("", d)
    d = RE_NOTAS_DE_CONFIGURACION.sub("", d)
    return d.rstrip("\n") + "\n" if d.strip() else d


def precio_vigente(entrada: dict, hoy: date) -> bool:
    if not entrada.get("precio_mercado_cop") or not entrada.get("fecha_precio"):
        return False
    edad = hoy - date.fromisoformat(entrada["fecha_precio"])
    return edad <= timedelta(days=VIGENCIA_PRECIO_DIAS)


def base_vacia() -> dict:
    return {"modelos": {}, "configuraciones": {}}


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
    modelos, configuraciones = nueva["modelos"], nueva["configuraciones"]
    resumen = {"modelos_altas": 0, "modelos_actualizados": 0,
               "configuraciones_altas": 0, "configuraciones_actualizadas": 0, "sin_terminar": []}
    fecha_lista = datos.get("fecha_lista") or hoy.isoformat()
    vistos_en_esta_corrida = set()

    for p in datos["productos"]:
        if not p.get("descripcion"):
            resumen["sin_terminar"].append(p["id"])
            continue

        # --- el modelo
        mid = p["id_modelo"]
        anterior_m = modelos.get(mid)
        de_la_lista = set(p.get("supuestos_lista") or [])
        investigados = [s for s in p.get("supuestos") or [] if s not in de_la_lista]
        paleta = list(dict.fromkeys(((anterior_m or {}).get("paleta") or []) + (p.get("colores_oficiales") or [])))
        modelo = {
            "titulo": p["titulo_modelo"],
            "categoria": p.get("categoria"),
            "marca": p.get("marca"),
            "descripcion": descripcion_de_modelo(p["descripcion"]),
            "meta_titulo": p["titulo_modelo"],
            "meta_descripcion": p.get("meta_descripcion"),
            "paleta": paleta,
            "supuestos_paleta": list(dict.fromkeys(
                ((anterior_m or {}).get("supuestos_paleta") or []) + [s for s in investigados if s.startswith("colores")])),
            "supuestos_investigacion": list(dict.fromkeys(
                ((anterior_m or {}).get("supuestos_investigacion") or [])
                + [s for s in investigados
                   if not s.startswith("colores") and not RE_SUPUESTO_DE_LA_LISTA.search(s)])),
            "fuentes_ficha": deepcopy(p.get("fuentes_ficha") or (anterior_m or {}).get("fuentes_ficha") or []),
            "fotos": deepcopy((anterior_m or {}).get("fotos") or []),
            "fecha_alta": anterior_m["fecha_alta"] if anterior_m else fecha_lista,
        }
        modelos[mid] = modelo
        if mid not in vistos_en_esta_corrida:
            resumen["modelos_actualizados" if anterior_m else "modelos_altas"] += 1
            vistos_en_esta_corrida.add(mid)

        # --- la configuración
        anterior = configuraciones.get(p["id"])
        conf = {
            "id_modelo": mid,
            "titulo": p["titulo"],
            **{campo: deepcopy(p.get(campo)) for campo in PRECIO if campo != "fecha_precio"},
        }
        conf["fecha_precio"] = (p.get("fecha_precio") or hoy.isoformat()) if p.get("precio_mercado_cop") else None
        if not p.get("precio_mercado_cop") and anterior and anterior.get("precio_mercado_cop"):
            # El paso 4 no encontró precio: se conserva el anterior, con su fecha,
            # como referencia. Ya está vencido, así que la lista siguiente lo pide.
            for campo in PRECIO:
                conf[campo] = deepcopy(anterior.get(campo))
        conf["fuentes_precio"] = conf.get("fuentes_precio") or []
        conf["notas_precio"] = conf.get("notas_precio") or []
        conf["colores_de_la_lista"] = list(p.get("colores_familia") or [])
        # El mensaje de la lista en que suele venir: es lo que permite decir que
        # desapareció. Un producto que hoy llegó solo en un aviso no lo pierde.
        conf["bloques"] = p.get("bloques") or (anterior or {}).get("bloques") or []
        conf["ultimo_costo_cop"] = p.get("precio_proveedor_cop")
        conf["visto_por_ultima_vez"] = fecha_lista
        conf["fecha_alta"] = anterior["fecha_alta"] if anterior else fecha_lista
        if anterior and fecha_lista < anterior["visto_por_ultima_vez"]:
            # Una lista más vieja que la última vista —se volvió a consolidar para
            # corregir una descripción— no hace retroceder lo que mueve la lista.
            for campo in ("bloques", "ultimo_costo_cop", "visto_por_ultima_vez", "colores_de_la_lista"):
                conf[campo] = deepcopy(anterior.get(campo))
        configuraciones[p["id"]] = conf
        resumen["configuraciones_actualizadas" if anterior else "configuraciones_altas"] += 1
    return nueva, resumen


def _dinero(errores, clave, e):
    for campo in DINERO:
        valor = e.get(campo)
        if valor is not None and (not isinstance(valor, int) or isinstance(valor, bool)):
            errores.append(f"{clave}: {campo} tiene que ser pesos enteros, no {valor!r}")


def validar(base: dict) -> dict:
    """Se niega a cargar una base que daría por bueno un dato que no lo es."""
    errores = []
    modelos, configuraciones = base.get("modelos"), base.get("configuraciones")
    if not isinstance(modelos, dict) or not isinstance(configuraciones, dict):
        raise ValueError("conocidos.json no es válido: tiene que tener «modelos» y «configuraciones»")
    for clave, m in modelos.items():
        if not m.get("titulo") or id_de_titulo(m["titulo"]) != clave:
            errores.append(f"modelo {clave}: no corresponde al título «{m.get('titulo')}»")
        for campo in ("paleta", "supuestos_paleta", "supuestos_investigacion", "fuentes_ficha", "fotos"):
            if not isinstance(m.get(campo), list):
                errores.append(f"modelo {clave}: {campo} tiene que ser una lista")
        if not m.get("fecha_alta"):
            errores.append(f"modelo {clave}: falta fecha_alta")
    for clave, c in configuraciones.items():
        if not c.get("titulo") or id_de_titulo(c["titulo"]) != clave:
            errores.append(f"configuración {clave}: no corresponde al título «{c.get('titulo')}»")
        if c.get("id_modelo") not in modelos:
            errores.append(f"configuración {clave}: su modelo «{c.get('id_modelo')}» no está en la base")
        _dinero(errores, clave, c)
        if c.get("precio_mercado_cop"):
            if not c.get("fecha_precio"):
                errores.append(f"{clave}: precio de mercado sin fecha_precio; no se sabría si venció")
            if not c.get("fuentes_precio"):
                errores.append(f"{clave}: precio de mercado sin fuentes; no se podría defender")
        for campo in ("bloques", "colores_de_la_lista", "fuentes_precio", "notas_precio"):
            if not isinstance(c.get(campo), list):
                errores.append(f"configuración {clave}: {campo} tiene que ser una lista")
        for campo in ("visto_por_ultima_vez", "fecha_alta"):
            if not c.get(campo):
                errores.append(f"configuración {clave}: falta {campo}")
    if errores:
        raise ValueError("conocidos.json no es válido:\n- " + "\n- ".join(errores))
    return base


def cargar(ruta=BASE) -> dict:
    ruta = Path(ruta)
    if not ruta.exists():
        return base_vacia()
    documento = json.loads(ruta.read_text(encoding="utf-8"))
    return validar({"modelos": documento.get("modelos"), "configuraciones": documento.get("configuraciones")})


def guardar(base: dict, ruta=BASE):
    """Ordenado y con LF, para que el diff de un commit sea el de los productos que cambiaron."""
    validar(base)
    documento = {
        "_leeme": [
            "Productos ya investigados. Lo escribe `scripts/conocidos.py consolidar`; no se edita a mano.",
            "modelos: lo que es del equipo (ficha, paleta, fotos). configuraciones: lo que es de cada SKU",
            f"(precio de mercado, que vence a los {VIGENCIA_PRECIO_DIAS} días de su fecha_precio, y lo que mueve la lista).",
        ],
        "modelos": dict(sorted(base["modelos"].items())),
        "configuraciones": dict(sorted(base["configuraciones"].items())),
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
                   help="último recurso para un precio sin fecha (por omisión, hoy)")
    c.add_argument("--escribir", action="store_true", help="sin esto, solo informa")
    args = ap.parse_args()

    datos = json.loads(Path(args.productos).read_text(encoding="utf-8"))
    base, resumen = consolidar(cargar(args.base), datos, date.fromisoformat(args.fecha))
    print(f"modelos: {resumen['modelos_altas']} altas, {resumen['modelos_actualizados']} actualizados · "
          f"configuraciones: {resumen['configuraciones_altas']} altas, "
          f"{resumen['configuraciones_actualizadas']} actualizadas · "
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
