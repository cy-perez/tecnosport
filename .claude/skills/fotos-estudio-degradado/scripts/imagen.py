"""
Núcleo de imagen de fotos-estudio-degradado.

Todo lo que toca píxeles vive aquí: carga en sRGB, recorte, limpieza de la
máscara, escala, corrección tonal sobre L*, enfoque, fondo plantilla,
sombra, composición, codificación JPEG/AVIF/WebP y métricas de control.

Convenciones: los colores van en float32 0..1 codificados en sRGB; el alfa en
float32 0..1; los "niveles" son la escala 0..255 de 8 bits.
"""
from __future__ import annotations

import hashlib
import io
import json
import math
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import entorno  # noqa: E402  (sólo biblioteca estándar)

entorno.exigir()  # sin dependencias, un mensaje claro en lugar de un traceback

import cv2  # noqa: E402
import numpy as np  # noqa: E402
from PIL import Image, ImageCms, ImageOps, features  # noqa: E402

from avif10 import cache_libavif as _cache_libavif, localizar_avifenc  # noqa: E402,F401

try:  # fotos de iPhone
    from pillow_heif import register_heif_opener

    register_heif_opener()
    HEIF_OK = True
except ImportError:  # pragma: no cover
    HEIF_OK = False

RAIZ_SKILL = Path(__file__).resolve().parent.parent
CONFIG_POR_DEFECTO = RAIZ_SKILL / "config.json"
LANCZOS = Image.Resampling.LANCZOS
SRGB_ICC = ImageCms.ImageCmsProfile(ImageCms.createProfile("sRGB")).tobytes()


# =========================================================================== configuración

def cargar_config(ruta: str | Path | None = None, ajustes: list[str] | None = None) -> dict:
    """Lee config.json y aplica ajustes 'clave=valor' (claves anidadas con punto)."""
    ruta = Path(ruta) if ruta else CONFIG_POR_DEFECTO
    cfg = json.loads(ruta.read_text(encoding="utf-8"))
    cfg = {k: v for k, v in cfg.items() if not k.startswith("_")}
    for ajuste in ajustes or []:
        clave, sep, valor = ajuste.partition("=")
        if not sep:
            raise ValueError(f"ajuste sin '=': {ajuste}")
        try:
            dato = json.loads(valor)
        except json.JSONDecodeError:
            dato = valor
        nodo, partes = cfg, clave.strip().split(".")
        for p in partes[:-1]:
            if p not in nodo or not isinstance(nodo[p], dict):
                raise KeyError(f"parámetro desconocido: {clave}")
            nodo = nodo[p]
        if partes[-1] not in nodo:
            raise KeyError(f"parámetro desconocido: {clave}")
        nodo[partes[-1]] = dato
    cfg["_ruta"] = str(ruta)
    return cfg


def factor_px(cfg: dict) -> float:
    """Los valores en px del config están pensados para un lienzo de 2000 px."""
    return max(cfg["lienzo"]) / 2000.0


def hex_a_rgb(h: str) -> tuple[int, int, int]:
    h = h.strip().lstrip("#")
    return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)


# =========================================================================== color

_M = np.array([[0.4124564, 0.3575761, 0.1804375],
               [0.2126729, 0.7151522, 0.0721750],
               [0.0193339, 0.1191920, 0.9503041]], np.float32)
_M_INV = np.linalg.inv(_M).astype(np.float32)
_BLANCO = np.array([0.95047, 1.0, 1.08883], np.float32)
_D = 6.0 / 29.0


def srgb_a_lineal(c: np.ndarray) -> np.ndarray:
    c = np.asarray(c, np.float32)
    return np.where(c <= 0.04045, c / 12.92, ((np.maximum(c, 0) + 0.055) / 1.055) ** 2.4).astype(np.float32)


def lineal_a_srgb(c: np.ndarray) -> np.ndarray:
    c = np.clip(c, 0, 1)
    return np.where(c <= 0.0031308, c * 12.92, 1.055 * np.power(c, 1 / 2.4) - 0.055).astype(np.float32)


def _f(t):
    return np.where(t > _D ** 3, np.cbrt(t), t / (3 * _D * _D) + 4.0 / 29.0)


def _f_inv(t):
    return np.where(t > _D, t ** 3, 3 * _D * _D * (t - 4.0 / 29.0))


def rgb_a_lab(rgb: np.ndarray) -> np.ndarray:
    xyz = (srgb_a_lineal(rgb) @ _M.T) / _BLANCO
    f = _f(xyz)
    return np.stack([116 * f[..., 1] - 16, 500 * (f[..., 0] - f[..., 1]),
                     200 * (f[..., 1] - f[..., 2])], axis=-1).astype(np.float32)


def lab_a_lineal(lab: np.ndarray) -> np.ndarray:
    """Lab → RGB lineal sin recortar (valores fuera de 0..1 = fuera del gamut sRGB)."""
    fy = (lab[..., 0] + 16) / 116
    xyz = np.stack([_f_inv(fy + lab[..., 1] / 500), _f_inv(fy), _f_inv(fy - lab[..., 2] / 200)], axis=-1)
    return ((xyz * _BLANCO) @ _M_INV.T).astype(np.float32)


def lab_a_rgb(lab: np.ndarray) -> np.ndarray:
    return lineal_a_srgb(lab_a_lineal(lab))


def limitar_gamut(L0: np.ndarray, L1: np.ndarray, a: np.ndarray, b: np.ndarray, pasos: int = 8):
    """Reduce el cambio de L* donde el color nuevo saldría del gamut sRGB.

    Subir L* con a* y b* fijos puede sacar un color saturado del gamut; al recortarlo
    cambiaría su crominancia. Aquí se busca, píxel a píxel, el mayor cambio que cabe.
    Devuelve (L corregido, máscara de píxeles limitados).
    """
    tol = 1e-4
    lin = lab_a_lineal(np.stack([L1, a, b], axis=-1))
    fuera = ((lin < -tol) | (lin > 1 + tol)).any(axis=-1)
    if not fuera.any():
        return L1, fuera
    l0, l1, aa, bb = L0[fuera], L1[fuera], a[fuera], b[fuera]
    lo = np.zeros_like(l0)
    hi = np.ones_like(l0)
    for _ in range(pasos):
        t = (lo + hi) / 2
        lt = lab_a_lineal(np.stack([l0 + t * (l1 - l0), aa, bb], axis=-1))
        ok = ((lt >= -tol) & (lt <= 1 + tol)).all(axis=-1)
        lo = np.where(ok, t, lo)
        hi = np.where(ok, hi, t)
    salida = L1.copy()
    salida[fuera] = l0 + lo * (l1 - l0)
    return salida, fuera


def l_a_y(L):
    return _f_inv((np.asarray(L, np.float32) + 16) / 116)


def y_a_l(Y):
    return 116 * _f(np.asarray(Y, np.float32)) - 16


# =========================================================================== fondo plantilla

def _hash32(x: np.ndarray) -> np.ndarray:
    """Hash entero 'lowbias32': determinista en cualquier sistema y versión de numpy."""
    x = x.astype(np.uint32, copy=True)
    x ^= x >> np.uint32(16)
    x *= np.uint32(0x7FEB352D)
    x ^= x >> np.uint32(15)
    x *= np.uint32(0x846CA68B)
    x ^= x >> np.uint32(16)
    return x


def ruido_tpdf(ancho: int, alto: int, semilla: int) -> np.ndarray:
    """Ruido triangular en [-1, 1] (float64), idéntico en Linux y Windows."""
    k = _hash32(np.array([semilla & 0xFFFFFFFF], np.uint32))[0]
    idx = np.arange(ancho * alto, dtype=np.uint32) * np.uint32(2)
    u1 = _hash32(idx ^ k).astype(np.float64) / 4294967296.0
    u2 = _hash32((idx + np.uint32(1)) ^ k).astype(np.float64) / 4294967296.0
    return (u1 - u2).reshape(alto, ancho)


def degradado_ideal(ancho: int, alto: int, centro: str, esquinas: str,
                    radio_interior: float = 0.0) -> np.ndarray:
    """Degradado radial circular en niveles (float64, H×W×3): centro en el centro, esquinas en las esquinas.

    `radio_interior` es la fracción del radio que se queda en el color del centro antes de
    empezar la rampa; la referencia del catálogo la mide en 0,26. Con 0 la rampa arranca en
    el píxel central, que es el comportamiento anterior.
    """
    xs = np.arange(ancho, dtype=np.float64) - (ancho - 1) / 2.0
    ys = np.arange(alto, dtype=np.float64) - (alto - 1) / 2.0
    t = np.sqrt(xs[None, :] ** 2 + ys[:, None] ** 2) / math.sqrt(xs[0] ** 2 + ys[0] ** 2)
    r0 = min(max(float(radio_interior), 0.0), 0.99)
    if r0:
        t = np.clip((t - r0) / (1.0 - r0), 0.0, 1.0)
    c0 = np.array(hex_a_rgb(centro), np.float64)
    c1 = np.array(hex_a_rgb(esquinas), np.float64)
    return c0 + (c1 - c0) * t[..., None]


def fondo_plano(cfg: dict) -> bool:
    """¿El fondo es un color liso? Lo es cuando el centro y las esquinas son el mismo color."""
    return hex_a_rgb(cfg["fondo_centro"]) == hex_a_rgb(cfg["fondo_esquinas"])


def describir_fondo(cfg: dict) -> str:
    """«#FFFFFF plano» o «#FFFFFF → #A5A5A5», para los mensajes de la terminal."""
    if fondo_plano(cfg):
        return f"{cfg['fondo_centro']} plano"
    return f"{cfg['fondo_centro']} → {cfg['fondo_esquinas']}"


def generar_fondo(cfg: dict) -> np.ndarray:
    ancho, alto = cfg["lienzo"]
    ideal = degradado_ideal(ancho, alto, cfg["fondo_centro"], cfg["fondo_esquinas"],
                            float(cfg.get("fondo_radio_interior", 0.0)))
    ruido = ruido_tpdf(ancho, alto, int(cfg["dither_semilla"])) * float(cfg["dither_niveles"])
    return np.clip(np.floor(ideal + ruido[..., None] + 0.5), 0, 255).astype(np.uint8)


def huella_pixeles(arr: np.ndarray) -> str:
    h = hashlib.sha256()
    h.update(repr(arr.shape).encode())
    h.update(np.ascontiguousarray(arr).tobytes())
    return h.hexdigest()


