#!/usr/bin/env python3
"""Deja las tipografias del kit, en TTF estatico, donde el servidor compone la portada de redes.

Uso (desde la raiz del repo):
    python packages/marca/generador/fuentes_servidor.py

Por que existe: la portada que acompana cada publicacion en Facebook e Instagram la dibuja la API
con Java2D (ADR-0102), y Java2D no lee woff2 ni sabe elegir un peso de una fuente variable. Asi que
de las mismas woff2 que sirve el sitio se sacan instancias fijas, con fontTools —lo mismo que ya
pide `npm run kit`, ninguna dependencia nueva—. Salir de las woff2 y no de otra descarga garantiza
que la portada escribe con las mismas letras que el sitio, recortadas al mismo alfabeto.

Tambien copia el logo en negativo que va sobre la banda grafito.

Si cambian las tipografias del kit, se vuelve a correr y se comitean los archivos que deja.
"""
import shutil
from pathlib import Path

from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

RAIZ = Path(__file__).resolve().parents[3]
MARCA = RAIZ / "packages" / "marca"
DESTINO = RAIZ / "apps" / "api" / "infrastructure" / "src" / "main" / "resources" / "difusion"

# Los pesos son los que declara tokens.json para cada familia: el display a 700 para el nombre
# del producto, el texto a 400 y 500 para la categoria y el pie de la banda.
INSTANCIAS = [
    ("archivo-variable.woff2", "archivo-700.ttf", {"wght": 700, "wdth": 100}),
    ("ibmplexsans-variable.woff2", "ibmplexsans-400.ttf", {"wght": 400, "wdth": 100}),
    ("ibmplexsans-variable.woff2", "ibmplexsans-500.ttf", {"wght": 500, "wdth": 100}),
]

LICENCIAS = ["OFL-archivo.txt", "OFL-ibmplexsans.txt"]


def main() -> None:
    fuentes = DESTINO / "fuentes"
    fuentes.mkdir(parents=True, exist_ok=True)
    for origen, destino, ejes in INSTANCIAS:
        variable = TTFont(MARCA / "fuentes" / origen)
        estatica = instantiateVariableFont(variable, ejes)
        # woff2 -> TTF plano: Java2D solo lee sfnt sin comprimir.
        estatica.flavor = None
        estatica.save(fuentes / destino)
        print(f"{destino}: {(fuentes / destino).stat().st_size // 1024} KB")
    # La OFL obliga a distribuir la licencia junto a los archivos.
    for licencia in LICENCIAS:
        shutil.copyfile(MARCA / "fuentes" / licencia, fuentes / licencia)
    shutil.copyfile(
        MARCA / "dist" / "raster" / "logo-mono-negativo-800w.png",
        DESTINO / "logo-mono-negativo.png",
    )
    print("logo-mono-negativo.png copiado")


if __name__ == "__main__":
    main()
