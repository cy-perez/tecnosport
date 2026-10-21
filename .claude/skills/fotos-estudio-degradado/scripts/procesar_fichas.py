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
`LIENZO_MINIMO` px. Una REVISAR no cuenta, aunque se apruebe a ojo: decisión del
negocio del 10/10/2026. El modelo que no llega queda **en espera**, con la razón de
cada foto que falló, y la tanda no se da por terminada hasta que todos pasen o la
persona saque alguno a mano con `--sacar`.

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
- **Un kit lo declara la persona** (`--kit`, decisión del 18/10/2026): en un modelo que
  se vende con piezas sueltas —la consola con sus Joy-Con, la tablet con teclado y
  mouse— una foto cuyo único reparo es `varias_piezas` cuenta como LISTA. En los demás
  modelos sigue sin contar: tres piezas o más pueden ser dos productos distintos o la
  gama de colores, y eso no lo decide el script.

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


def devolver(raiz: Path) -> dict:
    """Copia cada resultado del reporte a la carpeta de su modelo, con el nombre de su original."""
    raiz = Path(raiz)
    salida = raiz / TRABAJO / "salida"
    registros = leer_reporte(raiz).get("fotos", [])
    resumen = {"devueltas": 0, "repetir": [], "revisar": [], "sin_original": []}
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


def evaluar(mid: str, carpeta: Path, reporte: dict, cfg: dict, kit: bool = False) -> dict:
    """Las fotos LISTA del modelo según el reporte, y la razón de cada una que no lo es.

    En un kit, una REVISAR cuyo único motivo son las piezas sueltas cuenta como LISTA."""
    registros = reporte.get("fotos", [])
    buenas, _ = unicas(carpeta)
    listas, fallidas = [], []
    for original in buenas:
        foto = registro_de(original, mid, registros)
        if foto is None:
            fallidas.append({"foto": original.name, "estado": SIN_PROCESAR,
                             "motivo": "el retoque no la procesó: si es HEIC, falta pillow-heif "
                                       "(pip install pillow-heif); si no, se reintenta en la próxima corrida"})
            continue
        estado = foto.get("estado")
        if kit and solo_piezas_sueltas(foto):
            estado = "LISTA"
        lienzo = int((foto.get("lienzo") or [0])[0])
        motivos = "; ".join(texto_de_motivo(m) for m in foto.get("motivos", []))
        # Una aprobación a ojo solo pesa si había algo que aprobar: un REVISAR automático. En un
        # kit, las piezas sueltas no son algo que aprobar.
        reparos = [m for m in foto.get("motivos_auto", [])
                   if isinstance(m, dict) and m.get("estado") == "REVISAR"
                   and not (kit and m.get("codigo") == MOTIVO_DE_KIT)]
        aprobada_a_ojo = bool(foto.get("revisada")) and bool(reparos)
        if estado == "LISTA" and aprobada_a_ojo:
            motivos_auto = "; ".join(m.get("texto", "") for m in reparos)
            fallidas.append({"foto": original.name, "estado": "REVISAR",
                             "motivo": "salió REVISAR y se aprobó a ojo; para la tanda solo cuenta una LISTA"
                                       + (f" ({motivos_auto})" if motivos_auto else "")})
        elif estado == "LISTA" and lienzo < int(cfg["lienzo_minimo"]):
            fallidas.append({"foto": original.name, "estado": "LIENZO",
                             "motivo": f"salió en un lienzo de {lienzo} px; el sitio pide por lo menos "
                                       f"{cfg['lienzo_minimo']} px, y ni en ese lienzo reúne el modelo "
                                       f"{MINIMO_LISTA} fotos LISTA: hacen falta fotos de más resolución"})
        elif estado == "LISTA":
            listas.append(f"{original.stem}.jpg")
        else:
            fallidas.append({"foto": original.name, "estado": estado or "?", "motivo": motivos or "sin motivo"})
    return {"listas": listas, "fallidas": fallidas}