def cargar_fondo(cfg: dict) -> tuple[np.ndarray, str]:
    """Devuelve (fondo uint8 H×W×3, origen). Usa el archivo de assets si coincide con el config."""
    generado = generar_fondo(cfg)
    ruta = RAIZ_SKILL / cfg.get("fondo_archivo", "")
    if ruta.is_file():
        arr = np.asarray(Image.open(ruta))
        if arr.ndim == 2:
            arr = np.repeat(arr[..., None], 3, axis=2)
        if arr.shape == generado.shape and np.array_equal(arr, generado):
            return arr, f"assets ({ruta.name})"
    return generado, "generado con semilla fija (el archivo de assets no corresponde a este config)"


# =========================================================================== carga y preparación

def estimar_calidad_jpeg(im: Image.Image) -> int | None:
    tablas = getattr(im, "quantization", None)
    if not tablas or 0 not in tablas:
        return None
    base = [16, 11, 10, 16, 24, 40, 51, 61, 12, 12, 14, 19, 26, 58, 60, 55, 14, 13, 16, 24, 40, 57, 69, 56,
            14, 17, 22, 29, 51, 87, 80, 62, 18, 22, 37, 56, 68, 109, 103, 77, 24, 35, 55, 64, 81, 104, 113, 92,
            49, 64, 78, 87, 103, 121, 120, 101, 72, 92, 95, 98, 112, 100, 103, 99]
    escala = 100.0 * sum(tablas[0]) / sum(base)
    q = (200 - escala) / 2 if escala <= 100 else 5000 / escala
    return int(round(min(100, max(1, q))))


def cargar_foto(ruta: Path, lado_max: int | None = None) -> dict:
    """Abre la foto, aplica la orientación EXIF y convierte su perfil ICC a sRGB.

    La orientación se aplica antes de descartar metadatos (si no, las fotos de celular
    quedan giradas) y el perfil se convierte, no se borra (una foto Display P3 cambiaría de color).

    Con `lado_max`, una foto mayor se reduce **antes** de pasar a float: una de 8000 px ocupa 768 MB
    en float32 y el reproceso del 20/10/2026 murió por memoria en ella. `info["factor_carga"]` dice
    cuánto se redujo, para llevar las coordenadas de la foto original (las exclusiones) a esta.
    """
    im = Image.open(ruta)
    info = {"formato": im.format, "ancho_archivo": im.width, "alto_archivo": im.height}
    try:
        info["orientacion_exif"] = int(im.getexif().get(0x0112, 1))
    except Exception:
        info["orientacion_exif"] = 1
    if im.format in ("JPEG", "MPO"):
        info["calidad_jpeg_estimada"] = estimar_calidad_jpeg(im)
    icc = im.info.get("icc_profile")
    im = ImageOps.exif_transpose(im)
    info["factor_carga"] = 1.0
    if lado_max and max(im.size) > lado_max:
        f = lado_max / max(im.size)
        if im.mode == "P":
            im = im.convert("RGBA")
        info["reducida_desde"] = [im.width, im.height]
        im = im.resize((max(1, round(im.width * f)), max(1, round(im.height * f))), LANCZOS, reducing_gap=3.0)
        info["factor_carga"] = round(f, 6)

    if im.mode in ("I;16", "I;16B", "I;16L", "I"):
        arr = np.asarray(im, dtype=np.float32)
        arr = arr / (65535.0 if arr.max() > 255 else 255.0)
        im = Image.fromarray(np.clip(arr * 255 + 0.5, 0, 255).astype(np.uint8)).convert("RGB")

    alfa = None
    if im.mode in ("RGBA", "LA", "PA") or "transparency" in im.info:
        rgba = im.convert("RGBA")
        a = np.asarray(rgba.getchannel("A"), dtype=np.float32) / 255.0
        if (a < 0.98).mean() > 0.01 and (a > 0.5).mean() > 0.001:  # transparencia útil: ya viene recortada
            alfa = a
        im = rgba.convert("RGB")

    info["perfil_icc"] = None
    if icc:
        try:
            origen = ImageCms.ImageCmsProfile(io.BytesIO(icc))
            info["perfil_icc"] = (ImageCms.getProfileDescription(origen) or "").strip() or "sin nombre"
            if im.mode not in ("RGB", "CMYK", "L"):
                im = im.convert("RGB")
            im = ImageCms.profileToProfile(im, origen, ImageCms.createProfile("sRGB"), outputMode="RGB",
                                           renderingIntent=ImageCms.Intent.RELATIVE_COLORIMETRIC)
        except Exception as e:  # perfil dañado: se asume sRGB y se avisa
            info["perfil_icc_error"] = f"{type(e).__name__}: {e}"
            im = im.convert("RGB")
    else:
        im = im.convert("RGB")
    rgb = np.asarray(im, dtype=np.float32) / 255.0
    info["ancho"], info["alto"] = rgb.shape[1], rgb.shape[0]
    return {"rgb": rgb, "alfa": alfa, "info": info}


def huella_archivo(ruta: Path) -> str:
    h = hashlib.sha1()
    with open(ruta, "rb") as fh:
        for bloque in iter(lambda: fh.read(1 << 20), b""):
            h.update(bloque)
    return h.hexdigest()


# =========================================================================== remuestreo

def redim(arr: np.ndarray, ancho: int, alto: int) -> np.ndarray:
    """Lanczos con antialias (Pillow, modo F) para mapas 2D o imágenes H×W×C en float32."""
    if arr.ndim == 3:
        return np.dstack([redim(arr[..., c], ancho, alto) for c in range(arr.shape[2])])
    im = Image.fromarray(np.ascontiguousarray(arr, dtype=np.float32))
    return np.asarray(im.resize((int(ancho), int(alto)), LANCZOS), dtype=np.float32)


def rellenar_color(color: np.ndarray, alfa: np.ndarray, sigma: float = 6.0) -> np.ndarray:
    """Extiende el color del producto hacia la zona transparente (evita bordes oscuros al filtrar)."""
    num = cv2.GaussianBlur(color * alfa[..., None], (0, 0), sigma)
    den = cv2.GaussianBlur(alfa, (0, 0), sigma)[..., None]
    lleno = num / np.maximum(den, 1e-6)
    peso = np.clip(alfa / 0.02, 0, 1)[..., None]
    return np.clip(color * peso + lleno * (1 - peso), 0, 1).astype(np.float32)


def redim_premultiplicado(color: np.ndarray, alfa: np.ndarray, ancho: int, alto: int):
    """Lanczos sobre color×alfa y alfa; luego se divide. Así el color bajo la transparencia no ensucia el borde."""
    a = np.clip(redim(alfa, ancho, alto), 0, 1)
    p = np.clip(redim(color * alfa[..., None], ancho, alto), 0, None)
    c = np.clip(p / np.maximum(a[..., None], 1e-3), 0, 1)
    c = np.where(a[..., None] > 1e-3, c, 0).astype(np.float32)
    return rellenar_color(c, a), a


def elemento(radio_px: float) -> np.ndarray:
    r = max(0, int(round(radio_px)))
    return cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (2 * r + 1, 2 * r + 1))


def contraer(alfa: np.ndarray, px: float) -> np.ndarray:
    return alfa if px <= 0 else cv2.erode(alfa, elemento(px))


def dilatar(alfa: np.ndarray, px: float) -> np.ndarray:
    return alfa if px <= 0 else cv2.dilate(alfa, elemento(px))


def caja(alfa: np.ndarray, umbral: float):
    filas = np.where((alfa >= umbral).any(axis=1))[0]
    if len(filas) == 0:
        return None
    cols = np.where((alfa >= umbral).any(axis=0))[0]
    return int(cols[0]), int(filas[0]), int(cols[-1]) + 1, int(filas[-1]) + 1


# =========================================================================== recorte

class Recortador:
    """Envoltura de rembg. isnet-general-use por defecto: BiRefNet se queda sin memoria en 3 GB."""

    def __init__(self, modelo: str):
        from rembg import new_session
        self.modelo = modelo
        self.sesion = new_session(modelo)

    def mascara(self, rgb: np.ndarray) -> np.ndarray:
        from rembg import remove
        im = Image.fromarray(np.clip(rgb * 255 + 0.5, 0, 255).astype(np.uint8))
        m = remove(im, session=self.sesion, only_mask=True, post_process_mask=False)
        return np.asarray(m, dtype=np.float32) / 255.0


def mascara_dos_pasadas(rec: Recortador, rgb: np.ndarray) -> np.ndarray:
    """Pasada 1 sobre la foto reducida para ubicar el producto; pasada 2 sobre el recorte a resolución completa."""
    alto, ancho = rgb.shape[:2]
    esc = min(1.0, 2048 / max(ancho, alto))
    chica = redim(rgb, round(ancho * esc), round(alto * esc)) if esc < 1 else rgb
    m1 = rec.mascara(np.clip(chica, 0, 1))
    b = caja(m1, 0.5)
    if b is None:
        return np.zeros((alto, ancho), np.float32)
    x0, y0, x1, y1 = [v / esc for v in b]
    pad = 0.08 * max(x1 - x0, y1 - y0)
    X0, Y0 = int(max(0, x0 - pad)), int(max(0, y0 - pad))
    X1, Y1 = int(min(ancho, x1 + pad)), int(min(alto, y1 + pad))
    if (X1 - X0) * (Y1 - Y0) > 0.55 * ancho * alto:  # el producto ya llena la foto
        return np.clip(redim(m1, ancho, alto), 0, 1) if esc < 1 else m1
    m = np.zeros((alto, ancho), np.float32)
    m[Y0:Y1, X0:X1] = rec.mascara(rgb[Y0:Y1, X0:X1])
    return m


