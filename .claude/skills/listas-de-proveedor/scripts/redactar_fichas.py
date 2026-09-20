#!/usr/bin/env python3
"""
Compone la descripción de cada producto con la estructura de
`referencias/descripciones.md` y la escribe en productos.json.

Uso:
    python3 redactar_fichas.py productos.json --prosa prosa.json \
        --icecat catalogo/icecat/fichas --mi catalogo/mi-fichas.json

Reparte el trabajo como lo reparte la skill: **la estructura y la tabla las
arma el script** con los datos de la ficha oficial, y **la prosa la escribe una
persona** en `prosa.json` —la apertura, las viñetas de beneficio, el contenido
de la caja y las notas—. Así las 96 descripciones salen iguales de forma y
distintas de fondo, que es lo que pide el estándar.

Lo que este script NO hace, a propósito:

- **No inventa.** Si un atributo no está en la ficha, la fila no aparece. Un
  producto sin prosa autorada queda sin descripción y marcado, no con una
  descripción genérica.
- **No promete garantía en plazos.** El texto remite a la garantía legal
  colombiana y a la política de la tienda, porque el plazo es un dato del
  negocio. Nunca un marcador `[[ ]]`, que la regla 4 del proyecto prohíbe en
  texto publicado.
- **No omite la atribución de Icecat.** Cuando la ficha sale de Open Icecat, el
  bloque de fuentes lleva la mención y el enlace: es condición de la licencia,
  no un crédito opcional.

Sin dependencias: solo biblioteca estándar.
"""

import argparse
import json
import re
from pathlib import Path

LIMITE_META_TITULO = 60
LIMITE_META_DESCRIPCION = 155

# Atributo de Icecat -> fila de la tabla. El orden de esta lista es el orden de
# la tabla, y la primera coincidencia gana: un producto no repite fila.
FILAS = [
    ("Pantalla", ["Diagonal de la pantalla", "Resolución de la pantalla",
                  "Tipo de pantalla", "Máxima velocidad de actualización"]),
    ("Procesador", ["Familia de procesador", "Modelo del procesador",
                    "Número de núcleos de procesador"]),
    ("Memoria", ["Capacidad de RAM", "Capacidad de almacenamiento interno",
                 "Tarjetas de memoria compatibles"]),
    ("Cámara", ["Resolución de la cámara trasera (numérica)",
                "Tipo de cámara trasera",
                "Resolución de la cámara frontal (numérica)"]),
    ("Batería", ["Capacidad de batería", "Potencia de carga requerida (máx.)",
                 "Adaptador AC incluido"]),
    ("Conectividad", ["Estándar Wi-Fi", "Versión de Bluetooth",
                      "Comunicación de Campo Cercano (NFC)", "Conector USB"]),
    ("Sistema operativo", ["Sistema operativo instalado", "Plataforma"]),
    ("Resistencia", ["Código IP (International Protection)"]),
    ("Dimensiones y peso", ["Altura", "Ancho", "Profundidad", "Peso"]),
]


def limpiar(valor):
    return re.sub(r"\s+", " ", str(valor)).strip()


def tabla_icecat(ficha):
    """Las filas que la ficha soporta, en el orden del estándar."""
    por_atributo = {}
    for e in ficha.get("especificaciones") or []:
        por_atributo.setdefault(limpiar(e["atributo"]), limpiar(e["valor"]))
    filas = []
    for etiqueta, atributos in FILAS:
        partes = [por_atributo[a] for a in atributos if por_atributo.get(a)]
        if partes:
            filas.append((etiqueta, " · ".join(dict.fromkeys(partes))))
    return filas


