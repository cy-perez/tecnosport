#!/usr/bin/env python3
"""Comprueba que las tipografias del kit sirven para escribir este sitio.

Existe porque recortar una fuente al alfabeto latino puede perder un caracter sin
que falle nada: el navegador no avisa, dibuja el glifo de la fuente de respaldo —o
un rectangulo— y solo se ve mirando la pantalla con la palabra exacta delante. Un
recorte mal hecho es justo el tipo de defecto que pasa la revision y llega a
produccion.

Dos propiedades, y las dos se comprueban sobre el archivo que de verdad esta
commiteado, no sobre lo que el generador dice que hizo:

  A. Cada fuente tiene los caracteres que el sitio escribe. Latino basico, los
     acentos y la enye del espanol, los signos de apertura, las comillas y rayas
     tipograficas, y el simbolo de peso.

  B. Una fuente variable sigue siendo variable. `fuentes.css` declara rangos como
     `font-weight: 100 900`; si el recorte hubiera fijado un peso, el CSS estaria
     mintiendo y el navegador sintetizaria las negritas — mas feo y sin avisar.

Uso:
    python3 comprobar_fuentes.py fuentes/
"""
import sys
from pathlib import Path

# Lo que este sitio escribe de verdad. No es "el alfabeto latino" en abstracto: son
# los caracteres que aparecen en la interfaz, en los textos legales y en los nombres
# del catalogo.
OBLIGATORIOS = (
    "".join(chr(c) for c in range(0x20, 0x7F))  # ASCII imprimible: letras, digitos, $ % & /
    + "áéíóúÁÉÍÓÚ"                              # acentos
    + "üÜñÑ"                                    # dieresis y enye
    + "¿¡ªº°"                                   # signos de apertura y ordinales
    + "“”‘’«»"                                  # comillas tipograficas
    + "–—…"                                     # raya, semirraya y puntos suspensivos
    + "€·×"                                     # euro, punto medio, por
)


def caracteres_de(ruta):
    from fontTools.ttLib import TTFont
    fuente = TTFont(str(ruta), lazy=True)
    mapa = set()
    for tabla in fuente["cmap"].tables:
        mapa.update(tabla.cmap.keys())
    variable = "fvar" in fuente
    ejes = {}
    if variable:
        ejes = {e.axisTag: (int(e.minValue), int(e.maxValue)) for e in fuente["fvar"].axes}
    fuente.close()
    return mapa, variable, ejes


def main():
    # La salida lleva caracteres que cp1252 no sabe escribir, y en Windows eso no
    # degrada el mensaje: lo mata con UnicodeEncodeError.
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass
    destino = Path(sys.argv[1] if len(sys.argv) > 1 else "fuentes")
    archivos = sorted(destino.glob("*.woff2"))
    if not archivos:
        print("  ERROR: no hay .woff2 en '{}'.".format(destino))
        return 1

    try:
        from fontTools.ttLib import TTFont  # noqa: F401
    except ImportError:
        # No se calla ni pasa: quien corre esto sin fontTools tiene que saber que no
        # se comprobo nada, que no es lo mismo que "esta bien".
        print("  NO COMPROBADO: falta fontTools.  pip install fonttools brotli")
        return 2

    problemas = []
    for arch in archivos:
        mapa, variable, ejes = caracteres_de(arch)
        faltan = [c for c in OBLIGATORIOS if ord(c) not in mapa]
        if faltan:
            # Con el codigo de cada uno, y no solo el caracter: la consola de Windows
            # es cp1252 y una enye perdida se imprimia como un rombo negro, que es
            # exactamente igual a como se imprimen las otras treinta. Un aviso que no
            # se puede leer no dice cual falta.
            problemas.append("{}: faltan {} caracteres -> {}".format(
                arch.name, len(faltan),
                " ".join("U+{:04X} ({})".format(ord(c), c) for c in faltan[:12])))
        # El nombre del archivo es el contrato con `fuentes.css`, que declara el rango
        # de pesos para las que se llaman "-variable".
        if "-variable" in arch.stem and not variable:
            problemas.append("{}: se llama variable y ya no tiene eje de pesos".format(arch.name))
        if variable and "wght" not in ejes:
            problemas.append("{}: es variable pero perdio el eje wght".format(arch.name))
        print("  {:<30} {:>5} caracteres  {}".format(
            arch.name, len(mapa),
            "variable wght {}-{}".format(*ejes["wght"]) if "wght" in ejes else "peso fijo"))

    if problemas:
        print("\n  ERROR: el recorte dejo fuera algo que el sitio escribe:")
        for p in problemas:
            print("    - " + p)
        return 1

    print("\n{} fuente(s) con todo lo que el sitio escribe.".format(len(archivos)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