def quitar_adornos(m: np.ndarray, rgb: np.ndarray, cfg: dict) -> tuple[np.ndarray, dict]:
    """Quita de la máscara los elementos ajenos que el recorte conservó: los destellos de «Galaxy AI»
    y adornos de render parecidos.

    Un adorno es una isla que cumple las tres cosas a la vez: está separada del cuerpo principal, es
    pequeña frente a él y su color no aparece en él. Una pieza legítima —el segundo audífono, el
    estuche, la tapa— comparte el color del cuerpo, así que se conserva. Medido sobre el catálogo:
    los destellos quedan a 36–72 de distancia en a*b* y ocupan el 0,04–0,41 % del cuerpo; las piezas
    legítimas quedan a 0,3–2,4 y ocupan del 38 al 100 %.

    No toca nada que se superponga al producto: eso abriría un hueco y es REPETIR, no un retoque.
    """
    datos = {"adornos_quitados": 0, "adornos": []}
    if not cfg.get("adornos_quitar", True):
        return m, datos
    binaria = (m > float(cfg.get("alfa_umbral_binario", 0.5))).astype(np.uint8)
    n, etiquetas, stats, _ = cv2.connectedComponentsWithStats(binaria, connectivity=8)
    if n <= 2:
        return m, datos
    areas = stats[1:, cv2.CC_STAT_AREA].astype(np.float64)
    principal = int(np.argmax(areas)) + 1
    area_principal = areas[principal - 1]
    lab = cv2.cvtColor(np.clip(rgb * 255.0, 0, 255).astype(np.uint8), cv2.COLOR_RGB2LAB).astype(np.float32)

    def cromaticidad(idx: int) -> tuple[float, float]:
        sel = etiquetas == idx
        return float(lab[..., 1][sel].mean()) - 128.0, float(lab[..., 2][sel].mean()) - 128.0

    a_p, b_p = cromaticidad(principal)
    dist_min = float(cfg.get("adorno_distancia_ab_min", 20.0))
    area_max = float(cfg.get("adorno_area_max_fraccion", 0.15))
    fuera = []
    for i, area in enumerate(areas, start=1):
        if i == principal or area > area_max * area_principal:
            continue
        a_i, b_i = cromaticidad(i)
        d = math.hypot(a_i - a_p, b_i - b_p)
        if d >= dist_min:
            fuera.append((i, round(d, 1), round(100.0 * area / area_principal, 3)))
    if not fuera:
        return m, datos
    quita = np.isin(etiquetas, [i for i, _, _ in fuera]).astype(np.uint8)
    k = max(3, int(round(max(m.shape) * 0.004)) | 1)   # margen para el antialiasing del adorno
    quita = cv2.dilate(quita, np.ones((k, k), np.uint8)) > 0
    m = m.copy()
    m[quita] = 0.0
    datos["adornos_quitados"] = len(fuera)
    datos["adornos"] = [{"distancia_ab": d, "area_pct": a} for _, d, a in fuera]
    return m, datos


def limpiar_islas(m: np.ndarray, fraccion_min: float, fraccion_aviso: float,
                  umbral: float = 0.5) -> tuple[np.ndarray, dict]:
    """Quita fragmentos menores que `fraccion_min` del área del producto y cuenta las piezas grandes.

    `umbral` decide qué cuenta como producto al agrupar en islas, y es el parámetro
    que salva las superficies de malla. En el JBL Flip 7 la tela de la rejilla sale
    del modelo con alfa entre 0,2 y 0,5: con el corte en 0,5 se parte en fragmentos
    que esta función descarta por pequeños, y el parlante se publica sin cuerpo.
    Bajando el corte a 0,2 la malla entra entera —el producto crece un 81 %— y en
    una máscara limpia el efecto es de apenas un 1 %.
    """
    binaria = (m > umbral).astype(np.uint8)
    n, etiquetas, stats, _ = cv2.connectedComponentsWithStats(binaria, connectivity=8)
    datos = {"islas_descartadas": 0, "mayor_isla_descartada": 0.0, "piezas": 0}
    if n <= 1:
        return m * 0, datos
    areas = stats[1:, cv2.CC_STAT_AREA].astype(np.float64)
    total = areas.sum()
    conservar = np.where(areas >= fraccion_min * total)[0]
    descartar = np.where(areas < fraccion_min * total)[0]
    datos["islas_descartadas"] = int(len(descartar))
    datos["mayor_isla_descartada"] = round(float(areas[descartar].max() / total), 5) if len(descartar) else 0.0
    datos["aviso_pieza"] = bool(len(descartar) and areas[descartar].max() >= fraccion_aviso * total)
    # Las islas que merecen aviso, recortadas a su caja: si al final quedan dentro del producto
    # —un lente o un logo que el modelo vio solo, y el relleno de huecos volvió a encerrar— no se
    # perdió nada (ver `islas_perdidas`).
    datos["_descartadas"] = [
        (int(stats[i + 1, 0]), int(stats[i + 1, 1]),
         etiquetas[stats[i + 1, 1]:stats[i + 1, 1] + stats[i + 1, 3],
                   stats[i + 1, 0]:stats[i + 1, 0] + stats[i + 1, 2]] == i + 1)
        for i in descartar if areas[i] >= fraccion_aviso * total]
    datos["piezas"] = int((areas[conservar] >= 0.05 * areas[conservar].sum()).sum())
    zona = np.isin(etiquetas, conservar + 1).astype(np.uint8)
    k = max(3, int(round(max(m.shape) * 0.004)) | 1)
    zona = cv2.dilate(zona, np.ones((k, k), np.uint8))
    return m * zona, datos


# =========================================================================== completar el producto
#
# Decisión del 20/10/2026: la pantalla de un celular sale tal cual la foto original. El modelo de
# recorte toma por fondo lo claro de un fondo de pantalla —el blanco del iPhone 15, el lavanda del
# Redmi Pad 2— y deja dentro del equipo huecos encerrados por el bisel; `solidificar_interior` solo
# cierra los menores del 0,5 % (para no tapar un asa), así que por los grandes se veía la sombra de
# contacto: manchas grises en 40 de las fotos de la tanda del 15/10/2026, varias de ellas LISTA.
# Con la tapa blanca sobre fondo blanco pasaba lo mismo por el contorno: un mordisco abierto.
#
# La regla: lo que el recorte dejó fuera dentro del producto se devuelve al producto, con sus
# píxeles originales, salvo que en la foto sea fondo. Las constantes viven aquí y no en config.json
# porque no cambian el estilo del catálogo, y allá entrarían en la huella de la configuración.

# Un píxel es fondo si queda a menos de esta ΔE76 del color del fondo de la foto. Los renders de
# fabricante traen el fondo plano —el blanco o el #F5F5F7 de Apple, con ruido de compresión de 1
# o 2—, y una tapa blanca o plateada queda a 3 o más: por eso no puede ser mayor.
FONDO_TOLERANCIA_DE = 2.5
# Un fondo de estudio es claro y parejo; una mesa o una cama no, y ahí no se juzga nada por color.
FONDO_L_MINIMO = 75.0
FONDO_PAREJO_MINIMO = 0.6
# Una región es fondo si al menos esta fracción de sus píxeles lo es.
REGION_FONDO_MINIMO = 0.85
# Las piezas cuya envolvente convexa se completa: las que pesan algo en el recorte (un lápiz o un
# audífono suelto también, pero el 2 % deja fuera las motas).
PIEZA_MINIMA = 0.02
# Las muescas menores que esto, en fracción del producto, son el antialias de una esquina redonda.
MUESCA_MINIMA = 0.0005
# Una muesca se devuelve al producto solo si casi nada en ella es fondo y si continúa el color de
# lo que tiene al lado: la sombra del piso bajo una fila de teléfonos también cae dentro de la
# envolvente, y no es producto.
MUESCA_FONDO_MAXIMO = 0.15
MUESCA_SALTO_MAXIMO = 6.0
# Una pieza es «rectangular» si su envolvente convexa cubre al menos esto de su rectángulo mínimo:
# un celular o una tablet de frente. Ahí la referencia de las muescas es el rectángulo, porque la
# envolvente convexa no recupera una esquina mordida entera, solo su triángulo. Un render en
# perspectiva no llega y se queda con la envolvente.
PIEZA_RECTANGULAR = 0.95


def fondo_de_la_foto(rgb: np.ndarray, m: np.ndarray) -> dict | None:
    """El color del fondo de la foto: la mediana Lab del anillo del borde de la foto, fuera del
    recorte; si ese anillo no sirve, la de lo que está lejos del recorte.

    El anillo va primero porque «lejos del recorte» incluye lo que el modelo no vio: en el Redmi
    Pad 2 _5 era el panel trasero lavanda, y el fondo salía disparejo y sin leer. Se salta el 1 %
    del borde, como `estimador.py`, por el filete oscuro del material de Apple.

    None si no es un fondo de estudio —oscuro, o disparejo como una mesa—: ahí el color no dice
    qué es fondo y qué es producto, y no se completa nada por color."""
    alto, ancho = m.shape
    esc = min(1.0, 512 / max(alto, ancho))
    tam = (max(1, round(ancho * esc)), max(1, round(alto * esc)))
    chica = cv2.resize(rgb, tam, interpolation=cv2.INTER_AREA)
    mchica = (cv2.resize(m, tam, interpolation=cv2.INTER_AREA) > 0.02).astype(np.uint8)

    def leer(zona: np.ndarray, minimo: int):
        if zona.sum() < minimo:
            return None
        lab = rgb_a_lab(chica[zona].reshape(-1, 1, 3))[:, 0]
        mediana = np.median(lab, axis=0)
        parejo = float((np.linalg.norm(lab - mediana, axis=1) <= FONDO_TOLERANCIA_DE).mean())
        if mediana[0] < FONDO_L_MINIMO or parejo < FONDO_PAREJO_MINIMO:
            return None
        return {"lab": mediana.astype(np.float32), "parejo": round(parejo, 3)}

    k = max(1, round(0.01 * max(tam)))
    anillo = np.zeros(mchica.shape, bool)
    anillo[k:k + 3, k:-k] = anillo[-k - 3:-k, k:-k] = True
    anillo[k:-k, k:k + 3] = anillo[k:-k, -k - 3:-k] = True
    leido = leer(anillo & (mchica == 0), 200)
    if leido is not None:
        return leido
    return leer(cv2.dilate(mchica, elemento(0.03 * max(tam))) == 0, 500)


def mapa_de_fondo(rgb: np.ndarray, fondo: dict | None) -> np.ndarray | None:
    """Píxel a píxel, ¿es del color del fondo de la foto?"""
    if fondo is None:
        return None
    lab = rgb_a_lab(rgb)
    return np.linalg.norm(lab - fondo["lab"], axis=-1) <= FONDO_TOLERANCIA_DE


