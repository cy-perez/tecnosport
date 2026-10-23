#!/usr/bin/env python3
"""
Procesa las fotos de las carpetas de modelo de `catalogo/4-entregables/fichas/`.

Decisión del 08/10/2026: las fotos de cada modelo viven en su carpeta, y cada
foto retocada queda junto a su original, **con el mismo nombre** (la extensión
puede cambiar: la maestra siempre es JPEG):

    fichas/Samsung Galaxy S25 Ultra/
    ├── Fotos originales/samsung-galaxy-s25-ultra_1.png
    ├── Fotos procesadas/samsung-galaxy-s25-ultra_1.jpg        la maestra
    │                    web/1200/samsung-galaxy-s25-ultra_1.avif   el carrusel de la ficha
    │                    web/1200/samsung-galaxy-s25-ultra_1.jpg    la vista previa de WhatsApp
    │                    web/480/samsung-galaxy-s25-ultra_1.avif    la miniatura y la tarjeta
    └── estado-fotos.json                                      con --tanda: apto, en espera o fuera

Uso:
    python3 procesar_fichas.py catalogo/4-entregables/fichas --tanda catalogo/5-api/por-proponer.json
    python3 procesar_fichas.py catalogo/4-entregables/fichas --tanda catalogo/5-api/por-proponer.json --devolver
    python3 procesar_fichas.py catalogo/4-entregables/fichas --tanda catalogo/5-api/por-proponer.json \\
        --sacar <id del modelo> --motivo "sin material oficial"
    python3 procesar_fichas.py catalogo/4-entregables/fichas --tanda catalogo/5-api/por-proponer.json \\
        --kit <id del modelo> --motivo "consola con los Joy-Con sueltos"

No reimplementa nada del estilo: arma un árbol de trabajo en `<fichas>/_estudio/`
—`crudas/<id del modelo>/` con copias de las originales—, corre `procesar.py` sobre
él con `--por-producto --variantes` y devuelve cada resultado a la carpeta de su
modelo. Las hojas de revisión y el reporte quedan en `_estudio/salida/`.

**La tanda es el bloque 2 de la ingesta de tecnología** (ADR-0079). Con `--tanda`
solo se procesan los modelos de `por-proponer.json` —los que el sitio no vende—, y
cada uno necesita `MINIMO_LISTA` fotos LISTA en un lienzo de por lo menos
`LIENZO_MINIMO` px. Una REVISAR no cuenta, salvo que una persona la apruebe a ojo
tras mirarla al 100 % (`marcar.py --aprobar`, decisión del 22/10/2026). El modelo
que no llega queda **en espera**, con la razón de cada foto que falló, y la tanda
no se da por terminada hasta que todos pasen o la persona saque alguno a mano con
`--sacar`. Para acortar esa espera, el informe deja los comandos de revisión
listos y los mosaicos al 100 % de las REVISAR que detienen la tanda se generan
de una vez (`preparar_revision`): tres de los cuatro modelos en espera del
15/10/2026 esperaban por una sola foto que, mirada a tamaño real, estaba bien.

Para no gastar retoques en vano, y para que el veredicto sea cierto:

- **Antes de procesar** se cuentan las originales que pueden salir LISTA: una foto
  cuyo lado mayor no alcanza `LIENZO_MINIMO × ocupación / ampliación_revisar` no
  puede, porque el producto nunca mide más que la foto. Si no hay cuatro, el
  modelo espera sin procesarse.
- **El lienzo lo elige la compuerta**, no la mejor foto: `procesar.py` recibe
  `--lienzo-minimo LIENZO_MINIMO --listas-minimas MINIMO_LISTA` y toma el mayor
  escalón donde caben cuatro LISTA (`salidas.elegir_lienzo_tanda`). Hasta el
  16/10/2026 lo fijaba la mejor foto, y una sola foto grande dejaba a las demás en
  REVISAR.
- **Un modelo que cambió se reprocesa entero** (`--solo` con todas sus fotos), no
  solo la foto nueva: el lienzo depende de todas las fotos del modelo, y una foto que
  se procesara sola elegiría el suyo.
- **Un modelo ya decidido no se vuelve a procesar** mientras sus originales no
  cambien: `estado-fotos.json` guarda la huella del contenido de la carpeta. Si la
  persona dice que dejó fotos nuevas y la huella es la misma, la salida lo dice.
  Pero el veredicto **sí se recalcula** contra el reporte en cada corrida: una foto
  marcada a ojo como REPETIR después de la tanda deja de contar.
- **Cada foto se empareja con su registro del reporte por archivo y por huella**, no
  por nombre: un registro de una foto que ya se reemplazó no cuenta.
- **Un celular o una tablet no tiene huecos pasantes** (decisión del 20/10/2026): la
  categoría de la ficha (`CATEGORÍA:`) decide qué modelos van con `--sin-huecos`, y en
  ellos todo lo que el recorte encierre se devuelve al producto. Así la pantalla sale
  tal cual la foto original, aunque su fondo de pantalla sea claro.
- **Una carpeta con fotos que no está en la tanda se avisa**: la tanda solo procesa
  `por-proponer.json`, y un modelo que falte ahí se quedaba sin fotos sin que nada lo dijera.
- **Un modelo `sinFicha` no bloquea la tanda** (22/10/2026): la API lo espera pero la lista no lo
  trae —el bloque 1 no lo exportó, casi siempre por falta de descripción—, así que no puede salir
  borrador de él. Sus fotos se procesan igual, para no perder el trabajo, y el informe lo dice.
- **Un kit lo declara la persona** (`--kit`, decisión del 18/10/2026): en un modelo que
  se vende con piezas sueltas —la consola con sus Joy-Con, la tablet con teclado y
  mouse— una foto cuyo único reparo es `varias_piezas` cuenta como LISTA. En los demás
  modelos sigue sin contar: tres piezas o más pueden ser dos productos distintos o la
  gama de colores, y eso no lo decide el script.
- **En audífonos, el par con su estuche no es una anomalía** (22/10/2026): hasta
  `PIEZAS_DE_UN_PAR` piezas cuentan como LISTA si `varias_piezas` es su único reparo, porque
  dos auriculares y el estuche disparaban el motivo en casi toda foto buena de unos TWS. Con
  cinco o más piezas sigue decidiendo la persona.

Sale con 0 si la tanda está completa y con 3 si algún modelo espera fotos o el
retoque sigue en segundo plano.

Las fotos REPETIR no se devuelven: no hay archivo que subir. La parte de preparar,
devolver y decidir usa solo la biblioteca estándar —Pillow, si está, para medir las
originales—, para que sus pruebas corran donde no está OpenCV.
"""

import argparse
import hashlib
import json
import math
import re
import shutil
import subprocess
import sys
import unicodedata
from datetime import datetime, timezone
from pathlib import Path

ORIGINALES = "Fotos originales"   # los mismos nombres que listas-de-proveedor/scripts/fichas.py
PROCESADAS = "Fotos procesadas"
WEB = "web"
RETIRADAS = "_retiradas"          # en «Fotos procesadas»: lo que se retiró porque la foto pasó a REPETIR
TRABAJO = "_estudio"
ESTADO = "estado-fotos.json"
SACADOS = "sacados.json"          # en _estudio/: los modelos sacados que no tienen carpeta
KITS = "kits.json"                # en _estudio/: los modelos que se venden con piezas sueltas
SIN_HUECOS = "sin-huecos.json"    # en _estudio/: los modelos que la persona declaró sin huecos pasantes
# El único motivo REVISAR que un kit perdona: las piezas sueltas son el producto.
MOTIVO_DE_KIT = "varias_piezas"
EXT_FOTO = {".jpg", ".jpeg", ".jfif", ".png", ".webp", ".heic", ".heif", ".avif"}  # = EXTENSIONES de procesar.py
CONFIG = Path(__file__).resolve().parent.parent / "config.json"
sys.path.insert(0, str(Path(__file__).resolve().parent))
import salidas  # noqa: E402  (solo biblioteca estándar)

# Decisión del negocio del 10/10/2026 (ADR-0079): cuatro fotos LISTA por modelo.
MINIMO_LISTA = 4
# Y en un lienzo de por lo menos esto: 1200 es la mayor variante que sirve el sitio
# (tools/subida-de-imagenes.mjs); por debajo, la ficha se ve con menos resolución de la que
# pide. No va en config.json porque no cambia cómo se retoca, y allá entraría en la huella de
# la configuración y obligaría a reprocesar el catálogo entero.
LIENZO_MINIMO = 1200

# Decisión del 20/10/2026: estas categorías de la ficha no tienen huecos pasantes —lo que el
# bisel encierra es la pantalla—, y procesar.py las recibe con --sin-huecos. Un reloj, unos
# audífonos o una consola pueden tenerlos (la correa cerrada, el arco): ahí decide el color.
CATEGORIAS_SIN_HUECOS = {"celulares", "tablets"}

