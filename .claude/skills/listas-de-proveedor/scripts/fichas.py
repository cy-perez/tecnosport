#!/usr/bin/env python3
"""La carpeta de cada modelo en `catalogo/4-entregables/fichas/`: todo lo del modelo en un sitio.

Decisión del 08/10/2026. Antes la información de un producto vivía repartida en
`catalogo/` —la ficha en `icecat/fichas/<id>.json`, las fotos al lado, la prosa
en `prosa.json`, lo de mi.com en `mi/`— y las fotos retocadas en otra carpeta
que terminó perdiéndose. Ahora cada modelo tiene una carpeta con nombre legible:

    fichas/Samsung Galaxy S25 Ultra/
    ├── samsung-galaxy-s25-ultra-ficha.txt   la genera la skill en cada corrida
    ├── Fotos originales/                    las descarga la persona: <id>_1.jpg, <id>_2.png…
    ├── Fotos procesadas/                    las escribe fotos-estudio-degradado, mismo nombre en .jpg
    └── Fuente de la marca/                  en marcas que bloquean la lectura automática, y en Motorola

**La carpeta se reconoce por su archivo de ficha**, `<id del modelo>-ficha.txt`,
no por el nombre: si el título del modelo se corrige con una equivalencia, la
carpeta se sigue encontrando y las fotos que ya tiene no se pierden.

**Nada de esto borra.** La skill escribe la ficha y crea las carpetas que falten;
las fotos que dejó la persona no se tocan nunca.

**Solo tiene carpeta lo que se va a ofrecer** (regla 18, 15/10/2026): un modelo cuyas
configuraciones quedan todas por debajo de la ganancia mínima no la recibe, porque la
carpeta es donde la persona descarga fotos y nadie debe buscar fotos de lo que no va
a entrar. Si el modelo ya tenía carpeta de una lista anterior, no se borra —puede
tener fotos—: `retirados_con_carpeta` la informa y la persona decide.
"""

import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from colores import colores_sugeridos  # noqa: E402
from margen import margen, se_ofrece  # noqa: E402

ORIGINALES = "Fotos originales"
PROCESADAS = "Fotos procesadas"
FUENTE_DE_LA_MARCA = "Fuente de la marca"

# Marcas cuyo sitio no se lee de forma automática y en las que la persona guarda
# la página de especificaciones en la carpeta del modelo. No es una preferencia:
# mi.com responde 403 a los agentes de IA, JBL pone un captcha y Amazon y
# Nintendo excluyen a Claude en su robots.txt (verificado el 08/10/2026). Esas
# barreras no se eluden; la página que guarda una persona desde su navegador sí
# se puede leer.
MARCAS_SIN_LECTURA_AUTOMATICA = {"Xiaomi", "JBL", "Amazon", "Nintendo"}
# Motorola sí se lee: su página de producto trae la tabla en un JSON del HTML. Pero a veces no trae
# la ficha completa, y la de especificaciones del soporte LATAM responde 403 a los agentes
# (22/10/2026). La carpeta queda lista por si la persona tiene que guardarla.
MARCAS_CON_FUENTE_OPCIONAL = {"Motorola"}

# Lo que se quita del título para armar el slug de mi.com: la red, la conectividad y lo que va
# entre paréntesis. Las submarcas van sin «Xiaomi» delante.
RE_RED_O_CONECTIVIDAD = re.compile(r"\b(?:5G|4G|WiFi|Wi-Fi)\b", re.I)
RE_PULGADAS = re.compile(r"(\d+)(?:[.,](\d+))?\s*[\"″]")


def slugs_de_mi(titulo_modelo: str) -> list[str]:
    """Los slugs de mi.com/co que hay que probar para un modelo, el más probable primero.

    mi.com no es del todo regular —el 22/10/2026 el POCO F9 Ultra estaba en `poco-f9-ultra`, sin
    la red, y el Redmi Note 17 Pro Max en `redmi-note-17-pro-max-5g`, con ella—, así que se dan
    las dos formas. Y responde 200 a cualquier slug: la página buena es la que no termina en
    `/co/errors/404` (`referencias/fichas-tecnicas.md`).
    """
    t = re.sub(r"\(.*?\)", " ", titulo_modelo)
    t = re.sub(r"^Xiaomi\s+(?=(?:Redmi|POCO)\b)", "", t.strip(), flags=re.I)
    t = RE_PULGADAS.sub(lambda m: f"{m.group(1)}-{m.group(2)}-inch" if m.group(2) else f"{m.group(1)}-inch", t)

    def slug(texto):
        return re.sub(r"[^a-z0-9]+", "-", texto.lower()).strip("-")

    sin_red = slug(RE_RED_O_CONECTIVIDAD.sub(" ", t))
    con_red = slug(re.sub(r"\b(?:WiFi|Wi-Fi)\b", " ", t, flags=re.I))
    return list(dict.fromkeys([sin_red, con_red]))