def _salto_por_region(etiquetas: np.ndarray, n: int, producto: np.ndarray, rgb: np.ndarray) -> np.ndarray:
    """Por región etiquetada, la ΔE76 mediana entre sus píxeles junto al producto y el producto
    que tienen al lado; NaN donde no hay con qué comparar.

    Una muesca que el recorte le mordió a una tapa continúa el color de la tapa: el salto es de
    pocas unidades. La sombra del piso de un render no: arranca donde termina el marco del equipo.
    El color vecino se calcula una vez para todas las regiones."""
    sigma = 3.0
    prod = producto.astype(np.float32)
    peso = cv2.GaussianBlur(prod, (0, 0), sigma)
    junto = (etiquetas > 0) & (peso > 0.15)
    salida = np.full(n, np.nan)
    if not junto.any():
        return salida
    vecino = cv2.GaussianBlur(rgb * prod[..., None], (0, 0), sigma)
    color = vecino[junto] / peso[junto][:, None]
    de = np.linalg.norm(rgb_a_lab(rgb[junto].reshape(-1, 1, 3))[:, 0]
                        - rgb_a_lab(color.reshape(-1, 1, 3))[:, 0], axis=1)
    cual = etiquetas[junto]
    orden = np.argsort(cual, kind="stable")
    cual, de = cual[orden], de[orden]
    cortes = np.flatnonzero(np.diff(cual)) + 1
    for grupo, valores in zip(np.split(cual, cortes), np.split(de, cortes)):
        if len(valores) >= 10:
            salida[grupo[0]] = float(np.median(valores))
    return salida


def _fraccion_por_region(etiquetas: np.ndarray, n: int, mapa: np.ndarray) -> np.ndarray:
    """Por región etiquetada, la fracción de sus píxeles que cumplen `mapa`. Un solo barrido."""
    total = np.bincount(etiquetas.ravel(), minlength=n)
    si = np.bincount(etiquetas.ravel(), weights=mapa.ravel().astype(np.float64), minlength=n)
    return si / np.maximum(total, 1)


# La decisión se toma sobre una copia de este lado mayor como mucho: hay fotos de 8000 px en el
# catálogo, y la conversión a Lab y los conteos a resolución completa pedían más de 3 GB.
LADO_DE_DECISION = 2048


def completar_producto(a: np.ndarray, rgb: np.ndarray, sin_huecos: bool,
                       umbral: float = 0.5, prohibido: np.ndarray | None = None,
                       fondo: dict | None | str = "leer") -> tuple[np.ndarray, dict]:
    """Devuelve al producto lo que el recorte dejó fuera de él, y deja fuera lo que es fondo.

    - **Huecos encerrados**: en un celular o una tablet (`sin_huecos`, la categoría de la ficha)
      se rellenan siempre: no tienen huecos pasantes, así que lo encerrado por el bisel es la
      pantalla. En lo demás se rellenan si en la foto no son del color del fondo: el hueco de una
      correa cerrada sí lo es, y sigue abierto.
    - **Muescas**: solo en celulares y tablets, cuyas piezas son convexas. Lo que falta hasta la
      envolvente convexa de cada pieza se rellena si casi nada en ello es fondo y si continúa el
      color de lo que tiene al lado; la cuña de fondo entre dos teléfonos escalonados y la sombra
      del piso del render se quedan fuera.

    `prohibido` son las zonas que la persona excluyó a mano (`marcar.py --excluir`): nunca vuelven.
    Lo rellenado sube a alfa 1: el color lo pone después la foto original (ver procesar.py).
    """
    datos = {"sin_huecos": bool(sin_huecos), "fondo_leido": False, "huecos_rellenos": 0,
             "huecos_abiertos": 0, "muescas_rellenas": 0, "relleno_pct": 0.0, "fondo": None}
    binaria_full = (a > umbral).astype(np.uint8)
    area_full = float(binaria_full.sum())
    if area_full == 0:
        return a, datos
    if fondo == "leer":       # quien ya lo leyó (procesar.py) lo pasa, y no se lee dos veces
        fondo = fondo_de_la_foto(rgb, a)
    datos["fondo_leido"] = fondo is not None
    datos["fondo"] = fondo

    alto, ancho = a.shape
    esc = min(1.0, LADO_DE_DECISION / max(alto, ancho))
    if esc < 1:
        tam = (max(1, round(ancho * esc)), max(1, round(alto * esc)))
        a_d = cv2.resize(a, tam, interpolation=cv2.INTER_AREA)
        rgb_d = cv2.resize(rgb, tam, interpolation=cv2.INTER_AREA)
    else:
        a_d, rgb_d = a, rgb
    binaria = (a_d > umbral).astype(np.uint8)
    area = float(binaria.sum())
    if area == 0:
        return a, datos
    es_fondo = mapa_de_fondo(rgb_d, fondo)
    huecos = np.zeros(binaria.shape, bool)
    muescas_si = np.zeros(binaria.shape, bool)

    n, etiquetas, stats, _ = cv2.connectedComponentsWithStats(1 - binaria, connectivity=4)
    borde = np.unique(np.concatenate([etiquetas[0], etiquetas[-1], etiquetas[:, 0], etiquetas[:, -1]]))
    encerrado = np.ones(n, bool)
    encerrado[0] = False
    encerrado[borde] = False
    if sin_huecos:
        rellenar = encerrado
    elif es_fondo is not None:
        rellenar = encerrado & (_fraccion_por_region(etiquetas, n, es_fondo) < REGION_FONDO_MINIMO)
    else:
        rellenar = np.zeros(n, bool)
    datos["huecos_rellenos"] = int(rellenar.sum())
    datos["huecos_abiertos"] = int((encerrado & ~rellenar).sum())
    huecos = rellenar[etiquetas]

    if sin_huecos and es_fondo is not None:
        lleno = (binaria > 0) | huecos
        np_, piezas, st, _ = cv2.connectedComponentsWithStats(lleno.astype(np.uint8), connectivity=8)
        casco = np.zeros(binaria.shape, np.uint8)
        for j in range(1, np_):
            if st[j, cv2.CC_STAT_AREA] < PIEZA_MINIMA * area:
                continue
            x, y, w, h = st[j, cv2.CC_STAT_LEFT], st[j, cv2.CC_STAT_TOP], st[j, cv2.CC_STAT_WIDTH], st[j, cv2.CC_STAT_HEIGHT]
            contornos, _ = cv2.findContours((piezas[y:y + h, x:x + w] == j).astype(np.uint8), cv2.RETR_EXTERNAL,
                                            cv2.CHAIN_APPROX_SIMPLE, offset=(int(x), int(y)))
            envolvente = cv2.convexHull(np.vstack(contornos))
            rect = cv2.minAreaRect(envolvente)
            area_rect = rect[1][0] * rect[1][1]
            if area_rect > 0 and cv2.contourArea(envolvente) >= PIEZA_RECTANGULAR * area_rect:
                cv2.fillPoly(casco, [np.round(cv2.boxPoints(rect)).astype(np.int32)], 1)
                datos["piezas_rectangulares"] = datos.get("piezas_rectangulares", 0) + 1
            else:
                cv2.fillPoly(casco, [envolvente], 1)
        falta = (casco > 0) & ~lleno
        nm, muescas, sm, _ = cv2.connectedComponentsWithStats(falta.astype(np.uint8), connectivity=4)
        if nm > 1:
            fraccion = _fraccion_por_region(muescas, nm, es_fondo)
            salto = _salto_por_region(muescas, nm, lleno, rgb_d)
            si = np.zeros(nm, bool)
            datos["muescas"] = []
            for k in range(1, nm):
                if sm[k, cv2.CC_STAT_AREA] < MUESCA_MINIMA * area:
                    continue
                s = None if np.isnan(salto[k]) else round(float(salto[k]), 2)
                datos["muescas"].append({"fondo": round(float(fraccion[k]), 3), "salto_de": s,
                                         "area_pct": round(100 * float(sm[k, cv2.CC_STAT_AREA]) / area, 2)})
                si[k] = fraccion[k] <= MUESCA_FONDO_MAXIMO and s is not None and s <= MUESCA_SALTO_MAXIMO
            datos["muescas_rellenas"] = int(si.sum())
            # Píxel a píxel, lo que es fondo se queda fuera: más allá de la esquina redonda de un
            # teléfono hay fondo, y con el rectángulo como referencia esa esquina saldría cuadrada.
            muescas_si = si[muescas] & ~es_fondo

    if not (huecos.any() or muescas_si.any()):
        return a, datos
    # De vuelta a la resolución de la foto: lo rellenado es lo que allá también está fuera del recorte.
    fuera = binaria_full == 0
    if esc < 1:
        huecos = cv2.resize(cv2.dilate(huecos.astype(np.uint8), np.ones((3, 3), np.uint8)), (ancho, alto),
                            interpolation=cv2.INTER_NEAREST) > 0
        muescas_si = cv2.resize(muescas_si.astype(np.uint8), (ancho, alto), interpolation=cv2.INTER_NEAREST) > 0
        # un hueco agrandado no puede derramarse sobre el fondo exterior de la foto
        exterior = np.pad(fuera.astype(np.uint8), 1, constant_values=1)
        cv2.floodFill(exterior, None, (0, 0), 2)
        huecos &= exterior[1:-1, 1:-1] != 2
    relleno = (huecos | muescas_si) & fuera
    if prohibido is not None:
        relleno &= ~prohibido
    a = a.copy()
    a[relleno] = 1.0
    datos["relleno_pct"] = round(100.0 * float(relleno.sum()) / area_full, 2)
    return a, datos


def rellenar_huecos(binaria: np.ndarray) -> np.ndarray:
    """La máscara con todo lo que encierra relleno: lo que no se alcanza desde el borde."""
    fuera = np.pad((~binaria.astype(bool)).astype(np.uint8), 1, constant_values=1)
    cv2.floodFill(fuera, None, (0, 0), 2)
    return fuera[1:-1, 1:-1] != 2


def zona_vigilada(alfa: np.ndarray, es_fondo: np.ndarray | None, margen_px: float) -> np.ndarray:
    """Dónde se mide si el interior del producto cambió frente a la foto original.

    La silueta con sus huecos, salvo los que en la foto son fondo —el aro de una correa—: por un
    hueco que no lo es se ve la sombra, y eso es lo que tiene que saltar; con el alfa ≥ 0,99 solo,
    el hueco quedaba fuera de la medición. **Sin un fondo legible** —un PNG recortado, una foto
    sobre una mesa— no hay con qué saber qué hueco es real, y bajo la transparencia de un PNG la
    foto no guarda nada: se mide solo lo opaco. Así marcaba falsamente el mosquetón del JBL Clip 5.
    """
    silueta = alfa >= 0.5
    if es_fondo is not None:
        silueta = rellenar_huecos(silueta) & ~(es_fondo & ~silueta)
    return contraer(silueta.astype(np.float32), margen_px) > 0.5


