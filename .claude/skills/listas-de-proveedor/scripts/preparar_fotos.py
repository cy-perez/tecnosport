#!/usr/bin/env python3
"""
Prepara la descarga de fotos de producto.

El entorno donde corre la skill solo alcanza una lista corta de dominios, así que
no puede bajar imágenes de sitios de marca. Este script arma el paquete para que
la descarga se haga en el computador del usuario, donde sí hay salida a internet:

    python3 preparar_fotos.py productos.json --salida fotos/

Deja en la carpeta de salida:
    urls.csv       una fila por producto y foto, con columnas para pegar el enlace
    descargar.py   descargador sin dependencias, se ejecuta en Windows, Mac o Linux
    LEEME.txt      los tres pasos

Cada fila pide también la fuente y la licencia. No es burocracia: es lo que
permite responder si mañana alguien reclama por una imagen, y lo que evita subir
fotos de kits de prensa, que están licenciadas para uso editorial y no para una
tienda.
"""

import argparse
import csv
import json
from datetime import date
from pathlib import Path

FOTOS_POR_PRODUCTO = 4
ENCUADRES = ["frontal", "posterior", "angulo", "detalle"]

DESCARGADOR = '''#!/usr/bin/env python3
"""
Descarga las fotos listadas en urls.csv. No necesita instalar nada.

    python descargar.py

Guarda cada archivo en crudas/<id-del-producto>/<id>-NN.<ext> y escribe
crudas/<id>/origen.json con la fuente y la licencia de cada imagen, que es el
respaldo de por qué esa foto se puede publicar.

Después, en el entorno de la skill:
    python3 normalizar_imagenes.py crudas/ --salida imagenes/
"""

import csv
import json
import mimetypes
import urllib.request
from collections import defaultdict
from pathlib import Path

AGENTE = "Mozilla/5.0 (compatible; catalogo-tecnosport/1.0)"
BASE = Path(__file__).parent


def descargar(url, destino):
    pedido = urllib.request.Request(url, headers={"User-Agent": AGENTE})
    with urllib.request.urlopen(pedido, timeout=30) as r:
        datos = r.read()
        ext = mimetypes.guess_extension(r.headers.get_content_type()) or ".jpg"
    if ext == ".jpe":
        ext = ".jpg"
    final = destino.with_suffix(ext)
    final.parent.mkdir(parents=True, exist_ok=True)
    final.write_bytes(datos)
    return final, len(datos)


def main():
    filas = []
    for archivo in sorted(BASE.glob("urls*.csv")):
        filas += list(csv.DictReader(archivo.open(encoding="utf-8-sig")))
    pendientes = [f for f in filas if f["url"].strip()]
    if not pendientes:
        print("Ningún urls*.csv tiene enlaces todavía.")
        return

    origenes = defaultdict(list)
    ok = fallos = 0
    for f in pendientes:
        carpeta = BASE / "crudas" / f["id_producto"]
        destino = carpeta / f"{f['id_producto']}-{int(f['n_foto']):02d}"
        if any(destino.parent.glob(destino.name + ".*")):
            print(f"  ya estaba: {destino.name}")
            continue
        try:
            final, peso = descargar(f["url"].strip(), destino)
            origenes[f["id_producto"]].append({
                "archivo": final.name, "url": f["url"].strip(),
                "fuente": f.get("fuente", ""), "licencia": f.get("licencia", ""),
                "encuadre": f.get("encuadre", ""),
            })
            ok += 1
            print(f"  ok: {final.name} ({peso // 1024} KB)")
        except Exception as e:
            fallos += 1
            print(f"  FALLO: {f['id_producto']} foto {f['n_foto']} -> {e}")

    for pid, lista in origenes.items():
        ruta = BASE / "crudas" / pid / "origen.json"
        previo = json.loads(ruta.read_text(encoding="utf-8")) if ruta.exists() else []
        ruta.write_text(json.dumps(previo + lista, ensure_ascii=False, indent=2),
                        encoding="utf-8")

    print(f"\\n{ok} descargadas, {fallos} con error.")
    if fallos:
        print("Los errores suelen ser enlaces que exigen sesión o que ya caducaron.")


if __name__ == "__main__":
    main()
'''

LEEME = """FOTOS DE PRODUCTO — tres pasos

0. Si las marcas están en Open Icecat, corre antes icecat_local.py: deja
   urls-icecat.csv ya lleno y solo quedan por completar las marcas que no cubre.

1. Abre urls.csv y llena la columna `url` con el enlace directo de cada imagen
   (el que termina en .jpg, .png o .webp). Llena también `fuente` y `licencia`.

   De dónde deben salir, en este orden:
     a. Fotos propias del inventario.
     b. El paquete de imágenes del proveedor o distribuidor. Pídeselo: casi
        siempre lo tiene y es el único que viene con permiso para revender.
     c. El portal de partners o distribuidores de la marca, si la tienda está
        registrada como revendedor autorizado.

   Lo que NO sirve: las salas de prensa (Apple Newsroom, Samsung Mobile Press,
   centros de medios de Xiaomi). Sus condiciones autorizan uso editorial o
   personal, no publicar el producto en una tienda. Tampoco sirven las fotos
   tomadas de otras tiendas ni los bancos de imágenes sin licencia comprada.

2. Ejecuta el descargador:
       python descargar.py
   Deja las imágenes en crudas/<id-del-producto>/ y anota el origen de cada una.

3. Sube la carpeta crudas/ y normalízala al estándar de publicación:
       python3 normalizar_imagenes.py crudas/ --salida imagenes/

   Quedan en 2000x2000 con fondo blanco, más las variantes de 1200, 800 y 400 px.
"""