def _evaluado_de_verdad(previo: dict) -> bool:
    """Si el estado guardado salió de mirar el reporte. Uno «por procesar», o con fotos que el
    retoque no procesó, no se puede dar por decidido aunque la carpeta no haya cambiado."""
    return previo.get("motivo") != "por procesar" and not any(
        f.get("estado") == SIN_PROCESAR for f in previo.get("fallidas", []))


def decidir(mid: str, carpeta, cfg: dict, reporte=None, medidor=medir, fecha_tanda=None,
            sacados=None, kits=None) -> tuple:
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
        if mismo and previo.get("estado") in (APTO, EN_ESPERA) and _evaluado_de_verdad(previo):
            return dict(previo, sin_cambios=previo.get("estado") == EN_ESPERA), False
        return dict(base, estado=EN_ESPERA, listas=[], faltan=MINIMO_LISTA, fallidas=antes["fallidas"],
                    motivo="por procesar"), True

    ev = evaluar(mid, carpeta, reporte, cfg, kit=mid in (kits or {}))
    vistas = {x["foto"] for x in antes["fallidas"]}
    fallidas = antes["fallidas"] + [f for f in ev["fallidas"] if f["foto"] not in vistas]
    if len(ev["listas"]) >= MINIMO_LISTA:
        return dict(base, estado=APTO, listas=ev["listas"], faltan=0, fallidas=fallidas, motivo=None), False
    faltan = MINIMO_LISTA - len(ev["listas"])
    return dict(base, estado=EN_ESPERA, listas=ev["listas"], faltan=faltan, fallidas=fallidas,
                motivo=f"salieron {len(ev['listas'])} fotos LISTA y necesita {MINIMO_LISTA}"), False


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
    """(fecha de la lista, ids de los modelos que esperan fotos)."""
    datos = json.loads(Path(ruta).read_text(encoding="utf-8"))
    return datos.get("fechaLista"), list(dict.fromkeys(datos.get("modelos") or []))


def informe(estados: list) -> str:
    lineas = []
    aptos = [e for e in estados if e["estado"] == APTO]
    fuera = [e for e in estados if e["estado"] == FUERA]
    espera = [e for e in estados if e["estado"] == EN_ESPERA]
    lineas.append(f"Tanda: {len(aptos)} aptos, {len(espera)} en espera, {len(fuera)} fuera de la tanda")
    for e in espera:
        nota = " · sin fotos nuevas desde el último intento" if e.get("sin_cambios") else ""
        lineas.append(f"\nEN ESPERA  {e['modelo']}: faltan {e['faltan']} LISTA ({e.get('motivo')}){nota}")
        for f in e.get("fallidas", []):
            lineas.append(f"  - {f['foto']} [{f['estado']}] {f['motivo']}")
    for e in fuera:
        lineas.append(f"\nFUERA      {e['modelo']}: {e.get('motivo')}")
    lineas.append("\nTANDA COMPLETA: revisa a ojo las hojas de las LISTA y sigue con /fichas-de-tecnologia"
                  if not espera
                  else "\nLA TANDA ESPERA FOTOS: deja las nuevas en «Fotos originales» y vuelve a correrla")
    return "\n".join(lineas)


def nombres_de(raiz: Path, ids) -> list:
    """Los nombres con que procesar.py conoce las fotos de esos modelos, para `--solo`."""
    nombres = []
    for carpeta in modelos(raiz, set(ids)).values():
        buenas, _ = unicas(carpeta)
        nombres.extend(slug(f.stem) for f in buenas)
    return nombres


def correr_tanda(raiz: Path, ids: list, cfg: dict, procesar_fn, solo_devolver=False, medidor=medir,
                 fecha_tanda=None) -> list:
    """Decide, procesa lo que haga falta, devuelve y vuelve a decidir. Escribe el estado de cada modelo.

    `procesar_fn(nombres)` corre el retoque sobre esas fotos y devuelve False si quedó en
    segundo plano."""
    por_id = modelos(raiz, set(ids))
    sacados = leer_sacados(raiz)
    kits = leer_kits(raiz)
    decisiones = {}
    if not solo_devolver:
        decisiones = {mid: decidir(mid, por_id.get(mid), cfg, None, medidor, fecha_tanda, sacados)
                      for mid in ids}
        a_procesar = [mid for mid, (_, p) in decisiones.items() if p]
        if a_procesar:
            copiadas, errores = preparar(raiz, set(a_procesar))
            for e in errores:
                print(f"[error] {e}")
            print(f"{copiadas} foto(s) nuevas o cambiadas; se procesan enteros {len(a_procesar)} modelo(s)")
            if procesar_fn(nombres_de(raiz, a_procesar)) is False:
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