# Segunda opinión del recorte por el color del fondo (20/10/2026). Casi todo el material es render
# de fabricante sobre un fondo plano: ahí lo que no es del color del fondo y está pegado al producto
# es producto, y lo que es del color del fondo y se alcanza desde el borde de la foto no lo es.
# Una región de color se suma si al menos esta fracción de sus píxeles tiene croma (C*) de esto o más:
# la sombra del piso de un render es gris, y una tapa blanca no se suma por aquí (la cuida el modelo).
SUMA_CROMA_MIN = 5.0
SUMA_FRACCION_CROMA = 0.6
# Y si mide por lo menos esta fracción del producto: el panel trasero del Redmi Pad 2 es un 40 %.
SUMA_AREA_MIN = 0.005
# Lo que se quita: solo donde el modelo dudó (alfa bajo esto), y solo manchas de por lo menos esta
# fracción del producto que sobreviven a una apertura: el antialias del borde no se toca.
QUITA_ALFA_MAX = 0.9
QUITA_AREA_MIN = 0.002
# Una región solo se suma si no se desvanece hacia el fondo: como mucho SUMA_CASI_FONDO_MAX de sus
# píxeles a menos de SUMA_CASI_FONDO_DE del color del fondo. Un fondo de color pálido con viñeta y el
# reflejo de piso de un celular de color se desvanecen —medido: el 22 % del reflejo queda a menos de
# 10—; el panel del Redmi Pad 2 corta seco contra el blanco y queda en el 0,5 % (revisión del 20/10/2026).
SUMA_CASI_FONDO_DE = 10.0
SUMA_CASI_FONDO_MAX = 0.05


def segunda_opinion(a: np.ndarray, rgb: np.ndarray, fondo: dict | None,
                    umbral: float = 0.5, prohibido: np.ndarray | None = None) -> tuple[np.ndarray, dict]:
    """Corrige el recorte con el color del fondo de la foto, cuando es un fondo de estudio.

    - **Suma** las regiones de color pegadas al producto que el modelo no vio y que no tocan el
      borde de la foto: el panel trasero lavanda del Redmi Pad 2, que salía sin él.
    - **Quita** lo que el modelo marcó con duda, es del color del fondo y se alcanza desde el borde
      de la foto sin cruzar producto: el arco blanco que aparecía sobre el TCL Pad. Un teléfono
      blanco no se pierde por aquí: lo rodea un marco que no es del color del fondo, y lo que el
      modelo ve con seguridad (alfa ≥ 0,9) no se toca.

    Sin un fondo legible no hace nada. Decide a escala reducida, como `completar_producto`. Una
    región que toca `prohibido` —lo excluido a mano— no se suma: sumarla devolvería, fuera del
    rectángulo, el resto de lo que la persona quiso quitar."""
    datos = {"sumado_pct": 0.0, "quitado_pct": 0.0}
    if fondo is None:
        return a, datos
    binaria_full = a > umbral
    area_full = float(binaria_full.sum())
    if area_full == 0:
        return a, datos
    alto, ancho = a.shape
    esc = min(1.0, LADO_DE_DECISION / max(alto, ancho))
    if esc < 1:
        tam = (max(1, round(ancho * esc)), max(1, round(alto * esc)))
        a_d = cv2.resize(a, tam, interpolation=cv2.INTER_AREA)
        rgb_d = cv2.resize(rgb, tam, interpolation=cv2.INTER_AREA)
    else:
        a_d, rgb_d = a, rgb
    binaria = a_d > umbral
    area = float(binaria.sum())
    lab = rgb_a_lab(rgb_d)
    de = np.linalg.norm(lab - fondo["lab"], axis=-1)
    es_fondo = de <= FONDO_TOLERANCIA_DE
    k = elemento(max(1.0, 1.5 * max(a_d.shape) / 1000))

    # ---- sumar: lo que no es fondo, no es producto todavía, tiene color y toca el producto
    otro = (~es_fondo & ~binaria).astype(np.uint8)
    otro = cv2.morphologyEx(otro, cv2.MORPH_OPEN, k)
    n, et, st, _ = cv2.connectedComponentsWithStats(otro, connectivity=8)
    suma = np.zeros(binaria.shape, bool)
    if n > 1:
        croma = np.hypot(lab[..., 1], lab[..., 2]) >= SUMA_CROMA_MIN
        frac_croma = _fraccion_por_region(et, n, croma)
        vecino = cv2.dilate(binaria.astype(np.uint8), elemento(3)) > 0
        toca = np.bincount(et[vecino].ravel(), minlength=n) > 0
        borde = np.zeros(n, bool)
        borde[np.unique(np.concatenate([et[0], et[-1], et[:, 0], et[:, -1]]))] = True
        se_desvanece = _fraccion_por_region(et, n, de < SUMA_CASI_FONDO_DE) > SUMA_CASI_FONDO_MAX
        prohibida = np.zeros(n, bool)
        if prohibido is not None and prohibido.any():
            p_d = (cv2.resize(prohibido.astype(np.uint8), (a_d.shape[1], a_d.shape[0]),
                              interpolation=cv2.INTER_AREA) > 0) if esc < 1 else prohibido
            prohibida[np.unique(et[p_d])] = True
        si = (toca & ~borde & ~prohibida & ~se_desvanece & (frac_croma >= SUMA_FRACCION_CROMA)
              & (st[:, cv2.CC_STAT_AREA] >= SUMA_AREA_MIN * area))
        si[0] = False
        suma = si[et]

    # ---- quitar: lo dudoso del color del fondo que se alcanza desde el borde sin cruzar producto
    seguro = a_d >= QUITA_ALFA_MAX
    paso = (es_fondo & ~seguro).astype(np.uint8)
    # La inundación entra por la franja del borde de la foto, no por su primer píxel: el material de
    # Apple trae un filete oscuro de 1 a 3 px que no es del color del fondo y la cerraba entera.
    k_borde = max(2, round(0.01 * max(a_d.shape)))
    franja = np.zeros(paso.shape, bool)
    franja[:k_borde], franja[-k_borde:], franja[:, :k_borde], franja[:, -k_borde:] = True, True, True, True
    paso[franja & ~seguro] = 1
    exterior = np.pad(paso, 1, constant_values=1)
    cv2.floodFill(exterior, None, (0, 0), 2)
    candidato = ((exterior[1:-1, 1:-1] == 2) & binaria).astype(np.uint8)
    candidato = cv2.morphologyEx(candidato, cv2.MORPH_OPEN, k)
    n, et, st, _ = cv2.connectedComponentsWithStats(candidato, connectivity=8)
    si = st[:, cv2.CC_STAT_AREA] >= QUITA_AREA_MIN * area
    si[0] = False
    quita = si[et]

    if not (suma.any() or quita.any()):
        return a, datos
    if esc < 1:
        suma = cv2.resize(suma.astype(np.uint8), (ancho, alto), interpolation=cv2.INTER_NEAREST) > 0
        quita = cv2.resize(cv2.dilate(quita.astype(np.uint8), np.ones((3, 3), np.uint8)), (ancho, alto),
                           interpolation=cv2.INTER_NEAREST) > 0
        quita &= a < QUITA_ALFA_MAX
    a = a.copy()
    a[suma & ~binaria_full] = 1.0
    a[quita] = 0.0
    datos["sumado_pct"] = round(100.0 * float((suma & ~binaria_full).sum()) / area_full, 2)
    datos["quitado_pct"] = round(100.0 * float(quita.sum()) / area_full, 2)
    return a, datos


def huecos_encerrados(alfa: np.ndarray, umbral: float = 0.5, fraccion_min: float = 0.005) -> list[float]:
    """Los huecos que el producto encierra y que miden por lo menos `fraccion_min` de él, como
    fracción de su área. Por ellos se ve la sombra de contacto: en un celular no debe quedar ninguno."""
    solido = (alfa >= umbral).astype(np.uint8)
    area = float(solido.sum())
    if area == 0:
        return []
    n, etiquetas, stats, _ = cv2.connectedComponentsWithStats(1 - solido, connectivity=4)
    borde = set(np.unique(np.concatenate([etiquetas[0], etiquetas[-1], etiquetas[:, 0], etiquetas[:, -1]])))
    return sorted((round(float(stats[i, cv2.CC_STAT_AREA]) / area, 4) for i in range(1, n)
                   if i not in borde and stats[i, cv2.CC_STAT_AREA] >= fraccion_min * area), reverse=True)


def interior_con_el_original(color: np.ndarray, original: np.ndarray, alfa: np.ndarray,
                             banda_px: float, umbral: float = 0.5) -> np.ndarray:
    """El color del producto: los píxeles de la foto original adentro, el borde descontaminado afuera.

    `descontaminar` estima un color de primer plano en todo píxel con alfa parcial, y el modelo
    deja alfa parcial en el 40 al 73 % del interior de algunas fotos —una tablet de pantalla oscura,
    un teléfono blanco—. Subir después el alfa a 1 no devolvía el color: el TCL Pad salía borroso y
    el «Redmi Pad 2» de la pantalla, ilegible. Con la misma rampa de `solidificar_interior`, a
    `banda_px` del borde ya se publica el original tal cual."""
    dist = cv2.distanceTransform((alfa >= umbral).astype(np.uint8), cv2.DIST_L2, 3)
    w = np.clip(dist / max(float(banda_px), 1e-6), 0.0, 1.0)[..., None]
    return (color * (1.0 - w) + original * w).astype(np.float32)


def islas_perdidas(descartadas: list, alfa: np.ndarray, umbral: float, total: float) -> float:
    """La mayor isla descartada que sigue fuera del producto final, como fracción de `total`.

    `limpiar_islas` descarta las islas pequeñas antes de que el producto se complete, y medido sobre
    la tanda del 15/10/2026 casi todas eran del producto: los lentes de un OPPO A6k, la manzana de un
    iPhone 16e, el «REDMI» de una tablet, que el modelo vio sueltos porque no vio la superficie de
    alrededor. El relleno de huecos los vuelve a encerrar, así que avisar por ellos dejaba en REVISAR
    fotos sin nada perdido. Una isla cuenta si más de la mitad de ella queda fuera."""
    mayor = 0.0
    for x, y, recorte in descartadas:
        h, w = recorte.shape
        fuera = recorte & (alfa[y:y + h, x:x + w] <= umbral)
        if fuera.sum() > 0.5 * recorte.sum():
            mayor = max(mayor, float(recorte.sum()) / total)
    return round(mayor, 5)


def niveles_alfa(m: np.ndarray, bajo: float = 0.08, alto: float = 0.92) -> np.ndarray:
    return np.clip((m - bajo) / (alto - bajo), 0, 1).astype(np.float32)


