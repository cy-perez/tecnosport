#!/usr/bin/env python3
"""Descarga las tipografias del kit y las deja autoalojadas en woff2.

Uso:
    python3 fuentes.py tokens.json --out kit/fuentes
    python3 fuentes.py --familias "Inter" "Fraunces" --out kit/fuentes
    python3 fuentes.py --out kit/fuentes --desde-local   # recorta lo que ya hay

Las familias se **recortan al alfabeto latino** antes de comprimir. Lo que sube
Google trae cirilico, griego y vietnamita dentro: IBM Plex Sans pesaba 225 KB y
este sitio no escribe ni una letra de eso. Con `--completas` se deja entera.

Por que autoalojar y no enlazar a Google Fonts: el sitio deja de depender de un
tercero, carga antes, y el kit se puede entregar completo sin conexion. Ademas
la licencia OFL exige distribuir el texto de la licencia junto a los archivos,
asi que se descarga tambien.

Genera:
    fuentes/*.woff2      los archivos
    fuentes/OFL-*.txt    la licencia de cada familia
    fuentes.css          los @font-face listos para enlazar
"""
import argparse, json, re, sys, urllib.parse, urllib.request
from pathlib import Path

RAW = "https://raw.githubusercontent.com/google/fonts/main/{path}"
LICENCIAS = ["ofl", "apache", "ufl"]

# Los caracteres que se conservan al recortar. Son los rangos "latin" y "latin-ext" que
# publica Google Fonts, y no una lista inventada: ahi estan los acentos, la enye, los
# signos de apertura, las comillas y rayas tipograficas (U+2000-206F), el euro y el
# simbolo de marca registrada. El peso todavia era el que subio de Google: las familias
# vienen con cirilico, griego y vietnamita dentro, que este sitio no escribe en ninguna
# parte. IBM Plex Sans pesaba 225 KB por eso.
#
# Se incluye latin-ext y no solo latin porque el catalogo lo escriben proveedores: un
# nombre de producto con una letra centroeuropea no puede salir en tofu por ahorrar 8 KB.
RANGO_LATINO = (
    "U+0000-00FF,U+0131,U+0152-0153,U+02BB-02BC,U+02C6,U+02DA,U+02DC,"
    "U+0304,U+0308,U+0329,U+2000-206F,U+2074,U+20AC,U+2122,U+2191,U+2193,"
    "U+2212,U+2215,U+FEFF,U+FFFD,"
    "U+0100-02BA,U+02BD-02C5,U+02C7-02CC,U+02CE-02D7,U+02DD-02FF,"
    "U+1D00-1DBF,U+1E00-1E9F,U+1EF2-1EFF,U+2020,U+20A0-20AB,U+20AD-20C0,"
    "U+2113,U+2C60-2C7F,U+A720-A7FF"
)

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
    """Si brotli esta disponible. Solo mira; no instala nada.

    Instalaba brotli por su cuenta con `pip --break-system-packages` cuando no lo
    encontraba, sin preguntar. Viene de la skill que genero este kit, y choca con
    el "no agregues dependencias sin preguntar" del CLAUDE.md del proyecto: quien
    regenera en una maquina limpia se encontraba con un paquete instalado que no
    pidio, y en el entorno del sistema. Ahora se dice que falta y como ponerlo
    —`por_que_empeoraria` ya escribe la linea de pip—, que es lo que deja la
    decision donde tiene que estar.
    """
    try:
        import brotli  # noqa
        return True
    except ImportError:
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


def subconjunto(origen, salida, unicodes, flavor=None):
    """Recorta una fuente a `unicodes` y la escribe en `salida`.

    Conserva el eje de pesos: `Subsetter` no instancia las variables, asi que una
    familia variable sigue siendo variable despues del recorte. Eso importa aqui,
    donde `fuentes.css` declara `font-weight: 100 900`; si el recorte fijara un peso,
    el CSS estaria mintiendo y el navegador sintetizaria las negritas.

    `name_IDs` va mas alla del valor por omision (0-6) para conservar el 13 y el 14,
    que son la licencia y su URL dentro del propio archivo. La OFL se cumple con el
    OFL-*.txt que ya se distribuye al lado, pero quitar el aviso de dentro de la
    fuente al recortarla es gratis de evitar.
    """
    from fontTools import subset
    opciones = subset.Options()
    opciones.name_IDs = [0, 1, 2, 3, 4, 5, 6, 13, 14]
    opciones.notdef_outline = True
    if flavor:
        opciones.flavor = flavor
    fuente = subset.load_font(str(origen), opciones)
    recortador = subset.Subsetter(options=opciones)
    recortador.populate(unicodes=subset.parse_unicodes(unicodes))
    recortador.subset(fuente)
    subset.save_font(fuente, str(salida), opciones)
    fuente.close()