def instrucciones_de_la_fuente(m: dict) -> list[str]:
    """Qué página de la marca guardar en «Fuente de la marca», y cuál no.

    El 22/10/2026 llegaron primero las páginas «General» de dos modelos Xiaomi: sin peso, ni caja,
    ni configuraciones, y hubo que pedirlas otra vez.
    """
    marca, titulo = m.get("marca"), m.get("titulo_modelo") or m.get("titulo")
    carpeta = f"«{FUENTE_DE_LA_MARCA}»"
    if marca == "Xiaomi":
        urls = [f"https://www.mi.com/co/product/{s}/specs/" for s in slugs_de_mi(titulo)]
        L = [f"El sitio de Xiaomi no se puede leer de forma automática. Abre {urls[0]}"]
        L += [f"(si termina en /co/errors/404, prueba {u})" for u in urls[1:]]
        L += [f"y guárdala con Ctrl+S, «solo HTML», en la carpeta {carpeta}. Tiene que ser la pestaña",
              "«Especificaciones» (la URL termina en /specs/), no «General»: la general no trae peso,",
              "contenido de la caja ni configuraciones. Si las dos dan 404, busca el modelo en mi.com/co.",
              "Comprueba lo guardado con: python scripts/fuente_de_marca.py validar catalogo/4-entregables/fichas"]
        return L
    if marca == "JBL":
        return ["La página de JBL pide un captcha, pero el specsheet en PDF se lee. Busca en jbl.com el",
                f"«{titulo} Specsheet» (PDF) y guárdalo en la carpeta {carpeta}; ahí vienen la ficha, el peso",
                "y el contenido de la caja."]
    if marca in MARCAS_CON_FUENTE_OPCIONAL:
        return ["La página de producto de motorola.com/co se lee sola. Si no trae la ficha completa, guarda",
                "la de «Especificaciones» del soporte LATAM (es-latam.support.motorola.com), que responde",
                f"403 a los agentes, en la carpeta {carpeta}."]
    return [f"El sitio de {marca} no se puede leer de forma automática. Guarda la página de especificaciones",
            f"del modelo (Ctrl+S, «solo HTML») en la carpeta {carpeta}."]


def nombre_de_carpeta(titulo: str) -> str:
    """El título del modelo como nombre de carpeta válido en Windows.

    Windows no admite `"` en un nombre: las pulgadas se escriben con la doble
    prima `″`, que se ve igual (decisión del 08/10/2026). Los demás caracteres
    prohibidos se cambian por un espacio.
    """
    nombre = titulo.replace('"', "″")
    nombre = re.sub(r'[<>:/\\|?*]', " ", nombre)
    return re.sub(r"\s+", " ", nombre).strip().rstrip(". ")


def archivo_de_ficha(id_modelo: str) -> str:
    return f"{id_modelo}-ficha.txt"


def carpeta_de_modelo(raiz: Path, id_modelo: str, titulo: str) -> Path:
    """La carpeta que ya tiene la ficha de este modelo, o la que le toca por título."""
    raiz = Path(raiz)
    if raiz.is_dir():
        for carpeta in raiz.iterdir():
            if (carpeta / archivo_de_ficha(id_modelo)).is_file():
                return carpeta
    return raiz / nombre_de_carpeta(titulo)


def id_de_carpeta(carpeta: Path):
    """El id del modelo de una carpeta, por su archivo de ficha; None si no tiene."""
    for f in Path(carpeta).glob("*-ficha.txt"):
        return f.name[: -len("-ficha.txt")]
    return None


def pesos(valor) -> str:
    return f"{valor:,}".replace(",", ".") if valor is not None else "por definir"