def lados_tocados(m: np.ndarray, fraccion: float) -> list[str]:
    """Lados de la foto recorridos por una racha de máscara (el producto sigue fuera del encuadre)."""
    alto, ancho = m.shape
    lados = []
    for nombre, linea, largo in (("arriba", m[0], ancho), ("abajo", m[-1], ancho),
                                 ("izquierda", m[:, 0], alto), ("derecha", m[:, -1], alto)):
        on = np.concatenate([[0], (linea > 0.5).astype(np.int8), [0]])
        cambios = np.flatnonzero(np.diff(on))
        rachas = cambios[1::2] - cambios[0::2]
        if len(rachas) and rachas.max() >= max(6, fraccion * largo):
            lados.append(nombre)
    return lados


# Un producto que roza el borde de la foto —recortada al ras, como el JBL Tune 730BT _1— no está
# cortado: su contacto con el borde se angosta hacia afuera, porque es una curva tangente al marco.
# Uno cortado atraviesa el borde con casi el mismo ancho que tiene un poco más adentro. Medido el
# 21/10/2026: en los roces del Tune el contacto es como mucho el 22 % de lo que hay a un 3 % hacia
# adentro y el 5 % del lado del producto; recortando esa foto a propósito, el corte da 1,03 y el 78 %.
RASANTE_ANGOSTO_MAX = 0.5
RASANTE_LARGO_MAX = 0.10


def separar_rasantes(m: np.ndarray, lados: list[str], umbral: float = 0.5) -> tuple[list[str], list[str]]:
    """(cortados, rasantes): de los lados que el producto toca, cuáles lo cortan y cuáles solo roza."""
    b = m > umbral
    c = caja(m, umbral)
    if c is None:
        return list(lados), []
    x0, y0, x1, y1 = c
    alto, ancho = b.shape
    cortados, rasantes = [], []
    for lado in lados:
        horizontal = lado in ("arriba", "abajo")
        prof = max(4, round(0.03 * (alto if horizontal else ancho)))
        borde, adentro = {"arriba": (b[0], b[min(prof, alto - 1)]), "abajo": (b[-1], b[max(-prof - 1, -alto)]),
                          "izquierda": (b[:, 0], b[:, min(prof, ancho - 1)]),
                          "derecha": (b[:, -1], b[:, max(-prof - 1, -ancho)])}[lado]
        extension = (x1 - x0) if horizontal else (y1 - y0)
        n_borde, n_adentro = float(borde.sum()), float(adentro.sum())
        if (n_adentro > 0 and n_borde <= RASANTE_ANGOSTO_MAX * n_adentro
                and n_borde <= RASANTE_LARGO_MAX * max(extension, 1)):
            rasantes.append(lado)
        else:
            cortados.append(lado)
    return cortados, rasantes


def toca_borde(m: np.ndarray, fraccion: float) -> bool:
    return bool(lados_tocados(m, fraccion))


def al_ras(alfa: np.ndarray, umbral: float) -> bool:
    """True si el recorte llega a los cuatro lados: típico de un PNG recortado al ras del producto."""
    return all(bool((linea >= umbral).any()) for linea in (alfa[0], alfa[-1], alfa[:, 0], alfa[:, -1]))


def descontaminar(img: np.ndarray, alfa: np.ndarray) -> tuple[np.ndarray, bool]:
    """Estima el color real del producto en los píxeles semitransparentes (quita el halo del fondo original)."""
    try:
        from pymatting import estimate_foreground_ml
        f = estimate_foreground_ml(img.astype(np.float64), alfa.astype(np.float64))
        return np.clip(f, 0, 1).astype(np.float32), True
    except Exception:
        return img, False


# =========================================================================== tono y enfoque (sólo L*)

def percentil(valores: np.ndarray, p: float) -> float:
    return float(np.percentile(valores, p)) if valores.size else 0.0


def corregir_tono(color: np.ndarray, alfa: np.ndarray, cfg_t: dict, fpx: float) -> tuple[np.ndarray, dict]:
    """Niveles automáticos acotados y microcontraste, sólo sobre L*; a* y b* no se tocan.

    El punto blanco lleva el percentil (100 − recorte) a blanco con un límite de exposición;
    el punto negro lleva el percentil de recorte a negro con un límite de desplazamiento.
    No hay balance de blancos, tono ni saturación: el color del producto no cambia.
    """
    lab = rgb_a_lab(color)
    L = lab[..., 0].copy()
    interior = contraer((alfa >= 0.5).astype(np.float32), 2 * fpx) > 0.5
    datos = {"activo": bool(cfg_t.get("activo", True)), "ev": 0.0, "negro_l": 0.0,
             "microcontraste": 0.0, "recorte_blancos_pct": 0.0, "recorte_negros_pct": 0.0,
             "gamut_limitado_pct": 0.0}
    if not datos["activo"] or interior.sum() < 500:
        return color, datos
    Y = l_a_y(L)
    recorte = float(cfg_t["recorte_max"])
    ev_max = float(cfg_t["ev_max"])
    yi = Y[interior]
    blanco = percentil(yi, 100 * (1 - recorte))
    negro = min(percentil(yi, 100 * recorte), float(l_a_y(cfg_t["negro_max_l"])))
    blanco = min(1.0, max(blanco, negro + 2 ** -ev_max))  # la ganancia total no pasa de ev_max
    ganancia = 1.0 / (blanco - negro)
    Y2 = np.clip((Y - negro) * ganancia, 0, None)
    datos["ev"] = round(math.log2(ganancia), 3)
    datos["negro_l"] = round(float(y_a_l(negro)), 2)
    y2i = Y2[interior]
    # sólo cuenta lo que este ajuste recorta; lo que ya venía quemado o empastado no es nuevo
    datos["recorte_blancos_pct"] = round(100 * float(((y2i >= 0.9999) & (yi < 0.9999)).mean()), 3)
    datos["recorte_negros_pct"] = round(100 * float(((y2i <= 1e-6) & (yi > 1e-6)).mean()), 3)
    L2 = y_a_l(np.minimum(Y2, 1.0))

    k = float(cfg_t.get("microcontraste", 0))
    if k > 0:
        sigma = float(cfg_t["microcontraste_radio_px"]) * fpx
        num = cv2.GaussianBlur(L2 * alfa, (0, 0), sigma)
        den = cv2.GaussianBlur(alfa, (0, 0), sigma)
        local = num / np.maximum(den, 1e-4)
        caida = np.clip(np.minimum(L2, 100 - L2) / 10.0, 0, 1)  # no empuja hacia el recorte
        L2 = L2 + k * (L2 - local) * caida * (den > 1e-3)
        datos["microcontraste"] = k
    L3, limitados = limitar_gamut(L, np.clip(L2, 0, 100).astype(np.float32), lab[..., 1], lab[..., 2])
    datos["gamut_limitado_pct"] = round(100 * float(limitados[interior].mean()), 3)
    lab[..., 0] = L3
    return lab_a_rgb(lab), datos


def solidificar_interior(alfa: np.ndarray, banda_px: float,
                         frac_agujero_max: float = 0.0,
                         umbral: float = 0.5,
                         cierre_px: float = 0.0,
                         fondo_px: np.ndarray | None = None) -> tuple[np.ndarray, float]:
    """Lleva a 1 el alfa del interior del producto y deja el borde como estaba.

    El modelo de recorte devuelve alfa parcial **dentro** del producto cuando su
    superficie se parece al fondo: en una foto del Galaxy A56 el 35,6 % de los
    píxeles interiores tenían alfa < 1, con mínimos de 0,53. Al componer, el gris
    del estudio se veía a través de esas zonas y la pantalla salía con manchas
    grises y oliva que no están en la foto original. Con el fondo blanco lo que se
    filtra es blanco y se nota menos, pero sigue aclarando el interior.

    El interior se define por distancia al cero más cercano, no por erosión de la
    silueta, y esa diferencia importa: `distanceTransform` mide también la
    distancia a un **agujero** del producto, así que un asa calada o el hueco de
    un aro siguen abiertos. Solo se rellena lo que está lejos de cualquier borde.

    La rampa evita la costura: en el borde manda el alfa original, a `banda_px`
    hacia adentro el alfa es 1, y entre medias sube suave. Nunca baja el alfa.
    """
    solido = (alfa >= umbral).astype(np.uint8)
    if not solido.any():
        return alfa, 0.0

    # Los agujeros pequeños del recorte se cierran; los grandes no. Un asa calada
    # o el hueco de un aro son una fracción grande del producto y tienen que
    # seguir abiertos; una mancha del modelo en mitad de una pantalla es diminuta.
    if frac_agujero_max > 0:
        n, etiquetas, stats, _ = cv2.connectedComponentsWithStats(1 - solido, 8)
        area_producto = float(solido.sum())
        borde = set(etiquetas[0, :]) | set(etiquetas[-1, :]) | set(etiquetas[:, 0]) | set(etiquetas[:, -1])
        for i in range(1, n):
            if i in borde:                                   # eso es el fondo, no un agujero
                continue
            if stats[i, cv2.CC_STAT_AREA] <= frac_agujero_max * area_producto:
                solido[etiquetas == i] = 1

    # Las grietas finas se cierran antes de medir la distancia. El modelo parte un
    # producto en dos piezas cuando una costura o un reflejo le baja el alfa en una
    # línea estrecha; si esa línea sobrevive, la rampa la trata como borde y el
    # fondo se ve a través de ella como un fleco claro en mitad del producto.
    # Un cierre morfológico une los dos lados sin mover el contorno exterior.
    # Pero el cierre no sabe de piezas: dos teléfonos a menos de `cierre_px` quedaban unidos por
    # una franja opaca de fondo —el Galaxy A17 de frente pegado al de perfil—. Con `fondo_px`, lo
    # que el cierre añade y en la foto es fondo se queda fuera.
    if cierre_px >= 1:
        k = int(cierre_px) | 1
        cerrado = cv2.morphologyEx(solido, cv2.MORPH_CLOSE,
                                   cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (k, k)))
        if fondo_px is not None:
            cerrado[(cerrado > solido) & fondo_px] = 0
        solido = cerrado

    dist = cv2.distanceTransform(solido, cv2.DIST_L2, 3)
    rampa = np.clip(dist / max(float(banda_px), 1e-6), 0.0, 1.0)
    nuevo = np.maximum(alfa, rampa).astype(np.float32)
    dentro = solido > 0
    subidos = float((nuevo[dentro] - alfa[dentro] > 1e-3).mean()) if dentro.any() else 0.0
    return nuevo, round(subidos, 4)