# Decisión del 22/10/2026: en audífonos, dos auriculares y su estuche —a veces con la tapa— son 3
# o 4 piezas, y casi toda foto buena de unos TWS disparaba `varias_piezas` (11 fotos de la tanda
# del 15/10). Hasta PIEZAS_DE_UN_PAR piezas cuentan como LISTA si ese es su único reparo; con 5 o
# más sigue decidiendo la persona (un kit declarado, que no tiene tope, o REPETIR si son dos
# productos). Las demás categorías no cambian.
CATEGORIAS_DE_PARES = {"audifonos"}
PIEZAS_DE_UN_PAR = 4

APTO, EN_ESPERA, FUERA = "apto", "en_espera", "fuera_de_tanda"
SALIDA_EN_ESPERA = 3
# procesar.py sale con 2 cuando no queda ninguna foto por procesar: no es un error.
SALIDAS_DE_PROCESAR_ACEPTADAS = (0, 2)
SIN_PROCESAR = "SIN PROCESAR"


def slug(texto: str) -> str:
    """El mismo de procesar.py, que nombra con él cada salida. Se copia porque
    procesar.py importa OpenCV al cargarse; si allá cambia, aquí también."""
    t = unicodedata.normalize("NFKD", texto).encode("ascii", "ignore").decode()
    t = re.sub(r"[^A-Za-z0-9]+", "-", t).strip("-").lower()
    return t or "foto"


def orden_natural(nombre: str):
    """`_10` después de `_2`, como `fotosDeCarpeta` en tools/material-catalogo.mjs: el orden de
    las LISTA es el orden en que se publican, y la primera es la principal."""
    return [int(p) if p.isdigit() else p.lower() for p in re.split(r"(\d+)", nombre)]


def huella_archivo(ruta: Path) -> str:
    """La misma de imagen.huella_archivo, que procesar.py guarda como `huella_origen`."""
    h = hashlib.sha1()
    with open(ruta, "rb") as fh:
        for bloque in iter(lambda: fh.read(1 << 20), b""):
            h.update(bloque)
    return h.hexdigest()


def id_de_carpeta(carpeta: Path):
    for f in carpeta.glob("*-ficha.txt"):
        return f.name[: -len("-ficha.txt")]
    return slug(carpeta.name)


def categoria_de(carpeta: Path):
    """La categoría que escribió listas-de-proveedor en la ficha («CATEGORÍA: celulares»), o None."""
    for f in carpeta.glob("*-ficha.txt"):
        for linea in f.read_text(encoding="utf-8", errors="replace").splitlines():
            clave, sep, valor = linea.partition(":")
            if sep and clave.strip().upper() in ("CATEGORÍA", "CATEGORIA"):
                return valor.strip().lower() or None
        return None
    return None


def sin_huecos(raiz: Path, solo=None) -> list:
    """Los ids de los modelos sin huecos pasantes, para `--sin-huecos`: los de una categoría que no
    los tiene y los que la persona declaró uno a uno (`_estudio/sin-huecos.json`)."""
    declarados = leer_sin_huecos(raiz)
    return [mid for mid, carpeta in modelos(raiz, solo).items()
            if categoria_de(carpeta) in CATEGORIAS_SIN_HUECOS or mid in declarados]


def leer_sin_huecos(raiz: Path) -> dict:
    ruta = Path(raiz) / TRABAJO / SIN_HUECOS
    return json.loads(ruta.read_text(encoding="utf-8")) if ruta.is_file() else {}


