"""La paleta del fabricante de cada modelo: qué modelos no la tienen, y fijarla con su fuente.

Desde ADR-0086 la paleta no es decorativa. La ficha la dice al pie de las fotos («Colores del
modelo según el fabricante»), cada color de ella se puede elegir, y un modelo sin paleta pasa
siempre por WhatsApp. Por eso el bloque 3 no propone un borrador nuevo sin paleta
(`tools/importar-lista-tecnologia.mjs --borradores`), y este script es el que la completa.

    python scripts/paletas.py pendientes
    python scripts/paletas.py fijar apple-airpods-pro-3 --colores Blanco \\
        --fuente "apple.com, especificaciones" --url https://support.apple.com/es-lamr/125135

`fijar` escribe en los dos sitios que la usan: la base de conocidos (`paleta`, y la fuente en
`supuestos_paleta`) y la corrida en curso (`colores_oficiales` de cada configuración del
modelo). **Reemplaza** lo que había, y también el supuesto viejo: «el fabricante no publica
variantes» es justo lo que se está corrigiendo, y `consolidar` lo volvería a sumar si quedara.

**Un modelo de un solo acabado lleva una paleta de un color**, nunca vacía. Que la marca no
publique variantes de color no quiere decir que no tenga color: quiere decir que tiene uno, y
con ese uno el sitio lo vende sin preguntar.
"""

import argparse
import json
import sys
from datetime import date
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import conocidos  # noqa: E402
import rutas  # noqa: E402

LARGO_MAXIMO_DE_UN_COLOR = 80  # el de la API (Producto.LARGO_MAXIMO_DE_UN_COLOR)


def limpiar_colores(colores) -> list:
    """Sin vacíos ni repetidos sin importar mayúsculas, como los guarda la API."""
    limpios = []
    for color in colores or []:
        uno = (color or "").strip()
        if not uno:
            continue
        if "\n" in uno or "\r" in uno:
            raise ValueError(f"Un color va en una sola línea: {uno!r}")
        if len(uno) > LARGO_MAXIMO_DE_UN_COLOR:
            raise ValueError(f"El color {uno!r} pasa de {LARGO_MAXIMO_DE_UN_COLOR} caracteres.")
        if all(c.lower() != uno.lower() for c in limpios):
            limpios.append(uno)
    if not limpios:
        raise ValueError("Una paleta tiene por lo menos un color: si el modelo se vende en un solo "
                         "acabado, ese es su color.")
    return limpios


def supuesto_de_fuente(fuente: str, url: str | None, fecha: str) -> str:
    """El supuesto que dice de dónde salió la paleta. Empieza por «colores»: así lo separa
    `consolidar` de los demás supuestos de la investigación."""
    donde = f"{fuente} ({url})" if url else fuente
    return f"colores: según {donde}, consultado el {fecha}."


def pendientes(base: dict, datos: dict | None) -> list:
    """Los modelos sin paleta, de la base y de la corrida, con lo que se sabe de cada uno."""
    filas = {}
    for mid, m in base["modelos"].items():
        if not m.get("paleta"):
            filas[mid] = {"id_modelo": mid, "titulo": m.get("titulo"), "marca": m.get("marca"),
                          "categoria": m.get("categoria"), "en_la_base": True, "en_la_corrida": False,
                          "supuestos_paleta": list(m.get("supuestos_paleta") or [])}
    for p in (datos or {}).get("productos") or []:
        if p.get("colores_oficiales"):
            continue
        fila = filas.setdefault(p["id_modelo"], {
            "id_modelo": p["id_modelo"], "titulo": p.get("titulo_modelo"), "marca": p.get("marca"),
            "categoria": p.get("categoria"), "en_la_base": False, "en_la_corrida": True,
            "supuestos_paleta": [s for s in p.get("supuestos") or [] if s.startswith("colores")]})
        fila["en_la_corrida"] = True
    return sorted(filas.values(), key=lambda f: f["id_modelo"])


def fijar(base: dict, datos: dict | None, mid: str, colores, fuente: str, url: str | None,
          fecha: str) -> dict:
    """Fija la paleta de un modelo en la base y en la corrida. Devuelve qué tocó."""
    if not fuente or not fuente.strip():
        raise ValueError("Una paleta sin fuente no se puede defender: di de dónde salió.")
    paleta = limpiar_colores(colores)
    supuesto = supuesto_de_fuente(fuente.strip(), url, fecha)
    tocado = {"base": False, "configuraciones": 0}

    modelo = base["modelos"].get(mid)
    if modelo is not None:
        modelo["paleta"] = list(paleta)
        modelo["supuestos_paleta"] = [supuesto]
        tocado["base"] = True

    for p in (datos or {}).get("productos") or []:
        if p.get("id_modelo") != mid:
            continue
        p["colores_oficiales"] = list(paleta)
        de_la_lista = set(p.get("supuestos_lista") or [])
        # Los supuestos de color de la investigación se reemplazan; los de la lista —«colores
        # sugeridos: la lista no marca colores…»— son de esta lista y se quedan.
        p["supuestos"] = [s for s in p.get("supuestos") or []
                          if s in de_la_lista or not s.startswith("colores")] + [supuesto]
        tocado["configuraciones"] += 1

    if not tocado["base"] and not tocado["configuraciones"]:
        raise ValueError(f"«{mid}» no está ni en la base ni en la corrida.")
    return tocado


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    sub = ap.add_subparsers(dest="orden", required=True)
    pen = sub.add_parser("pendientes", help="los modelos sin paleta, de la base y de la corrida")
    pen.add_argument("--productos", default=str(rutas.PRODUCTOS))
    fij = sub.add_parser("fijar", help="fija la paleta de un modelo con su fuente")
    fij.add_argument("id_modelo")
    fij.add_argument("--colores", required=True, help="separados por coma, en el orden de la marca")
    fij.add_argument("--fuente", required=True, help="dónde se leyó: «apple.com, especificaciones»")
    fij.add_argument("--url")
    fij.add_argument("--fecha", default=date.today().isoformat())
    fij.add_argument("--productos", default=str(rutas.PRODUCTOS))
    args = ap.parse_args()

    base = conocidos.cargar()
    ruta = Path(args.productos)
    datos = json.loads(ruta.read_text(encoding="utf-8")) if ruta.exists() else None

    if args.orden == "pendientes":
        filas = pendientes(base, datos)
        if not filas:
            print("Todos los modelos tienen paleta.")
            return
        print(f"{len(filas)} modelo(s) sin paleta:")
        for f in filas:
            donde = " y ".join(d for d, si in (("base", f["en_la_base"]), ("corrida", f["en_la_corrida"])) if si)
            print(f"- {f['id_modelo']} · {f['titulo']} ({donde})")
            for s in f["supuestos_paleta"]:
                print(f"    {s}")
        return

    try:
        tocado = fijar(base, datos, args.id_modelo, args.colores.split(","), args.fuente, args.url, args.fecha)
    except ValueError as error:
        sys.exit(str(error))
    if tocado["base"]:
        conocidos.guardar(base)
    if tocado["configuraciones"]:
        ruta.write_text(json.dumps(datos, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="")
    print(f"{args.id_modelo}: paleta fijada en la base={tocado['base']} y en "
          f"{tocado['configuraciones']} configuración(es) de la corrida.")


if __name__ == "__main__":
    main()
