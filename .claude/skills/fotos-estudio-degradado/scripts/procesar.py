#!/usr/bin/env python3
"""
Tomas de estudio con el estilo de TecnoSport: fondo degradado gris, sombra de contacto y encuadre fijo.

Por foto: aísla el producto, corrige sólo su luminancia (con límites), lo escala para que
su lado mayor ocupe el 85 % del lienzo, lo centra sobre el fondo plantilla con un
sombra de contacto y exporta la maestra JPEG y los archivos web (AVIF a 10 bits si
hay avifenc). Al final escribe reporte.json y regenera las hojas de revisión.

Uso:
    python procesar.py FOTOS_O_CARPETAS... -o SALIDA [opciones]

Ejemplos:
    python procesar.py /mnt/user-data/uploads -o /home/claude/estudio --zip
    python procesar.py fotos/ -o estudio/ --mapa nombres.csv --variantes
    python procesar.py -o estudio/ --solo tenis-runner-blanco        # reprocesa por nombre
    python procesar.py fotos/ -o estudio/ --segundo-plano             # lotes de más de 10 fotos
    python procesar.py --avance estudio/

Los parámetros viven en config.json; --ajuste clave=valor los cambia para una corrida
(p. ej. --ajuste avif_10bits=nunca). Cambiarlos para algunas fotos rompe la uniformidad
del catálogo, y el reporte lo advierte.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import importlib.util
import json
import math
import os
import platform
import re
import subprocess
import sys
import time
import traceback
import unicodedata
from pathlib import Path

AQUI = Path(__file__).resolve().parent
sys.path.insert(0, str(AQUI))

EXTENSIONES = {".jpg", ".jpeg", ".png", ".heic", ".heif", ".webp"}
CLAVES_SALIDA = ("lienzo", "anchos_variantes", "formatos_web", "carpeta_maestras", "carpeta_web")
VERSION = "1.0"


def utf8() -> None:
    for flujo in (sys.stdout, sys.stderr):
        try:
            flujo.reconfigure(encoding="utf-8", errors="replace")
        except Exception:
            pass


import entorno  # noqa: E402  (sólo biblioteca estándar)

if __name__ == "__main__":
    entorno.exigir()

import cv2  # noqa: E402
import numpy as np  # noqa: E402
from PIL import Image, UnidentifiedImageError, features  # noqa: E402

import hojas  # noqa: E402
import imagen  # noqa: E402
import reporte  # noqa: E402


# =========================================================================== utilidades

def slug(texto: str) -> str:
    t = unicodedata.normalize("NFKD", texto).encode("ascii", "ignore").decode()
    t = re.sub(r"[^A-Za-z0-9]+", "-", t).strip("-").lower()
    return t or "foto"


def recolectar(entradas: list[str], excluir: Path) -> list[tuple[Path, str]]:
    """Devuelve (ruta, grupo). El grupo es la subcarpeta de la entrada en que estaba la foto —el
    producto, cuando se procesa un árbol como `crudas/`— y queda vacío para fotos sueltas."""
    rutas, vistos = [], set()
    for e in entradas:
        p = Path(e)
        if p.is_dir():
            candidatos = sorted(x for x in p.rglob("*") if x.is_file() and x.suffix.lower() in EXTENSIONES
                                and not any(parte.startswith(".") for parte in x.relative_to(p).parts))
        elif p.is_file():
            if p.suffix.lower() not in EXTENSIONES:
                print(f"[aviso] formato no admitido, se omite: {p.name} (usa JPG, PNG, HEIC o WebP)")
                continue
            candidatos = [p]
        else:
            print(f"[aviso] no existe: {e}")
            continue
        for x in candidatos:
            r = x.resolve()
            if r in vistos or excluir == r or excluir in r.parents:
                continue
            vistos.add(r)
            padres = x.relative_to(p).parts[:-1] if p.is_dir() else ()
            rutas.append((x, slug(padres[-1]) if padres else ""))
    return rutas


def estimar_lado_producto(ruta: Path) -> int | None:
    """Lado mayor del contenido, antes de recortar: sirve para elegir el lienzo sin gastar el recorte.

    Mide la caja de lo que no es fondo claro. Si el fondo no es claro —una foto sobre una mesa—
    devuelve la imagen entera, que es la estimación conservadora: el lienzo sale mayor, no menor,
    y la ampliación real la sigue reportando el aviso de escala tras el recorte.
    """
    try:
        with Image.open(ruta) as im:
            W0, H0 = im.size
            if not W0 or not H0:
                return None
            g = im.convert("L")
            if max(W0, H0) > 800:
                k = 800 / max(W0, H0)
                g = g.resize((max(1, round(W0 * k)), max(1, round(H0 * k))), Image.BILINEAR)
            arr = np.asarray(g, np.int16)
    except Exception:
        return None
    if arr.size == 0:
        return None
    factor = max(W0, H0) / max(arr.shape)
    cont = arr < 247
    if cont.sum() < 20:
        return max(W0, H0)
    ys, xs = np.nonzero(cont)
    lado = max(int(ys.max() - ys.min()) + 1, int(xs.max() - xs.min()) + 1)
    return max(1, int(round(lado * factor)))


def elegir_lienzo(lados: list[int], cfg: dict) -> int:
    """Mayor escalón de `lienzos_escala` que el producto alcanza sin pasar de `ampliacion_tolerada`.

    Si no llega a ninguno devuelve el menor: un producto con poco material se publica al máximo
    que ese material da, en vez de quedarse fuera del catálogo.
    """
    escala = sorted({int(x) for x in cfg.get("lienzos_escala") or []}, reverse=True)
    if not escala:
        return int(cfg["lienzo"][0])
    mejor = max([x for x in lados if x], default=0)
    ocupacion, tolerada = float(cfg["ocupacion"]), float(cfg.get("ampliacion_tolerada", 1.0))
    for lado in escala:
        if mejor * tolerada >= lado * ocupacion:
            return lado
    return escala[-1]


def leer_mapa(ruta: str) -> dict[str, str]:
    """CSV archivo,nombre (acepta coma, punto y coma o tabulador, con o sin encabezado)."""
    mapa: dict[str, str] = {}
    with open(ruta, newline="", encoding="utf-8-sig") as fh:
        muestra = fh.read(4096)
        fh.seek(0)
        try:
            dialecto = csv.Sniffer().sniff(muestra, delimiters=",;\t")
        except csv.Error:
            dialecto = csv.excel
        for fila in csv.reader(fh, dialecto):
            fila = [c.strip() for c in fila]
            if len(fila) < 2 or not fila[0] or not fila[1] or fila[0].lower() == "archivo":
                continue
            mapa[fila[0].lower()] = fila[1]
            mapa.setdefault(Path(fila[0]).stem.lower(), fila[1])
    return mapa


def config_publica(cfg: dict) -> dict:
    return {k: v for k, v in cfg.items() if not k.startswith("_")}


def huella_config(cfg: dict) -> str:
    texto = json.dumps(config_publica(cfg), sort_keys=True, ensure_ascii=False)
    return hashlib.sha1(texto.encode("utf-8")).hexdigest()[:12]


def vivo(pid: int) -> bool:
    if not pid:
        return False
    if os.name == "nt":  # os.kill(pid, 0) en Windows enviaría Ctrl+C: se consulta con la API
        import ctypes
        k32 = ctypes.windll.kernel32
        h = k32.OpenProcess(0x1000, False, int(pid))
        if not h:
            return False
        codigo = ctypes.c_ulong()
        ok = k32.GetExitCodeProcess(h, ctypes.byref(codigo))
        k32.CloseHandle(h)
        return bool(ok) and codigo.value == 259
    try:
        os.kill(int(pid), 0)
        return True
    except ProcessLookupError:
        return False
    except PermissionError:
        return True


# =========================================================================== contexto del lote

class Contexto:
    def __init__(self, cfg: dict, raiz: Path, huella: str, avif10: dict | None):
        self.cfg, self.raiz, self.huella_config, self.avif10 = cfg, raiz, huella, avif10
        self.fpx = imagen.factor_px(cfg)
        self.ancho, self.alto = int(cfg["lienzo"][0]), int(cfg["lienzo"][1])
        fondo, self.origen_fondo = imagen.cargar_fondo(cfg)
        self.huella_fondo = imagen.huella_pixeles(fondo)
        self.fondo = fondo.astype(np.float32) / 255.0
        ideal = imagen.degradado_ideal(self.ancho, self.alto, cfg["fondo_centro"], cfg["fondo_esquinas"],
                                       float(cfg.get("fondo_radio_interior", 0.0)))
        self.ideal = (ideal / 255.0).astype(np.float32)
        self.sombra_rgb = np.array(imagen.hex_a_rgb(cfg["sombra_color"]), np.float32) / 255.0
        self.trabajo = raiz / reporte.TRABAJO
        self.grupo = ""          # producto en curso; vacío = salida plana
        self._rec = None

    def carpeta_maestra(self) -> Path:
        """<raiz>/<producto>/maestra en modo por producto; <raiz>/maestras en el plano."""
        return self.raiz / self.grupo / "maestra" if self.grupo else self.raiz / self.cfg["carpeta_maestras"]

    def ruta_web(self, nombre: str, ancho: int, fmt: str) -> Path:
        """Por producto la carpeta dice el ancho y el archivo no lo repite."""
        if self.grupo:
            return self.raiz / self.grupo / str(ancho) / f"{nombre}.{fmt}"
        return self.raiz / self.cfg["carpeta_web"] / f"{nombre}-{ancho}.{fmt}"

    @property
    def rec(self) -> imagen.Recortador:
        if self._rec is None:
            print(f"    cargando el modelo de recorte {self.cfg['modelo_recorte']} "
                  "(la primera vez se descarga desde GitHub)…", flush=True)
            self._rec = imagen.Recortador(self.cfg["modelo_recorte"])
        return self._rec

    def rel(self, ruta: Path) -> str:
        return ruta.relative_to(self.raiz).as_posix()


# =========================================================================== una foto

def procesar_foto(ctx: Contexto, ruta: Path, nombre: str, previo: dict) -> dict:
    cfg, fpx, W, H = ctx.cfg, ctx.fpx, ctx.ancho, ctx.alto
    t0 = time.time()
    motivos, avisos, notas = [], [], []

    def motivo(estado: str, codigo: str, texto: str) -> None:
        motivos.append({"estado": estado, "codigo": codigo, "texto": texto})

    huella = imagen.huella_archivo(ruta)
    misma = bool(previo) and previo.get("huella_origen") == huella
    reg = {"nombre": nombre, "archivo": ruta.name, "ruta_origen": str(ruta.resolve()), "huella_origen": huella,
           "procesada": reporte.ahora(), "huella_config": ctx.huella_config,
           "marcas": list(previo.get("marcas", [])) if misma else [],
           "exclusiones": list(previo.get("exclusiones", [])) if misma else [],
           "revisada": None, "salidas": {}}
    if previo and not misma:
        notas.append("Es otra foto para este nombre: se descartaron las marcas y exclusiones de la anterior.")

    def cerrar() -> dict:
        reg["motivos_auto"], reg["avisos"], reg["notas"] = motivos, avisos, notas
        reg["segundos"] = round(time.time() - t0, 1)
        return reg

    # ---- 5.1 preparar: orientación EXIF, perfil a sRGB, alfa útil
    foto = imagen.cargar_foto(ruta)
    rgb, alfa_png, info = foto["rgb"], foto["alfa"], foto["info"]
    reg["entrada"] = info
    Ho, Wo = rgb.shape[:2]
    if info.get("formato") in ("HEIF", "WEBP"):
        notas.append(f"Convertida desde {info['formato']} al inicio.")
    if info.get("orientacion_exif", 1) not in (0, 1):
        notas.append(f"Se aplicó la orientación EXIF ({info['orientacion_exif']}) antes de descartar los metadatos.")
    perfil = info.get("perfil_icc")
    if perfil and "srgb" not in perfil.lower():
        notas.append(f"Perfil de color «{perfil}» convertido a sRGB.")
    if info.get("perfil_icc_error"):
        avisos.append("El perfil de color de la foto está dañado: se asumió sRGB.")
    calidad_in = info.get("calidad_jpeg_estimada")
    if calidad_in is not None and calidad_in < 85:
        notas.append(f"JPEG recomprimido (calidad ≈ {calidad_in}), típico de fotos reenviadas por WhatsApp: "
                     "sus artefactos se conservan.")
    mini_dir = ctx.trabajo / "miniaturas"
    mini_dir.mkdir(parents=True, exist_ok=True)
    imagen.miniatura(rgb, hojas.MINI).save(hojas.ruta_miniatura(ctx.raiz, nombre, "antes"), quality=90)
    hojas.ruta_miniatura(ctx.raiz, nombre, "despues").unlink(missing_ok=True)

    # ---- 5.2 aislar el producto
    if alfa_png is not None:
        m = alfa_png.copy()
        metodo = "alfa del PNG"
        notas.append("El PNG ya traía transparencia: se usó su recorte sin pasar por el modelo.")
    else:
        m = imagen.mascara_dos_pasadas(ctx.rec, rgb)
        metodo = f"rembg:{cfg['modelo_recorte']}"
    if reg["exclusiones"]:
        area = float((m > 0.5).sum())
        quitado = 0.0
        for zona in reg["exclusiones"]:
            x0, y0, x1, y1 = [int(round(v)) for v in zona]
            x0, x1 = sorted((max(0, min(Wo, x0)), max(0, min(Wo, x1))))
            y0, y1 = sorted((max(0, min(Ho, y0)), max(0, min(Ho, y1))))
            quitado += float((m[y0:y1, x0:x1] > 0.5).sum())
            m[y0:y1, x0:x1] = 0
        notas.append(f"Se excluyeron {len(reg['exclusiones'])} zona(s) marcada(s) a mano "
                     f"({100 * quitado / max(area, 1):.1f} % del recorte).")
    m, adornos = imagen.quitar_adornos(m, rgb, cfg)
    if adornos["adornos_quitados"]:
        detalle = ", ".join(f"{x['area_pct']:.2f} %" for x in adornos["adornos"])
        notas.append(f"Se quitaron {adornos['adornos_quitados']} elemento(s) ajenos al producto "
                     f"({detalle} del cuerpo): están separados, son pequeños y su color no aparece en él "
                     "(destellos de render y adornos parecidos). Confirma que no eran parte del producto.")
    m, islas = imagen.limpiar_islas(m, cfg["isla_min_fraccion"], cfg["isla_aviso_fraccion"])
    a = m if alfa_png is not None else imagen.niveles_alfa(m)
    recorte = {"metodo": metodo, "toca_borde": False, "lados": [], **islas, **adornos}
    reg["recorte"] = recorte
    caja0 = imagen.caja(a, cfg["alfa_caja_min"])
    if caja0 is None:
        motivo("REPETIR", "sin_producto", "No se encontró el producto en la foto (el recorte quedó vacío).")
        return cerrar()

    lados = imagen.lados_tocados(a, cfg["borde_corte_fraccion"])
    recorte["lados"] = lados
    if alfa_png is not None:
        if imagen.al_ras(a, cfg["alfa_caja_min"]):
            notas.append("El PNG viene recortado al ras del producto.")
        elif lados:
            motivo("REVISAR", "png_borde", f"El recorte del PNG llega al borde ({', '.join(lados)}): "
                                           "confirma que el producto está completo.")
    elif lados:
        recorte["toca_borde"] = True
        motivo("REPETIR", "cortado", f"El producto sale del encuadre por {', '.join(lados)}: está cortado o el "
                                     "recorte incluyó la superficie. Repite la foto dejando margen alrededor.")
    if islas.get("aviso_pieza"):
        motivo("REVISAR", "pieza_descartada", f"Se descartó un fragmento del {100 * islas['mayor_isla_descartada']:.2f} % "
                                              "del producto: confirma que no era parte de él (cordón, correa, antena).")
    elif islas["islas_descartadas"]:
        notas.append(f"Se descartaron {islas['islas_descartadas']} fragmento(s) sueltos del recorte.")
    if islas["piezas"] >= 3:
        motivo("REVISAR", "varias_piezas", f"El recorte tiene {islas['piezas']} piezas separadas: confirma que son un "
                                           "solo producto (par o kit). Si son productos distintos, márcala REPETIR.")
    elif islas["piezas"] == 2:
        notas.append("El recorte tiene 2 piezas separadas (¿un par?).")
    if alfa_png is None:  # dominante de color: se mira el fondo original lejos del producto
        esc = min(1.0, 512 / max(Wo, Ho))
        tam = (max(1, round(Wo * esc)), max(1, round(Ho * esc)))
        chica = cv2.resize(rgb, tam, interpolation=cv2.INTER_AREA)
        mchica = (cv2.resize(a, tam, interpolation=cv2.INTER_AREA) > 0.02).astype(np.uint8)
        lejos = cv2.dilate(mchica, imagen.elemento(0.03 * max(tam))) == 0
        if lejos.sum() > 2000:
            lab_f = imagen.rgb_a_lab(chica[lejos].reshape(-1, 1, 3))[:, 0]
            a_med, b_med = float(np.median(lab_f[:, 1])), float(np.median(lab_f[:, 2]))
            if math.hypot(a_med, b_med) > cfg["dominante_croma_min"]:
                notas.append(f"El fondo original no es neutro (a* {a_med:+.0f}, b* {b_med:+.0f}): si era blanco o gris, "
                             "la foto tiene una dominante de color que se conserva a propósito; si el producto no se "
                             "ve de su color real, repite la foto con luz de día.")

    # ---- 5.4 escala: caja sobre el alfa final, lado mayor al 85 % del lienzo
    x0, y0, x1, y1 = caja0
    bw, bh = x1 - x0, y1 - y0
    obj_w, obj_h = cfg["ocupacion"] * W, cfg["ocupacion"] * H
    s = min(obj_w / bw, obj_h / bh)
    margen = int(math.ceil(0.04 * max(bw, bh) + 12 / min(s, 1.0) + 4))
    cx0, cy0, cx1, cy1 = max(0, x0 - margen), max(0, y0 - margen), min(Wo, x1 + margen), min(Ho, y1 + margen)
    a_c = a[cy0:cy1, cx0:cx1]
    cw, ch = cx1 - cx0, cy1 - cy0
    contr = cfg["contraccion_borde_px"] * fpx
    b = None
    for intento in range(5):  # la contracción y el suavizado mueven la caja: se ajusta hasta clavar el tamaño
        nw, nh = max(1, round(cw * s)), max(1, round(ch * s))
        ar = np.clip(imagen.redim(a_c, nw, nh), 0, 1)
        ac = imagen.contraer(ar, contr)
        b = imagen.caja(ac, cfg["alfa_caja_min"])
        if b is None:
            break
        ajuste = min(obj_w / (b[2] - b[0]), obj_h / (b[3] - b[1]))
        if abs(ajuste - 1) * max(b[2] - b[0], b[3] - b[1]) < 0.5 or intento == 4:
            break
        s *= ajuste
    if b is None:
        motivo("REPETIR", "recorte_debil", "El recorte desaparece al escalar: el producto es demasiado fino o el "
                                           "recorte es muy débil.")
        return cerrar()
    lado0 = max(bw, bh)
    necesario = int(math.ceil(lado0 * s))
    reg["escala"] = round(s, 3)
    reg["caja_origen"] = {"x0": x0, "y0": y0, "x1": x1, "y1": y1, "lado_mayor": lado0}
    if s >= cfg["ampliacion_repetir"]:
        motivo("REPETIR", "ampliacion", f"El producto se amplió {s:.2f}× (mide {lado0} px en la foto y necesita "
                                        f"≥ {necesario} px): se verá borroso. Repite la foto más cerca o con más "
                                        "resolución y envíala como documento, no como foto de WhatsApp.")
    elif s >= cfg["ampliacion_revisar"]:
        motivo("REVISAR", "ampliacion", f"El producto se amplió {s:.2f}× (mide {lado0} px; lo ideal es ≥ {necesario} px): "
                                        "revisa la nitidez a tamaño real.")
    elif s > 1.005:
        notas.append(f"Ampliado {s:.2f}× (mide {lado0} px; lo ideal es ≥ {necesario} px).")

    # ---- 5.2 (borde) y remuestreo: descontaminar donde sea más barato y fiel
    img_c = rgb[cy0:cy1, cx0:cx1]
    descontaminado = True
    if alfa_png is not None:
        F, ar = imagen.redim_premultiplicado(imagen.rellenar_color(img_c, a_c), a_c, nw, nh)
        ref_c = np.clip(imagen.redim(img_c, nw, nh), 0, 1)
        descontaminado = None
    elif s <= 1.0:
        # foto opaca que se reduce: se reduce completa (no hay transparencia que ensucie) y se descontamina a tamaño final
        ref_c = np.clip(imagen.redim(img_c, nw, nh), 0, 1)
        F, descontaminado = imagen.descontaminar(ref_c, ar)
    else:
        F0, descontaminado = imagen.descontaminar(img_c, a_c)
        F, ar = imagen.redim_premultiplicado(F0, a_c, nw, nh)
        ref_c = np.clip(imagen.redim(img_c, nw, nh), 0, 1)
    foto.clear()
    del rgb, m, a
    if descontaminado is False:
        avisos.append("No se pudo descontaminar el borde (pymatting): puede quedar un halo del fondo original.")
    ac = imagen.contraer(ar, contr)
    interior = imagen.contraer((ac >= 0.99).astype(np.float32), 3 * fpx) > 0.5
    if interior.sum() < 200:
        interior = ac >= 0.99

    # notas sobre la foto original (se conservan, no se corrigen)
    if interior.sum() > 500:
        quemadas = 100.0 * float((ref_c[interior].max(axis=1) >= 254.5 / 255).mean())
        if quemadas > cfg["luces_quemadas_pct"]:
            notas.append(f"Altas luces quemadas en el {quemadas:.1f} % del producto: se conservan; ningún ajuste "
                         "recupera ese detalle.")

    # ---- fiabilidad del recorte: ¿el producto se parecía a su fondo original?
    if alfa_png is None:
        co = imagen.contraste_original(ref_c, ac, cfg)
        recorte["contraste_original"] = co
        if co["sectores"] and co["fraccion"] > cfg["contraste_original_sectores_max"]:
            motivo("REVISAR", "contraste_original",
                   f"El producto se parecía a su fondo original en el {100 * co['fraccion']:.0f} % del contorno "
                   f"({donde_texto(co)}; ΔE mínima {co['de_min']}): ahí el recorte pudo comerse parte del producto o "
                   "sumar parte de la superficie. Revisa esa zona a tamaño real; si falla, repite la foto sobre un "
                   "fondo que contraste con el producto.")

    # ---- 5.3 tono (sólo L*) y enfoque después de redimensionar
    F2, tono = imagen.corregir_tono(F, ac, cfg["tono"], fpx)
    F3 = imagen.enfocar(F2, ac, cfg["enfoque"], fpx)
    del F, F2
    reg["tono"] = tono
    reg["enfoque"] = dict(cfg["enfoque"])

    solido = float((ac > 0.5).sum())
    inciertos = float(((ac > 0.15) & (ac < 0.85)).sum()) / max(1.0, solido)
    recorte["bordes_inciertos"] = round(inciertos, 4)
    if inciertos > cfg["bordes_inciertos_max"]:
        motivo("REVISAR", "bordes_inciertos", f"Bordes inciertos ({100 * inciertos:.1f} % del producto): posible "
                                              "superficie transparente o espejada, tejido calado o fondo parecido al "
                                              "producto. Revisa el contorno a tamaño real.")

    # ---- encuadre en el lienzo
    ox = int(round(W / 2 - (b[0] + b[2]) / 2))
    oy = int(round(H / 2 - (b[1] + b[3]) / 2))
    sx0, sy0, dx0, dy0 = max(0, -ox), max(0, -oy), max(0, ox), max(0, oy)
    ww, hh = min(nw - sx0, W - dx0), min(nh - sy0, H - dy0)
    alfa_l = np.zeros((H, W), np.float32)
    color_l = np.zeros((H, W, 3), np.float32)
    alfa_l[dy0:dy0 + hh, dx0:dx0 + ww] = ac[sy0:sy0 + hh, sx0:sx0 + ww]
    color_l[dy0:dy0 + hh, dx0:dx0 + ww] = F3[sy0:sy0 + hh, sx0:sx0 + ww]
    del F3
    if float(ac.sum()) - float(alfa_l.sum()) > 1.0:
        avisos.append("Parte del recorte quedó fuera del lienzo (revisa el encuadre).")
    bf = imagen.caja(alfa_l, cfg["alfa_caja_min"])
    dcx, dcy = (bf[0] + bf[2]) / 2 - W / 2, (bf[1] + bf[3]) / 2 - H / 2
    reg["caja_producto"] = {"x0": bf[0], "y0": bf[1], "x1": bf[2], "y1": bf[3], "ancho": bf[2] - bf[0],
                            "alto": bf[3] - bf[1], "lado_mayor": max(bf[2] - bf[0], bf[3] - bf[1]),
                            "desvio_centro_px": round(math.hypot(dcx, dcy), 2)}

    # ---- 5.5–5.7 fondo plantilla, sombra de contacto y composición
    sombra = imagen.sombra(alfa_l, cfg)
    final = imagen.componer(ctx.fondo, sombra, ctx.sombra_rgb, color_l, alfa_l)
    del color_l
    img8 = imagen.a_8bits(final)
    sep = imagen.separacion(final, alfa_l, cfg)
    reg["sombra"] = {"color": cfg["sombra_color"], "opacidad": cfg["sombra_opacidad"],
                     "dilatacion_px": cfg["sombra_dilatacion_px"], "sigma_px": cfg["sombra_sigma_px"],
                     "desplazamiento_y_px": cfg["sombra_desplazamiento_y_px"], "separacion": sep}
    if sep["sectores"] and sep["fraccion"] > cfg["separacion_sectores_max"]:
        motivo("REVISAR", "separacion", f"El producto se confunde con el fondo nuevo en el {100 * sep['fraccion']:.0f} % "
                                        f"del contorno ({donde_texto(sep)}; ΔL* mínima {sep['dl_min']}): producto claro "
                                        "sobre fondo claro. El fondo no se cambia para mantener el catálogo uniforme; "
                                        "confirma que la silueta se lee.")
    elif sep["sin_separacion"] >= 3:
        notas.append(f"El borde apenas se distingue del fondo nuevo en {sep['sin_separacion']} de {sep['sectores']} "
                     f"tramos ({donde_texto(sep)}).")

    (ctx.trabajo / "mascaras").mkdir(parents=True, exist_ok=True)
    Image.fromarray(imagen.a_8bits(alfa_l), "L").save(ctx.trabajo / "mascaras" / f"{nombre}.png", optimize=True)
    imagen.miniatura(img8, hojas.MINI).save(hojas.ruta_miniatura(ctx.raiz, nombre, "despues"), quality=90)

    repetir = any(x["estado"] == "REPETIR" for x in motivos) or any(x["estado"] == "REPETIR" for x in reg["marcas"])
    if repetir:
        # no va a las carpetas de entrega; queda una vista previa para revisar y explicar
        (ctx.trabajo / "vistas").mkdir(parents=True, exist_ok=True)
        leida = ctx.trabajo / "vistas" / f"{nombre}.jpg"
        imagen.guardar_jpeg(img8, leida, cfg["jpeg_calidad"])
        reg["vista_previa"] = ctx.rel(leida)
    else:
        # ---- 5.8 exportar
        carpeta_m = ctx.carpeta_maestra()
        carpeta_m.mkdir(parents=True, exist_ok=True)
        leida = carpeta_m / f"{nombre}.jpg"
        kb = round(imagen.guardar_jpeg(img8, leida, cfg["jpeg_calidad"]) / 1024)
        reg["salidas"]["maestra"] = {"ruta": ctx.rel(leida), "kb": kb, "calidad": cfg["jpeg_calidad"]}
        if kb > cfg["jpeg_peso_max_kb"]:
            avisos.append(f"La maestra pesa {kb} KB (objetivo ≤ {cfg['jpeg_peso_max_kb']} KB); no se baja la calidad "
                          "para no perder detalle.")
        reg["salidas"]["web"] = exportar_web(ctx, nombre, final, img8, alfa_l, sombra, avisos)
        (ctx.trabajo / "vistas" / f"{nombre}.jpg").unlink(missing_ok=True)

    # ---- control de crominancia sobre el archivo escrito
    with Image.open(leida) as im:
        dec = np.asarray(im.convert("RGB"), dtype=np.float32) / 255.0
    ys, xs = np.nonzero(interior)
    Y, X = ys + oy, xs + ox
    dentro = (Y >= 0) & (Y < H) & (X >= 0) & (X < W)
    ys, xs, Y, X = ys[dentro], xs[dentro], Y[dentro], X[dentro]
    if len(ys) > 400_000:
        paso = int(math.ceil(len(ys) / 400_000))
        ys, xs, Y, X = ys[::paso], xs[::paso], Y[::paso], X[::paso]
    if len(ys):
        reg["delta_croma"] = imagen.delta_croma(ref_c[ys, xs], dec[Y, X])
        if reg["delta_croma"] > cfg["croma_max_delta"]:
            motivo("REVISAR", "croma", f"La crominancia del producto cambió {reg['delta_croma']:.2f} (máximo "
                                       f"{cfg['croma_max_delta']}): compara el color con la foto original.")
    else:
        reg["delta_croma"] = 0.0
        notas.append("El producto es tan fino que no hay interior para medir la crominancia.")
    return cerrar()


def donde_texto(datos: dict) -> str:
    """Lista de direcciones, o «casi todo el contorno» cuando abarca 6 de las 8."""
    partes = [p for p in (datos.get("donde") or "").split(", ") if p]
    return "casi todo el contorno" if len(partes) >= 6 else (", ".join(partes) or "sin ubicar")


def exportar_web(ctx: Contexto, nombre: str, final: np.ndarray, img8: np.ndarray, alfa_l: np.ndarray,
                 sombra: np.ndarray, avisos: list[str]) -> list[dict]:
    cfg, W, H = ctx.cfg, ctx.ancho, ctx.alto
    # referencia para medir escalones: degradado ideal (sin tramado) con la misma sombra
    g = sombra[..., None]
    ref = ctx.ideal * (1 - g)
    ref += ctx.sombra_rgb * g
    ref *= 255.0
    producto = (alfa_l > 0.004).astype(np.float32)
    region = imagen.dilatar(producto, 24 * ctx.fpx) < 0.5
    web = []
    escalones: dict[str, list[tuple[int, float, int]]] = {}
    anchos = {int(x) for x in cfg["anchos_variantes"] if int(x) <= W}
    if ctx.grupo:
        anchos.add(W)   # el lienzo del producto siempre se publica, aunque sea menor que los anchos pedidos
    for w in sorted(anchos, reverse=True):
        h = round(w * H / W)
        if (w, h) == (W, H):
            v8, refw, regw = img8, ref, region
            v16 = imagen.a_16bits(final) if ctx.avif10 else None
        else:
            vf = np.clip(imagen.redim(final, w, h), 0, 1)
            aw = np.clip(imagen.redim(alfa_l, w, h), 0, 1)
            v8 = imagen.cuantizar_tramado(vf, 1.0 - aw, int(cfg["dither_semilla"]) + w, float(cfg["dither_niveles"]))
            v16 = imagen.a_16bits(vf) if ctx.avif10 else None
            refw = imagen.redim(ref, w, h)
            # margen extra: el filtro de reducción arrastra el producto unos píxeles hacia el entorno
            lejos = imagen.dilatar(producto, 24 * ctx.fpx + 4.0 * W / w) < 0.5
            regw = imagen.region_reducida(lejos, w, h)
        for fmt in cfg["formatos_web"]:
            res = imagen.codificar_web(fmt, v8, v16, refw, regw, cfg, ctx.avif10 if fmt == "avif" else None)
            ruta = ctx.ruta_web(nombre, w, fmt)
            ruta.parent.mkdir(parents=True, exist_ok=True)
            ruta.write_bytes(res["datos"])
            kb = round(len(res["datos"]) / 1024)
            web.append({"ruta": ctx.rel(ruta), "ancho": w, "alto": h, "formato": fmt, "kb": kb,
                        "calidad": res["calidad"], "bits": res["bits"], "banding": res["banding"],
                        "banding_ok": res["banding_ok"], "intentos": res["intentos"]})
            if not res["banding_ok"]:
                escalones.setdefault(fmt, []).append((w, res["banding"], res["calidad"]))
            limite = cfg["pesos_max_kb"].get(fmt, {}).get(str(w))
            if limite and kb > limite:
                avisos.append(f"{ruta.name} de {w} px pesa {kb} KB (objetivo ≤ {limite} KB).")
    for fmt, fallos in escalones.items():  # un aviso por formato, no uno por archivo
        anchos = ", ".join(str(w) for w, _, _ in sorted(fallos, reverse=True))
        bajo, alto = min(b for _, b, _ in fallos), max(b for _, b, _ in fallos)
        rango = f"{bajo:.2f}" if abs(alto - bajo) < 0.005 else f"{bajo:.2f}–{alto:.2f}"
        texto = (f"{fmt.upper()} {anchos} px: el degradado conserva escalones ({rango} niveles; máximo "
                 f"{cfg['banding_umbral']}) aun con calidad {max(q for _, _, q in fallos)}.")
        if fmt == "webp":
            texto += " Es un límite de WebP con pérdida: usa JPEG como respaldo de AVIF."
        avisos.append(texto)
    return web


def registro_error(ctx: Contexto, ruta: Path, nombre: str, previo: dict, e: BaseException) -> dict:
    if isinstance(e, UnidentifiedImageError):
        texto = "No se pudo abrir la imagen (archivo dañado o formato no soportado): pide el archivo de nuevo."
    elif isinstance(e, MemoryError):
        texto = "Memoria insuficiente al procesar esta foto: procésala sola o redúcela a unos 4000 px."
    else:
        texto = f"Error inesperado al procesar ({type(e).__name__}: {e}); el detalle quedó en el registro."
    reg = {"nombre": nombre, "archivo": ruta.name, "ruta_origen": str(ruta.resolve()), "procesada": reporte.ahora(),
           "huella_config": ctx.huella_config, "marcas": list(previo.get("marcas", [])),
           "exclusiones": list(previo.get("exclusiones", [])), "revisada": None, "salidas": {},
           "motivos_auto": [{"estado": "REPETIR", "codigo": "error", "texto": texto}], "avisos": [], "notas": []}
    try:
        reg["huella_origen"] = imagen.huella_archivo(ruta)
    except OSError:
        pass
    return reg


# =========================================================================== segundo plano

def lanzar_segundo_plano(raiz: Path) -> int:
    trabajo = raiz / reporte.TRABAJO
    trabajo.mkdir(parents=True, exist_ok=True)
    log = trabajo / "proceso.log"
    args = [x for x in sys.argv[1:] if x != "--segundo-plano"]
    cmd = [sys.executable, "-u", str(Path(__file__).resolve())] + args
    env = dict(os.environ, PYTHONIOENCODING="utf-8")
    kw: dict = {}
    if os.name == "nt":
        kw["creationflags"] = 0x00000008 | 0x00000200  # DETACHED_PROCESS | CREATE_NEW_PROCESS_GROUP
    else:
        kw["start_new_session"] = True
    reporte.escribir_json(trabajo / "avance.json", {"estado": "iniciando", "inicio": reporte.ahora()})
    with open(log, "w", encoding="utf-8") as fh:
        proc = subprocess.Popen(cmd, stdout=fh, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL, env=env, **kw)
    exe = "python" if os.name == "nt" else "python3"
    print(f"Procesando en segundo plano (PID {proc.pid}).")
    print(f"Avance:   {exe} {Path(__file__).resolve()} --avance \"{raiz}\"")
    print(f"Registro: {log}")
    return 0


def mostrar_avance(raiz: Path) -> int:
    trabajo = raiz / reporte.TRABAJO
    try:
        datos = json.loads((trabajo / "avance.json").read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        print(f"No hay un proceso registrado en {raiz}.")
        return 1
    estado = datos.get("estado", "?")
    total, hechas = datos.get("total", 0), datos.get("hechas", 0)
    codigo = 0
    if estado in ("iniciando", "procesando"):
        if datos.get("pid") and not vivo(datos["pid"]):
            print(f"El proceso se detuvo sin terminar ({hechas}/{total} fotos). Revisa el registro y vuelve a lanzarlo.")
            codigo = 1
        else:
            texto = f"Procesando: {hechas}/{total} fotos"
            if datos.get("actual"):
                texto += f" · ahora: {datos['actual']}"
            spf = datos.get("segundos_por_foto")
            if spf and total:
                minutos = round(spf * (total - hechas) / 60)
                texto += " · falta menos de un minuto" if minutos < 1 else (
                    " · falta un minuto" if minutos == 1 else f" · faltan unos {minutos} min")
            print(texto)
            codigo = 3
    elif estado == "terminado":
        print(f"Terminado: {datos.get('resumen', '')}")
        for h in datos.get("hojas", []):
            print(f"  hoja: {h}")
        if datos.get("zip"):
            print(f"  ZIP: {datos['zip']}")
    else:
        print(f"Estado: {estado}. {datos.get('detalle', '')}")
        codigo = 1
    log = trabajo / "proceso.log"
    if log.is_file():
        lineas = log.read_text(encoding="utf-8", errors="replace").splitlines()[-6:]
        if lineas:
            print("— últimas líneas del registro —")
            print("\n".join(lineas))
    return codigo


# =========================================================================== principal

def main() -> int:
    utf8()
    p = argparse.ArgumentParser(description="Tomas de estudio con fondo degradado gris (TecnoSport)",
                                formatter_class=argparse.RawDescriptionHelpFormatter, epilog=__doc__)
    p.add_argument("entradas", nargs="*", help="fotos o carpetas (JPG, PNG, HEIC, WebP)")
    p.add_argument("-o", "--salida", help="carpeta de salida (se reutiliza para reprocesar)")
    p.add_argument("--config", help="config.json alternativo")
    p.add_argument("--ajuste", action="append", default=[], metavar="CLAVE=VALOR",
                   help="cambia un parámetro del config para esta corrida (claves anidadas con punto)")
    p.add_argument("--mapa", help="CSV archivo,nombre para nombrar las salidas")
    p.add_argument("--variantes", action="store_true",
                   help="genera los anchos y formatos propuestos (AVIF + WebP de 480 a 2000 px)")
    p.add_argument("--solo", action="append", default=[], metavar="NOMBRES",
                   help="procesa sólo estos nombres (separados por coma); sin entradas, usa las rutas del reporte")
    p.add_argument("--por-producto", dest="por_producto", action="store_true", default=None,
                   help="salida <producto>/maestra y <producto>/<ancho>, con el lienzo que permita el "
                        "material de cada producto; se activa sola si las fotos vienen en subcarpetas")
    p.add_argument("--plano", dest="por_producto", action="store_false",
                   help="fuerza la salida plana de siempre aunque las fotos vengan en subcarpetas")
    p.add_argument("--nuevas", action="store_true",
                   help="salta las fotos ya procesadas cuyo archivo de origen no ha cambiado")
    p.add_argument("--zip", action="store_true", help="empaqueta los entregables al terminar")
    p.add_argument("--segundo-plano", action="store_true", help="lanza el proceso desacoplado y sale")
    p.add_argument("--avance", metavar="SALIDA", help="muestra el avance de un proceso en segundo plano")
    a = p.parse_args()

    if a.avance:
        return mostrar_avance(Path(a.avance).resolve())
    if not a.salida:
        p.error("falta -o SALIDA")
    raiz = Path(a.salida).resolve()
    solo = {slug(x) for grupo in a.solo for x in grupo.split(",") if x.strip()}
    if not a.entradas and not solo:
        p.error("indica fotos o carpetas (o --solo NOMBRES para reprocesar lo que ya está en el reporte)")
    if a.segundo_plano:
        return lanzar_segundo_plano(raiz)

    avance = raiz / reporte.TRABAJO / "avance.json"
    try:
        return ejecutar(a, raiz, solo, avance)
    except SystemExit:
        raise
    except BaseException as e:  # deja constancia para quien consulte --avance
        traceback.print_exc()
        try:
            reporte.escribir_json(avance, {"estado": "error", "detalle": f"{type(e).__name__}: {e}",
                                           "fin": reporte.ahora()})
        except Exception:
            pass
        return 1


def ejecutar(a, raiz: Path, solo: set[str], avance: Path) -> int:
    try:
        cfg = imagen.cargar_config(a.config, a.ajuste)
    except (KeyError, ValueError, OSError, json.JSONDecodeError) as e:
        print(f"Configuración no válida: {e}")
        return 2
    if a.variantes:
        cfg["anchos_variantes"] = list(cfg["anchos_variantes_propuestos"])
        cfg["formatos_web"] = list(cfg["formatos_web_propuestos"])
    # al reprocesar en una carpeta existente se heredan sus opciones de salida, salvo que se pidan otras
    explicitas = bool(a.variantes or a.config) or any(
        x.partition("=")[0].strip().split(".")[0] in CLAVES_SALIDA for x in (a.ajuste or []))
    cfg_carpeta = (reporte.cargar(raiz) or {}).get("configuracion") if raiz.is_dir() else None
    if cfg_carpeta and not explicitas:
        heredadas = [k for k in CLAVES_SALIDA if k in cfg_carpeta and cfg_carpeta[k] != cfg.get(k)]
        for k in heredadas:
            cfg[k] = cfg_carpeta[k]
        if heredadas:
            print(f"[info] se usan las opciones de salida con que se creó esta carpeta ({', '.join(heredadas)}).")
    errores = []
    if not 0.3 <= float(cfg["ocupacion"]) <= 0.98:
        errores.append("ocupacion debe estar entre 0.3 y 0.98")
    for fmt in cfg["formatos_web"]:
        if fmt not in ("avif", "jpg", "webp"):
            errores.append(f"formato web no soportado: {fmt} (usa avif, jpg o webp)")
    if "webp" in cfg["formatos_web"] and not features.check("webp"):
        errores.append("esta instalación de Pillow no escribe WebP")
    avif10 = None
    if "avif" in cfg["formatos_web"]:
        modo = str(cfg.get("avif_10bits", "auto")).lower()
        if modo != "nunca":
            avif10 = imagen.localizar_avifenc(cfg)
        if modo == "siempre" and avif10 is None:
            errores.append("avif_10bits=siempre pero no hay avifenc (python avif10.py instalar)")
        if avif10 is None and not imagen.soporte_avif_pillow():
            errores.append("no hay codificador AVIF: instala avifenc (python avif10.py instalar) o actualiza Pillow")
    ignorados = [w for w in cfg["anchos_variantes"] if int(w) > int(cfg["lienzo"][0])]
    if ignorados and a.por_producto is False:
        print(f"[aviso] anchos mayores que el lienzo, se ignoran: {ignorados}")
    if errores:
        print("No se puede procesar:\n  - " + "\n  - ".join(errores))
        return 2

    raiz.mkdir(parents=True, exist_ok=True)
    previo = reporte.cargar(raiz)
    previas = {f["nombre"]: f for f in previo.get("fotos", [])}

    trabajos: list[tuple[Path, str, str]] = []
    if a.entradas:
        mapa = leer_mapa(a.mapa) if a.mapa else {}
        usados: set[str] = set()
        for ruta, grupo in recolectar(a.entradas, raiz):
            base = slug(mapa.get(ruta.name.lower()) or mapa.get(ruta.stem.lower()) or ruta.stem)
            nombre, n = base, 2
            while nombre in usados:
                nombre, n = f"{base}-{n}", n + 1
            usados.add(nombre)
            trabajos.append((ruta, nombre, grupo))
        if solo:
            trabajos = [(r, n, g) for r, n, g in trabajos if n in solo]
    else:
        for nombre in sorted(solo):
            f = previas.get(nombre)
            if not f:
                continue
            ruta = Path(f.get("ruta_origen", ""))
            if ruta.is_file():
                trabajos.append((ruta, nombre, f.get("grupo", "")))
            else:
                print(f"[aviso] {nombre}: ya no existe su foto original ({ruta})")
    if a.nuevas and previas:
        antes = len(trabajos)
        pendientes = []
        huellas: dict[tuple, str] = {}

        def huella_para(lienzo) -> str:
            # por producto cada foto se guardó con la huella de SU lienzo, no la del lote
            clave = tuple(lienzo) if lienzo else ()
            if clave not in huellas:
                huellas[clave] = huella_config({**cfg, "lienzo": list(lienzo)} if lienzo else cfg)
            return huellas[clave]

        for t in trabajos:
            f = previas.get(t[1])
            if (f and f.get("huella_origen") and f.get("huella_origen") == imagen.huella_archivo(t[0])
                    and f.get("huella_config") == huella_para(f.get("lienzo"))):
                continue
            pendientes.append(t)
        saltadas = antes - len(pendientes)
        if saltadas:
            print(f"[info] --nuevas: {saltadas} foto(s) ya procesadas sin cambios, se saltan.")
        trabajos = pendientes
    if solo:
        faltan = solo - {n for _, n, _ in trabajos}
        if faltan:
            print(f"[aviso] sin foto para: {', '.join(sorted(faltan))}")
    sin_heif = [r for r, _, _ in trabajos if r.suffix.lower() in (".heic", ".heif") and not imagen.HEIF_OK]
    if sin_heif:
        print("[aviso] hay fotos HEIC y falta pillow-heif (pip install pillow-heif); se omiten: "
              + ", ".join(r.name for r in sin_heif))
        trabajos = [t for t in trabajos if t[0] not in sin_heif]
    if not trabajos:
        print("No hay fotos para procesar.")
        return 2

    huella = huella_config(cfg)
    cfg_prev = previo.get("configuracion")
    if cfg_prev:
        claves = [k for k in CLAVES_SALIDA if not (k == "lienzo" and (a.por_producto or cfg_prev.get("por_producto")))]
        distintas = [k for k in claves if cfg_prev.get(k) != cfg.get(k)]
        pendientes = set(previas) - {n for _, n, _ in trabajos}
        if distintas and pendientes:
            print(f"La configuración de salidas cambió ({', '.join(distintas)}) respecto a las {len(previas)} fotos que "
                  f"ya están en {raiz}.\nReprocesa todo el lote con la misma configuración o usa otra carpeta de salida.")
            return 2

    por_producto = bool(a.por_producto) or (a.por_producto is None and any(g for _, _, g in trabajos))
    lienzo_de: dict[str, int] = {}
    if por_producto:
        lados: dict[str, list[int]] = {}
        for ruta, _, grupo in trabajos:
            lados.setdefault(grupo, []).append(estimar_lado_producto(ruta) or 0)
        for grupo, ls in lados.items():
            lienzo_de[grupo] = elegir_lienzo(ls, cfg)
        reparto = {}
        for grupo, lado in lienzo_de.items():
            reparto[lado] = reparto.get(lado, 0) + 1
        detalle = " · ".join(f"{n} a {lado}" for lado, n in sorted(reparto.items(), reverse=True))
        print(f"Salida por producto: {len(lienzo_de)} producto(s), lienzo según su material ({detalle})")

    contextos: dict[int, Contexto] = {}

    def contexto_de(lado: int) -> Contexto:
        if lado not in contextos:
            c = dict(cfg)
            c["lienzo"] = [lado, lado]
            contextos[lado] = Contexto(c, raiz, huella_config(c), avif10)
        return contextos[lado]

    ctx = contexto_de(int(cfg["lienzo"][0]))
    ancho, alto = ctx.ancho, ctx.alto
    if not por_producto:
        print(f"Lienzo {ancho}×{alto} · producto al {round(100 * cfg['ocupacion'])} % · fondo {cfg['fondo_centro']} → "
              f"{cfg['fondo_esquinas']} ({ctx.origen_fondo})")
    else:
        print(f"Producto al {round(100 * cfg['ocupacion'])} % · fondo {cfg['fondo_centro']} → "
              f"{cfg['fondo_esquinas']} ({ctx.origen_fondo})")
    if not ctx.origen_fondo.startswith("assets"):
        print("[aviso] el fondo de assets no coincide con config.json; si el cambio es a propósito, "
              "regenéralo con fondo.py --generar para que todo el catálogo use el mismo")
    if "avif" in cfg["formatos_web"]:
        print("AVIF: " + (f"10 bits con avifenc {avif10['version']}" if avif10 else
                          "8 bits con Pillow (sube la calidad si ve escalones; python avif10.py instalar para 10 bits)"))
    print(f"{len(trabajos)} foto(s) → {raiz}", flush=True)

    inicio = time.time()
    estado_avance = {"estado": "procesando", "pid": os.getpid(), "total": len(trabajos), "hechas": 0,
                     "inicio": reporte.ahora()}
    reporte.escribir_json(avance, estado_avance)
    ronda = int(previo.get("rondas", 0)) + 1
    procesadas: list[str] = []
    for i, (ruta, nombre, grupo) in enumerate(trabajos, 1):
        activo = contexto_de(lienzo_de.get(grupo, int(cfg["lienzo"][0]))) if por_producto else ctx
        activo.grupo = grupo if por_producto else ""
        etiqueta = f"{grupo}/{nombre}" if activo.grupo else nombre
        print(f"[{i}/{len(trabajos)}] {ruta.name} → {etiqueta}"
              + (f"  [lienzo {activo.ancho}]" if por_producto else ""), flush=True)
        estado_avance["actual"] = ruta.name
        reporte.escribir_json(avance, estado_avance)
        previa = previas.get(nombre, {})
        try:
            reg = procesar_foto(activo, ruta, nombre, previa)
        except Exception as e:  # una foto rota no detiene el lote
            if not isinstance(e, UnidentifiedImageError):
                traceback.print_exc()
            reg = registro_error(activo, ruta, nombre, previa, e)
        reg["ronda"] = ronda
        if grupo:
            reg["grupo"] = grupo
        if por_producto:
            reg["lienzo"] = [activo.ancho, activo.alto]
        # salidas viejas que ya no corresponden; una foto nueva reemplaza lo retenido
        nuevas = set(reporte.rutas_salida(reg))
        reporte.borrar_salidas(raiz, [r for r in reporte.rutas_salida(previa) if r not in nuevas])
        if previa.get("salidas_retenidas"):
            import shutil
            shutil.rmtree(raiz / reporte.TRABAJO / "retenidas" / nombre, ignore_errors=True)
        estado = reporte.estado_final(reg)
        detalle = f"{reg.get('segundos', 0):.0f} s"
        if reg.get("escala"):
            detalle += f" · escala {reg['escala']:.2f}×"
        print(f"    {estado} ({detalle})", flush=True)
        for m in reg.get("motivos", []):
            print(f"    • {m}", flush=True)
        for x in reg.get("avisos", []):
            print(f"    ! {x}", flush=True)
        previas[nombre] = reg
        procesadas.append(nombre)
        estado_avance["hechas"] = i
        estado_avance["segundos_por_foto"] = round((time.time() - inicio) / i, 1)
        reporte.escribir_json(avance, estado_avance)

    cfg_rep = config_publica(cfg)
    cfg_rep["por_producto"] = bool(por_producto)
    if por_producto:
        cfg_rep["lienzos_por_producto"] = {g: [l, l] for g, l in sorted(lienzo_de.items())}
    rep = {"skill": "fotos-estudio-degradado", "version": VERSION, "rondas": ronda,
           "configuracion": cfg_rep, "huella_config": huella,
           "entorno": entorno(ctx), "fondo": {"origen": ctx.origen_fondo, "huella_pixeles": ctx.huella_fondo},
           "fotos": list(previas.values())}
    # por producto la huella cambia con el lienzo, que es justo lo que se quiere: no se avisa por eso
    huellas_ok = {huella} | ({huella_config({**cfg, "lienzo": [l, l]}) for l in set(lienzo_de.values())}
                             if por_producto else set())
    otras = sorted(f["nombre"] for f in rep["fotos"] if f.get("huella_config") not in ({None} | huellas_ok))
    if otras:
        rep["advertencias_lote"] = [f"Procesadas con otros parámetros (el catálogo no queda uniforme): {', '.join(otras)}"]
    reporte.guardar(raiz, rep)
    rutas_h, ubicacion = hojas.generar(raiz, rep, int(cfg["fotos_por_hoja"]))
    rep["hojas_revision"] = [h.name for h in rutas_h]
    reporte.guardar(raiz, rep)

    print(f"\nRonda {ronda} lista en {round(time.time() - inicio)} s: "
          + reporte.texto_resumen({"resumen": reporte.resumen([previas[n] for n in procesadas])}))
    if len(rep["fotos"]) > len(procesadas):
        print("Acumulado en la carpeta: " + reporte.texto_resumen(rep))
    for x in rep.get("advertencias_lote", []):
        print(f"[aviso] {x}")
    por_hoja: dict[str, list[str]] = {}
    for n in procesadas:
        por_hoja.setdefault(ubicacion.get(n, "?"), []).append(n)
    print("Hojas de revisión (fotos de esta ronda):")
    for h in sorted(por_hoja):
        print(f"  {raiz / h}: {', '.join(por_hoja[h])}")
    fin = {"estado": "terminado", "total": len(trabajos), "hechas": len(trabajos), "resumen": reporte.texto_resumen(rep),
           "hojas": [str(h) for h in rutas_h], "fin": reporte.ahora()}
    if a.zip:
        z = reporte.empaquetar(raiz, rep)
        fin["zip"] = str(z)
        print(f"ZIP: {z}")
    reporte.escribir_json(avance, fin)
    return 0


def entorno(ctx: Contexto) -> dict:
    from importlib import metadata

    def version(paquete: str) -> str | None:
        try:
            return metadata.version(paquete)
        except metadata.PackageNotFoundError:
            return None

    avif = "no se usa"
    if "avif" in ctx.cfg["formatos_web"]:
        avif = f"10 bits con avifenc {ctx.avif10['version']}" if ctx.avif10 else "8 bits con Pillow"
    return {"python": platform.python_version(), "sistema": f"{platform.system()} {platform.machine()}",
            "pillow": version("pillow"), "numpy": version("numpy"), "opencv": cv2.__version__,
            "rembg": version("rembg"), "onnxruntime": version("onnxruntime"), "pymatting": version("pymatting"),
            "pillow_heif": version("pillow-heif") or version("pillow_heif"), "avif": avif}


if __name__ == "__main__":
    sys.exit(main())