def enfocar(color: np.ndarray, alfa: np.ndarray, cfg_e: dict, fpx: float) -> np.ndarray:
    """Máscara de enfoque sobre L*, sólo dentro del producto (umbral suave en niveles de 8 bits)."""
    cantidad = float(cfg_e["cantidad"])
    if cantidad <= 0:
        return color
    lab = rgb_a_lab(color)
    L = lab[..., 0].copy()
    detalle = L - cv2.GaussianBlur(L, (0, 0), float(cfg_e["radio_px"]) * fpx)
    umbral = float(cfg_e["umbral"]) * 100.0 / 255.0
    peso = np.clip((np.abs(detalle) - 0.5 * umbral) / umbral, 0, 1)
    nuevo = np.clip(L + cantidad * detalle * peso * alfa, 0, 100).astype(np.float32)
    lab[..., 0], _ = limitar_gamut(L, nuevo, lab[..., 1], lab[..., 2])
    return lab_a_rgb(lab)


# =========================================================================== sombra y composición

def sombra(alfa_lienzo: np.ndarray, cfg: dict) -> np.ndarray:
    """Sombra de contacto: dilata la silueta, la baja y la desenfoca.

    El desplazamiento va en píxeles del lienzo y hacia abajo; las filas que quedan
    descubiertas arriba se rellenan con cero para no arrastrar la silueta.
    """
    fpx = factor_px(cfg)
    d = dilatar(alfa_lienzo, cfg["sombra_dilatacion_px"] * fpx)
    dy = int(round(float(cfg.get("sombra_desplazamiento_y_px", 0.0)) * fpx))
    if dy:
        d = np.roll(d, dy, axis=0)
        if dy > 0:
            d[:dy] = 0.0
        else:
            d[dy:] = 0.0
    s = cv2.GaussianBlur(d, (0, 0), cfg["sombra_sigma_px"] * fpx) * float(cfg["sombra_opacidad"])
    return np.clip(s, 0.0, 1.0).astype(np.float32)


def componer(fondo: np.ndarray, sombra_alfa: np.ndarray, color_sombra, producto: np.ndarray, alfa: np.ndarray):
    """Fondo → sombra → producto, en modo normal. La sombra queda siempre debajo del producto."""
    g = sombra_alfa[..., None]
    salida = fondo * (1 - g)
    salida += np.asarray(color_sombra, np.float32) * g
    salida += alfa[..., None] * (producto - salida)
    return salida


def a_8bits(x: np.ndarray) -> np.ndarray:
    return np.clip(np.floor(x * 255.0 + 0.5), 0, 255).astype(np.uint8)


def a_16bits(x: np.ndarray) -> np.ndarray:
    return np.clip(np.floor(x * 65535.0 + 0.5), 0, 65535).astype(np.uint16)


def cuantizar_tramado(x: np.ndarray, peso: np.ndarray | None, semilla: int, niveles: float = 1.0) -> np.ndarray:
    """8 bits con ruido triangular. Al reducir una imagen el tramado del fondo se promedia y el
    degradado vuelve a quedar en escalones; se tramó de nuevo sólo donde pesa (el entorno)."""
    alto, ancho = x.shape[:2]
    ruido = (ruido_tpdf(ancho, alto, semilla) * niveles).astype(np.float32)
    if peso is not None:
        ruido *= peso
    return np.clip(np.floor(x * 255.0 + ruido[..., None] + 0.5), 0, 255).astype(np.uint8)


def miniatura(img: np.ndarray, lado: int = 300) -> Image.Image:
    """Miniatura sRGB de 8 bits a partir de un arreglo float 0..1 o uint8."""
    alto, ancho = img.shape[:2]
    esc = min(1.0, 2.0 * lado / max(alto, ancho))
    if esc < 1:
        img = cv2.resize(img, (max(1, round(ancho * esc)), max(1, round(alto * esc))), interpolation=cv2.INTER_AREA)
    arr = img if img.dtype == np.uint8 else a_8bits(img)
    im = Image.fromarray(arr, "RGB")
    im.thumbnail((lado, lado), LANCZOS)
    return im


_DIRECCIONES = ("derecha", "abajo a la derecha", "abajo", "abajo a la izquierda",
                "izquierda", "arriba a la izquierda", "arriba", "arriba a la derecha")


