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

def puede_woff2():
    """Si de verdad se puede producir woff2 aqui y ahora.

    `hay_brotli()` solo mira brotli, y la conversion necesita **las dos cosas**:
    `fontTools` para comprimir y para leer el rango de pesos de una variable, y
    brotli para el algoritmo. Con brotli puesto y fontTools ausente —el caso de
    esta maquina el 21 de septiembre de 2026— la comprobacion daba verde,
    `procesar` fallaba cara por cara con `ModuleNotFoundError` y el kit se
    llenaba de TTF.

    Se comprueba importando exactamente lo que usa `procesar`, no algo parecido.
    """
    try:
        from fontTools.ttLib.woff2 import compress  # noqa: F401
    except Exception:
        return False
    return hay_brotli()


def por_que_empeoraria(destino, comprimir, sin_comprimir=False):
    """Por que NO se deben regenerar las fuentes ahora mismo, o None si se puede.

    Sin `brotli` esto produce TTF en vez de woff2, y sin `fontTools` tampoco lee
    el rango de pesos de una variable. Si el kit **ya** tiene woff2 buenos,
    seguir adelante los reemplaza por archivos peores: el comando deja el kit
    peor que antes de ejecutarlo.

    Paso de verdad el 21 de septiembre de 2026, regenerando el kit para anadir un
    token: `fuentes.css` acabo apuntando a `.ttf` con `font-weight: 400` donde
    antes habia `.woff2` con `100 900`. No fallo nada, no se rompio ninguna
    prueba, y en el navegador se habria visto como la tipografia de respaldo.

    Devuelve el texto del error en vez de imprimirlo o lanzarlo, porque lo usan
    dos sitios: este script y `kit_ui.py --fuentes`, que importa este modulo y
    llama a `procesar()` sin pasar por `main()`.
    """
    if comprimir or sin_comprimir:
        return None
    destino = Path(destino)
    if not (destino.is_dir() and any(destino.glob("*.woff2"))):
        return None
    return ("  ERROR: falta fonttools o brotli para generar woff2, y en '{}' ya hay .woff2\n"
            "         buenos. Seguir los reemplazaria por TTF: mas pesados y sin el rango\n"
            "         de pesos de las variables.\n"
            "         Instala las dos cosas y repite:  pip install fonttools brotli\n"
            "         O, si de verdad quieres los TTF, pasa --sin-comprimir.".format(destino))


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

def formato_de(nombre):
    """El `format()` que le toca al archivo que de verdad se escribio.

    Estaba escrito a mano como 'woff2' para todas las caras, y la conversion a
    woff2 puede fallar y dejar el TTF (ver `procesar`): el resultado era un .ttf
    declarado como woff2. El navegador lo carga igual —adivina por los bytes—,
    asi que nada se rompe a la vista y nadie se entera.
    """
    return "woff2" if nombre.lower().endswith(".woff2") else "truetype"


def css_fuentes(caras, carpeta="fuentes"):
    L = ["/* Tipografias autoalojadas. Enlaza este archivo ANTES de tokens.css.",
         "   font-display: swap hace que el texto se vea con la fuente de respaldo",
         "   mientras descarga, en vez de quedar invisible. */", ""]
    for c in caras:
        L += ["@font-face {",
              '  font-family: "{}";'.format(c["familia"]),
              "  src: url('./{}/{}') format('{}');".format(
                  carpeta, c["archivo"], formato_de(c["archivo"])),
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

    comprimir = not a.sin_comprimir and puede_woff2()
    destino = Path(a.out)

    motivo = por_que_empeoraria(destino, comprimir, a.sin_comprimir)
    if motivo:
        print(motivo)
        sys.exit(1)
    if not comprimir and not a.sin_comprimir:
        print("  AVISO: falta fonttools o brotli, asi que no se puede generar woff2.")
        print("         Se dejaran los TTF, que pesan el doble y no traen el rango")

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
