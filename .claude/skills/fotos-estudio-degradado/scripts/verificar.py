#!/usr/bin/env python3
"""
Verifica las salidas contra los criterios de aceptación, releyendo los archivos.

    python verificar.py SALIDA            # usa la configuración guardada en reporte.json
    python verificar.py SALIDA --config otra.json

Por cada foto: salidas exactas con el patrón de nombres; maestra de 8 bits RGB con perfil
sRGB y sin EXIF/XMP/IPTC; archivos web del tamaño correcto que se decodifican; caja del
producto (según la máscara registrada) y centro dentro de tolerancia; esquinas con el
color del fondo; crominancia sin cambios; pesos dentro del objetivo (si no, aviso); y
estados coherentes con la ampliación y el corte. Sale con código 1 si algo falla.
"""
from __future__ import annotations

import argparse
import io
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import entorno  # noqa: E402  (sólo biblioteca estándar)

entorno.exigir()

import numpy as np  # noqa: E402
from PIL import Image, ImageCms, JpegImagePlugin  # noqa: E402

import imagen  # noqa: E402
import reporte  # noqa: E402


def rutas_esperadas(cfg: dict, nombre: str) -> dict:
    ancho, alto = cfg["lienzo"]
    web = []
    for w in cfg["anchos_variantes"]:
        if int(w) > ancho:
            continue
        for fmt in cfg["formatos_web"]:
            web.append({"ruta": f"{cfg['carpeta_web']}/{nombre}-{int(w)}.{fmt}", "ancho": int(w),
                        "alto": round(int(w) * alto / ancho), "formato": fmt})
    return {"maestra": f"{cfg['carpeta_maestras']}/{nombre}.jpg", "web": web}


def marcadores_jpeg(datos: bytes) -> list[tuple[int, bytes]]:
    """Segmentos del JPEG hasta el primer SOS: (marcador, carga útil)."""
    i, out = 2, []
    while i + 4 <= len(datos) and datos[i] == 0xFF:
        m = datos[i + 1]
        if m == 0xFF:
            i += 1
            continue
        if m == 0xDA:
            break
        largo = int.from_bytes(datos[i + 2:i + 4], "big")
        out.append((m, datos[i + 4:i + 2 + largo]))
        i += 2 + largo
    return out


def metadatos_jpeg(seg: list[tuple[int, bytes]], quien: str) -> list[str]:
    """EXIF, XMP o IPTC presentes en un JPEG (no deben quedar: pueden llevar GPS o datos del celular)."""
    fallos = []
    app1 = [c for m, c in seg if m == 0xE1]
    if any(c.startswith(b"Exif\x00") for c in app1):
        fallos.append(f"{quien} tiene EXIF")
    if any(c.startswith(b"http://ns.adobe.com/xap/1.0/") for c in app1):
        fallos.append(f"{quien} tiene XMP")
    if any(m == 0xED for m, _ in seg):
        fallos.append(f"{quien} tiene IPTC (APP13)")
    return fallos


def revisar_maestra(ruta: Path, cfg: dict) -> tuple[list[str], list[str], dict]:
    fallos, avisos, datos_m = [], [], {}
    ancho, alto = cfg["lienzo"]
    datos = ruta.read_bytes()
    seg = marcadores_jpeg(datos)
    fallos += metadatos_jpeg(seg, "la maestra")
    sof = [(m, c) for m, c in seg if m in (0xC0, 0xC1, 0xC2)]
    if not sof:
        fallos.append("no se encontró el encabezado SOF del JPEG")
    else:
        m, c = sof[0]
        if c[0] != 8:
            fallos.append(f"la maestra tiene {c[0]} bits por canal")
        datos_m["progresivo"] = m == 0xC2
        if m != 0xC2:
            avisos.append("la maestra no es JPEG progresivo")
    try:
        im = Image.open(io.BytesIO(datos))
        im.load()
    except Exception as e:
        return fallos + [f"la maestra no se decodifica: {e}"], avisos, datos_m
    if im.size != (ancho, alto):
        fallos.append(f"la maestra mide {im.width}×{im.height}")
    if im.mode != "RGB":
        fallos.append(f"la maestra está en modo {im.mode}")
    if JpegImagePlugin.get_sampling(im) != 0:
        avisos.append("la maestra no usa submuestreo 4:4:4")
    icc = im.info.get("icc_profile")
    if not icc:
        fallos.append("la maestra no tiene perfil ICC")
    else:
        desc = ImageCms.getProfileDescription(ImageCms.ImageCmsProfile(io.BytesIO(icc))) or ""
        datos_m["perfil"] = desc.strip()
        if "srgb" not in desc.lower():
            fallos.append(f"el perfil de la maestra no es sRGB ({desc.strip()})")
    arr = np.asarray(im.convert("RGB"), dtype=np.float32)
    esquina = np.array(imagen.hex_a_rgb(cfg["fondo_esquinas"]), np.float32)
    tol = float(cfg["tolerancia_esquinas"])
    peores = []
    for parche in (arr[:8, :8], arr[:8, -8:], arr[-8:, :8], arr[-8:, -8:]):
        media = parche.reshape(-1, 3).mean(axis=0)
        peores.append(float(np.abs(media - esquina).max()))
    datos_m["esquinas_desvio_max"] = round(max(peores), 2)
    if max(peores) > tol:
        fallos.append(f"las esquinas se desvían {max(peores):.1f} niveles de {cfg['fondo_esquinas']}")
    return fallos, avisos, datos_m


