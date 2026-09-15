#!/usr/bin/env python3
"""
Deja en `crudas/` solo las fotos publicables de cada producto.

Se corre entre la descarga y el retoque. Quita dos cosas que el catálogo de un
fabricante trae mezcladas con las fotos buenas y que nadie quiere ver en una
ficha:

1. **Fotos donde el producto está cortado**, es decir, donde la silueta se sale
   del marco. Son tomas de detalle o de estilo de vida: se ven bien en la página
   del fabricante, pero en una ficha de producto el cliente quiere ver el equipo
   completo. Se detectan midiendo cuánto producto toca cada borde.

2. **Lo que no es una foto**: pictogramas, logos de característica y láminas de
   texto. `icecat_local.py` ya descarta los de Icecat por su tipo, pero el
   paquete que manda un proveedor viene sin metadatos, así que aquí se vuelven a
   filtrar por su aspecto: casi sin color y con muy poca tinta.

Lo descartado **no se borra**: se mueve a `descartadas/<id>/` junto con un
`motivos.json`. Una foto rechazada por error tiene que poder recuperarse, y el
motivo tiene que poder discutirse.

Uso:
    python filtrar_fotos.py crudas/                     # filtra en sitio
    python filtrar_fotos.py crudas/ --diagnostico       # solo mide, no mueve
    python filtrar_fotos.py crudas/ --maximo 4
"""

import argparse
import json
import shutil
from pathlib import Path

try:
    from PIL import Image
except ImportError:                                    # pragma: no cover
    raise SystemExit("falta Pillow: python -m pip install Pillow")

FOTOS_POR_PRODUCTO = 4
ANALISIS_PX = 420        # se mide sobre una copia chica: es igual de fiable y rápido
BANDA_BORDE = 2          # grosor en px de la franja de borde que se inspecciona
UMBRAL_FONDO = 20        # cuánto se tiene que apartar un píxel del fondo (0-255)

# Cuánto borde puede tocar el producto antes de considerarlo cortado. Calibrado
# contra fotos reales de Icecat: una foto bien encuadrada da 0,00 en los cuatro
# lados, y una recortada pasa de 0,20 por el lado donde se sale (medidas del
# Honor Watch Choice 2i: 0,00 la buena; 0,27 / 0,24 / 0,43 las tres cortadas).
# El margen entre los dos grupos es amplio, así que el umbral no es delicado.
TOCA_BORDE = 0.06

# Un pictograma, un logo de característica o una etiqueta de eficiencia
# energética no son fotos. Se reconocen por dos señales, no por el tamaño:
#
#  - El **modo** del archivo. Una foto llega en color verdadero (RGB o RGBA).
#    Los pictogramas de Icecat llegan en escala de grises con alfa (LA) y las
#    etiquetas de energía en paleta (P). Ninguna cámara ni banco de imágenes
#    entrega una foto de producto así.
#  - La **cantidad de tonos**, pero solo como piso contra imágenes degeneradas.
#    Este umbral tiene que ser bajo: una foto legítima de producto negro sobre
#    fondo blanco tiene muy pocos tonos —los Galaxy Buds Core dan 37 y son una
#    foto impecable de 1920x1280— y un umbral alto la tiraría a la basura. El
#    trabajo de reconocer pictogramas lo hace el modo, no esto.
MODOS_NO_FOTOGRAFICOS = {"P", "L", "LA", "1"}
MIN_TONOS = 12
MIN_TINTA = 0.02         # fracción mínima de píxeles que no son fondo
MIN_LADO = 160           # por debajo de esto no es una foto, es un icono

# La skill de retoque encuadra el producto a 1700 px. Por debajo de eso hay que
# ampliar y ningún ajuste inventa detalle, así que la foto se marca pero NO se
# descarta: en marcas como Honor o Samsung, Icecat a veces no tiene nada mejor
# y una foto pequeña es mejor que ninguna.
LADO_ESTUDIO = 1700


def cargar(ruta):
    im = Image.open(ruta)
    im.load()
    return im