def texto_de_ficha(productos: list, retirados: list = ()) -> str:
    """La ficha de un modelo: los datos del equipo y una línea por configuración.

    `retirados` son las configuraciones del modelo que la regla 18 deja fuera: van aparte,
    con su margen, para que se vea por qué la lista las trajo y el sitio no las ofrece.
    """
    m = productos[0]
    paleta = ", ".join(m.get("colores_oficiales") or []) or "por confirmar"
    L = [
        f"MODELO: {m.get('titulo_modelo') or m['titulo']}",
        f"ID DEL MODELO: {m.get('id_modelo') or m['id']}",
        f"CATEGORÍA: {m.get('categoria', '')}",
        f"MARCA: {m.get('marca') or 'por confirmar'}",
        f"PALETA OFICIAL: {paleta}",
        "",
        "CONFIGURACIONES DE ESTA LISTA",
        "-" * 60,
    ]
    for p in productos:
        # Lo mismo que llega al panel (exportar_lista.sugeridos): sin emojis, la paleta completa.
        lista = p.get("colores_sugeridos") or colores_sugeridos(p.get("colores_familia"), p.get("colores_oficiales"))
        sugeridos = ", ".join(lista) or "ninguno (falta la paleta del modelo)"
        if lista and not p.get("colores_familia"):
            sugeridos += " (la paleta completa: la lista no marca colores)"
        L.append(f"- {p['titulo']} (SKU {p['id']})")
        L.append(f"  costo {pesos(p.get('precio_proveedor_cop'))} · mercado {pesos(p.get('precio_mercado_cop'))}"
                 f" · colores sugeridos: {sugeridos}")
    if retirados:
        L += ["", "NO SE OFRECEN: NO LLEGAN A LA GANANCIA MÍNIMA (REGLA 18)", "-" * 60]
        for p in retirados:
            L.append(f"- {p['titulo']}: costo {pesos(p.get('precio_proveedor_cop'))} · mercado "
                     f"{pesos(p.get('precio_mercado_cop'))} · margen {margen(p) * 100:.1f} %")
    L += ["", f"META TÍTULO: {m.get('meta_titulo', '')}", f"META DESCRIPCIÓN: {m.get('meta_descripcion', '')}",
          "", "DESCRIPCIÓN", "-" * 60, m.get("descripcion") or "(pendiente de redactar)"]

    if m.get("marca") in MARCAS_SIN_LECTURA_AUTOMATICA | MARCAS_CON_FUENTE_OPCIONAL:
        L += ["", "FICHA DE LA MARCA", "-" * 60] + instrucciones_de_la_fuente(m)

    supuestos = list(dict.fromkeys(s for p in productos for s in p.get("supuestos") or []))
    if supuestos:
        L += ["", "SUPUESTOS", "-" * 60] + [f"- {s}" for s in supuestos]
    revisar = [f"- {p['titulo']}: {r}" for p in productos for r in p.get("revisar") or []]
    if revisar:
        L += ["", "PENDIENTES ANTES DE PUBLICAR", "-" * 60] + revisar
    fuentes = [f for p in productos[:1] for f in p.get("fuentes_ficha") or []]
    if fuentes:
        L += ["", "FUENTES DE LA FICHA", "-" * 60]
        L += [f"- {'Contenido de la caja: ' if f.get('seccion') == 'caja' else ''}"
              f"{f.get('fuente') or f.get('texto')}" + (f" — {f['url']}" if f.get("url") else "")
              + (f" (consultado el {f['fecha']})" if f.get("fecha") else "") for f in fuentes]
    con_fuentes = [p for p in productos if p.get("fuentes_precio")]
    if con_fuentes:
        L += ["", "FUENTES DEL PRECIO DE MERCADO", "-" * 60]
        for p in con_fuentes:
            L.append(f"{p['titulo']}:")
            for f in p["fuentes_precio"]:
                precio = f.get("precio_cop") or f.get("precio")
                enlace = f.get("enlace") or f.get("url") or ""
                nivel = f" ({f['nivel']})" if f.get("nivel") else ""
                L.append(f"- {f.get('tienda', '?')}{nivel}: {pesos(precio) if precio else '?'} — {enlace}")
    return "\n".join(L) + "\n"


def escribir(datos: dict, raiz: Path) -> list:
    """Escribe la ficha de cada modelo de la lista y crea las carpetas que falten.

    Devuelve las carpetas, en el orden de la lista.
    """
    raiz = Path(raiz)
    raiz.mkdir(parents=True, exist_ok=True)
    por_modelo = {}
    for p in datos["productos"]:
        por_modelo.setdefault(p.get("id_modelo") or p["id"], []).append(p)
    carpetas = []
    for mid, productos in por_modelo.items():
        ofrecidos = [p for p in productos if se_ofrece(p)]
        if not ofrecidos:
            continue
        retirados = [p for p in productos if not se_ofrece(p)]
        carpeta = carpeta_de_modelo(raiz, mid, ofrecidos[0].get("titulo_modelo") or ofrecidos[0]["titulo"])
        (carpeta / ORIGINALES).mkdir(parents=True, exist_ok=True)
        if ofrecidos[0].get("marca") in MARCAS_SIN_LECTURA_AUTOMATICA | MARCAS_CON_FUENTE_OPCIONAL:
            (carpeta / FUENTE_DE_LA_MARCA).mkdir(exist_ok=True)
        (carpeta / archivo_de_ficha(mid)).write_text(texto_de_ficha(ofrecidos, retirados),
                                                     encoding="utf-8", newline="")
        carpetas.append(carpeta)
    return carpetas


def retirados_con_carpeta(datos: dict, raiz: Path) -> list:
    """Los modelos que la regla 18 deja fuera enteros y que ya tenían carpeta: [(título, carpeta)].

    `escribir` no les crea carpeta, pero tampoco borra la que dejó una lista anterior: puede
    tener fotos que la persona descargó. Se informan para que ella decida.
    """
    por_modelo = {}
    for p in datos["productos"]:
        por_modelo.setdefault(p.get("id_modelo") or p["id"], []).append(p)
    salida = []
    for mid, productos in por_modelo.items():
        if any(se_ofrece(p) for p in productos):
            continue
        carpeta = carpeta_de_modelo(raiz, mid, productos[0].get("titulo_modelo") or productos[0]["titulo"])
        if (carpeta / archivo_de_ficha(mid)).is_file():
            salida.append((productos[0].get("titulo_modelo") or productos[0]["titulo"], carpeta))
    return salida