def declarar_sin_huecos(raiz: Path, mid: str, motivo: str, quitar: bool = False) -> dict:
    """Anota (o quita) un modelo sin huecos pasantes en `_estudio/sin-huecos.json` (decisión del
    20/10/2026). La categoría no basta en todos los casos: unos audífonos de diadema tienen huecos
    de verdad, pero el estuche transparente de los Redmi Buds 6 Active no, y su interior casi blanco
    pasaba por fondo y salía mutilado. Lo declara la persona, con su motivo, y no el script.

    Cambia cómo se retoca, así que el modelo queda «por procesar»: la siguiente tanda lo rehace
    entero aunque sus originales no hayan cambiado."""
    carpeta = modelos(raiz, {mid}).get(mid)
    if carpeta is None:
        raise ValueError(f"{mid} no tiene carpeta con «{ORIGINALES}» en {raiz}.")
    declarados = leer_sin_huecos(raiz)
    if quitar:
        declarados.pop(mid, None)
    else:
        if not motivo or not motivo.strip():
            raise ValueError("Declarar un modelo sin huecos pide el motivo: por qué no tiene huecos pasantes.")
        declarados[mid] = {"motivo": motivo.strip(), "fecha": ahora()}
    ruta = Path(raiz) / TRABAJO / SIN_HUECOS
    ruta.parent.mkdir(parents=True, exist_ok=True)
    ruta.write_text(json.dumps(declarados, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="")
    previo = leer_estado(carpeta)
    if previo is not None:
        escribir_estado(carpeta, dict(previo, motivo="por procesar"))
    return declarados


def fuera_de_la_tanda(raiz: Path, ids) -> list:
    """Las carpetas con fotos originales que ninguna tanda ha procesado y cuyo modelo no está en
    esta: no se procesan, y hay que decirlo. El Redmi Note 17 Pro y el Pro Max se quedaron así sin
    una sola foto procesada. Las de tandas anteriores ya tienen su `estado-fotos.json` y no se
    listan: si no, cada tanda nueva repetiría todo el catálogo."""
    return [carpeta.name for mid, carpeta in modelos(raiz).items()
            if mid not in set(ids) and originales(carpeta) and not (carpeta / ESTADO).is_file()]


def modelos(raiz: Path, solo=None) -> dict:
    """{id del modelo: carpeta} de las carpetas que tienen «Fotos originales»; con `solo`, de esos ids."""
    salida = {}
    for carpeta in sorted(Path(raiz).iterdir()):
        if carpeta.is_dir() and not carpeta.name.startswith("_") and (carpeta / ORIGINALES).is_dir():
            mid = id_de_carpeta(carpeta)
            if solo is None or mid in solo:
                salida[mid] = carpeta
    return salida


def originales(carpeta: Path) -> list:
    if not (carpeta / ORIGINALES).is_dir():
        return []
    return sorted((f for f in (carpeta / ORIGINALES).iterdir() if f.is_file() and f.suffix.lower() in EXT_FOTO),
                  key=lambda f: orden_natural(f.name))


def unicas(carpeta: Path) -> tuple:
    """(originales que se procesan, duplicadas). Dos originales cuyo nombre da el mismo slug
    —«x_1.jpg» y «x-1.png»— saldrían con el mismo nombre: la primera se procesa, la otra no, y
    no cuenta para las cuatro."""
    vistas, buenas, duplicadas = {}, [], []
    for f in originales(carpeta):
        clave = slug(f.stem)
        if clave in vistas:
            duplicadas.append((f, vistas[clave]))
        else:
            vistas[clave] = f.name
            buenas.append(f)
    return buenas, duplicadas


def preparar(raiz: Path, solo=None) -> tuple:
    """Deja `_estudio/crudas/<id>/` igual que las originales de cada modelo. Devuelve (copiadas, errores).

    Borra de `crudas/` lo que ya no está en las originales: una copia vieja de una foto
    reemplazada se procesaría otra vez, y procesar.py desempataría la nueva con «-2».
    """
    crudas = Path(raiz) / TRABAJO / "crudas"
    copiadas, errores = 0, []
    for mid, carpeta in modelos(raiz, solo).items():
        buenas, duplicadas = unicas(carpeta)
        for dup, primera in duplicadas:
            errores.append(f"{carpeta.name}: «{primera}» y «{dup.name}» quedarían con el mismo nombre; "
                           f"renombra una")
        destino = crudas / mid
        nombres = {f.name for f in buenas}
        if destino.is_dir():
            for vieja in destino.iterdir():
                if vieja.is_file() and vieja.name not in nombres:
                    vieja.unlink()
        for f in buenas:
            copia = destino / f.name
            if (copia.is_file() and copia.stat().st_size == f.stat().st_size
                    and copia.stat().st_mtime_ns == f.stat().st_mtime_ns):
                continue
            destino.mkdir(parents=True, exist_ok=True)
            shutil.copy2(f, copia)
            copiadas += 1
    return copiadas, errores


# Pistas de kit desde la ficha (22/10/2026): el Switch 2, el PartyBox Encore 2 y la Fly Go Pad «con
# teclado y mouse» esperaron una corrida entera por piezas sueltas que la ficha ya anunciaba. Es una
# pista para la persona, no una declaración: tres piezas también pueden ser dos productos.
RE_PISTA_DE_KIT = re.compile(r"\bcon (?:teclado|mouse|rat[oó]n|l[aá]piz|joy-?con|micr[oó]fonos?|controles?)\b"
                             r"|\bbundle\b|\bkit\b|\bcombo\b|\s\+\s", re.IGNORECASE)
CATEGORIAS_DE_KIT = {"consolas"}


def dato_de_ficha(carpeta: Path, *claves):
    """El valor de una línea `CLAVE: valor` de la ficha que escribió listas-de-proveedor, o None."""
    buscadas = {c.upper() for c in claves}
    for f in carpeta.glob("*-ficha.txt"):
        for linea in f.read_text(encoding="utf-8", errors="replace").splitlines():
            clave, sep, valor = linea.partition(":")
            if sep and clave.strip().upper() in buscadas:
                return valor.strip() or None
        return None
    return None


def pista_de_kit(carpeta: Path):
    """Por qué el modelo parece venderse con piezas sueltas, o None."""
    if (categoria_de(carpeta) or "") in CATEGORIAS_DE_KIT:
        return "es una consola: su foto de catálogo suele mostrar los controles sueltos"
    titulo = dato_de_ficha(carpeta, "MODELO") or carpeta.name
    m = RE_PISTA_DE_KIT.search(titulo)
    return f"el título dice «{m.group(0).strip()}»" if m else None


def pistas_de_kit(raiz: Path, ids, kits=None) -> list:
    """[(id, razón)] de los modelos que parecen un kit y no están declarados como tal."""
    kits = kits or {}
    return [(mid, razon) for mid, carpeta in modelos(raiz, set(ids)).items()
            if mid not in kits and (razon := pista_de_kit(carpeta))]


def leer_reporte(raiz: Path) -> dict:
    ruta = Path(raiz) / TRABAJO / "salida" / "reporte.json"
    return json.loads(ruta.read_text(encoding="utf-8")) if ruta.is_file() else {"fotos": []}


def registro_de(original: Path, grupo: str, registros: list):
    """El registro del reporte que es de esta original: mismo modelo, mismo archivo y, si el
    registro trae huella, el mismo contenido. Sin `archivo` (reportes viejos), por nombre."""
    for foto in registros:
        if foto.get("grupo") != grupo:
            continue
        if foto.get("archivo"):
            if foto["archivo"] != original.name:
                continue
        elif foto.get("nombre") != slug(original.stem):
            continue
        if foto.get("huella_origen") and foto["huella_origen"] != huella_archivo(original):
            continue
        return foto
    return None


def con_retoque_anterior(raiz: Path, ids, registros: list) -> list:
    """Las originales de esos modelos cuyo registro se procesó con un retoque anterior.

    Se parte de las originales que hay en la carpeta, no de los registros del reporte: uno de una
    foto renombrada o borrada —el `XXiaomi Redmi Pad 2 11″ 4G_6.webp` que se corrigió a `Xiaomi …`—
    se quedaba en el reporte con la versión vieja, y el aviso lo contaba como una foto por rehacer
    aunque no había nada que rehacer (21/10/2026)."""
    viejas = []
    for mid, carpeta in modelos(raiz, set(ids)).items():
        buenas, _ = unicas(carpeta)
        for original in buenas:
            foto = registro_de(original, mid, registros)
            if foto is not None and foto.get("salidas") and foto.get("version_retoque") != salidas.VERSION_RETOQUE:
                viejas.append(f"{carpeta.name}/{original.name}")
    return viejas


def retirar(procesadas: Path, stem: str) -> int:
    """Mueve a `_retiradas/` la maestra y los anchos web de una foto que ya no se publica.

    Una foto que pasa a REPETIR no se devuelve, y su versión anterior se quedaba en «Fotos
    procesadas»: tools/material-catalogo.mjs sube todo `.jpg` de esa carpeta, así que el Redmi Pad 2
    _5 malformado del 19/10/2026 seguía listo para subirse. Se mueve y no se borra: es de la persona
    decidir si lo quiere."""
    movidos = 0
    candidatos = [procesadas / f"{stem}.jpg"]
    if (procesadas / WEB).is_dir():
        candidatos += list((procesadas / WEB).glob(f"*/{stem}.*"))
    for archivo in candidatos:
        if archivo.is_file():
            destino = procesadas / RETIRADAS / archivo.relative_to(procesadas)
            destino.parent.mkdir(parents=True, exist_ok=True)
            archivo.replace(destino)
            movidos += 1
    return movidos


def devolver(raiz: Path) -> dict:
    """Copia cada resultado del reporte a la carpeta de su modelo, con el nombre de su original."""
    raiz = Path(raiz)
    salida = raiz / TRABAJO / "salida"
    registros = leer_reporte(raiz).get("fotos", [])
    resumen = {"devueltas": 0, "repetir": [], "revisar": [], "sin_original": [], "retiradas": []}
    usados = set()
    for mid, carpeta in modelos(raiz).items():
        buenas, _ = unicas(carpeta)
        for original in buenas:
            foto = registro_de(original, mid, registros)
            if foto is None:
                continue
            usados.add(id(foto))
            etiqueta = f"{carpeta.name}/{original.name}"
            estado = foto.get("estado")
            sal = foto.get("salidas") or {}
            if estado == "REPETIR" or not sal.get("maestra"):
                resumen["repetir"].append(etiqueta)
                if retirar(carpeta / PROCESADAS, original.stem):
                    resumen["retiradas"].append(etiqueta)
                continue
            if estado == "REVISAR":
                resumen["revisar"].append(etiqueta)
            procesadas = carpeta / PROCESADAS
            procesadas.mkdir(exist_ok=True)
            shutil.copy2(salida / sal["maestra"]["ruta"], procesadas / f"{original.stem}.jpg")
            # Los anchos de una versión anterior de esta foto se van: un lienzo más pequeño deja
            # anchos que el nuevo no genera, y se subirían como si fueran de la foto de hoy.
            if (procesadas / WEB).is_dir():
                for vieja in (procesadas / WEB).glob(f"*/{original.stem}.*"):
                    vieja.unlink()
            for w in sal.get("web", []):
                ruta = Path(w["ruta"])
                destino = procesadas / WEB / ruta.parent.name / f"{original.stem}{ruta.suffix}"
                destino.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(salida / ruta, destino)
            resumen["devueltas"] += 1
    for foto in registros:
        if id(foto) not in usados:
            resumen["sin_original"].append(f"{foto.get('grupo')}/{foto.get('nombre')}")
    return resumen


# --------------------------------------------------------------------------
# La tanda del bloque 2 (ADR-0079)
# --------------------------------------------------------------------------

def cargar_config() -> dict:
    return {**json.loads(CONFIG.read_text(encoding="utf-8")), "lienzo_minimo": LIENZO_MINIMO}


def lado_minimo(cfg: dict) -> int:
    """El lado mayor por debajo del cual una foto no puede salir LISTA en `lienzo_minimo`.

    El producto mide como mucho lo que mide la foto, y `procesar.py` marca REVISAR
    desde `ampliacion_revisar`: si la foto entera ampliada hasta ahí no llena el
    producto en el lienzo mínimo, ningún recorte lo hará."""
    return math.ceil(int(cfg["lienzo_minimo"]) * float(cfg["ocupacion"]) / float(cfg["ampliacion_revisar"]))


def medir(ruta: Path):
    """(ancho, alto), o None si no se puede saber: sin Pillow, o un formato que no abre.
    Lo que no se mide no se descarta: lo decide el retoque."""
    try:
        from PIL import Image
    except ImportError:
        return None
    try:
        with Image.open(ruta) as im:
            return im.size
    except Exception:
        return None


def revisar_antes(carpeta: Path, cfg: dict, medidor=medir) -> dict:
    """Cuántas originales pueden salir LISTA, y por qué no las demás, sin procesar nada."""
    minimo = lado_minimo(cfg)
    buenas, duplicadas = unicas(carpeta)
    viables = []
    fallidas = [{"foto": dup.name, "estado": "DUPLICADA",
                 "motivo": f"sale con el mismo nombre que «{primera}»: renómbrala para que cuente"}
                for dup, primera in duplicadas]
    for f in buenas:
        medida = medidor(f)
        if medida is not None and max(medida) < minimo:
            fallidas.append({"foto": f.name, "estado": "PEQUEÑA",
                             "motivo": f"mide {max(medida)} px de lado mayor y necesita por lo menos {minimo} px "
                                       f"para salir LISTA en un lienzo de {cfg['lienzo_minimo']} px"})
        else:
            viables.append(f.name)
    return {"viables": viables, "fallidas": fallidas}


def huella_de_originales(carpeta: Path) -> str:
    """Cambia si se añade, quita, renombra o reemplaza una original, aunque conserve tamaño y fecha."""
    h = hashlib.sha256()
    for f in originales(carpeta):
        h.update(f"{f.name}|{huella_archivo(f)}\n".encode())
    return h.hexdigest()


def leer_estado(carpeta: Path):
    ruta = carpeta / ESTADO
    return json.loads(ruta.read_text(encoding="utf-8")) if ruta.is_file() else None


def escribir_estado(carpeta: Path, estado: dict) -> None:
    (carpeta / ESTADO).write_text(json.dumps(estado, ensure_ascii=False, indent=2) + "\n",
                                  encoding="utf-8", newline="")


def leer_sacados(raiz: Path) -> dict:
    ruta = Path(raiz) / TRABAJO / SACADOS
    return json.loads(ruta.read_text(encoding="utf-8")) if ruta.is_file() else {}


def leer_kits(raiz: Path) -> dict:
    ruta = Path(raiz) / TRABAJO / KITS
    return json.loads(ruta.read_text(encoding="utf-8")) if ruta.is_file() else {}


def declarar_kit(raiz: Path, mid: str, motivo: str, quitar: bool = False) -> dict:
    """Anota (o quita) un modelo como kit en `_estudio/kits.json`. Vale para todas las tandas:
    un kit no deja de serlo con la lista siguiente."""
    kits = leer_kits(raiz)
    if quitar:
        kits.pop(mid, None)
    else:
        if not motivo or not motivo.strip():
            raise ValueError("Declarar un kit pide el motivo: qué piezas sueltas trae el producto.")
        kits[mid] = {"motivo": motivo.strip(), "fecha": ahora()}
    ruta = Path(raiz) / TRABAJO / KITS
    ruta.parent.mkdir(parents=True, exist_ok=True)
    ruta.write_text(json.dumps(kits, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="")
    return kits


def solo_piezas_sueltas(foto: dict) -> bool:
    """Si la foto salió REVISAR únicamente por `varias_piezas`, sin marcas a ojo ni otro motivo."""
    if foto.get("estado") != "REVISAR" or foto.get("marcas"):
        return False
    codigos = {m.get("codigo") for m in foto.get("motivos_auto", [])
               if isinstance(m, dict) and m.get("estado") in ("REVISAR", "REPETIR")}
    return codigos == {MOTIVO_DE_KIT}


def ahora() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def texto_de_motivo(m) -> str:
    """El reporte guarda los motivos finales como texto (reporte.estado_final) y los automáticos
    como diccionarios: se aceptan los dos."""
    if isinstance(m, str):
        return m
    return (m or {}).get("texto") or (m or {}).get("codigo", "")


def evaluar(mid: str, carpeta: Path, reporte: dict, cfg: dict, kit: bool = False,
            par: bool = False) -> dict:
    """Las fotos LISTA del modelo según el reporte, y la razón de cada una que no lo es.

    En un kit, una REVISAR cuyo único motivo son las piezas sueltas cuenta como LISTA. Y desde el
    22/10/2026 cuenta también la REVISAR que una persona aprobó a ojo con `marcar.py --aprobar`:
    hasta entonces no contaba, y cuatro modelos de la lista del 15/10 esperaban fotos nuevas con
    tomas que, miradas a tamaño real, estaban bien. Cuáles contaron así queda en
    `aprobadas_a_ojo`, para que se sepa qué pasó la compuerta por una persona y no por el script.

    El orden de `listas` es el orden en que el borrador sube las fotos, y **la primera es la
    principal**: sin decir nada manda el orden natural de los nombres —la `_1` que se descargó
    primero—, y una foto marcada con `marcar.py --principal` pasa al frente (22/10/2026)."""
    registros = reporte.get("fotos", [])
    buenas, _ = unicas(carpeta)
    listas, fallidas, aprobadas = [], [], []
    principal = None
    for original in buenas:
        foto = registro_de(original, mid, registros)
        if foto is None:
            fallidas.append({"foto": original.name, "estado": SIN_PROCESAR, "motivo": motivo_sin_registro(original)})
            continue
        estado = foto.get("estado")
        # En un kit las piezas sueltas son el producto, sin tope; en audífonos (`par`), hasta
        # PIEZAS_DE_UN_PAR piezas son el par con su estuche y también se perdonan (22/10/2026).
        piezas = int((foto.get("recorte") or {}).get("piezas") or 0)
        perdona_piezas = kit or (par and 0 < piezas <= PIEZAS_DE_UN_PAR)
        if perdona_piezas and solo_piezas_sueltas(foto):
            estado = "LISTA"
        lienzo = int((foto.get("lienzo") or [0])[0])
        motivos = "; ".join(texto_de_motivo(m) for m in foto.get("motivos", []))
        # Una aprobación a ojo solo pesa si había algo que aprobar: un REVISAR automático. En un
        # kit, o en un par con su estuche, las piezas sueltas no son algo que aprobar.
        reparos = [m for m in foto.get("motivos_auto", [])
                   if isinstance(m, dict) and m.get("estado") == "REVISAR"
                   and not (perdona_piezas and m.get("codigo") == MOTIVO_DE_KIT)]
        aprobada_a_ojo = bool(foto.get("revisada")) and bool(reparos)
        if estado == "LISTA" and lienzo < int(cfg["lienzo_minimo"]):
            fallidas.append({"foto": original.name, "estado": "LIENZO",
                             "motivo": f"salió en un lienzo de {lienzo} px; el sitio pide por lo menos "
                                       f"{cfg['lienzo_minimo']} px, y ni en ese lienzo reúne el modelo "
                                       f"{MINIMO_LISTA} fotos LISTA: hacen falta fotos de más resolución"})
        elif estado == "LISTA":
            listas.append(f"{original.stem}.jpg")
            if foto.get("principal"):
                principal = f"{original.stem}.jpg"
            if aprobada_a_ojo:
                aprobadas.append(f"{original.stem}.jpg")
        else:
            fallida = {"foto": original.name, "estado": estado or "?", "motivo": motivos or "sin motivo"}
            if not perdona_piezas and solo_piezas_sueltas(foto):
                fallida["solo_piezas"] = True       # para sugerir el kit en el informe
            fallidas.append(fallida)
    if principal in listas:
        # La primera es la que el borrador sube de principal; sin marca manda el orden natural.
        listas.remove(principal)
        listas.insert(0, principal)
    return {"listas": listas, "fallidas": fallidas, "aprobadas_a_ojo": aprobadas}


def motivo_sin_registro(original: Path) -> str:
    """Por qué una original no tiene registro en el reporte. Una HEIC sin pillow-heif es la causa de
    siempre; cualquier otra —dos corridas que se pisaron el reporte (22/10/2026)— no tiene que ver
    con HEIC, y decirlo mandaba a buscar donde no era."""
    if original.suffix.lower() in (".heic", ".heif"):
        return ("el retoque no la procesó: es HEIC y probablemente falta pillow-heif "
                "(pip install pillow-heif); se reintenta en la próxima corrida")
    return ("no tiene registro en el reporte de _estudio/salida —¿se pisaron dos corridas?—: "
            "el modelo se rehace")


def faltan_en_el_reporte(mid: str, carpeta: Path, registros) -> list:
    """Las originales del modelo que no tienen registro en el reporte. Un modelo apto no se vuelve a
    procesar mientras su carpeta no cambie, pero si otra corrida le borró los registros —la parte 12 de
    la tanda del 22/10/2026 pisó el reporte de la 11— el veredicto sale con 0 LISTA y la tanda lo daba
    por «sin fotos nuevas»: hay que rehacerlo, y en esta misma corrida."""
    if registros is None:
        return []
    buenas, _ = unicas(carpeta)
    return [f.name for f in buenas if registro_de(f, mid, registros) is None]


def _evaluado_de_verdad(previo: dict) -> bool:
    """Si el estado guardado salió de mirar el reporte. Uno «por procesar», o con fotos que el
    retoque no procesó, no se puede dar por decidido aunque la carpeta no haya cambiado."""
    return previo.get("motivo") != "por procesar" and not any(
        f.get("estado") == SIN_PROCESAR for f in previo.get("fallidas", []))


def decidir(mid: str, carpeta, cfg: dict, reporte=None, medidor=medir, fecha_tanda=None,
            sacados=None, kits=None, registros=None) -> tuple:
    """(estado del modelo, ¿hay que procesarlo?). Sin `reporte` solo decide lo que se sabe antes de procesar.

    Con `reporte` siempre recalcula el veredicto —salvo el de un modelo sacado—: lo que se marcó
    a ojo después de la corrida anterior tiene que contar."""
    sacados = sacados or {}
    if carpeta is None:
        if mid in sacados and sacados[mid].get("fechaLista") == fecha_tanda:
            return dict(sacados[mid], modelo=mid, estado=FUERA), False
        return {"modelo": mid, "estado": EN_ESPERA, "listas": [], "faltan": MINIMO_LISTA,
                "fallidas": [], "motivo": "no tiene carpeta: corre el paso 5 de /listas-de-proveedor, "
                                          "o sácalo de la tanda", "fecha": ahora()}, False
    huella = huella_de_originales(carpeta)
    previo = leer_estado(carpeta) or {}
    mismo = previo.get("huella") == huella
    # Sacado de la tanda: vale para esta lista. Con fotos nuevas, o en la tanda de otra lista, se
    # vuelve a intentar.
    if previo.get("estado") == FUERA and mismo and previo.get("fechaLista") == fecha_tanda:
        return previo, False

    base = {"modelo": mid, "huella": huella, "fechaLista": fecha_tanda, "fecha": ahora(), "sin_cambios": False}
    antes = revisar_antes(carpeta, cfg, medidor)
    if len(antes["viables"]) < MINIMO_LISTA:
        faltan = MINIMO_LISTA - len(antes["viables"])
        return dict(base, estado=EN_ESPERA, listas=[], faltan=faltan, fallidas=antes["fallidas"],
                    sin_cambios=mismo and previo.get("estado") == EN_ESPERA,
                    motivo=f"tiene {len(antes['viables'])} originales que pueden salir LISTA y necesita "
                           f"{MINIMO_LISTA}: no se procesó"), False

    if reporte is None:
        if (mismo and previo.get("estado") in (APTO, EN_ESPERA) and _evaluado_de_verdad(previo)
                and not faltan_en_el_reporte(mid, carpeta, registros)):
            return dict(previo, sin_cambios=previo.get("estado") == EN_ESPERA), False
        return dict(base, estado=EN_ESPERA, listas=[], faltan=MINIMO_LISTA, fallidas=antes["fallidas"],
                    motivo="por procesar"), True

    ev = evaluar(mid, carpeta, reporte, cfg, kit=mid in (kits or {}),
                 par=categoria_de(carpeta) in CATEGORIAS_DE_PARES)
    vistas = {x["foto"] for x in antes["fallidas"]}
    fallidas = antes["fallidas"] + [f for f in ev["fallidas"] if f["foto"] not in vistas]
    if len(ev["listas"]) >= MINIMO_LISTA:
        return dict(base, estado=APTO, listas=ev["listas"], faltan=0, fallidas=fallidas, motivo=None,
                    aprobadas_a_ojo=ev["aprobadas_a_ojo"]), False
    faltan = MINIMO_LISTA - len(ev["listas"])
    return dict(base, estado=EN_ESPERA, listas=ev["listas"], faltan=faltan, fallidas=fallidas,
                motivo=f"salieron {len(ev['listas'])} fotos LISTA y necesita {MINIMO_LISTA}",
                aprobadas_a_ojo=ev["aprobadas_a_ojo"]), False


def sacar(raiz: Path, carpeta, mid: str, motivo: str, fecha_tanda) -> dict:
    """La única salida de la espera, y es de la persona: el modelo queda fuera de esta tanda.
    Si después deja fotos nuevas, o llega la tanda de otra lista, vuelve a intentarse. Un modelo
    sin carpeta se anota en `_estudio/sacados.json`."""
    if not motivo or not motivo.strip():
        raise ValueError("Sacar un modelo de la tanda pide el motivo: queda escrito en su estado.")
    texto = f"sacado de la tanda por la persona: {motivo.strip()}"
    if carpeta is None:
        sacados = leer_sacados(raiz)
        sacados[mid] = {"motivo": texto, "fechaLista": fecha_tanda, "fecha": ahora()}
        ruta = Path(raiz) / TRABAJO / SACADOS
        ruta.parent.mkdir(parents=True, exist_ok=True)
        ruta.write_text(json.dumps(sacados, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="")
        return dict(sacados[mid], modelo=mid, estado=FUERA)
    previo = leer_estado(carpeta) or {}
    estado = dict(previo, modelo=mid, estado=FUERA, huella=huella_de_originales(carpeta),
                  fechaLista=fecha_tanda, motivo=texto, fecha=ahora())
    escribir_estado(carpeta, estado)
    return estado


def leer_tanda(ruta: Path) -> tuple:
    """(fecha de la lista, ids de los modelos que esperan fotos, ids sin ficha en la lista).

    Los `sinFicha` los anota el inventario (tools/importar-lista-tecnologia.mjs): la API los espera
    pero la lista no los trae, porque el bloque 1 no los exportó —casi siempre por falta de
    descripción—. Sus fotos se procesan para no perder el trabajo, pero no bloquean la tanda: el
    bloque 3 no puede hacerles borrador, y el Note 17 Pro Max retuvo la tanda entera del 15/10/2026
    esperando la cuarta foto de un borrador que no podía existir."""
    datos = json.loads(Path(ruta).read_text(encoding="utf-8"))
    ids = list(dict.fromkeys(datos.get("modelos") or []))
    return datos.get("fechaLista"), ids, set(datos.get("sinFicha") or [])


def candidatas_a_ojo(e: dict) -> list:
    """Las fotos REVISAR de un modelo en espera que una persona puede aprobar tras mirarlas al
    100 % (decisión del 22/10/2026). Las de solo piezas sueltas no: su salida es el kit."""
    return [f for f in e.get("fallidas", []) if f.get("estado") == "REVISAR" and not f.get("solo_piezas")]


def informe(estados: list, sin_ficha=frozenset(), salida=None) -> str:
    sin_ficha = set(sin_ficha)
    lineas = []
    aptos = [e for e in estados if e["estado"] == APTO and e["modelo"] not in sin_ficha]
    fuera = [e for e in estados if e["estado"] == FUERA]
    espera = [e for e in estados if e["estado"] == EN_ESPERA and e["modelo"] not in sin_ficha]
    pendientes_de_ficha = [e for e in estados if e["modelo"] in sin_ficha and e["estado"] != FUERA]
    lineas.append(f"Tanda: {len(aptos)} aptos, {len(espera)} en espera, {len(fuera)} fuera de la tanda"
                  + (f", {len(pendientes_de_ficha)} sin ficha en la lista" if pendientes_de_ficha else ""))
    for e in espera:
        nota = " · sin fotos nuevas desde el último intento" if e.get("sin_cambios") else ""
        lineas.append(f"\nEN ESPERA  {e['modelo']}: faltan {e['faltan']} LISTA ({e.get('motivo')}){nota}")
        for f in e.get("fallidas", []):
            lineas.append(f"  - {f['foto']} [{f['estado']}] {f['motivo']}")
        piezas = [f for f in e.get("fallidas", []) if f.get("solo_piezas")]
        if piezas:
            # Lo decide la persona, nunca el script: tres piezas también pueden ser dos productos.
            lineas.append(f"  ¿Es un kit? {len(piezas)} foto(s) solo esperan por las piezas sueltas. Si el producto se "
                          f"vende con ellas: --kit {e['modelo']} --motivo \"qué piezas trae\"")
        if salida is not None and candidatas_a_ojo(e):
            # La revisión queda a un paso: mirar el mosaico y, si está bien, aprobar. La decide
            # la persona, nunca el script (ver «Una REVISAR aprobada a ojo sí cuenta»).
            lineas.append("  Una REVISAR que al 100 % se ve bien se aprueba y cuenta como LISTA (22/10/2026):")
            for f in candidatas_a_ojo(e):
                nombre = slug(Path(f["foto"]).stem)
                lineas.append(f"    python \"{Path(__file__).with_name('ampliar.py')}\" \"{salida}\" {nombre}")
                lineas.append(f"    python \"{Path(__file__).with_name('marcar.py')}\" \"{salida}\" {nombre} "
                              "--aprobar \"revisada al 100 %: qué se miró\"")
    for e in pendientes_de_ficha:
        n = len(e.get("listas") or [])
        lineas.append(f"\nSIN FICHA  {e['modelo']}: la lista no lo trae —el bloque 1 no lo exportó, casi siempre "
                      f"por falta de descripción— y no saldrá borrador ({n} LISTA; no bloquea la tanda). "
                      "Complétalo en el bloque 1 o sácalo con --sacar.")
    for e in fuera:
        lineas.append(f"\nFUERA      {e['modelo']}: {e.get('motivo')}")
    lineas.append("\nTANDA COMPLETA: revisa a ojo las hojas de las LISTA y sigue con /fichas-de-tecnologia"
                  if not espera
                  else "\nLA TANDA ESPERA FOTOS: deja las nuevas en «Fotos originales» y vuelve a correrla")
    return "\n".join(lineas)


def preparar_revision(raiz: Path, estados: list, sin_ficha=frozenset(), ampliar_fn=None) -> list:
    """Los mosaicos al 100 % de las REVISAR que detienen la tanda, generados de una vez.

    Tres de los cuatro modelos en espera del 15/10/2026 esperaban por una sola foto REVISAR que,
    mirada a tamaño real, estaba bien; el ciclo llevó siete días porque cada revisión pedía correr
    `ampliar.py` a mano. Solo para los modelos a los que les falta poco (hasta 2 LISTA): a los
    demás les hacen falta fotos nuevas de todas formas. Si `ampliar.py` falla —sin OpenCV, por
    ejemplo—, no pasa nada: los comandos impresos en el informe siguen valiendo."""
    salida = Path(raiz) / TRABAJO / "salida"
    if ampliar_fn is None:
        def ampliar_fn(nombre):
            r = subprocess.run([sys.executable, str(Path(__file__).with_name("ampliar.py")), str(salida), nombre],
                               capture_output=True, text=True)
            lineas = (r.stdout or "").strip().splitlines()
            return lineas[-1] if r.returncode == 0 and lineas else None
    mosaicos = []
    for e in estados:
        if e.get("estado") != EN_ESPERA or e.get("modelo") in set(sin_ficha) or (e.get("faltan") or 0) > 2:
            continue
        for f in candidatas_a_ojo(e):
            ruta = ampliar_fn(slug(Path(f["foto"]).stem))
            if ruta:
                mosaicos.append((f"{e['modelo']}/{f['foto']}", ruta))
    return mosaicos


def nombres_de(raiz: Path, ids) -> list:
    """Los nombres con que procesar.py conoce las fotos de esos modelos, para `--solo`."""
    nombres = []
    for carpeta in modelos(raiz, set(ids)).values():
        buenas, _ = unicas(carpeta)
        nombres.extend(slug(f.stem) for f in buenas)
    return nombres


def partes(raiz: Path, ids: list, lote) -> list:
    """Los modelos a procesar, repartidos en partes de modelos enteros de hasta `lote` fotos.

    Un modelo se procesa entero —el lienzo depende de todas sus fotos—, así que uno más grande que el
    lote va solo. Sin lote, una sola parte: el comportamiento de siempre. Existe por la tanda del
    22/10/2026: 297 fotos en una sola llamada a procesar.py no caben en el límite de una terminal en
    primer plano, y en segundo plano un reproceso de 83 ya había muerto por memoria (SKILL.md)."""
    if not lote:
        return [list(ids)] if ids else []
    carpetas = modelos(raiz, set(ids))
    salida, actual, n = [], [], 0
    for mid in ids:
        k = len(unicas(carpetas[mid])[0]) if mid in carpetas else 0
        if actual and n + k > int(lote):
            salida.append(actual)
            actual, n = [], 0
        actual.append(mid)
        n += k
    if actual:
        salida.append(actual)
    return salida


def correr_tanda(raiz: Path, ids: list, cfg: dict, procesar_fn, solo_devolver=False, medidor=medir,
                 fecha_tanda=None, lote=None) -> list:
    """Decide, procesa lo que haga falta, devuelve y vuelve a decidir. Escribe el estado de cada modelo.

    `procesar_fn(nombres)` corre el retoque sobre esas fotos y devuelve False si quedó en
    segundo plano. Con `lote`, una llamada por parte (`partes`), en serie; se devuelve y se decide
    una sola vez, al final."""
    por_id = modelos(raiz, set(ids))
    sacados = leer_sacados(raiz)
    kits = leer_kits(raiz)
    decisiones = {}
    if not solo_devolver:
        registros = leer_reporte(raiz).get("fotos", [])
        decisiones = {mid: decidir(mid, por_id.get(mid), cfg, None, medidor, fecha_tanda, sacados,
                                   registros=registros)
                      for mid in ids}
        a_procesar = [mid for mid, (_, p) in decisiones.items() if p]
        for mid, razon in pistas_de_kit(raiz, a_procesar, kits):
            # Antes de procesar, para no esperar una corrida entera por las piezas sueltas.
            print(f"[pista] {mid} parece un kit ({razon}). Si se vende con piezas sueltas: "
                  f"--kit {mid} --motivo \"qué piezas trae\"")
        if a_procesar:
            copiadas, errores = preparar(raiz, set(a_procesar))
            for e in errores:
                print(f"[error] {e}")
            print(f"{copiadas} foto(s) nuevas o cambiadas; se procesan enteros {len(a_procesar)} modelo(s)")
            repartidos = partes(raiz, a_procesar, lote)
            for i, parte in enumerate(repartidos, 1):
                if len(repartidos) > 1:
                    print(f"\n— parte {i} de {len(repartidos)}: {', '.join(parte)}", flush=True)
                if procesar_fn(nombres_de(raiz, parte)) is False:
                    return []   # quedó en segundo plano: se termina con --devolver
    devolver(raiz)
    reporte = leer_reporte(raiz)
    estados = []
    for mid in ids:
        estado, _ = decidir(mid, por_id.get(mid), cfg, reporte, medidor, fecha_tanda, sacados, kits)
        if mid in decisiones and decisiones[mid][0].get("sin_cambios"):
            # Se dice aunque se reevalúe: quien confirmó fotos nuevas tiene que saber que no llegaron.
            estado = dict(estado, sin_cambios=True)
        if por_id.get(mid) is not None:
            escribir_estado(por_id[mid], {k: v for k, v in estado.items() if k != "sin_cambios"})
        estados.append(estado)
    return estados


# --------------------------------------------------------------------------
# Las portadas (22/10/2026)
# --------------------------------------------------------------------------

def filas_de_portadas(estados: list, sin_ficha=frozenset()) -> list:
    """[(id, [nombres de sus LISTA en el orden en que se suben])] de los modelos aptos que van a
    borrador. El primero es la principal: la portada del producto en el sitio."""
    return [(e["modelo"], [slug(Path(x).stem) for x in e.get("listas") or []])
            for e in estados if e.get("estado") == APTO and e["modelo"] not in set(sin_ficha)]


def orden_de_portadas(filas: list, salida: Path) -> str:
    """La portada actual de cada modelo y la orden para cambiar las que haga falta en una sola llamada."""
    lineas = ["Portada de cada modelo (la primera es la que se sube de principal):"]
    for mid, nombres in filas:
        lineas.append(f"  {mid}: {nombres[0]}" + (f"   otras: {', '.join(nombres[1:])}" if nombres[1:] else ""))
    lineas.append("Para cambiar portadas, una por modelo y en una sola llamada (las hojas se regeneran al final):")
    lineas.append(f"  python \"{Path(__file__).with_name('marcar.py')}\" \"{salida}\" <nombre> <nombre> ... "
                  "--principal --sin-hojas")
    lineas.append(f"  python \"{Path(__file__).with_name('hojas.py')}\" \"{salida}\"")
    lineas.append("Y vuelve a correr la tanda para que la nueva principal pase al frente de estado-fotos.json.")
    return "\n".join(lineas)


def hacer_portadas(salida: Path, filas: list, por_hoja: int = 8, lado: int = 180) -> list:
    """`portadas-N.jpg` en la salida: una fila por modelo con sus LISTA numeradas, la principal
    resaltada. Sale de las miniaturas que ya guardó el retoque; sin Pillow, no se arma y no pasa nada."""
    try:
        from PIL import Image, ImageDraw, ImageFont
    except ImportError:
        return []
    try:
        fuente = ImageFont.truetype("arial.ttf", 18)
    except OSError:
        fuente = ImageFont.load_default()
    for vieja in Path(salida).glob("portadas-*.jpg"):
        vieja.unlink()
    rutas, maximo = [], max((len(n) for _, n in filas), default=0)
    ancho = 20 + max(maximo, 1) * (lado + 12) + 20
    for h in range(0, len(filas), por_hoja):
        bloque = filas[h:h + por_hoja]
        hoja = Image.new("RGB", (ancho, 20 + len(bloque) * (lado + 66)), "white")
        d = ImageDraw.Draw(hoja)
        for i, (mid, nombres) in enumerate(bloque):
            y = 20 + i * (lado + 66)
            d.text((20, y), mid, fill=(20, 20, 20), font=fuente)
            for k, nombre in enumerate(nombres):
                x = 20 + k * (lado + 12)
                mini = Path(salida) / ".trabajo" / "miniaturas" / f"{nombre}-despues.jpg"
                if mini.is_file():
                    with Image.open(mini) as im:
                        im = im.convert("RGB")
                        im.thumbnail((lado, lado))
                        hoja.paste(im, (x, y + 26))
                color = (20, 125, 60) if k == 0 else (70, 70, 70)
                d.rectangle([x - 3, y + 23, x + lado + 2, y + 26 + lado + 2], outline=color if k == 0 else (200, 200, 200),
                            width=4 if k == 0 else 1)
                d.text((x + 4, y + 28), f"{k + 1}{' principal' if k == 0 else ''}", fill=color, font=fuente)
                etiqueta = nombre
                while len(etiqueta) > 4 and d.textlength(etiqueta, font=fuente) > lado:
                    etiqueta = etiqueta[2:]
                d.text((x, y + 32 + lado), etiqueta if etiqueta == nombre else "…" + etiqueta[1:],
                       fill=(90, 90, 90), font=fuente)
        ruta = Path(salida) / f"portadas-{h // por_hoja + 1}.jpg"
        hoja.save(ruta, quality=85)
        rutas.append(ruta)
    return rutas


def retoque_en_curso(raiz: Path):
    """El avance de un retoque en segundo plano que no ha terminado, o None.

    Un avance en «procesando» cuyo proceso ya no existe no es un retoque en curso: el proceso murió
    —se cerró la sesión, lo mató la memoria— sin escribir «terminado», y esperarlo dejaba el
    `--devolver` bloqueado para siempre (21/10/2026, la JBL Tune 730BT _1).
    """
    ruta = Path(raiz) / TRABAJO / "salida" / ".trabajo" / "avance.json"
    try:
        datos = json.loads(ruta.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return None
    if datos.get("estado") not in ("iniciando", "procesando"):
        return None
    if datos.get("pid") and not salidas.vivo(datos["pid"]):
        return None
    return datos


# --------------------------------------------------------------------------
# Validar antes de procesar (22/10/2026)
# --------------------------------------------------------------------------

def estimar_producto(ruta: Path):
    """El lado del producto que mide el estimador de la skill, o None. El estimador pide NumPy y
    Pillow; sin ellos no se estima nada y la validación lo dice, en vez de inventar el número."""
    try:
        import estimador
    except ImportError:
        return None
    try:
        return estimador.estimar_lado_fiable(ruta)
    except Exception:
        return None


def validar(raiz: Path, ids, cfg: dict, medidor=medir, estimar=estimar_producto) -> dict:
    """Lo que hay que saber de las originales antes de gastar un retoque, sin retocar nada.

    Por modelo: los nombres que no siguen `<id>_N` —la convención de la skill de listas, que el
    resto del flujo da por hecha—, los archivos de un formato que no se lee (hoy se ignoran sin
    avisar), las fotos bajo el lado mínimo y cuántas LISTA se estiman en un lienzo de
    LIENZO_MINIMO con el mismo estimador y la misma regla que usa la tanda. Y en toda la tanda, las
    fotos idénticas en dos modelos: la Redmi Pad 2 bundle y la 4G compartían sus seis fotos.
    Es lo que el 22/10/2026 se validó a mano, foto por foto, antes de procesar 297."""
    minimo = lado_minimo(cfg)
    por_modelo, huellas = {}, {}
    for mid, carpeta in modelos(raiz, set(ids) if ids else None).items():
        todos = sorted((f for f in (carpeta / ORIGINALES).iterdir() if f.is_file()),
                       key=lambda f: orden_natural(f.name))
        fotos = [f for f in todos if f.suffix.lower() in EXT_FOTO]
        patron = re.compile(rf"{re.escape(mid)}_\d+")
        lados = []
        datos = {"carpeta": carpeta.name, "fotos": len(fotos),
                 "nombres_fuera": [f.name for f in fotos if not patron.fullmatch(f.stem)],
                 "no_admitidos": [f.name for f in todos if f.suffix.lower() not in EXT_FOTO
                                  and not f.name.startswith(".")],
                 "pequenas": []}
        for f in fotos:
            huellas.setdefault(huella_archivo(f), []).append(f"{carpeta.name}/{f.name}")
            medida = medidor(f)
            if medida is not None and max(medida) < minimo:
                datos["pequenas"].append(f.name)
            if estimar is not None:
                lados.append(estimar(f) or 0)
        datos["listas_estimadas"] = (salidas.listas_en(int(cfg["lienzo_minimo"]), lados, cfg)
                                     if estimar is not None else None)
        por_modelo[mid] = datos
    duplicados = sorted(sorted(v) for v in huellas.values() if len({x.split("/")[0] for x in v}) > 1)
    completa = all(d["listas_estimadas"] is None or d["listas_estimadas"] >= MINIMO_LISTA
                   for d in por_modelo.values())
    return {"modelos": por_modelo, "duplicados": duplicados, "completa": completa, "lado_minimo": minimo}


def informe_validacion(v: dict) -> str:
    lineas = []
    for mid, d in v["modelos"].items():
        est = d["listas_estimadas"]
        ok = (est is None or est >= MINIMO_LISTA) and not (d["nombres_fuera"] or d["no_admitidos"])
        texto = "sin estimar (falta NumPy o Pillow)" if est is None else f"{est} LISTA estimadas en {LIENZO_MINIMO}"
        lineas.append(f"[{'OK' if ok else 'ATENCIÓN'}] {d['carpeta']} ({mid}) · {d['fotos']} fotos · {texto}")
        if d["nombres_fuera"]:
            lineas.append(f"    nombres que no siguen «{mid}_N»: {', '.join(d['nombres_fuera'])}")
        if d["no_admitidos"]:
            lineas.append(f"    formato que no se lee (no cuenta): {', '.join(d['no_admitidos'])}")
        if d["pequenas"]:
            lineas.append(f"    bajo el mínimo de {v['lado_minimo']} px (no puede salir LISTA): {', '.join(d['pequenas'])}")
    por_modelos: dict = {}
    for grupo in v["duplicados"]:
        clave = tuple(sorted({x.split("/")[0] for x in grupo}))
        por_modelos.setdefault(clave, []).append(" = ".join(x.split("/", 1)[1] for x in grupo))
    for clave, iguales in por_modelos.items():
        lineas.append(f"\n[aviso] {len(iguales)} foto(s) idénticas en {' y '.join(clave)}: si son productos "
                      f"distintos, sus fotos deberían mostrar la diferencia ({'; '.join(iguales)})")
    nombres = sum(len(d["nombres_fuera"]) for d in v["modelos"].values())
    if nombres:
        lineas.append(f"\n{nombres} foto(s) no siguen «<id>_N»: --renombrar las deja así en orden natural, en los "
                      "modelos que todavía no tienen procesadas.")
    lineas.append("\nVALIDACIÓN: " + ("todos los modelos pueden reunir sus " + str(MINIMO_LISTA) + " LISTA"
                                       if v["completa"] else "hay modelos que no reúnen " + str(MINIMO_LISTA)
                                       + " LISTA estimadas: faltan fotos de más resolución"))
    return "\n".join(lineas)


def renombrar(raiz: Path, ids) -> dict:
    """Deja las originales de cada modelo como `<id>_N`, en orden natural, en dos pasos para que un
    nombre no pise a otro, y escribe el registro en `_estudio/renombradas-<fecha>.json`.

    Se niega en un modelo que ya tiene «Fotos procesadas» o `estado-fotos.json`: el nombre entra en
    la huella de la carpeta, así que renombrar fuerza el reproceso y caduca las aprobaciones."""
    hechas, negados = [], []
    for mid, carpeta in modelos(raiz, set(ids) if ids else None).items():
        procesadas = carpeta / PROCESADAS
        if (carpeta / ESTADO).is_file() or (procesadas.is_dir() and any(procesadas.iterdir())):
            negados.append(mid)
            continue
        plan = []
        for i, f in enumerate(originales(carpeta), 1):
            nuevo = f.with_name(f"{mid}_{i}{f.suffix.lower()}")
            if f.name != nuevo.name:
                plan.append((f, nuevo))
        temporales = [(f, f.with_name(f"__renombrando__{k}{f.suffix}"), n) for k, (f, n) in enumerate(plan)]
        for f, t, _ in temporales:
            f.rename(t)
        for _, t, n in temporales:
            t.rename(n)
        hechas += [{"carpeta": carpeta.name, "antes": f.name, "despues": n.name} for f, n in plan]
    archivo = None
    if hechas:
        archivo = Path(raiz) / TRABAJO / f"renombradas-{datetime.now().strftime('%Y-%m-%d-%H%M%S')}.json"
        archivo.parent.mkdir(parents=True, exist_ok=True)
        archivo.write_text(json.dumps(hechas, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="")
    return {"renombradas": hechas, "negados": negados, "archivo": str(archivo) if archivo else None}


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("fichas", nargs="?", default="catalogo/4-entregables/fichas")
    ap.add_argument("--tanda", help="por-proponer.json: el bloque 2, solo esos modelos y con la compuerta")
    ap.add_argument("--devolver", action="store_true",
                    help="solo copia a cada modelo lo que ya está en _estudio/salida (tras un --segundo-plano)")
    ap.add_argument("--segundo-plano", action="store_true", help="lanza procesar.py desacoplado y sale")
    ap.add_argument("--lote", type=int, metavar="N",
                    help="con --tanda: procesa por partes de modelos enteros de hasta N fotos, en serie y en "
                         "primer plano (20 es un buen tamaño)")
    ap.add_argument("--sacar", metavar="ID", help="con --tanda: saca ese modelo de la tanda")
    ap.add_argument("--kit", metavar="ID", help="con --tanda: ese modelo se vende con piezas sueltas, y "
                                                "sus fotos con varias piezas cuentan como LISTA")
    ap.add_argument("--quitar-kit", metavar="ID", help="con --tanda: deshace un --kit")
    ap.add_argument("--sin-huecos", metavar="ID", help="ese modelo no tiene huecos pasantes aunque su categoría "
                                                       "los admita: lo que el recorte encierre vuelve al producto")
    ap.add_argument("--quitar-sin-huecos", metavar="ID", help="deshace un --sin-huecos")
    ap.add_argument("--motivo", help="con --sacar, --kit o --sin-huecos: por qué, queda escrito")
    ap.add_argument("--validar", action="store_true",
                    help="no retoca nada: informa nombres, formatos, duplicados y LISTA estimadas por modelo")
    ap.add_argument("--renombrar", action="store_true",
                    help="con --validar: deja las originales como <id>_N en orden natural (solo en modelos "
                         "sin procesadas)")
    a = ap.parse_args()
    raiz = Path(a.fichas)
    if a.lote is not None and (a.lote < 1 or a.segundo_plano):
        ap.error("--lote pide un número positivo y va en primer plano: las partes corren una tras otra")
    if a.renombrar and not a.validar:
        ap.error("--renombrar va con --validar: primero se ve qué cambia")
    if a.validar:
        ids = leer_tanda(Path(a.tanda))[1] if a.tanda else None
        if a.renombrar:
            r = renombrar(raiz, ids)
            print(f"{len(r['renombradas'])} foto(s) renombradas" + (f"; registro en {r['archivo']}" if r["archivo"] else ""))
            for mid in r["negados"]:
                print(f"[no se renombró] {mid}: ya tiene fotos procesadas o estado-fotos.json, y renombrar cambia "
                      "la huella de la carpeta —fuerza el reproceso y caduca las aprobaciones—. Si de verdad hay "
                      "que hacerlo, renómbralas a mano sabiendo eso.")
        v = validar(raiz, ids, cargar_config())
        print(informe_validacion(v))
        return 0 if v["completa"] else SALIDA_EN_ESPERA
    if a.sin_huecos or a.quitar_sin_huecos:
        mid = a.sin_huecos or a.quitar_sin_huecos
        try:
            declarar_sin_huecos(raiz, mid, a.motivo or "", quitar=bool(a.quitar_sin_huecos))
        except ValueError as e:
            print(e)
            return 1
        print(f"{mid}: {'vuelve a la regla de su categoría' if a.quitar_sin_huecos else 'sin huecos pasantes'}. "
              "Queda por procesar: la siguiente tanda rehace todas sus fotos.")
        return 0
    base = [sys.executable, str(Path(__file__).with_name("procesar.py")), str(raiz / TRABAJO / "crudas"),
            "-o", str(raiz / TRABAJO / "salida"), "--por-producto", "--variantes"]
    if raiz.is_dir():
        celulares = sin_huecos(raiz)
        if celulares:
            base += ["--sin-huecos", ",".join(celulares)]

    def correr(orden) -> bool:
        if a.segundo_plano:
            subprocess.run(orden + ["--segundo-plano"], check=True)
            extra = f' --tanda "{a.tanda}"' if a.tanda else ""
            print(f"Cuando termine: python3 {Path(__file__).name} \"{raiz}\"{extra} --devolver")
            return False
        salida = subprocess.run(orden).returncode
        if salida not in SALIDAS_DE_PROCESAR_ACEPTADAS:
            raise SystemExit(f"procesar.py salió con {salida}: revisa el registro en {raiz / TRABAJO / 'salida'}")
        return True

    if a.tanda:
        fecha, ids, sin_ficha = leer_tanda(Path(a.tanda))
        cfg = cargar_config()
        if a.sacar:
            if a.sacar not in ids:
                print(f"{a.sacar} no está en la tanda.")
                return 1
            print(informe([sacar(raiz, modelos(raiz, {a.sacar}).get(a.sacar), a.sacar, a.motivo or "", fecha)]))
            # Sacarlo vale para esta tanda. Si la decisión es no venderlo, hay que registrarla
            # donde la lista siguiente la vea, o volverá a proponerse (el TCL Pad Lugh, 21/10/2026).
            print("\nSi la decisión es no vender este modelo —no solo en esta tanda—, regístralo para "
                  "que la próxima lista no lo vuelva a proponer:\n"
                  f"  python .claude/skills/listas-de-proveedor/scripts/conocidos.py excluir {a.sacar} "
                  "--motivo \"por qué\"")
            return 0
        if a.kit or a.quitar_kit:
            mid = a.kit or a.quitar_kit
            if mid not in ids:
                print(f"{mid} no está en la tanda.")
                return 1
            declarar_kit(raiz, mid, a.motivo or "", quitar=bool(a.quitar_kit))
            print(f"{mid}: {'ya no es un kit' if a.quitar_kit else 'kit, sus piezas sueltas cuentan'}. "
                  "Vuelve a correr la tanda para recalcular su veredicto.")
            return 0
        if a.devolver and retoque_en_curso(raiz):
            print("El retoque sigue en segundo plano: espera a que termine y vuelve a correr --devolver.")
            return SALIDA_EN_ESPERA
        # Cada modelo que cambió se procesa entero, con --solo: el lienzo depende de todas sus fotos,
        # y lo elige la compuerta —el mayor escalón donde caben MINIMO_LISTA fotos LISTA—.
        compuerta = ["--lienzo-minimo", str(LIENZO_MINIMO), "--listas-minimas", str(MINIMO_LISTA)]
        procesar_fn = lambda nombres: correr(base + compuerta + sum((["--solo", n] for n in nombres), []))  # noqa: E731
        estados = correr_tanda(raiz, ids, cfg, procesar_fn, solo_devolver=a.devolver, fecha_tanda=fecha,
                               lote=a.lote)
        if not estados:
            return SALIDA_EN_ESPERA   # en segundo plano: la tanda todavía no está completa
        print(informe(estados, sin_ficha, salida=raiz / TRABAJO / "salida"))
        mosaicos = preparar_revision(raiz, estados, sin_ficha)
        if mosaicos:
            print("\nMosaicos al 100 % de las REVISAR que detienen la tanda, listos para mirar:")
            for etiqueta, ruta in mosaicos:
                print(f"  - {etiqueta}: {ruta}")
        viejas = con_retoque_anterior(raiz, ids, leer_reporte(raiz).get("fotos", []))
        if viejas:
            print(f"\n[aviso] {len(viejas)} foto(s) de la tanda se procesaron con un retoque anterior al "
                  f"{salidas.VERSION_RETOQUE}: la tanda no las rehace sola. Para rehacerlas sin mover el lienzo: "
                  "procesar.py ... --solo <nombres> --conservar-lienzo, y después esta orden con --devolver.")
        fuera = fuera_de_la_tanda(raiz, ids)
        if fuera:
            print(f"\n[aviso] {len(fuera)} carpeta(s) con fotos que no están en la tanda y no se procesaron "
                  "(no están en por-proponer.json): " + ", ".join(fuera))
        espera = any(e["estado"] == EN_ESPERA and e["modelo"] not in sin_ficha for e in estados)
        if not espera:
            filas = filas_de_portadas(estados, sin_ficha)
            if filas:
                salida = raiz / TRABAJO / "salida"
                rutas = hacer_portadas(salida, filas)
                print("\n" + orden_de_portadas(filas, salida))
                for r in rutas:
                    print(f"  hoja de portadas: {r}")
        print(f"\nHojas de revisión: {raiz / TRABAJO / 'salida'}")
        # Un modelo sin ficha en la lista no detiene la entrega: el bloque 3 no puede hacerle
        # borrador de todas formas, y la tanda se entrega con lo que sí puede llegar al panel.
        return (SALIDA_EN_ESPERA
                if any(e["estado"] == EN_ESPERA and e["modelo"] not in sin_ficha for e in estados) else 0)

    if not a.devolver:
        copiadas, errores = preparar(raiz)
        for e in errores:
            print(f"[error] {e}")
        print(f"{copiadas} foto(s) nuevas o cambiadas para procesar")
        if correr(base + ["--nuevas"]) is False:
            return 0

    r = devolver(raiz)
    print(f"{r['devueltas']} foto(s) en «{PROCESADAS}»")
    for titulo, lista in (("REVISAR (mira la hoja de revisión antes de subirlas)", r["revisar"]),
                          ("REPETIR (no se devolvieron: hay que conseguir otra)", r["repetir"]),
                          ("sin original (se borró o se renombró)", r["sin_original"]),
                          (f"retiradas a «{PROCESADAS}/{RETIRADAS}» (su versión anterior ya no se sube)",
                           r["retiradas"])):
        if lista:
            print(f"\n{titulo}:")
            for x in lista:
                print(f"  - {x}")
    print(f"\nHojas de revisión: {raiz / TRABAJO / 'salida'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
