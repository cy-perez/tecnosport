#!/usr/bin/env python3
"""Descarga las tipografias del kit y las deja autoalojadas en woff2.

Uso:
    python3 fuentes.py tokens.json --out kit/fuentes
    python3 fuentes.py --familias "Inter" "Fraunces" --out kit/fuentes

Por que autoalojar y no enlazar a Google Fonts: el sitio deja de depender de un
tercero, carga antes, y el kit se puede entregar completo sin conexion. Ademas
la licencia OFL exige distribuir el texto de la licencia junto a los archivos,
asi que se descarga tambien.

Genera:
    fuentes/*.woff2      los archivos
    fuentes/OFL-*.txt    la licencia de cada familia
    fuentes.css          los @font-face listos para enlazar
"""
import argparse, json, re, shutil, subprocess, sys, urllib.parse, urllib.request
from pathlib import Path

RAW = "https://raw.githubusercontent.com/google/fonts/main/{path}"
LICENCIAS = ["ofl", "apache", "ufl"]

def traer(url, timeout=60):
    req = urllib.request.Request(url, headers={"User-Agent": "skill-diseno-ui-web"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.read()

def slug(nombre):
    return re.sub(r"[^a-z0-9]", "", nombre.lower())

def buscar_familia(nombre):
    """Ruta en el repositorio y archivos, leidos de METADATA.pb.

    Se usa raw en vez de la API de GitHub porque la API limita peticiones por IP
    y falla sin aviso.
    """
    s = slug(nombre)
    for lic in LICENCIAS:
        ruta = "{}/{}".format(lic, s)
        try:
            meta = traer(RAW.format(path=ruta + "/METADATA.pb"), 25).decode("utf-8", "replace")
        except Exception:
            continue
        archivos = re.findall(r'filename:\s*"([^"]+)"', meta)
        if archivos:
            return ruta, sorted(set(archivos)), lic
    return None, None, None

def hay_brotli():
    try:
        import brotli  # noqa
        return True
    except ImportError:
        print("  brotli no esta instalado; intentando instalarlo...")
        try:
            subprocess.run([sys.executable, "-m", "pip", "install", "brotli",
                            "--break-system-packages", "-q", "--timeout", "90"],
                           check=True, capture_output=True, timeout=180)
            import brotli  # noqa
            return True
        except Exception:
            return False

def rango_pesos(ruta_ttf):
    """Si es fuente variable devuelve (min, max) del eje wght."""
    try:
        from fontTools.ttLib import TTFont
        f = TTFont(ruta_ttf, lazy=True)
        if "fvar" in f:
            for eje in f["fvar"].axes:
                if eje.axisTag == "wght":
                    r = (int(eje.minValue), int(eje.maxValue))
                    f.close(); return r
        f.close()
    except Exception:
        pass
    return None

PESOS = {"thin":100,"extralight":200,"light":300,"regular":400,"medium":500,
         "semibold":600,"bold":700,"extrabold":800,"black":900}

def es_italica(nombre_archivo):
    """El repositorio de Google Fonts trae la italica junto a la redonda, y su
    nombre ordena ANTES ('Archivo-Italic[...]' < 'Archivo[...]'). Coger el primer
    archivo variable de la lista descargaba la italica y la declaraba como
    normal: el kit entero salia inclinado."""
    return "italic" in Path(nombre_archivo).stem.lower()


def cara_italica(ruta_ttf):
    """Comprobacion sobre el archivo ya descargado, por si el nombre engana."""
    try:
        from fontTools.ttLib import TTFont
        f = TTFont(ruta_ttf, lazy=True)
        it = bool(f["OS/2"].fsSelection & 1) or f["post"].italicAngle != 0
        f.close()
        return it
    except Exception:
        return False


def peso_de(nombre_archivo):
    base = Path(nombre_archivo).stem.lower()
    italica = "italic" in base
    for k in sorted(PESOS, key=len, reverse=True):
        if k in base.replace("italic", ""):
            return PESOS[k], italica
    return 400, italica

def procesar(familia, pesos_pedidos, destino, comprimir):
    ruta, archivos, lic = buscar_familia(familia)
    if not archivos:
        print("  [!] No se encontro '{}' en google/fonts.".format(familia))
        print("      Revisa el nombre exacto, o marca 'origen': 'local' en tokens.json")
        print("      y adjunta los archivos manualmente.")
        return []
    destino.mkdir(parents=True, exist_ok=True)

    romanos = [a for a in archivos if not es_italica(a)]
    variables = [a for a in romanos if "[" in a]
    caras = []
    elegidos = variables[:1] if variables else [
        a for a in romanos if peso_de(a)[0] in pesos_pedidos and not peso_de(a)[1]]
    if not elegidos:
        elegidos = romanos[:1] or archivos[:1]

    for arch in elegidos:
        try:
            datos = traer(RAW.format(path=urllib.parse.quote(ruta + "/" + arch)), 90)
        except Exception as e:
            print("  [!] No se pudo descargar {}: {}".format(arch, type(e).__name__))
            continue
        tmp = destino / arch
        tmp.write_bytes(datos)
        if cara_italica(tmp):
            print("  [!] {} es una cara italica; se descarta.".format(arch))
            tmp.unlink()
            continue
        rango = rango_pesos(tmp)
        salida = destino / (slug(familia) + ("-variable" if rango else
                  "-{}".format(peso_de(arch)[0])) + ".woff2")
        if comprimir:
            try:
                from fontTools.ttLib.woff2 import compress
                compress(str(tmp), str(salida))
                tmp.unlink()
                final = salida
            except Exception as e:
                print("  [!] No se pudo convertir a woff2 ({}). Se deja el TTF.".format(
                    type(e).__name__))
                final = tmp
        else:
            final = tmp
        caras.append({"familia": familia, "archivo": final.name,
                      "peso": "{} {}".format(*rango) if rango else str(peso_de(arch)[0]),
                      "variable": bool(rango),
                      "kb": round(final.stat().st_size / 1024)})
        print("  {:<28} {:>6} KB   {}".format(
            final.name, caras[-1]["kb"], "variable" if rango else "peso " + caras[-1]["peso"]))

    for nombre_lic in ("OFL.txt", "LICENSE.txt"):
        try:
            txt = traer(RAW.format(path=ruta + "/" + nombre_lic), 25)
            (destino / "{}-{}".format(Path(nombre_lic).stem, slug(familia)) ).with_suffix(".txt").write_bytes(txt)
            break
        except Exception:
            continue
    return caras

def css_fuentes(caras, carpeta="fuentes"):
    L = ["/* Tipografias autoalojadas. Enlaza este archivo ANTES de tokens.css.",
         "   font-display: swap hace que el texto se vea con la fuente de respaldo",
         "   mientras descarga, en vez de quedar invisible. */", ""]
    for c in caras:
        L += ["@font-face {",
              '  font-family: "{}";'.format(c["familia"]),
              "  src: url('./{}/{}') format('woff2');".format(carpeta, c["archivo"]),
              "  font-weight: {};".format(c["peso"]),
              "  font-style: normal;",
              "  font-display: swap;",
              "}", ""]
    return "\n".join(L)

def main():
    ap = argparse.ArgumentParser(description="Descarga y autoaloja las tipografias del kit.")
    ap.add_argument("tokens", nargs="?")
    ap.add_argument("--familias", nargs="*", default=None)
    ap.add_argument("--out", default="fuentes")
    ap.add_argument("--sin-comprimir", action="store_true", help="Deja el TTF sin pasar a woff2")
    a = ap.parse_args()

    pedidos = []
    if a.familias:
        pedidos = [(f, [400, 500, 700]) for f in a.familias]
    elif a.tokens:
        t = json.loads(Path(a.tokens).read_text(encoding="utf-8"))
        tip = t.get("tipografia") or {}
        for rol in ("display", "texto", "mono"):
            d = tip.get(rol) or {}
            fam = d.get("familia")
            if fam and d.get("origen", "google") == "google" and not any(
                    p in str(fam).upper() for p in ("NOMBRE", "XXX", "TODO", "PENDIENTE")):
                pedidos.append((fam, d.get("pesos") or [400, 500, 700]))
    if not pedidos:
        raise SystemExit("Indica un tokens.json con tipografia definida, o usa --familias.")

    vistos, unicos = set(), []
    for f, p in pedidos:
        if f.lower() not in vistos:
            vistos.add(f.lower()); unicos.append((f, p))

    comprimir = not a.sin_comprimir and hay_brotli()
    if not comprimir and not a.sin_comprimir:
        print("  AVISO: sin brotli no se puede generar woff2. Se dejaran los TTF,")
        print("         que pesan el doble. Instala brotli y vuelve a ejecutar.\n")

    destino = Path(a.out)
    caras = []
    for fam, pesos in unicos:
        print("{}:".format(fam))
        caras += procesar(fam, pesos, destino, comprimir)

    if caras:
        css = destino.parent / "fuentes.css"
        css.write_text(css_fuentes(caras, destino.name), encoding="utf-8")
        total = sum(c["kb"] for c in caras)
        print("\n{} archivo(s), {} KB en total.".format(len(caras), total))
        print("Generado {} — enlazalo antes de tokens.css.".format(css))
        print("La licencia de cada familia queda junto a los archivos: distribuirla es obligatorio.")

if __name__ == "__main__":
    main()
