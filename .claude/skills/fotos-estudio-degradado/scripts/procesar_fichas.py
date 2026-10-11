#!/usr/bin/env python3
"""
Procesa las fotos de las carpetas de modelo de `catalogo/entregables/fichas/`.

Decisión del 08/10/2026: las fotos de cada modelo viven en su carpeta, y cada
foto retocada queda junto a su original, **con el mismo nombre** (la extensión
puede cambiar: la maestra siempre es JPEG):

    fichas/Samsung Galaxy S25 Ultra/
    ├── Fotos originales/samsung-galaxy-s25-ultra_1.png
    ├── Fotos procesadas/samsung-galaxy-s25-ultra_1.jpg        la maestra
    │                    web/1200/samsung-galaxy-s25-ultra_1.avif   los anchos para el sitio
    └── estado-fotos.json                                      con --tanda: apto, en espera o fuera

Uso:
    python3 procesar_fichas.py catalogo/entregables/fichas --tanda catalogo/por-proponer.json
    python3 procesar_fichas.py catalogo/entregables/fichas --tanda catalogo/por-proponer.json --devolver
    python3 procesar_fichas.py catalogo/entregables/fichas --tanda catalogo/por-proponer.json \\
        --sacar <id del modelo> --motivo "sin material oficial"

No reimplementa nada del estilo: arma un árbol de trabajo en `<fichas>/_estudio/`
—`crudas/<id del modelo>/` con copias de las originales—, corre `procesar.py` sobre
él con `--por-producto --variantes --nuevas` y devuelve cada resultado a la carpeta
de su modelo. Las hojas de revisión y el reporte quedan en `_estudio/salida/`.

**La tanda es el bloque 2 de la ingesta de tecnología** (ADR-0079). Con `--tanda`
solo se procesan los modelos de `por-proponer.json` —los que el sitio no vende—, y
cada uno necesita `MINIMO_LISTA` fotos LISTA en un lienzo de por lo menos
`LIENZO_MINIMO` px. Una REVISAR no cuenta, aunque se apruebe a ojo: decisión del
negocio del 10/10/2026. El modelo que no llega queda **en espera**, con la razón de
cada foto que falló, y la tanda no se da por terminada hasta que todos pasen o la
persona saque alguno a mano con `--sacar`.

Para no gastar retoques en vano:

- **Antes de procesar** se cuentan las originales que pueden salir LISTA: una foto
  cuyo lado mayor no alcanza `lienzo_minimo × ocupación / ampliación_revisar` no
  puede, porque el producto nunca mide más que la foto. Si no hay cuatro, el
  modelo espera sin procesarse.
- **Un modelo ya decidido no se vuelve a procesar** mientras sus originales no
  cambien: `estado-fotos.json` guarda la huella de la carpeta. Si la persona dice
  que dejó fotos nuevas y la huella es la misma, la salida lo dice.

Sale con 0 si la tanda está completa y con 3 si algún modelo espera fotos.

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
EXT_FOTO = {".jpg", ".jpeg", ".png", ".webp", ".heic", ".heif"}
CONFIG = Path(__file__).resolve().parent.parent / "config.json"

# Decisión del negocio del 10/10/2026 (ADR-0079): cuatro fotos LISTA por modelo.
MINIMO_LISTA = 4
# Y en un lienzo de por lo menos esto: 1200 es la mayor variante que sirve el sitio
# (tools/subida-de-imagenes.mjs); por debajo, la ficha se ve con menos resolución de la que
# pide. No va en config.json porque no cambia cómo se retoca, y allá entraría en la huella de
# la configuración y obligaría a reprocesar el catálogo entero.
LIENZO_MINIMO = 1200

APTO, EN_ESPERA, FUERA = "apto", "en_espera", "fuera_de_tanda"
SALIDA_EN_ESPERA = 3


def slug(texto: str) -> str:
    """El mismo de procesar.py, que nombra con él cada salida. Se copia porque
    procesar.py importa OpenCV al cargarse; si allá cambia, aquí también."""
    t = unicodedata.normalize("NFKD", texto).encode("ascii", "ignore").decode()
    t = re.sub(r"[^A-Za-z0-9]+", "-", t).strip("-").lower()
    return t or "foto"


def id_de_carpeta(carpeta: Path):
    for f in carpeta.glob("*-ficha.txt"):
        return f.name[: -len("-ficha.txt")]
    return slug(carpeta.name)


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
    return sorted(f for f in (carpeta / ORIGINALES).iterdir() if f.is_file() and f.suffix.lower() in EXT_FOTO)


def preparar(raiz: Path, solo=None) -> tuple:
    """Copia las originales a `_estudio/crudas/<id>/`. Devuelve (copiadas, errores).

    Dos originales cuyo nombre da el mismo slug —«x_1.jpg» y «x-1.png»— saldrían
    con el mismo nombre y uno pisaría al otro: se informa y ninguno se procesa.
    """
    crudas = Path(raiz) / TRABAJO / "crudas"
    copiadas, errores = 0, []
    for mid, carpeta in modelos(raiz, solo).items():
        vistos = {}
        destino = crudas / mid
        for f in originales(carpeta):
            clave = slug(f.stem)
            if clave in vistos:
                errores.append(f"{carpeta.name}: «{vistos[clave]}» y «{f.name}» quedarían con el mismo nombre")
                continue
            vistos[clave] = f.name
            copia = destino / f.name
            if copia.is_file() and copia.stat().st_size == f.stat().st_size and copia.stat().st_mtime >= f.stat().st_mtime:
                continue
            destino.mkdir(parents=True, exist_ok=True)
            shutil.copy2(f, copia)
            copiadas += 1
    return copiadas, errores


def leer_reporte(raiz: Path) -> dict:
    ruta = Path(raiz) / TRABAJO / "salida" / "reporte.json"
    return json.loads(ruta.read_text(encoding="utf-8")) if ruta.is_file() else {"fotos": []}


def devolver(raiz: Path) -> dict:
    """Copia cada resultado del reporte a la carpeta de su modelo, con el nombre de su original."""
    raiz = Path(raiz)
    salida = raiz / TRABAJO / "salida"
    reporte = leer_reporte(raiz)
    por_id = modelos(raiz)
    resumen = {"devueltas": 0, "repetir": [], "revisar": [], "sin_original": []}
    for foto in reporte.get("fotos", []):
        carpeta = por_id.get(foto.get("grupo") or "")
        original = None
        if carpeta:
            original = next((f for f in originales(carpeta) if slug(f.stem) == foto["nombre"]), None)
        if original is None:
            resumen["sin_original"].append(f"{foto.get('grupo')}/{foto['nombre']}")
            continue
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
        for w in sal.get("web", []):
            ruta = Path(w["ruta"])
            destino = procesadas / WEB / ruta.parent.name / f"{original.stem}{ruta.suffix}"
            destino.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(salida / ruta, destino)
        resumen["devueltas"] += 1
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
    viables, fallidas = [], []
    for f in originales(carpeta):
        medida = medidor(f)
        if medida is not None and max(medida) < minimo:
            fallidas.append({"foto": f.name, "estado": "PEQUEÑA",
                             "motivo": f"mide {max(medida)} px de lado mayor y necesita por lo menos {minimo} px "
                                       f"para salir LISTA en un lienzo de {cfg['lienzo_minimo']} px"})
        else:
            viables.append(f.name)
    return {"viables": viables, "fallidas": fallidas}


def huella_de_originales(carpeta: Path) -> str:
    """Cambia si se añade, quita, renombra o reemplaza una original."""
    h = hashlib.sha256()
    for f in originales(carpeta):
        st = f.stat()
        h.update(f"{f.name}|{st.st_size}|{int(st.st_mtime)}\n".encode())
    return h.hexdigest()


def leer_estado(carpeta: Path):
    ruta = carpeta / ESTADO
    return json.loads(ruta.read_text(encoding="utf-8")) if ruta.is_file() else None


def escribir_estado(carpeta: Path, estado: dict) -> None:
    (carpeta / ESTADO).write_text(json.dumps(estado, ensure_ascii=False, indent=2) + "\n",
                                  encoding="utf-8", newline="")


def ahora() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def evaluar(mid: str, carpeta: Path, reporte: dict, cfg: dict) -> dict:
    """Las fotos LISTA del modelo según el reporte, y la razón de cada una que no lo es."""
    por_nombre = {f["nombre"]: f for f in reporte.get("fotos", []) if f.get("grupo") == mid}
    listas, fallidas = [], []
    for original in originales(carpeta):
        foto = por_nombre.get(slug(original.stem))
        if foto is None:
            fallidas.append({"foto": original.name, "estado": "SIN PROCESAR",
                             "motivo": "no está en el reporte del retoque: vuelve a correr la tanda"})
            continue
        estado = foto.get("estado")
        lienzo = int((foto.get("lienzo") or [0])[0])
        motivos = "; ".join(m.get("texto") or m.get("codigo", "") for m in foto.get("motivos", []))
        if estado == "LISTA" and foto.get("revisada"):
            fallidas.append({"foto": original.name, "estado": "REVISAR",
                             "motivo": "salió REVISAR y se aprobó a ojo; para la tanda solo cuenta una LISTA"
                                       + (f" ({motivos})" if motivos else "")})
        elif estado == "LISTA" and lienzo < int(cfg["lienzo_minimo"]):
            fallidas.append({"foto": original.name, "estado": "LIENZO",
                             "motivo": f"salió en un lienzo de {lienzo} px; el sitio pide por lo menos "
                                       f"{cfg['lienzo_minimo']} px, y el lienzo lo fija la mejor foto del modelo"})
        elif estado == "LISTA":
            listas.append(f"{original.stem}.jpg")
        else:
            fallidas.append({"foto": original.name, "estado": estado or "?", "motivo": motivos or "sin motivo"})
    return {"listas": listas, "fallidas": fallidas}


def decidir(mid: str, carpeta, cfg: dict, reporte=None, medidor=medir) -> tuple:
    """(estado del modelo, ¿hay que procesarlo?). Sin `reporte` solo decide lo que se sabe antes de procesar."""
    if carpeta is None:
        return {"modelo": mid, "estado": EN_ESPERA, "listas": [], "faltan": MINIMO_LISTA,
                "fallidas": [], "motivo": "no tiene carpeta: corre el paso 5 de /listas-de-proveedor",
                "fecha": ahora()}, False
    huella = huella_de_originales(carpeta)
    previo = leer_estado(carpeta)
    if previo and previo.get("huella") == huella and previo.get("estado") in (APTO, FUERA):
        return previo, False
    if previo and previo.get("huella") == huella and previo.get("estado") == EN_ESPERA and reporte is None:
        sigue = dict(previo, sin_cambios=True)
        return sigue, False

    antes = revisar_antes(carpeta, cfg, medidor)
    base = {"modelo": mid, "huella": huella, "fecha": ahora(), "sin_cambios": False}
    if len(antes["viables"]) < MINIMO_LISTA:
        faltan = MINIMO_LISTA - len(antes["viables"])
        return dict(base, estado=EN_ESPERA, listas=[], faltan=faltan, fallidas=antes["fallidas"],
                    motivo=f"tiene {len(antes['viables'])} originales que pueden salir LISTA y necesita "
                           f"{MINIMO_LISTA}: no se procesó"), False
    if reporte is None:
        return dict(base, estado=EN_ESPERA, listas=[], faltan=MINIMO_LISTA, fallidas=antes["fallidas"],
                    motivo="por procesar"), True

    ev = evaluar(mid, carpeta, reporte, cfg)
    fallidas = antes["fallidas"] + [f for f in ev["fallidas"] if f["foto"] not in
                                    {x["foto"] for x in antes["fallidas"]}]
    if len(ev["listas"]) >= MINIMO_LISTA:
        return dict(base, estado=APTO, listas=ev["listas"], faltan=0, fallidas=fallidas, motivo=None), False
    faltan = MINIMO_LISTA - len(ev["listas"])
    return dict(base, estado=EN_ESPERA, listas=ev["listas"], faltan=faltan, fallidas=fallidas,
                motivo=f"salieron {len(ev['listas'])} fotos LISTA y necesita {MINIMO_LISTA}"), False


def sacar(carpeta: Path, mid: str, motivo: str) -> dict:
    """La única salida de la espera, y es de la persona: el modelo queda fuera de esta tanda.
    Si después deja fotos nuevas, la huella cambia y el modelo vuelve a intentarse."""
    if not motivo or not motivo.strip():
        raise ValueError("Sacar un modelo de la tanda pide el motivo: queda escrito en su estado.")
    previo = leer_estado(carpeta) or {}
    estado = dict(previo, modelo=mid, estado=FUERA, huella=huella_de_originales(carpeta),
                  motivo=f"sacado de la tanda por la persona: {motivo.strip()}", fecha=ahora())
    escribir_estado(carpeta, estado)
    return estado


def leer_tanda(ruta: Path) -> list:
    datos = json.loads(Path(ruta).read_text(encoding="utf-8"))
    return list(dict.fromkeys(datos.get("modelos") or []))


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
    lineas.append("\nTANDA COMPLETA: sigue con /fichas-de-tecnologia" if not espera
                  else "\nLA TANDA ESPERA FOTOS: deja las nuevas en «Fotos originales» y vuelve a correrla")
    return "\n".join(lineas)


def correr_tanda(raiz: Path, ids: list, cfg: dict, procesar_fn, solo_devolver=False, medidor=medir) -> list:
    """Decide, procesa lo que haga falta, devuelve y vuelve a decidir. Escribe el estado de cada modelo."""
    por_id = modelos(raiz, set(ids))
    decisiones = {}
    if not solo_devolver:
        decisiones = {mid: decidir(mid, por_id.get(mid), cfg, None, medidor) for mid in ids}
        a_procesar = {mid for mid, (_, p) in decisiones.items() if p}
        if a_procesar:
            copiadas, errores = preparar(raiz, a_procesar)
            for e in errores:
                print(f"[error] {e}")
            print(f"{copiadas} foto(s) nuevas o cambiadas para procesar en {len(a_procesar)} modelo(s)")
            if procesar_fn() is False:
                return []   # quedó en segundo plano: se termina con --devolver
    devolver(raiz)
    reporte = leer_reporte(raiz)
    estados = []
    for mid in ids:
        estado, _ = decidir(mid, por_id.get(mid), cfg, reporte, medidor)
        if mid in decisiones and decisiones[mid][0].get("sin_cambios"):
            # Se dice aunque se reevalúe: quien confirmó fotos nuevas tiene que saber que no llegaron.
            estado = dict(estado, sin_cambios=True)
        if por_id.get(mid) is not None:
            escribir_estado(por_id[mid], {k: v for k, v in estado.items() if k != "sin_cambios"})
        estados.append(estado)
    return estados


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("fichas", nargs="?", default="catalogo/entregables/fichas")
    ap.add_argument("--tanda", help="por-proponer.json: el bloque 2, solo esos modelos y con la compuerta")
    ap.add_argument("--devolver", action="store_true",
                    help="solo copia a cada modelo lo que ya está en _estudio/salida (tras un --segundo-plano)")
    ap.add_argument("--segundo-plano", action="store_true", help="lanza procesar.py desacoplado y sale")
    ap.add_argument("--sacar", metavar="ID", help="con --tanda: saca ese modelo de la tanda")
    ap.add_argument("--motivo", help="con --sacar: por qué, queda escrito en su estado")
    a = ap.parse_args()
    raiz = Path(a.fichas)
    orden = [sys.executable, str(Path(__file__).with_name("procesar.py")), str(raiz / TRABAJO / "crudas"),
             "-o", str(raiz / TRABAJO / "salida"), "--por-producto", "--variantes", "--nuevas"]

    def procesar_fn():
        if a.segundo_plano:
            subprocess.run(orden + ["--segundo-plano"], check=True)
            extra = f' --tanda "{a.tanda}"' if a.tanda else ""
            print(f"Cuando termine: python3 {Path(__file__).name} \"{raiz}\"{extra} --devolver")
            return False
        subprocess.run(orden, check=True)
        return True

    if a.tanda:
        ids = leer_tanda(Path(a.tanda))
        cfg = cargar_config()
        if a.sacar:
            carpeta = modelos(raiz, {a.sacar}).get(a.sacar)
            if a.sacar not in ids or carpeta is None:
                print(f"{a.sacar} no está en la tanda o no tiene carpeta.")
                return 1
            print(informe([sacar(carpeta, a.sacar, a.motivo or "")]))
            return 0
        estados = correr_tanda(raiz, ids, cfg, procesar_fn, solo_devolver=a.devolver)
        if not estados:
            return 0
        print(informe(estados))
        print(f"\nHojas de revisión: {raiz / TRABAJO / 'salida'}")
        return SALIDA_EN_ESPERA if any(e["estado"] == EN_ESPERA for e in estados) else 0

    if not a.devolver:
        copiadas, errores = preparar(raiz)
        for e in errores:
            print(f"[error] {e}")
        print(f"{copiadas} foto(s) nuevas o cambiadas para procesar")
        if procesar_fn() is False:
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