def verificar_foto(f: dict, raiz: Path, cfg: dict) -> dict:
    fallos, avisos, datos = [], [], {}
    sal = f.get("salidas") or {}
    estado = reporte.estado_final(f)
    if f.get("huella_config") and cfg.get("_huella") and f["huella_config"] != cfg["_huella"]:
        avisos.append("se procesó con otros parámetros que el resto del lote")
    if not sal:
        if estado != "REPETIR":
            fallos.append("no tiene salidas y no está marcada REPETIR")
        return {"ok": not fallos, "fallos": fallos, "avisos": avisos, "datos": datos}
    if estado == "REPETIR":
        fallos.append("está en REPETIR pero sus archivos siguen en las carpetas de entrega (usa marcar.py)")
    esperadas = rutas_esperadas(cfg, f["nombre"])
    registradas = {sal["maestra"]["ruta"]} | {w["ruta"] for w in sal.get("web", [])}
    todas = {esperadas["maestra"]} | {w["ruta"] for w in esperadas["web"]}
    if registradas != todas:
        faltan = sorted(todas - registradas)
        sobran = sorted(registradas - todas)
        fallos.append("las salidas no coinciden con la configuración"
                      + (f"; faltan {', '.join(faltan)}" if faltan else "")
                      + (f"; sobran {', '.join(sobran)}" if sobran else "")
                      + " (reprocesa con la misma configuración)")
    for rel in sorted(todas):
        if not (raiz / rel).is_file():
            fallos.append(f"no existe {rel}")
    ruta_m = raiz / esperadas["maestra"]
    if ruta_m.is_file():
        fm, am, dm = revisar_maestra(ruta_m, cfg)
        fallos += fm
        avisos += am
        datos.update(dm)
        kb = ruta_m.stat().st_size / 1024
        if kb > cfg["jpeg_peso_max_kb"]:
            avisos.append(f"la maestra pesa {kb:.0f} KB (objetivo ≤ {cfg['jpeg_peso_max_kb']} KB)")
    for w in esperadas["web"]:
        ruta = raiz / w["ruta"]
        if not ruta.is_file():
            continue
        try:
            im = Image.open(ruta)
            im.load()
        except Exception as e:
            fallos.append(f"{w['ruta']} no se decodifica: {e}")
            continue
        if im.size != (w["ancho"], w["alto"]):
            fallos.append(f"{w['ruta']} mide {im.width}×{im.height}")
        if w["formato"] == "jpg":
            fallos += metadatos_jpeg(marcadores_jpeg(ruta.read_bytes()), w["ruta"])
        kb = ruta.stat().st_size / 1024
        limite = cfg["pesos_max_kb"].get(w["formato"], {}).get(str(w["ancho"]))
        if limite and kb > limite:
            avisos.append(f"{Path(w['ruta']).name} pesa {kb:.0f} KB (objetivo ≤ {limite} KB)")
    caja = f.get("caja_producto") or {}
    ancho, alto = cfg["lienzo"]
    ruta_mascara = raiz / reporte.TRABAJO / "mascaras" / f"{f['nombre']}.png"
    if caja and ruta_mascara.is_file():  # se vuelve a medir sobre la máscara final guardada
        with Image.open(ruta_mascara) as im:
            m = np.asarray(im, dtype=np.float32) / 255.0
        b = imagen.caja(m, float(cfg["alfa_caja_min"]))
        if b is None or m.shape != (alto, ancho):
            fallos.append("la máscara guardada no corresponde al lienzo")
        else:
            lado = max(b[2] - b[0], b[3] - b[1])
            desvio = float(np.hypot((b[0] + b[2]) / 2 - ancho / 2, (b[1] + b[3]) / 2 - alto / 2))
            datos["lado_medido"], datos["desvio_medido"] = lado, round(desvio, 2)
            if lado != caja["lado_mayor"] or abs(desvio - caja["desvio_centro_px"]) > 0.01:
                fallos.append(f"la caja del reporte ({caja['lado_mayor']} px) no coincide con la máscara ({lado} px)")
            caja = dict(caja, lado_mayor=lado, desvio_centro_px=desvio)
    elif caja:
        avisos.append("no está la máscara guardada: la caja se toma del reporte")
    objetivo = round(cfg["ocupacion"] * min(ancho, alto)) if ancho == alto else None
    if caja:
        if objetivo is not None and abs(caja["lado_mayor"] - objetivo) > cfg["tolerancia_lado_px"]:
            fallos.append(f"el lado mayor del producto mide {caja['lado_mayor']} px (objetivo {objetivo} ± "
                          f"{cfg['tolerancia_lado_px']})")
        if caja["desvio_centro_px"] > cfg["tolerancia_centro_px"]:
            fallos.append(f"el producto está {caja['desvio_centro_px']:.1f} px fuera del centro")
    else:
        fallos.append("el reporte no registra la caja del producto")
    dc = f.get("delta_croma")
    if dc is None:
        fallos.append("el reporte no registra la diferencia de crominancia")
    elif dc > cfg["croma_max_delta"]:
        fallos.append(f"la crominancia del producto cambió {dc:.2f} (máximo {cfg['croma_max_delta']})")
    escala = f.get("escala") or 0
    corte = (f.get("recorte") or {}).get("toca_borde") and (f.get("recorte") or {}).get("metodo", "").startswith("rembg")
    if (escala >= cfg["ampliacion_repetir"] or corte) and estado != "REPETIR":
        fallos.append("debería estar marcada REPETIR (ampliación ≥ límite o producto cortado)")
    elif escala >= cfg["ampliacion_revisar"] and estado == "LISTA" and not f.get("revisada"):
        fallos.append("debería estar marcada REVISAR (ampliación ≥ límite)")
    return {"ok": not fallos, "fallos": fallos, "avisos": avisos, "datos": datos}