def direccion(angulo: float) -> str:
    """Ángulo de imagen (0° = derecha, 90° = abajo, porque y crece hacia abajo) → texto."""
    return _DIRECCIONES[int(((angulo + 22.5) % 360) // 45)]


def direcciones(angulos: list[float]) -> str:
    vistas = []
    for a in sorted(angulos, key=lambda v: (v + 360) % 360):
        d = direccion(a)
        if d not in vistas:
            vistas.append(d)
    return ", ".join(vistas)


def anillos_por_sector(img: np.ndarray, alfa: np.ndarray, cfg: dict, sectores: int = 36):
    """Medianas Lab justo dentro y justo fuera del contorno, por sectores angulares alrededor del centro
    de la caja del producto. Devuelve [(ángulo, lab_dentro, lab_fuera), ...]; ángulo 0° = derecha, 90° = abajo.
    Sólo se convierten a Lab los píxeles de los anillos (ahorra memoria con fotos grandes)."""
    fpx = factor_px(cfg)
    b = caja(alfa, 0.5)
    if b is None:
        return []
    pad = int(round(30 * fpx))
    alto, ancho = alfa.shape
    x0, y0 = max(0, b[0] - pad), max(0, b[1] - pad)
    x1, y1 = min(ancho, b[2] + pad), min(alto, b[3] + pad)
    a = alfa[y0:y1, x0:x1]
    solido = (a >= 0.9).astype(np.uint8)
    dentro = solido.astype(bool) & ~cv2.erode(solido, elemento(3 * fpx)).astype(bool)
    cubierto = (a > 0.1).astype(np.uint8)
    fuera = cv2.dilate(cubierto, elemento(6 * fpx)).astype(bool) & ~cv2.dilate(cubierto, elemento(2 * fpx)).astype(bool)
    cx, cy = (b[0] + b[2]) / 2 - x0, (b[1] + b[3]) / 2 - y0
    region = img[y0:y1, x0:x1]
    res = []
    for zona in (dentro, fuera):
        ys, xs = np.nonzero(zona)
        sec = ((np.arctan2(ys - cy, xs - cx) + np.pi) / (2 * np.pi) * sectores).astype(int) % sectores
        lab = rgb_a_lab(region[ys, xs].reshape(-1, 1, 3))[:, 0]
        res.append((sec, lab))
    (sd, ld), (sf, lf) = res
    salida = []
    for s in range(sectores):
        d, f = ld[sd == s], lf[sf == s]
        if len(d) < 20 or len(f) < 20:
            continue
        angulo = round(s * 360.0 / sectores - 180.0 + 180.0 / sectores, 1)
        salida.append((angulo, np.median(d, axis=0), np.median(f, axis=0)))
    return salida


def _resumen_sectores(valores: list[tuple[float, float]], minimo: float, clave: str, clave_min: str) -> dict:
    datos = {"sectores": len(valores), clave: 0, "fraccion": 0.0, "angulos": [], clave_min: None,
             "peor_angulo": None, "donde": ""}
    for v, angulo in valores:
        if v < minimo:
            datos[clave] += 1
            datos["angulos"].append(angulo)
    if valores:
        datos["fraccion"] = round(datos[clave] / len(valores), 3)
        peor = min(valores)
        datos[clave_min], datos["peor_angulo"] = round(float(peor[0]), 2), peor[1]
        datos["donde"] = direcciones(datos["angulos"])
    return datos


def separacion(final: np.ndarray, alfa: np.ndarray, cfg: dict, sectores: int = 36) -> dict:
    """¿Se distingue el producto del entorno ya compuesto? Diferencia de L* justo dentro y justo fuera
    del contorno, por sectores (medianas, robustas a logos)."""
    valores = [(abs(float(d[0] - f[0])), ang) for ang, d, f in anillos_por_sector(final, alfa, cfg, sectores)]
    return _resumen_sectores(valores, float(cfg["separacion_min_l"]), "sin_separacion", "dl_min")


def contraste_original(foto: np.ndarray, alfa: np.ndarray, cfg: dict, sectores: int = 36) -> dict:
    """¿Se parecía el producto a su fondo original? ΔE76 entre el borde del producto y el fondo real
    que lo rodeaba. Donde es bajo (blanco sobre blanco, negro sobre negro) el recorte es poco fiable:
    puede comerse parte del producto o sumar parte de la superficie."""
    valores = [(float(np.linalg.norm(d - f)), ang) for ang, d, f in anillos_por_sector(foto, alfa, cfg, sectores)]
    return _resumen_sectores(valores, float(cfg["contraste_original_min_de"]), "bajo_contraste", "de_min")


# =========================================================================== métricas

def metrica_banding(decodificada: np.ndarray, referencia: np.ndarray, region: np.ndarray) -> float:
    """Escalones del degradado, en niveles de 8 bits.

    Compara el promedio local del archivo decodificado con el degradado ideal en la zona
    del entorno. Un desplazamiento uniforme no es banding, así que se resta la mediana.
    Referencias medidas con este fondo: 8 bits perfecto ≈ 0,34; JPEG q92 ≈ 0,46;
    AVIF 10 bits q60 ≈ 0,45; AVIF 8 bits q80 ≈ 0,55, q70 ≈ 0,8, q60 ≈ 1,1.
    """
    if region.sum() < 1000:
        return 0.0
    err = decodificada.astype(np.float32).mean(axis=2) - referencia.astype(np.float32).mean(axis=2)
    local = cv2.GaussianBlur(err, (0, 0), 3.0)[region]
    local = local - np.median(local)
    return round(float(np.percentile(np.abs(local), 99)), 3)


def delta_croma(referencia: np.ndarray, salida: np.ndarray) -> float:
    """Diferencia media de crominancia (distancia en el plano a*b*) entre dos listas de píxeles sRGB."""
    if len(referencia) == 0:
        return 0.0
    a = rgb_a_lab(referencia.reshape(-1, 1, 3))[:, 0, 1:]
    b = rgb_a_lab(salida.reshape(-1, 1, 3))[:, 0, 1:]
    return round(float(np.hypot(*(a - b).T).mean()), 3)


class MapaDeRevision:
    """El tercer cuadro de la hoja de revisión: la foto original con lo que el retoque cambió.

    Azul, lo que se devolvió al producto (huecos, muescas, regiones de color); rojo, lo que se le
    quitó (fragmentos descartados, zonas del color del fondo); amarillo, lo que en el interior no
    coincide con el original. Lo de afuera del producto se apaga. Se arma a tamaño de miniatura en
    coordenadas de la foto original, así que no pesa en memoria. Los defectos de la tanda del
    15/10/2026 se veían aquí en segundos; en la hoja de antes había que ampliar foto por foto."""

    AZUL, ROJO, AMARILLO = (40, 110, 255), (235, 40, 40), (255, 210, 0)

    def __init__(self, rgb: np.ndarray, alfa_modelo: np.ndarray, lado: int):
        alto, ancho = alfa_modelo.shape
        self.esc = min(1.0, lado / max(alto, ancho))
        self.tam = (max(1, round(ancho * self.esc)), max(1, round(alto * self.esc)))
        self.base = cv2.resize(rgb, self.tam, interpolation=cv2.INTER_AREA)
        self.antes = cv2.resize(alfa_modelo, self.tam, interpolation=cv2.INTER_AREA)
        self.despues = self.antes
        self.quitado = np.zeros(self.antes.shape, bool)
        self.alterado = np.zeros(self.antes.shape, bool)

    def final(self, alfa: np.ndarray) -> None:
        self.despues = cv2.resize(alfa, self.tam, interpolation=cv2.INTER_AREA)

    def descartadas(self, islas: list) -> None:
        for x, y, recorte in islas:
            h, w = recorte.shape
            chica = cv2.resize(recorte.astype(np.uint8), (max(1, round(w * self.esc)), max(1, round(h * self.esc))),
                               interpolation=cv2.INTER_NEAREST) > 0
            y0, x0 = round(y * self.esc), round(x * self.esc)
            zona = self.quitado[y0:y0 + chica.shape[0], x0:x0 + chica.shape[1]]
            zona |= chica[:zona.shape[0], :zona.shape[1]]

    def alterados(self, ys: np.ndarray, xs: np.ndarray) -> None:
        """Píxeles alterados, en coordenadas de la foto original."""
        yy = np.clip(np.round(ys * self.esc).astype(int), 0, self.tam[1] - 1)
        xx = np.clip(np.round(xs * self.esc).astype(int), 0, self.tam[0] - 1)
        self.alterado[yy, xx] = True

    def imagen(self, umbral: float = 0.5) -> Image.Image:
        antes, despues = self.antes > umbral, self.despues > umbral
        v = self.base.copy()
        v[~despues] *= 0.4                                  # lo que no se publica, apagado
        # rojo solo lo que quedó fuera: una isla descartada que el relleno volvió a encerrar no se perdió
        for zona, color in ((despues & ~antes, self.AZUL), ((antes | self.quitado) & ~despues, self.ROJO),
                            (self.alterado, self.AMARILLO)):
            if zona.any():
                v[zona] = 0.35 * v[zona] + 0.65 * (np.array(color, np.float32) / 255.0)
        return Image.fromarray(a_8bits(v), "RGB")


def pixeles_alterados(referencia: np.ndarray, salida: np.ndarray) -> np.ndarray:
    """Por píxel, ¿se aparta del original más de `ALTERADO_DE`?"""
    if len(referencia) == 0:
        return np.zeros(0, bool)
    a = rgb_a_lab(referencia.reshape(-1, 1, 3))[:, 0]
    b = rgb_a_lab(salida.reshape(-1, 1, 3))[:, 0]
    return np.linalg.norm(a - b, axis=1) > ALTERADO_DE


# Un píxel del interior está alterado si se aparta del original más que esto (ΔE76): el JPEG q92 y
# el remuestreo dejan 1 o 2, y una mancha de la sombra sobre una pantalla blanca pasa de 15.
ALTERADO_DE = 10.0
# Y la foto se marca si lo está más que esta fracción del interior.
ALTERADO_MAXIMO = 0.01


def fraccion_alterada(referencia: np.ndarray, salida: np.ndarray) -> float:
    """Qué fracción de los píxeles se aparta del original más de `ALTERADO_DE`, en luminancia y color.

    `delta_croma` existe para el color, y no ve lo que solo cambia la luminancia: la sombra de
    contacto vista a través de un hueco en una pantalla blanca es gris, sin croma: así salió
    LISTA el iPhone 15 del 15/10/2026. procesar.py la mide sobre la silueta con sus huecos —salvo
    los que en la foto son fondo—, que es donde esa mancha aparece."""
    if len(referencia) == 0:
        return 0.0
    return round(float(pixeles_alterados(referencia, salida).mean()), 4)


# =========================================================================== codificación

def guardar_jpeg(img8: np.ndarray, ruta: Path, calidad: int) -> int:
    """JPEG progresivo 4:4:4 con perfil sRGB y sin EXIF, XMP ni IPTC."""
    buf = io.BytesIO()
    Image.fromarray(img8, "RGB").save(buf, "JPEG", quality=int(calidad), subsampling=0, progressive=True,
                                      optimize=True, icc_profile=SRGB_ICC)
    ruta.write_bytes(buf.getvalue())
    return buf.tell()


def _avif_8(img8: np.ndarray, q: int, cfg: dict) -> tuple[bytes, np.ndarray]:
    buf = io.BytesIO()
    Image.fromarray(img8, "RGB").save(buf, "AVIF", quality=int(q), speed=int(cfg["avif_velocidad"]),
                                      subsampling=cfg["avif_submuestreo"], range="full")
    datos = buf.getvalue()
    dec = np.asarray(Image.open(io.BytesIO(datos)).convert("RGB"), dtype=np.float32)
    return datos, dec


def _avif_10(img16: np.ndarray, q: int, cfg: dict, avif: dict) -> tuple[bytes, np.ndarray]:
    yuv = cfg["avif_submuestreo"].replace(":", "")
    with tempfile.TemporaryDirectory(prefix="estudio-avif-") as tmp:
        t = Path(tmp)
        cv2.imwrite(str(t / "entrada.png"), cv2.cvtColor(img16, cv2.COLOR_RGB2BGR))
        r = subprocess.run([avif["avifenc"], "-d", "10", "-q", str(int(q)), "-s", str(int(cfg["avif_velocidad"])),
                            "-y", yuv, "-r", "full", "--cicp", "1/13/6", "-j", "all",
                            str(t / "entrada.png"), str(t / "salida.avif")], capture_output=True, text=True)
        if r.returncode != 0:
            raise RuntimeError("avifenc falló: " + (r.stderr or r.stdout)[-300:])
        datos = (t / "salida.avif").read_bytes()
        r = subprocess.run([avif["avifdec"], "-d", "16", str(t / "salida.avif"), str(t / "dec.png")],
                           capture_output=True, text=True)
        if r.returncode != 0:
            raise RuntimeError("avifdec falló: " + (r.stderr or r.stdout)[-300:])
        dec16 = cv2.cvtColor(cv2.imread(str(t / "dec.png"), cv2.IMREAD_UNCHANGED), cv2.COLOR_BGR2RGB)
    return datos, dec16.astype(np.float32) / 257.0


def _webp(img8: np.ndarray, q: int) -> tuple[bytes, np.ndarray]:
    buf = io.BytesIO()
    Image.fromarray(img8, "RGB").save(buf, "WEBP", quality=int(q), method=6)
    datos = buf.getvalue()
    return datos, np.asarray(Image.open(io.BytesIO(datos)).convert("RGB"), dtype=np.float32)


def _jpg(img8: np.ndarray, q: int, cfg: dict) -> tuple[bytes, np.ndarray]:
    """JPEG web: progresivo, con perfil sRGB y sin metadatos (como la maestra, a otra calidad)."""
    buf = io.BytesIO()
    Image.fromarray(img8, "RGB").save(buf, "JPEG", quality=int(q), subsampling=cfg.get("jpg_submuestreo", "4:4:4"),
                                      progressive=True, optimize=True, icc_profile=SRGB_ICC)
    datos = buf.getvalue()
    return datos, np.asarray(Image.open(io.BytesIO(datos)).convert("RGB"), dtype=np.float32)


def region_reducida(region: np.ndarray, ancho: int, alto: int) -> np.ndarray:
    """Zona de entorno a otro tamaño. Se reduce por área, no con Lanczos: el rebote de Lanczos hace
    pasar por «entorno» píxeles cuyo lóbulo negativo toca el producto y la métrica se dispara."""
    return cv2.resize(region.astype(np.float32), (int(ancho), int(alto)), interpolation=cv2.INTER_AREA) > 0.999


def codificar_web(formato: str, img8: np.ndarray, img16: np.ndarray, ref: np.ndarray, region: np.ndarray,
                  cfg: dict, avif10: dict | None) -> dict:
    """Codifica y revisa el banding del archivo final; si aparece, sube la calidad por pasos.

    En AVIF se usa 10 bits cuando hay avifenc: con este fondo, 8 bits a calidad 60 deja escalones
    en bloque (≈1,1 niveles) y 10 bits a la misma calidad los reduce a la mitad sin subir el peso.
    JPEG progresivo a calidad 88 queda en ≈0,4. WebP con pérdida no baja de ≈1,1 niveles (anillos)
    ni a calidad 100: es un límite del formato, por eso el respaldo propuesto es JPEG.
    """
    umbral = float(cfg["banding_umbral"])
    diez = False
    if formato == "avif":
        q, q_max, paso = int(cfg["avif_calidad"]), int(cfg["avif_calidad_max"]), int(cfg["avif_paso_calidad"])
        diez = avif10 is not None
    elif formato == "jpg":
        q, q_max, paso = int(cfg["jpg_calidad"]), int(cfg["jpg_calidad_max"]), int(cfg["jpg_paso_calidad"])
    elif formato == "webp":
        q, q_max, paso = int(cfg["webp_calidad"]), int(cfg["webp_calidad_max"]), int(cfg["webp_paso_calidad"])
    else:
        raise ValueError(f"formato web no soportado: {formato}")
    intentos = []
    while True:
        if formato == "avif" and diez:
            datos, dec = _avif_10(img16, q, cfg, avif10)
        elif formato == "avif":
            datos, dec = _avif_8(img8, q, cfg)
        elif formato == "jpg":
            datos, dec = _jpg(img8, q, cfg)
        else:
            datos, dec = _webp(img8, q)
        b = metrica_banding(dec, ref, region)
        intentos.append({"calidad": q, "bits": 10 if diez else 8, "kb": round(len(datos) / 1024), "banding": b})
        if b <= umbral or q >= q_max:
            break
        q = min(q_max, q + paso)
    return {"datos": datos, "calidad": q, "bits": 10 if diez else 8, "banding": intentos[-1]["banding"],
            "banding_ok": intentos[-1]["banding"] <= umbral, "intentos": intentos}


def soporte_avif_pillow() -> bool:
    return bool(features.check("avif"))
