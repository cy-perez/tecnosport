#!/usr/bin/env python
"""Las diez piezas del carrusel de portada, recortadas desde las fotografías originales.

El resultado se versiona —`apps/web/public/imagenes/portada/hero/`— y los originales no, igual que
pasó con el ZIP del arte anterior: son 12 MB de fotografías de 5.000 a 8.000 px que no aporta nada
arrastrar en cada clonado. Lo que sí hace falta versionar es **el encuadre**, porque es una
decisión y se pierde: centrar el recorte ancho de calzado corta el tenis por arriba, y eso no se
adivina leyendo un .webp.

Cada línea declara dos anclas, una por eje, en tanto por uno del sobrante que se tira:

  0.0  pega el recorte al borde de arriba (o al de la izquierda)
  0.5  lo centra
  1.0  lo pega al borde de abajo (o al de la derecha)

Uso:
    python tools/recortar-hero.py                 # lee Imgs/ en la raíz del repositorio
    python tools/recortar-hero.py --origen RUTA   # desde otra carpeta
    python tools/recortar-hero.py --calidad 78    # otra calidad de WebP

Pide Pillow:  pip install pillow
"""

import argparse
import sys
from pathlib import Path

try:
    from PIL import Image
except ModuleNotFoundError:  # pragma: no cover - mensaje para quien lo corre, no lógica
    sys.exit("Falta Pillow. Instálalo con:  pip install pillow")

RAIZ = Path(__file__).resolve().parent.parent
DESTINO = RAIZ / "apps/web/public/imagenes/portada/hero"

# La proporción de la pieza ancha, la misma que tenía el arte de estudio al que sustituye: 1440x592.
# `alto-carrusel` la recorta todavía más en pantallas anchas, así que el encuadre tiene que aguantar
# perder otro tanto por arriba y por abajo.
PROPORCION_ANCHA = 1440 / 592

# La vertical es 4:5. La anterior era cuadrada y el texto iba debajo; ahora el texto va encima y un
# cuadrado no deja sitio para los dos. A 390 px de ventana son 487 px de alto.
PROPORCION_VERTICAL = 1000 / 1250

ANCHOS_ANCHA = (1440, 2880)
ANCHO_VERTICAL = 1000

# ancla_y se usa en el recorte ancho (lo que sobra es alto); ancla_x en el vertical (sobra ancho).
PIEZAS = {
    "ropa": {
        "archivo": "Ropa.jpg",
        "ancla_y": 0.42,
        "ancla_x": 0.55,
        # Sube un poco sobre el centro para no cortarle la cabeza a nadie: la pareja ocupa de y 0,21
        # a y 0,90 del original y el recorte ancho solo se queda con el 62 % del alto.
    },
    "calzado": {
        "archivo": "Zapatos.jpg",
        "ancla_y": 0.0,
        "ancla_x": 0.5,
        # Pegado arriba, y no es una preferencia: centrado corta el tenis por la mitad. El sujeto
        # vive en el tercio superior —de y 0,14 a y 0,42— y debajo solo hay muelle.
    },
    "bolsos": {
        "archivo": "Bolsos.jpg",
        "ancla_y": 0.56,
        "ancla_x": 0.5,
        # Un poco por debajo del centro: el bolso apoya en la mesa a y 0,89 y centrando se le corta
        # la base.
    },
    "tecnologia": {
        "archivo": "Tecnología.jpg",
        "ancla_y": 0.5,
        "ancla_x": 0.55,
    },
}


def recortar(imagen, proporcion, ancla_y, ancla_x):
    """El rectángulo más grande con esa proporción que cabe en la imagen, movido por las anclas."""
    ancho, alto = imagen.size
    alto_pedido = round(ancho / proporcion)
    if alto_pedido <= alto:
        y = round((alto - alto_pedido) * ancla_y)
        return imagen.crop((0, y, ancho, y + alto_pedido))
    ancho_pedido = round(alto * proporcion)
    x = round((ancho - ancho_pedido) * ancla_x)
    return imagen.crop((x, 0, x + ancho_pedido, alto))


def publicar(recorte, destino, ancho, calidad):
    """Baja el recorte a un ancho y lo escribe en WebP. **Nunca amplía**: ver ADR-0057."""
    alto = round(ancho * recorte.height / recorte.width)
    if ancho > recorte.width:
        print(f"  ! {destino.name}: la fuente solo da {recorte.width} px, no se amplía")
        return None
    pieza = recorte.resize((ancho, alto), Image.LANCZOS)
    destino.parent.mkdir(parents=True, exist_ok=True)
    pieza.save(destino, "WEBP", quality=calidad, method=6)
    kb = destino.stat().st_size / 1024
    print(f"  {destino.relative_to(RAIZ).as_posix()}  {ancho}x{alto}  {kb:.0f} KB")
    return kb


def main():
    analizador = argparse.ArgumentParser(description=__doc__)
    analizador.add_argument("--origen", default=str(RAIZ / "Imgs"))
    analizador.add_argument("--calidad", type=int, default=74)
    opciones = analizador.parse_args()

    origen = Path(opciones.origen)
    total = 0.0
    for clave, pieza in PIEZAS.items():
        ruta = origen / pieza["archivo"]
        if not ruta.exists():
            sys.exit(f"No está {ruta}")
        with Image.open(ruta) as imagen:
            imagen = imagen.convert("RGB")
            print(f"{clave}  <- {pieza['archivo']}  {imagen.width}x{imagen.height}")

            ancha = recortar(imagen, PROPORCION_ANCHA, pieza["ancla_y"], pieza["ancla_x"])
            for ancho in ANCHOS_ANCHA:
                kb = publicar(ancha, DESTINO / "ancho" / f"hero-{clave}-{ancho}.webp", ancho, opciones.calidad)
                total += kb or 0

            vertical = recortar(imagen, PROPORCION_VERTICAL, pieza["ancla_y"], pieza["ancla_x"])
            kb = publicar(
                vertical,
                DESTINO / "vertical" / f"vertical-{clave}-{ANCHO_VERTICAL}.webp",
                ANCHO_VERTICAL,
                opciones.calidad,
            )
            total += kb or 0

    print(f"\ncalidad {opciones.calidad}, {total:.0f} KB en total")


if __name__ == "__main__":
    main()