def archivos_ajenos(raiz: Path, cfg: dict, fotos: list[dict]) -> list[str]:
    esperados = set()
    for f in fotos:
        if f.get("salidas"):
            e = rutas_esperadas(cfg, f["nombre"])
            esperados |= {e["maestra"]} | {w["ruta"] for w in e["web"]}
    ajenos = []
    for carpeta in (cfg["carpeta_maestras"], cfg["carpeta_web"]):
        d = raiz / carpeta
        if d.is_dir():
            for p in sorted(d.rglob("*")):
                if p.is_file() and p.relative_to(raiz).as_posix() not in esperados:
                    ajenos.append(p.relative_to(raiz).as_posix())
    return ajenos


def verificar_carpeta(raiz: Path, cfg: dict | None = None) -> tuple[bool, dict]:
    rep = reporte.cargar(raiz)
    if not rep:
        return False, {"error": f"no hay {reporte.NOMBRE} en {raiz}"}
    if cfg is None:
        cfg = dict(rep["configuracion"], _huella=rep.get("huella_config"))
    resultados = {}
    ok = True
    for f in rep["fotos"]:
        v = verificar_foto(f, raiz, cfg)
        resultados[f["nombre"]] = v
        ok &= bool(v["ok"])
    ajenos = archivos_ajenos(raiz, cfg, rep["fotos"])
    ok &= not ajenos
    return ok, {"fotos": resultados, "archivos_ajenos": ajenos}


def main() -> int:
    for flujo in (sys.stdout, sys.stderr):
        try:
            flujo.reconfigure(encoding="utf-8", errors="replace")
        except Exception:
            pass
    p = argparse.ArgumentParser(description="Verifica las salidas de fotos-estudio-degradado")
    p.add_argument("salida", help="carpeta de salida (la que tiene reporte.json)")
    p.add_argument("--config", help="config.json a usar en lugar del guardado en el reporte")
    a = p.parse_args()
    cfg = imagen.cargar_config(a.config) if a.config else None
    ok, res = verificar_carpeta(Path(a.salida), cfg)
    if "error" in res:
        print(res["error"])
        return 1
    for nombre, v in res["fotos"].items():
        marca = "OK   " if v["ok"] else "FALLA"
        print(f"{marca} {nombre}")
        for x in v["fallos"]:
            print(f"      ✗ {x}")
        for x in v["avisos"]:
            print(f"      · {x}")
    for x in res["archivos_ajenos"]:
        print(f"FALLA archivo que no corresponde a ninguna foto: {x}")
    print("\nTodo cumple." if ok else "\nHay criterios sin cumplir.")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