def retoque_en_curso(raiz: Path):
    """El avance de un retoque en segundo plano que no ha terminado, o None."""
    ruta = Path(raiz) / TRABAJO / "salida" / ".trabajo" / "avance.json"
    try:
        datos = json.loads(ruta.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return None
    return datos if datos.get("estado") in ("iniciando", "procesando") else None


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("fichas", nargs="?", default="catalogo/4-entregables/fichas")
    ap.add_argument("--tanda", help="por-proponer.json: el bloque 2, solo esos modelos y con la compuerta")
    ap.add_argument("--devolver", action="store_true",
                    help="solo copia a cada modelo lo que ya está en _estudio/salida (tras un --segundo-plano)")
    ap.add_argument("--segundo-plano", action="store_true", help="lanza procesar.py desacoplado y sale")
    ap.add_argument("--sacar", metavar="ID", help="con --tanda: saca ese modelo de la tanda")
    ap.add_argument("--kit", metavar="ID", help="con --tanda: ese modelo se vende con piezas sueltas, y "
                                                "sus fotos con varias piezas cuentan como LISTA")
    ap.add_argument("--quitar-kit", metavar="ID", help="con --tanda: deshace un --kit")
    ap.add_argument("--sin-huecos", metavar="ID", help="ese modelo no tiene huecos pasantes aunque su categoría "
                                                       "los admita: lo que el recorte encierre vuelve al producto")
    ap.add_argument("--quitar-sin-huecos", metavar="ID", help="deshace un --sin-huecos")
    ap.add_argument("--motivo", help="con --sacar, --kit o --sin-huecos: por qué, queda escrito")
    a = ap.parse_args()
    raiz = Path(a.fichas)
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
        fecha, ids = leer_tanda(Path(a.tanda))
        cfg = cargar_config()
        if a.sacar:
            if a.sacar not in ids:
                print(f"{a.sacar} no está en la tanda.")
                return 1
            print(informe([sacar(raiz, modelos(raiz, {a.sacar}).get(a.sacar), a.sacar, a.motivo or "", fecha)]))
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
        estados = correr_tanda(raiz, ids, cfg, procesar_fn, solo_devolver=a.devolver, fecha_tanda=fecha)
        if not estados:
            return SALIDA_EN_ESPERA   # en segundo plano: la tanda todavía no está completa
        print(informe(estados))
        viejas = salidas.con_retoque_anterior(leer_reporte(raiz).get("fotos", []), set(ids))
        if viejas:
            print(f"\n[aviso] {len(viejas)} foto(s) de la tanda se procesaron con un retoque anterior al "
                  f"{salidas.VERSION_RETOQUE}: la tanda no las rehace sola. Para rehacerlas sin mover el lienzo: "
                  "procesar.py ... --solo <nombres> --conservar-lienzo, y después esta orden con --devolver.")
        fuera = fuera_de_la_tanda(raiz, ids)
        if fuera:
            print(f"\n[aviso] {len(fuera)} carpeta(s) con fotos que no están en la tanda y no se procesaron "
                  "(no están en por-proponer.json): " + ", ".join(fuera))
        print(f"\nHojas de revisión: {raiz / TRABAJO / 'salida'}")
        return SALIDA_EN_ESPERA if any(e["estado"] == EN_ESPERA for e in estados) else 0

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
                          ("sin original (se borró o se renombró)", r["sin_original"])):
        if lista:
            print(f"\n{titulo}:")
            for x in lista:
                print(f"  - {x}")
    print(f"\nHojas de revisión: {raiz / TRABAJO / 'salida'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