def mascara_producto(im):
    """Devuelve (máscara booleana por filas, ancho, alto) de lo que no es fondo.

    Si la imagen trae transparencia, el alfa ya dice qué es producto. Si no, se
    toma como fondo el color dominante del marco de la imagen, que en una foto
    de catálogo es el papel blanco o el degradado del estudio.
    """
    ancho, alto = im.size
    escala = min(1.0, ANALISIS_PX / max(ancho, alto))
    chica = im.resize((max(1, int(ancho * escala)), max(1, int(alto * escala))))
    w, h = chica.size

    if chica.mode in ("RGBA", "LA", "PA"):
        alfa = chica.convert("RGBA").getchannel("A").load()
        return [[alfa[x, y] > 25 for x in range(w)] for y in range(h)], w, h

    rgb = chica.convert("RGB")
    px = rgb.load()
    marco = []
    for x in range(w):
        marco += [px[x, 0], px[x, h - 1]]
    for y in range(h):
        marco += [px[0, y], px[w - 1, y]]
    marco.sort()
    fondo = marco[len(marco) // 2]

    def lejos(c):
        return max(abs(c[0] - fondo[0]), abs(c[1] - fondo[1]), abs(c[2] - fondo[2])) > UMBRAL_FONDO

    return [[lejos(px[x, y]) for x in range(w)] for y in range(h)], w, h


def toca_los_bordes(mask, w, h):
    """Fracción de cada borde que el producto ocupa."""
    b = min(BANDA_BORDE, w // 2, h // 2) or 1
    arriba = sum(any(mask[y][x] for y in range(b)) for x in range(w)) / w
    abajo = sum(any(mask[h - 1 - y][x] for y in range(b)) for x in range(w)) / w
    izq = sum(any(mask[y][x] for x in range(b)) for y in range(h)) / h
    der = sum(any(mask[y][w - 1 - x] for x in range(b)) for y in range(h)) / h
    return {"arriba": arriba, "abajo": abajo, "izquierda": izq, "derecha": der}


def contar_tonos(im):
    """Tonos distintos en una miniatura, con 5 bits por canal."""
    chica = im.convert("RGB")
    chica.thumbnail((120, 120))
    datos = chica.tobytes()
    return len({(datos[i] >> 3, datos[i + 1] >> 3, datos[i + 2] >> 3)
                for i in range(0, len(datos), 3)})


def medir(ruta):
    """Mide una foto y devuelve su diagnóstico."""
    try:
        im = cargar(ruta)
    except Exception as e:
        return {"motivo": f"archivo ilegible ({type(e).__name__})", "bordes": {}, "aviso": None}

    ancho, alto = im.size
    modo = im.mode
    tonos = contar_tonos(im)
    mask, w, h = mascara_producto(im)
    tinta = sum(sum(fila) for fila in mask) / (w * h)
    bordes = toca_los_bordes(mask, w, h)
    peor = max(bordes, key=bordes.get)

    d = {"tamano": f"{ancho}x{alto}", "modo": modo, "tonos": tonos,
         "tinta": round(tinta, 3),
         "bordes": {k: round(v, 3) for k, v in bordes.items()},
         "motivo": None, "aviso": None}

    if max(ancho, alto) < MIN_LADO:
        d["motivo"] = f"icono de {ancho}x{alto}: no es una foto"
    elif modo in MODOS_NO_FOTOGRAFICOS:
        d["motivo"] = (f"no es una fotografía: viene en modo {modo} "
                       f"(pictograma, logo o etiqueta de energía)")
    elif tonos < MIN_TONOS:
        d["motivo"] = f"imagen casi plana, solo {tonos} tonos: no hay nada que mostrar"
    elif tinta < MIN_TINTA:
        d["motivo"] = "casi no hay producto en el cuadro"
    elif bordes[peor] > TOCA_BORDE:
        d["motivo"] = (f"el producto se sale del marco por {peor} "
                       f"({bordes[peor] * 100:.0f} % de ese borde)")
    elif max(ancho, alto) < LADO_ESTUDIO:
        d["aviso"] = (f"{ancho}x{alto}: por debajo de los {LADO_ESTUDIO} px que pide "
                      f"el estándar de estudio, habrá que ampliarla")
    return d


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("crudas", help="carpeta con una subcarpeta por producto")
    ap.add_argument("--maximo", type=int, default=FOTOS_POR_PRODUCTO,
                    help="cuántas fotos conservar por producto")
    ap.add_argument("--diagnostico", action="store_true",
                    help="mide y explica, pero no mueve nada")
    a = ap.parse_args()

    raiz = Path(a.crudas)
    if not raiz.is_dir():
        raise SystemExit(f"no existe {raiz}")
    descartes = raiz.parent / "descartadas"

    total = quitadas = chicas = 0
    incompletos = []
    for carpeta in sorted(p for p in raiz.iterdir() if p.is_dir()):
        fotos = sorted(p for p in carpeta.iterdir()
                       if p.suffix.lower() in (".jpg", ".jpeg", ".png", ".webp"))
        if not fotos:
            continue
        buenas, malas = [], []
        for foto in fotos:
            d = medir(foto)
            total += 1
            (malas if d["motivo"] else buenas).append((foto, d))

        sobran = buenas[a.maximo:]
        buenas = buenas[:a.maximo]

        if a.diagnostico:
            print(f"\n{carpeta.name}  ({len(buenas)} sirven de {len(fotos)})")
            for foto, d in [*buenas, *malas]:
                borde = max(d["bordes"].values()) if d["bordes"] else "?"
                estado = d["motivo"] or (f"ok — {d['aviso']}" if d.get("aviso") else "ok")
                print(f"   {foto.name:44s} {d.get('tamano',''):>10s} "
                      f"{d.get('modo',''):4s} tonos={d.get('tonos','?'):>4} "
                      f"borde={borde}  {estado}")
            continue

        if malas or sobran:
            destino = descartes / carpeta.name
            destino.mkdir(parents=True, exist_ok=True)
            motivos = {}
            for foto, d in malas:
                shutil.move(str(foto), destino / foto.name)
                motivos[foto.name] = d["motivo"]
                quitadas += 1
            for foto, d in sobran:
                shutil.move(str(foto), destino / foto.name)
                motivos[foto.name] = f"sobra: ya había {a.maximo} fotos buenas"
            previo = destino / "motivos.json"
            if previo.exists():
                motivos = {**json.loads(previo.read_text(encoding="utf-8")), **motivos}
            previo.write_text(json.dumps(motivos, ensure_ascii=False, indent=2),
                              encoding="utf-8")

        if len(buenas) < a.maximo:
            incompletos.append((carpeta.name, len(buenas)))
        chicas += sum(1 for _, d in buenas if d.get("aviso"))

    if a.diagnostico:
        return

    print(f"{total} fotos revisadas, {quitadas} descartadas -> {descartes}")
    if chicas:
        print(f"{chicas} de las que quedan están por debajo de {LADO_ESTUDIO} px: "
              f"el retoque las va a marcar por ampliación.")
    if incompletos:
        print(f"\n{len(incompletos)} productos quedaron con menos de {a.maximo} fotos; "
              f"pídeselas al proveedor:")
        for pid, n in incompletos:
            print(f"  {n} de {a.maximo}  {pid}")


if __name__ == "__main__":
    main()