def tabla_mi(ficha):
    """
    La ficha de mi.com ya viene por secciones y redactada a mano en
    `mi-fichas.json`, así que aquí no se reformatea el valor: solo se quita la
    nota al pie que el fabricante cuelga con asterisco.

    No intentes separar palabras pegadas con una regla de mayúsculas: rompe
    justamente los datos que importan —«1.5K» queda «1.5 · K», «MediaTek» queda
    «Media · Tek» y «200MP» queda «200 · MP»—.
    """
    filas = []
    for etiqueta, valor in (ficha.get("f") or {}).items():
        v = re.sub(r"\*.*$", "", limpiar(valor)).strip()
        if v:
            filas.append((limpiar(etiqueta), v[:300]))
    return filas


def meta_titulo(producto):
    t = producto["titulo"]
    return t if len(t) <= LIMITE_META_TITULO else t[:LIMITE_META_TITULO].rstrip(" ,·-")


def meta_descripcion(producto, prosa):
    base = prosa.get("meta") or prosa.get("apertura", "")
    base = re.sub(r"\s+", " ", base).strip()
    if len(base) <= LIMITE_META_DESCRIPCION:
        return base
    corte = base[:LIMITE_META_DESCRIPCION].rsplit(" ", 1)[0]
    return corte.rstrip(" ,.;:") + "."


def notas_obligatorias(producto):
    """
    Las declaraciones que `descripciones.md` no deja omitir, cuando el dato del
    producto las dispara.
    """
    notas = []
    if producto.get("ram_virtual"):
        notas.append(
            f"La memoria RAM física es de {producto['ram']}. El fabricante permite "
            f"extenderla en {producto['ram_virtual']} adicionales que el equipo toma "
            "del almacenamiento interno; esa memoria extendida no es RAM física y no "
            "se suma a ella.")
    atributos = " ".join(producto.get("atributos") or [])
    if "eSIM" in atributos and "SIM" in atributos:
        notas.append("Admite una SIM física y una eSIM.")
    elif "eSIM" in atributos:
        notas.append("Este equipo funciona con eSIM: no tiene bandeja para SIM física.")
    if producto.get("compatible_con"):
        notas.append(
            f"Es un accesorio de la marca {producto.get('marca') or 'indicada'}, "
            f"compatible con equipos {producto['compatible_con']}. No es un producto "
            f"original de {producto['compatible_con']}.")
    notas.append(
        "Producto nuevo, sellado y sin activar. Aplica la garantía legal que la ley "
        "colombiana reconoce para bienes nuevos; el plazo y el procedimiento son los "
        "de la política de garantías de la tienda.")
    return notas


def memoria_del_producto(producto):
    """
    La RAM y el almacenamiento salen SIEMPRE de la línea del proveedor, nunca de
    la ficha.

    El índice de Icecat mezcla variantes: la ficha del Galaxy A56 declara 128GB
    y la que vendemos es de 256GB, la del A57 declara 8GB cuando la nuestra trae
    12GB. Copiar la ficha tal cual publica una tabla que contradice el título del
    propio producto, y el cliente lo comprueba en dos toques. El resto de la
    ficha —pantalla, cámara, batería— no cambia entre variantes y sí sirve.
    """
    partes = []
    if producto.get("ram"):
        partes.append(f"{producto['ram']} de RAM")
    if producto.get("almacenamiento"):
        partes.append(f"{producto['almacenamiento']} de almacenamiento")
    return " · ".join(partes) or None