def recortar_lo_que_hay(destino, unicodes):
    """Recorta los woff2 que ya estan en el kit, sin bajar nada.

    Existe para que anadir el recorte no signifique ademas traerse la version de hoy
    de cada familia: `procesar` descarga de google/fonts, y esas familias cambian.
    Mezclar las dos cosas en un commit deja sin responder cual de las dos movio lo
    que se vea en pantalla.

    No reescribe `fuentes.css`: los nombres, los pesos y el `format` son los mismos
    despues del recorte, y regenerar un archivo que no cambia solo ensucia el diff.
    """
    destino = Path(destino)
    archivos = sorted(destino.glob("*.woff2"))
    if not archivos:
        raise SystemExit("No hay .woff2 en '{}': nada que recortar.".format(destino))
    antes_total = despues_total = 0
    for arch in archivos:
        antes = arch.stat().st_size
        tmp = arch.with_name(arch.name + ".recortada")
        subconjunto(arch, tmp, unicodes, flavor="woff2")
        tmp.replace(arch)
        despues = arch.stat().st_size
        antes_total += antes
        despues_total += despues
        print("  {:<30} {:>5} KB -> {:>4} KB  ({:.0f} % menos)".format(
            arch.name, round(antes / 1024), round(despues / 1024),
            100 * (1 - despues / antes)))
    print("\n{} archivo(s): {} KB -> {} KB, {} KB menos ({:.0f} %).".format(
        len(archivos), round(antes_total / 1024), round(despues_total / 1024),
        round((antes_total - despues_total) / 1024),
        100 * (1 - despues_total / antes_total)))


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

def procesar(familia, pesos_pedidos, destino, comprimir, recortar=True):
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
        if recortar:
            antes = tmp.stat().st_size
            try:
                parcial = tmp.with_name(tmp.name + ".recortada")
                subconjunto(tmp, parcial, RANGO_LATINO)
                parcial.replace(tmp)
                print("  {:<28} recorte a latino: {} KB -> {} KB".format(
                    arch, round(antes / 1024), round(tmp.stat().st_size / 1024)))
            except Exception as e:
                # Que el recorte falle deja la fuente completa, que es lo que habia
                # antes de que esto existiera: mas pesada, nunca rota. Por eso avisa y
                # sigue, en vez de negarse como hace `por_que_empeoraria` con el woff2,
                # donde continuar si dejaba el kit peor.
                print("  [!] No se pudo recortar {} ({}). Se deja completa.".format(
                    arch, type(e).__name__))
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
    ap.add_argument("--desde-local", action="store_true",
                    help="Recorta los woff2 que ya estan en --out, sin descargar nada")
    ap.add_argument("--completas", action="store_true",
                    help="No recorta: deja cada familia con todos sus alfabetos")
    a = ap.parse_args()

    if a.desde_local:
        if a.completas:
            raise SystemExit("--desde-local solo sirve para recortar; con --completas no hace nada.")
        if not puede_woff2():
            raise SystemExit(
                "  ERROR: recortar un woff2 necesita fontTools y brotli.\n"
                "         Instala las dos cosas y repite:  pip install fonttools brotli")
        print("Recortando lo que ya hay en '{}' (sin red):".format(a.out))
        recortar_lo_que_hay(Path(a.out), RANGO_LATINO)
        print("fuentes.css no cambia: los nombres y los pesos son los mismos.")
        return

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
        caras += procesar(fam, pesos, destino, comprimir, recortar=not a.completas)

    if caras:
        css = destino.parent / "fuentes.css"
        css.write_text(css_fuentes(caras, destino.name), encoding="utf-8")
        total = sum(c["kb"] for c in caras)
        print("\n{} archivo(s), {} KB en total.".format(len(caras), total))
        print("Generado {} — enlazalo antes de tokens.css.".format(css))
        print("La licencia de cada familia queda junto a los archivos: distribuirla es obligatorio.")

if __name__ == "__main__":
    main()