def cubiertos_por_icecat(ruta):
    """Ids que ya tienen enlaces traídos del catálogo abierto."""
    ruta = Path(ruta)
    if not ruta.exists():
        return set()
    with ruta.open(encoding="utf-8-sig") as f:
        return {fila["id_producto"] for fila in csv.DictReader(f) if fila.get("url")}


def pedido_proveedor(productos, fecha):
    """Texto para pedirle al proveedor las fotos que ningún catálogo cubre."""
    por_marca = {}
    for p in productos:
        por_marca.setdefault(p.get("marca") or "SIN MARCA IDENTIFICADA", []).append(p)

    cab = [
        "PEDIDO DE FOTOS DE PRODUCTO",
        f"Fecha: {fecha}",
        f"{len(productos)} productos",
        "",
        "Lo que necesitamos de cada uno:",
        "- 4 fotos: frontal, posterior, en ángulo y un detalle",
        "- fondo blanco, sin marcas de agua, sin textos ni precios encima",
        "- mínimo 1500 x 1500 píxeles, en JPG o PNG",
        "- si el equipo viene en varios colores, una frontal por color",
        "",
        "Si tienen el paquete de imágenes del fabricante, con eso basta.",
        "",
    ]
    cuerpo = []
    for marca in sorted(por_marca):
        cuerpo.append(f"{marca.upper()} ({len(por_marca[marca])})")
        for p in sorted(por_marca[marca], key=lambda x: x["titulo"]):
            colores = ", ".join(p.get("colores_oficiales") or p.get("colores_familia") or [])
            marca_p = p.get("marca") or ""
            nombre = p["titulo"]
            if marca_p and nombre.upper().startswith(marca_p.upper()):
                nombre = nombre[len(marca_p):].strip()
            linea = f"  - {nombre}" + (f" ({colores})" if colores else "")
            if not marca_p and p.get("texto_origen"):
                linea += f"   [en la lista aparece como: {p['texto_origen'].strip()[:50]}]"
            cuerpo.append(linea)
        cuerpo.append("")
    return "\n".join(cab + cuerpo)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("productos")
    ap.add_argument("--salida", default="fotos")
    ap.add_argument("--cubiertos", default="fotos/urls-icecat.csv",
                    help="csv que dejó icecat_local.py con lo ya resuelto")
    ap.add_argument("--solo-sin-fotos", action="store_true",
                    help="omite los productos que ya tienen carpeta en crudas/")
    args = ap.parse_args()

    datos = json.loads(Path(args.productos).read_text(encoding="utf-8"))
    salida = Path(args.salida)
    salida.mkdir(parents=True, exist_ok=True)
    ya_resueltos = cubiertos_por_icecat(args.cubiertos)

    pendientes, filas = [], []
    for p in datos["productos"]:
        if p["id"] in ya_resueltos:
            continue
        if args.solo_sin_fotos and (salida / "crudas" / p["id"]).is_dir():
            continue
        pendientes.append(p)
        for n, encuadre in enumerate(ENCUADRES[:FOTOS_POR_PRODUCTO], start=1):
            filas.append({
                "id_producto": p["id"], "titulo": p["titulo"], "n_foto": n,
                "encuadre": encuadre, "url": "", "fuente": "", "licencia": "",
            })

    ruta_csv = salida / "urls.csv"
    with ruta_csv.open("w", newline="", encoding="utf-8-sig") as f:
        w = csv.DictWriter(f, fieldnames=["id_producto", "titulo", "n_foto",
                                          "encuadre", "url", "fuente", "licencia"])
        w.writeheader()
        w.writerows(filas)

    (salida / "descargar.py").write_text(DESCARGADOR, encoding="utf-8")
    (salida / "LEEME.txt").write_text(LEEME, encoding="utf-8")

    fecha = datos.get("fecha_lista") or date.today().isoformat()
    ruta_pedido = salida / "pedido-al-proveedor.txt"
    ruta_pedido.write_text(pedido_proveedor(pendientes, fecha), encoding="utf-8")

    print(f"{len(ya_resueltos)} productos ya resueltos por el catálogo abierto")
    print(f"{len(pendientes)} quedan para pedirle al proveedor "
          f"({len(filas)} fotos en total)")
    print(f"-> {ruta_pedido}  (para enviarle tal cual)")
    print(f"-> {ruta_csv}     (por si manda enlaces en vez de archivos)")
    print(f"-> {salida / 'descargar.py'}")


if __name__ == "__main__":
    main()