def componer(producto, prosa, ficha_icecat, ficha_mi):
    partes = [prosa["apertura"].strip(), ""]

    vinetas = prosa.get("vinetas") or []
    if vinetas:
        partes += ["## Características principales", ""]
        partes += [f"- {v}" for v in vinetas] + [""]

    filas = tabla_icecat(ficha_icecat) if ficha_icecat else []
    origen = "Open Icecat" if filas else None
    if not filas and ficha_mi:
        filas = tabla_mi(ficha_mi)
        origen = "el sitio oficial del fabricante"

    # La memoria manda desde la linea del proveedor, no desde la ficha.
    propia = memoria_del_producto(producto)
    if propia:
        filas = [(a, propia if a == "Memoria" else b) for a, b in filas]
        if not any(a == "Memoria" for a, _ in filas):
            filas.append(("Memoria", propia))

    if filas:
        partes += ["## Ficha técnica", "", "| Atributo | Detalle |", "|---|---|"]
        partes += [f"| {a} | {b} |" for a, b in filas] + [""]

    if producto.get("colores_oficiales"):
        partes += ["## Colores", "",
                   "Disponible en " + ", ".join(producto["colores_oficiales"]) + ".", ""]

    caja = prosa.get("caja") or []
    if caja:
        partes += ["## Contenido de la caja", ""]
        partes += [f"- {c}" for c in caja] + [""]

    partes += ["## Garantía y notas", ""]
    partes += [f"- {n}" for n in (prosa.get("notas") or []) + notas_obligatorias(producto)]
    partes += [""]

    fuentes = []
    if origen == "Open Icecat":
        fuentes.append(
            "Ficha técnica: Specs Icecat (https://icecat.biz). Icecat no responde por "
            "errores u omisiones en los datos del fabricante.")
    elif origen:
        fuentes.append(f"Ficha técnica: {origen}.")
    if fuentes:
        partes += ["---", ""] + fuentes

    return "\n".join(partes).strip() + "\n"


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("productos")
    ap.add_argument("--prosa", required=True)
    ap.add_argument("--icecat", help="carpeta de fichas de icecat_local.py")
    ap.add_argument("--mi", help="JSON de fichas de mi.com")
    a = ap.parse_args()

    datos = json.loads(Path(a.productos).read_text(encoding="utf-8"))
    prosas = json.loads(Path(a.prosa).read_text(encoding="utf-8"))
    dir_ice = Path(a.icecat) if a.icecat else None
    mi = json.loads(Path(a.mi).read_text(encoding="utf-8")) if a.mi else {}

    hechas, sin_prosa = 0, []
    for p in datos["productos"]:
        prosa = prosas.get(p["id"])
        if not prosa or not prosa.get("apertura"):
            sin_prosa.append(p["titulo"])
            continue
        # `"icecat": false` descarta la ficha entera. El indice mezcla productos
        # que comparten nombre: la del `Honor Pad X8b` (tablet de 11") es la del
        # `Honor X8b`, que es un celular de 6,7", y la del bundle de Switch 2 es
        # la del juego Mario Kart World, que pesa 10 gramos. Si la ficha
        # contradice la categoria del producto, no se usa ni un dato de ella.
        ficha_ice = None
        if dir_ice and prosa.get("icecat") is not False:
            ruta = dir_ice / f"{p['id']}.json"
            if ruta.is_file():
                ficha_ice = json.loads(ruta.read_text(encoding="utf-8"))
        ficha_mi = mi.get(prosa.get("mi") or "")
        p["descripcion"] = componer(p, prosa, ficha_ice, ficha_mi)
        p["meta_titulo"] = meta_titulo(p)
        p["meta_descripcion"] = meta_descripcion(p, prosa)
        p["revisar"] = [r for r in p["revisar"] if "sin descripcion" not in r]
        hechas += 1

    for p in datos["productos"]:
        if p["titulo"] in sin_prosa and "sin descripcion" not in " ".join(p["revisar"]):
            p["revisar"].append(
                "sin descripcion: falta la ficha oficial del fabricante, asi que no "
                "hay de donde sacar los datos sin inventarlos")

    Path(a.productos).write_text(json.dumps(datos, ensure_ascii=False, indent=2),
                                 encoding="utf-8")
    print(f"{hechas} descripciones escritas | {len(sin_prosa)} sin prosa autorada")
    largos = [p["titulo"] for p in datos["productos"]
              if len(p.get("meta_titulo") or "") > LIMITE_META_TITULO
              or len(p.get("meta_descripcion") or "") > LIMITE_META_DESCRIPCION]
    if largos:
        print("metadatos fuera de limite:", largos)
    for t in sin_prosa:
        print("   sin prosa:", t)


if __name__ == "__main__":
    main()
