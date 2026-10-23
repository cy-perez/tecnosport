"""Dónde deja cada paso de la skill lo que produce, dentro de `catalogo/`.

La carpeta no está versionada, así que su forma solo existe aquí y en los comandos de
`SKILL.md`. Va por subproceso (decisión del 15/10/2026): antes los diez archivos de una
corrida quedaban sueltos en la raíz y había que saber de memoria cuál era de qué paso.

    catalogo/
    ├── productos.json   el estado de la corrida: lo crea el parser y cada paso le escribe
    ├── 1-lista/         pasos 1 a 3: lista.txt, revision.md, cambios.md, decisiones.json (las
    │                    escribe la persona) y productos-del-parser.json (la copia de la que parte
    │                    la comparación cada vez que se corre)
    ├── 2-precios/       paso 4, el precio: precios-vtex.json y su .log, alkosto-vitrina.json,
    │                    precios-descartados.json
    ├── 3-redaccion/     paso 4, la ficha y la prosa: fichas-marca.json, prosa.json, icecat/
    ├── 4-entregables/   paso 5: fichas/<Modelo>/ y comparativo-<fecha>.xlsx
    └── 5-api/           paso 6b: lista-api.json y por-proponer.json, la entrega a los bloques 2 y 3

Las rutas son relativas a la raíz del repositorio, desde donde se corren los comandos.
`tools/material-catalogo.mjs` y `fotos-estudio-degradado/scripts/procesar_fichas.py` repiten
las de `4-entregables/` y `3-redaccion/`: si aquí cambian, allá también.
"""

from pathlib import Path

CATALOGO = Path("catalogo")
PRODUCTOS = CATALOGO / "productos.json"
LISTA = CATALOGO / "1-lista"
PRECIOS = CATALOGO / "2-precios"
REDACCION = CATALOGO / "3-redaccion"
ICECAT = REDACCION / "icecat"
ENTREGABLES = CATALOGO / "4-entregables"
FICHAS = ENTREGABLES / "fichas"
API = CATALOGO / "5-api"
