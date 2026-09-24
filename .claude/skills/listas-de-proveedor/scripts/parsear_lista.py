#!/usr/bin/env python3
"""
Convierte una lista de proveedor (mensaje de WhatsApp) en datos estructurados.

Uso:
    python3 parsear_lista.py lista.txt --salida productos.json

Salida:
    productos.json   -> productos incluidos, descartados y líneas sin clasificar
    revision.md      -> reporte legible con lo que necesita confirmación humana

El script NO inventa datos: extrae lo que está escrito y marca con `revisar`
todo lo que quedó ambiguo. La investigación de precios de mercado, la ficha
técnica y la confirmación del nombre comercial oficial las hace Claude después.
"""

import argparse
import json
import re
import unicodedata
from pathlib import Path

VS16 = "\ufe0f"

# --------------------------------------------------------------------------
# Reglas de negocio (editables)
# --------------------------------------------------------------------------

# En las listas $1.850 significa 1.850.000 COP, pero a veces escriben el precio
# completo ($1.960.000). Se distingue por la cantidad de dígitos.
MULTIPLICADOR_PRECIO = 1000
DIGITOS_PRECIO_COMPLETO = 6

# Los cables quedaron por fuera (decisión del negocio, 14/09/2026): la lista nunca
# trae longitud, potencia ni marca, y sin eso no se publican.
# Los accesorios sueltos también quedaron por fuera (decisión del negocio,
# 15/09/2026): el control de consola, el pencil táctil y el rastreador tipo tag.
# La consola y la tablet sí entran; el accesorio que se vende aparte, no.
# El pencil y el tag ya caían en "variedad"; el control tenía su propia categoría
# y por eso hubo que sacar "accesorios_consola" de aquí. Se deja la categoría viva
# en ENCABEZADOS y en VINETAS_CATEGORIA a propósito: así el control cae en
# descartados con su motivo a la vista, y no en "sin clasificar".
# Los cargadores y las power bank salieron el 24/09/2026, por lo mismo que ya
# había sacado a los cables y a los accesorios sueltos: se venden aparte del
# equipo, con margen bajo y rotación lenta, y cada uno obliga a investigar un
# precio de mercado propio para muy poca venta. Lo que se vende junto al equipo
# entra; lo que alimenta al equipo, no. Quedan vivas en ENCABEZADOS y en
# VINETAS_CATEGORIA por la misma razón que accesorios_consola: así el cubo de
# 25 W y la power bank de 20.000 mAh caen en descartados con su motivo y no en
# "sin clasificar".
CATEGORIAS_INCLUIDAS = {
    "celulares",
    "tablets",
    "relojes",
    "audifonos",
    "consolas",
    "computadores",
    "proyectores",
    "parlantes",
}

# Solo se publica el equipo sellado sin activar.
CONDICIONES_PUBLICABLES = {"nuevo"}

# Un celular por debajo de este precio de proveedor no entra al análisis.
# Los demás productos (relojes, audífonos, parlantes...) no tienen mínimo.
PRECIO_MINIMO_CELULAR_COP = 500_000

# Marcas que no se analizan, en NINGUNA categoría (decisión del negocio,
# 15/09/2026). El retail colombiano de primera mano —Alkosto, Ktronix, Éxito,
# Olímpica, Panamericana— no las vende, así que no hay precio de mercado
# admisible con el cual calcular margen, y un margen sin fuente no se puede
# defender ante el negocio.
# La regla nació acotada a celulares, se amplió a tablets y terminó cubriendo
# todo el surtido de la marca el mismo día: el problema no era la categoría
# sino que el retail no trabaja esas marcas.
MARCAS_EXCLUIDAS = {
    "krono", "bmax", "itel", "zte", "infinix", "tecno",
}

# Un producto que después de fusionar repetidos sigue sin precio se descarta:
# sin costo no hay margen que calcular ni precio que publicar.
DESCARTAR_SIN_PRECIO = True

# Un computador entra solo si la lista trae marca y una referencia (línea o
# código de modelo: "Vivobook 15 X1504", "14-em0001la"). Procesador, RAM, disco y
# pulgadas describen decenas de equipos distintos; con eso solo no se publica.
DESCARTAR_COMPUTADOR_SIN_REFERENCIA = True

# Cuando el mismo producto aparece con precios distintos se toma el menor.
# Queda anotado en `supuestos`, no en `revisar`: ya está decidido.

# --------------------------------------------------------------------------
# Mapas de reconocimiento
# --------------------------------------------------------------------------

# Viñetas que solo indican condición: la categoría la pone la sección o el texto.
VINETAS_CONDICION = {
    "✔": "nuevo",
    "✅": "nuevo",
    "⚠": "nuevo_activado",
    "🚀": "nuevo",
    "📲": None,
    "📱": None,
}

# Viñetas que sí determinan la categoría.
VINETAS_CATEGORIA = {
    "⌚": "relojes",
    "🎧": "audifonos",
    "🔌": "cargadores",
    "🪫": "power_bank",
    "🔋": "power_bank",
    "🔥": "cables",
    "🎮": "accesorios_consola",
    "👾": "consolas",
    "💻": "computadores",
    "🖥": "computadores",
    "📽": "proyectores",
    "📺": "televisores",
    "📡": "routers",
    "🔊": "parlantes",
    "🖨": "impresoras",
    "🫟": "tintas",
    "🥶": "variedad",
    "🛴": "variedad",
    "📍": "variedad",
    "🧺": "variedad",
    "✍": "variedad",
    "🐦‍🔥": "lectores",
    "🐦": "lectores",
}

# Palabra clave del encabezado -> (categoría, marca fija, condición)
ENCABEZADOS = [
    ("IPH NUEVOS", ("celulares", "Apple", "nuevo")),
    ("NUEVOS ACTIVOS", ("celulares", "Apple", "nuevo_activado")),
    ("CON CAJA", ("celulares", "Apple", "con_caja")),
    ("IPHONE USADO", ("celulares", "Apple", "usado")),
    ("USADO", (None, None, "usado")),
    ("APPLE WATCH", ("relojes", "Apple", "nuevo")),
    ("RELOJES", ("relojes", None, "nuevo")),
    ("TABLET", ("tablets", None, "nuevo")),
    ("AUDIFONOS", ("audifonos", None, "nuevo")),
    ("AUDÍFONOS", ("audifonos", None, "nuevo")),
    ("CARGADORES", ("cargadores", None, "nuevo")),
    ("CABLE", ("cables", None, "nuevo")),
    ("POWER BAND", ("power_bank", "Xiaomi", "nuevo")),
    ("POWER BANK", ("power_bank", None, "nuevo")),
    ("ACCESORIOS DE PS5", ("accesorios_consola", "Sony", "nuevo")),
    ("COMPUTADORES", ("computadores", None, "nuevo")),
    ("FLECHA", ("celulares", None, "nuevo")),
    ("TELEVISORES", ("televisores", None, "nuevo")),
    ("PARLANTERIA", ("parlantes", None, "nuevo")),
    ("PARLANTE", ("parlantes", None, "nuevo")),
    ("ROUTERS", ("routers", None, "nuevo")),
    ("VARIEDAD", ("variedad", None, "nuevo")),
    ("LECTOR", ("lectores", None, "nuevo")),
    ("IMPRESORAS TERMICA", ("impresoras", None, "nuevo")),
    ("IMPRESORA", ("impresoras", None, "nuevo")),
    ("TINTAS", ("tintas", None, "nuevo")),
    ("REPUESTOS", ("repuestos", None, "nuevo")),
    ("ACCESORIOS", ("accesorios", None, "nuevo")),
    ("ACCESSORIOS", ("accesorios", None, "nuevo")),
    ("MERCANCIA", (None, None, "nuevo")),
    ("MERCANCÍA", (None, None, "nuevo")),
]

MARCAS = [
    "APPLE", "SAMSUNG", "XIAOMI", "HONOR", "JBL", "BOSE", "INFINIX", "TECNO",
    "ASUS", "HP", "LENOVO", "ACER", "DELL", "EPSON", "CANON", "TENDA",
    "TP-LINK", "MERCURY", "SONY", "NINTENDO", "MOTOROLA", "REALME", "ITEL",
    "OPPO", "HUAWEI", "ZTE", "ALCATEL", "NOKIA", "TCL", "KRONO", "BMAX",
    "CORN", "FLY", "LG", "MABE", "JALTECH", "SAT", "VIVO", "BECLAD",
]

# Submarcas: el token se queda en el modelo, pero la marca es la casa matriz.
SUBMARCAS = {
    "POCO": "Xiaomi", "REDMI": "Xiaomi", "NUBIA": "ZTE", "MOTO": "Motorola",
    "EDGE": "Motorola", "GALAXY": "Samsung", "IPHONE": "Apple", "IPAD": "Apple",
    "SPARK": "Tecno", "POVA": "Tecno", "CAMON": "Tecno", "MEGAPAD": "Tecno",
    "XPAD": "Infinix",
}

COLORES = {
    "⚫": "Negro", "🖤": "Negro", "◼": "Negro", "⬛": "Negro",
    "⚪": "Blanco", "🤍": "Blanco", "◻": "Blanco", "⬜": "Blanco",
    "🔵": "Azul", "💙": "Azul",
    "🩵": "Azul claro",
    "🩶": "Gris",
    "💜": "Morado", "🟣": "Morado",
    "💚": "Verde", "🟢": "Verde",
    "💛": "Amarillo", "🟡": "Amarillo",
    "❤": "Rojo", "🔴": "Rojo", "♥": "Rojo",
    "🩷": "Rosado",
    "🧡": "Naranja", "🟠": "Naranja",
    "🤎": "Café", "🟤": "Café",
}

TYPOS = {
    "LAPTO ": "LAPTOP ", "WACH": "WATCH", "CHOISE": "CHOICE", "TERA": "TB",
    " PM ": " PRO MAX ", "AUDIFONO": "AUDÍFONO", "ACCESSORIOS": "ACCESORIOS",
    "BUNDLEE": "BUNDLE", "MAUSE": "MOUSE", "FUSIÓN": "FUSION",
    # El aviso de llegada dice "edición especial" y la lista "ESPECIAL": es el mismo equipo.
    "EDICIÓN ESPECIAL": "ESPECIAL", "EDICION ESPECIAL": "ESPECIAL",
}

CONECTORES = [
    (r"\bTIPO\s*C\b", "USB-C"), (r"\bTIPO\s*A\b", "USB-A"),
    (r"\bMICRO\s*USB\b", "Micro USB"), (r"\bLIGHTNING\b", "Lightning"),
    (r"\bUSB\s*C\b", "USB-C"), (r"^\s*C\s*$", "USB-C"),
]

ALIAS = [
    (r"\bNINTENDO SWITCH 2\b", "Nintendo", "Switch 2"),
    (r"\bSWITCH 2\b", "Nintendo", "Switch 2"),
    (r"\bSWITCH\b", "Nintendo", "Switch"),
    (r"\bPS5\b", "Sony", "PlayStation 5"),
    (r"\bPS4\b", "Sony", "PlayStation 4"),
]

# El proveedor encabeza toda la sección con "XIAOMI", pero varias de esas
# referencias son de la línea Redmi y el fabricante las publica así: el nombre
# comercial, la ficha y la garantía son de Redmi. Publicarlas como Xiaomi manda
# al cliente a buscar una ficha que no existe.
#
# La tabla es explícita a propósito. Generalizar sería peor: en el mismo
# catálogo, las Smart Band, el Watch S4, los Buds 6 a secas y las power bank sí
# son Xiaomi. Cada línea se verificó contra mi.com/co el 15/09/2026; si mañana
# cambia, se corrige aquí.
SUBMARCA_XIAOMI = [
    (r"^Watch 5 (Active|Lite)\b", r"Redmi Watch 5 \1", True),
    (r"^Buds 6 (Play|Active)\b", r"Redmi Buds 6 \1", True),
    (r"^Buds 8\b", "Redmi Buds 8", True),
    (r"^Pad 2\b", "Redmi Pad 2", True),
    # Xiaomi publica las bandas como "Smart Band"; la lista a veces omite
    # "Smart". Esta sí se queda en la línea Xiaomi.
    (r"^Band (\d+)", r"Smart Band \1", False),
]

CASING = {
    "Jbl": "JBL", "Hp": "HP", "Lg": "LG", "Tp-Link": "TP-Link", "Ssd": "SSD",
    "Hdd": "HDD", "Ram": "RAM", "Ddr4": "DDR4", "Ddr5": "DDR5", "Gb": "GB",
    "Tb": "TB", "Mm": "mm", "Mah": "mAh", "Ps5": "PS5", "Ps4": "PS4",
    "Usb-C": "USB-C", "4K": "4K", "Ips": "IPS", "Sim": "SIM", "Tv": "TV",
    "Ii": "II", "Iii": "III", "1T": "1TB", "1Tb": "1TB", "512Gb": "512GB",
    "Iphone": "iPhone", "Ipad": "iPad", "Playstation": "PlayStation",
    "Ai": "AI", "I3": "i3", "I5": "i5", "I7": "i7", "I9": "i9",
    "Poco": "POCO", "Zte": "ZTE", "Tcl": "TCL", "Bmax": "BMAX", "Wifi": "WiFi",
    "Xpad": "XPAD", "Fe": "FE", "4G": "4G", "5G": "5G", "Nfc": "NFC",
    "Esim": "eSIM", "Gt": "GT", "Gt50": "GT 50", "Lapiz": "Lápiz",
    "Tactil": "Táctil", "Sim/Esim": "SIM + eSIM",
}

RE_PRECIO = re.compile(r"\$\s*([\d][\d.,]*)")
RE_PORCENTAJE = re.compile(r"\d{2,3}\s*%")
RE_FECHA = re.compile(r"(\d{1,2})\s*[/ ]\s*([A-ZÁÉÍÓÚ]{3,}|\d{1,2})\s*/\s*(\d{4})", re.I)
RE_TELEFONO = re.compile(r"^\D*\d{7,12}\D*$")
# Prefijo que agrega WhatsApp cuando se exporta el chat en vez de copiar el mensaje.
RE_EXPORTADO = re.compile(r"^\[[^\]]{5,30}\]\s*(\+?[\d ]{7,20}|[^:]{1,30}):\s*")
RE_ANOTACION = re.compile(
    r"^(1\s*SIM|DUAL\s*SIM|SIM\s*/?\s*ESIM|ESIM|NFC|INCLUYE\b.*|CON\s+\w+)$", re.I
)

RE_MEM_3 = re.compile(r"\(?\s*(\d{1,2})\s*\+\s*(\d{1,2})\s*[+/]\s*(\d{2,4})\s*(?:GB)?\s*\)?", re.I)
RE_MEM_2 = re.compile(r"\(\s*(\d{1,2})\s*\+\s*(\d{2,4})\s*(?:GB)?\s*\)", re.I)
RE_MEM_RAM = re.compile(r"(\d{1,2})\s*(?:GB)?\s*RAM\s*[-/+]\s*(\d{2,4})\s*(?:GB)?", re.I)
RE_MEM_BARRA = re.compile(r"\b(\d{1,2})\s*[/+]\s*(\d{2,4})\b")
RE_MEM_COMPU = re.compile(r"\(?\s*(\d{1,2})\s*(?:GB)?\s*(?:RAM)?\s*(?:DDR\d)?\s*\+\s*(\d{2,4})\s*(?:GB)?\s*(?:SSD)?\s*\)?", re.I)

RE_ALMACEN = re.compile(r"(?<!\d)(\d{2,4})\s*(GB|TB)?(?!\d)")
RE_PULGADAS = re.compile(r"(\d{1,2}(?:[.,]\d)?)\s*\"")
RE_MM = re.compile(r"(\d{2})\s*MM")
RE_MAH = re.compile(r"([\d.]+)\s*MAH", re.I)
RE_W = re.compile(r"(\d{2,3})\s*W\b", re.I)
RE_RED = re.compile(r"\b(5G|4G|LTE)\b")
RE_WIFI = re.compile(r"\bWI[-\s]?FI\b", re.I)

MESES = {
    "ENERO": 1, "FEBRERO": 2, "MARZO": 3, "ABRIL": 4, "MAYO": 5, "JUNIO": 6,
    "JULIO": 7, "AGOSTO": 8, "SEPTIEMBRE": 9, "OCTUBRE": 10,
    "NOVIEMBRE": 11, "DICIEMBRE": 12,
}

TITULOS_LISTA = ("LISTA DE", "LISTADO DE", "LISTA ", "LISTADO ")

# Ancho máximo, en palabras, de un encabezado que llega sin marcadores de negrita.
# Un encabezado es corto; la frase de cortesía con que el proveedor cierra la
# lista —"TE BRINDAMOS UNA AMPLIA VARIEDAD DE TECNOLOGÍA…"— contiene VARIEDAD y
# sin este tope se leería como el encabezado de esa sección.
PALABRAS_MAX_ENCABEZADO_SIN_NEGRITA = 6


# --------------------------------------------------------------------------
# Utilidades
# --------------------------------------------------------------------------

def limpiar(texto: str) -> str:
    return texto.replace(VS16, "").strip()


def sin_emojis(texto: str) -> str:
    return "".join(
        ch for ch in texto
        if unicodedata.category(ch) not in ("So", "Sk", "Cf") and ch not in "🏻🏼🏽🏾🏿"
    )


def normalizar(texto: str) -> str:
    t = " " + texto.upper() + " "
    for malo, bueno in TYPOS.items():
        t = t.replace(malo, bueno)
    return re.sub(r"\s+", " ", t).strip()


def slug(texto: str) -> str:
    t = unicodedata.normalize("NFKD", texto).encode("ascii", "ignore").decode()
    t = re.sub(r"[^a-zA-Z0-9]+", "-", t).strip("-").lower()
    return re.sub(r"-{2,}", "-", t)


def separar_pegados(texto: str) -> str:
    return re.sub(r"(\d)([A-Z]{2,})", r"\1 \2", texto)


def titulo_bonito(texto: str) -> str:
    palabras = [CASING.get(p.title(), p.title()) for p in texto.split()]
    salida = re.sub(r"\s+", " ", " ".join(palabras)).strip(" -/+(),.")
    return re.sub(r"(\d)I\b", r"\1i", salida)


def parsear_precio(texto: str):
    """Devuelve (valor_en_COP, ambiguo)."""
    m = RE_PRECIO.search(texto)
    if not m:
        return None, False
    crudo = re.sub(r"[^\d]", "", m.group(1))
    if not crudo:
        return None, False
    n = int(crudo)
    if len(crudo) >= DIGITOS_PRECIO_COMPLETO:
        return n, False
    return n * MULTIPLICADOR_PRECIO, len(crudo) == 5


def extraer_colores(texto: str):
    emojis, familias = [], []
    for ch in limpiar(texto):
        fam = COLORES.get(ch)
        if fam:
            emojis.append(ch)
            if fam not in familias:
                familias.append(fam)
    return emojis, familias


def extraer_memoria(texto: str, estilo="movil"):
    """Devuelve (ram, ram_virtual, almacenamiento, texto_sin_memoria)."""
    patrones = [RE_MEM_RAM, RE_MEM_3, RE_MEM_2, RE_MEM_BARRA]
    if estilo == "computador":
        patrones = [RE_MEM_COMPU, RE_MEM_RAM, RE_MEM_BARRA]
    for patron in patrones:
        for m in patron.finditer(texto):
            grupos = [int(g) for g in m.groups() if g]
            if len(grupos) == 3:
                ram, virtual, alm = grupos
            else:
                (ram, alm), virtual = grupos, None
            if alm not in (16, 32, 64, 128, 256, 512, 1024):
                continue
            return (f"{ram}GB", f"{virtual}GB" if virtual else None,
                    f"{alm}GB", texto[:m.start()] + " " + texto[m.end():])
    for m in RE_ALMACEN.finditer(texto):
        if int(m.group(1)) in (32, 64, 128, 256, 512, 1024):
            return (None, None, f"{int(m.group(1))}GB",
                    texto[:m.start()] + " " + texto[m.end():])
    if re.search(r"\b1\s*TB\b", texto, re.I):
        return None, None, "1TB", re.sub(r"\b1\s*TB\b", " ", texto, flags=re.I)
    return None, None, None, texto


def categoria_por_palabras(texto: str):
    t = texto.upper()
    if re.search(r"\bWATCH\b|\bBAND\b|\d{2}\s*MM\b", t):
        return "relojes"
    if re.search(r"\bTABLET\b|\bPAD\b|\bTAB\b|\bXPAD\b|\bMEGAPAD\b", t):
        return "tablets"
    if RE_MAH.search(t):
        return "power_bank"
    if re.search(r"\bCABLE\b", t):
        return "cables"
    if re.search(r"\bBUDS\b|\bDIADEMA\b|\bAUDÍFONO", t):
        return "audifonos"
    if re.search(r"\bLAPTOP\b|\bTODO EN UNO\b", t):
        return "computadores"
    return None


# --------------------------------------------------------------------------
# Nombres de modelo
# --------------------------------------------------------------------------

RE_IPHONE = re.compile(r"^(?P<gen>\d{1,2})\s*(?P<e>E\b)?\s*(?P<var>PRO MAX|PRO|PLUS|MAX|MINI|PM)?", re.I)
RE_GALAXY = re.compile(r"^(?P<serie>[SZAM])\s*(?P<gen>\d{1,3})\s*(?P<var>ULTRA|PLUS|FE|\+)?", re.I)


def modelo_apple(texto: str):
    m = RE_IPHONE.match(texto.strip())
    if not m:
        return None
    var = {"PM": "Pro Max", "MAX": "Pro Max", "PRO MAX": "Pro Max", "PRO": "Pro",
           "PLUS": "Plus", "MINI": "mini"}.get((m.group("var") or "").upper(), "")
    return f"iPhone {m.group('gen')}{'e' if m.group('e') else ''}" + (f" {var}" if var else "")


def modelo_galaxy(texto: str):
    m = RE_GALAXY.match(texto.strip())
    if not m:
        return None
    var = {"ULTRA": "Ultra", "PLUS": "Plus", "+": "Plus", "FE": "FE"}.get(
        (m.group("var") or "").upper(), "")
    return f"Galaxy {m.group('serie').upper()}{m.group('gen')}" + (f" {var}" if var else "")


# --------------------------------------------------------------------------
# Construcción del producto
# --------------------------------------------------------------------------

def construir_producto(texto, categoria, marca, condicion, seccion, linea):
    original = texto
    texto_plano = normalizar(sin_emojis(texto).replace("*", " ").replace("_", " ").replace("°", " "))
    texto_plano = separar_pegados(texto_plano)
    emojis_color, colores = extraer_colores(texto)
    precio, precio_ambiguo = parsear_precio(texto)

    prod = {
        "id": None, "categoria": categoria, "marca": marca, "modelo": None,
        "almacenamiento": None, "ram": None, "ram_virtual": None,
        "red": None, "atributos": [], "condicion": condicion,
        "colores_emoji": emojis_color, "colores_familia": colores,
        "colores_oficiales": [], "precio_proveedor_cop": precio,
        "precio_mercado_cop": None, "fuentes_precio": [], "titulo": None,
        "descripcion": None, "imagenes": [], "texto_origen": original.strip(),
        "seccion": seccion, "linea": linea, "revisar": [], "supuestos": [],
        "autenticidad": None, "compatible_con": None, "referencia": None, "sin_datos": [],
    }
    if precio_ambiguo:
        prod["revisar"].append("precio de 5 dígitos: confirmar si es acotado o completo")

    # Marca escrita en la línea (manda sobre la de la sección)
    for mk in MARCAS:
        if re.search(rf"\b{re.escape(mk)}\b", texto_plano):
            if re.search(rf"\(\s*{re.escape(mk)}\s*\)", texto_plano) and categoria in ("cargadores", "cables"):
                # "CUBO BECLAD (SAMSUNG)": la marca es Beclad y el paréntesis
                # dice con qué es compatible. Va en la descripción, no en el título.
                prod["atributos"].append(f"para {mk.title()}")
                prod["compatible_con"] = CASING.get(mk.title(), mk.title())
                texto_plano = re.sub(rf"\(\s*{re.escape(mk)}\s*\)", " ", texto_plano)
            else:
                prod["marca"] = CASING.get(mk.title(), mk.title())
                texto_plano = re.sub(rf"\b{re.escape(mk)}\b", " ", texto_plano)
            break
    if not prod["marca"]:
        for sub, madre in SUBMARCAS.items():
            if re.search(rf"\b{sub}\b", texto_plano):
                prod["marca"] = madre
                break

    if "ESIM" in texto_plano:
        prod["atributos"].append("eSIM")
        texto_plano = texto_plano.replace("ESIM", " ")
    if "ACTIVO" in texto_plano:
        prod["condicion"] = "nuevo_activado"
        texto_plano = texto_plano.replace("ACTIVO", " ")
    texto_plano = re.sub(r"\s+", " ", texto_plano).strip()

    resto = RE_PRECIO.sub(" ", texto_plano)

    # Red móvil o WiFi
    m = RE_RED.search(resto)
    if m:
        prod["red"] = m.group(1).upper().replace("LTE", "4G")
        resto = resto[:m.start()] + " " + resto[m.end():]
    elif RE_WIFI.search(resto):
        prod["red"] = "WiFi"
        resto = RE_WIFI.sub(" ", resto)

    if categoria in ("celulares", "tablets"):
        prod["ram"], prod["ram_virtual"], prod["almacenamiento"], resto = extraer_memoria(resto)
        if categoria == "tablets":
            p = RE_PULGADAS.search(resto)
            if p:
                prod["atributos"].insert(0, f'{p.group(1).replace(",", ".")}"')
                resto = resto.replace(p.group(0), " ")
        resto = re.sub(r"\b(GB|RAM)\b", " ", resto)
        if prod["almacenamiento"]:
            resto = re.sub(r"(?<!\d)(32|64|128|256|512|1024)(?!\d)", " ", resto)
        nombre = None
        if (prod["marca"] or "") == "Apple":
            nombre = modelo_apple(resto)
        elif (prod["marca"] or "") == "Samsung" and categoria == "celulares":
            nombre = modelo_galaxy(resto)
        prod["modelo"] = nombre or titulo_bonito(resto) or None
        if (prod["marca"] or "") == "Xiaomi" and categoria == "celulares":
            modelo = prod["modelo"] or ""
            if modelo.upper().startswith("NOTE"):
                prod["modelo"] = "Redmi " + modelo
                prod["supuestos"].append("la sección Xiaomi abrevia 'NOTE': se leyó como Redmi Note")
            elif re.match(r"^X\d", modelo):
                prod["modelo"] = "POCO " + modelo
                prod["supuestos"].append("la sección Xiaomi abrevia la serie X: se leyó como POCO")
        if prod["ram_virtual"]:
            prod["revisar"].append(
                f"la lista suma RAM virtual ({prod['ram']}+{prod['ram_virtual']}): "
                "publicar la RAM física y mencionar la extendida aparte"
            )
        if categoria == "tablets":
            prod["revisar"].append("tablets: confirmar la línea comercial completa (ej. Galaxy Tab A11)")
        if not prod["almacenamiento"]:
            prod["revisar"].append("no se reconoció la capacidad")

    elif categoria == "computadores":
        es_aio = "TODO EN UNO" in resto or "AIO" in resto
        cpu = re.search(r"\b(RYZEN\s*\d\s*\w+|ATHLON\s*\w+|CORE\s*I\d[\w-]*|INTEL\s*I\d\s*\w+|I\d[\s-]?\d{4,5}\w*|CELERON\s*\w+)\b", resto, re.I)
        if cpu:
            prod["atributos"].append(titulo_bonito(cpu.group(1)))
            resto = resto.replace(cpu.group(1), " ")
        prod["ram"], _, alm, resto = extraer_memoria(resto, estilo="computador")
        prod["almacenamiento"] = f"{alm} SSD" if alm else None
        p = RE_PULGADAS.search(resto)
        if p:
            prod["atributos"].insert(0, f'{p.group(1).replace(",", ".")}"')
            resto = resto.replace(p.group(0), " ")
        for basura in ("LAPTOP", "PORTATIL", "PORTÁTIL", "COMPUTADOR", "TODO EN UNO",
                       "SSD", "RAM", "DDR4", "DDR5", "GB", "AIO", "BOLSO"):
            resto = re.sub(rf"\b{basura}\b", " ", resto)
        sobra = titulo_bonito(re.sub(r"[()/+]", " ", resto))
        prod["modelo"] = ("Todo en Uno" if es_aio else "Portátil") + (f" {sobra}" if sobra else "")
        if "BOLSO" in texto_plano:
            prod["atributos"].append("incluye bolso")
        # Lo que sobra tras quitar CPU, memoria, disco y pulgadas es la referencia
        # (línea o código de modelo). Si no sobra nada, la lista no la trae.
        prod["referencia"] = sobra or None
        faltan = [d for d, hay in (("marca", prod["marca"]), ("referencia", sobra)) if not hay]
        if faltan:
            prod["sin_datos"] = faltan
        else:
            prod["revisar"].append("confirmar la referencia del equipo contra la ficha del fabricante")

    elif categoria == "cables":
        extremos = []
        for tramo in re.split(r"\s*[-–/]\s*|\s+A\s+", resto):
            tramo = tramo.strip()
            if not tramo:
                continue
            for patron, nombre in CONECTORES:
                if re.search(patron, tramo, re.I):
                    tramo = nombre
                    break
            extremos.append(tramo if tramo in [n for _, n in CONECTORES] else titulo_bonito(tramo))
        prod["modelo"] = "Cable " + " a ".join(extremos) if extremos else "Cable"
        prod["revisar"].append(
            "cable: falta longitud, potencia soportada y si es original o compatible; "
            "son los tres datos por los que se devuelve un cable"
        )

    else:
        for patron, mk, nombre in ALIAS:
            if re.search(patron, resto, re.I):
                prod["marca"] = prod["marca"] or mk
                resto = re.sub(patron, nombre, resto, flags=re.I)
        if categoria == "relojes":
            m = RE_MM.search(resto)
            if m:
                prod["atributos"].append(f"{m.group(1)}mm")
                resto = resto.replace(m.group(0), " ")
        if categoria == "power_bank":
            m = RE_MAH.search(resto)
            if m:
                prod["atributos"].append(f"{m.group(1)} mAh")
                resto = resto[:m.start()] + " " + resto[m.end():]
            w = RE_W.search(resto)
            if w:
                prod["atributos"].append(f"{w.group(1)}W")
                resto = resto[:w.start()] + " " + resto[w.end():]
            resto = "Power Bank " + resto
        if categoria == "cargadores":
            w = RE_W.search(resto)
            if w:
                prod["atributos"].append(f"{w.group(1)}W")
                resto = resto[:w.start()] + " " + resto[w.end():]
            if "ORIGINAL" in normalizar(seccion).upper():
                # La sección "CARGADORES ORIGINAL" es la palabra del proveedor:
                # se toma como original de la marca sin volver a preguntar.
                prod["autenticidad"] = "original"
                if prod["compatible_con"]:
                    prod["supuestos"].append(
                        f"la sección dice ORIGINAL y el paréntesis dice ({prod['compatible_con']}): "
                        f"es un {prod['marca'] or 'cargador'} original, compatible con {prod['compatible_con']}"
                    )
            else:
                prod["revisar"].append(
                    "cargadores: la sección no dice ORIGINAL; confirmar si es original de la marca "
                    "o compatible, publicarlo mal es riesgo de reclamo por publicidad engañosa"
                )
        if categoria in ("consolas", "accesorios_consola"):
            resto = re.sub(r"\b1\s*T\b", "1TB", resto)
        prod["modelo"] = titulo_bonito(re.sub(r"\(\s*\)", " ", resto)) or None
        if (prod["marca"] or "") == "Samsung" and (prod["modelo"] or "").startswith("Watch"):
            prod["modelo"] = "Galaxy " + prod["modelo"]
        prod["revisar"].append("confirmar nombre comercial oficial del modelo")

    aplicar_submarca(prod)
    armar_titulo(prod)
    if prod["condicion"] == "nuevo_activado":
        prod["revisar"].append("equipo con la garantía ya activada")
    return prod


def aplicar_submarca(prod):
    """Corrige el nombre comercial cuando la sección y el fabricante no coinciden.

    Solo toca lo que está en SUBMARCA_XIAOMI y deja el rastro en `supuestos`,
    que es donde va lo asumido: no bloquea la publicación, pero explica por qué
    el título dice Redmi si la lista decía Xiaomi.
    """
    if (prod.get("marca") or "") != "Xiaomi":
        return
    modelo = prod.get("modelo") or ""
    for patron, nuevo, es_redmi in SUBMARCA_XIAOMI:
        if re.match(patron, modelo):
            prod["modelo"] = re.sub(patron, nuevo, modelo)
            prod["supuestos"].append(
                "el proveedor lo escribe bajo Xiaomi, pero el fabricante publica esta "
                "referencia en la línea Redmi: el título usa el nombre comercial real"
                if es_redmi else
                "Xiaomi publica esta banda como «Smart Band»; la lista omitía «Smart»"
            )
            return


def armar_titulo(prod):
    pantalla = [a for a in prod["atributos"] if a.endswith('"')]
    otros = [a for a in prod["atributos"] if not a.endswith('"')]
    if prod["categoria"] == "computadores":
        partes = [prod["modelo"], prod["marca"]] + pantalla + otros[:1]
        if prod["ram"]:
            partes.append(f"{prod['ram']} RAM")
        partes += [prod["almacenamiento"]] + otros[1:]
    else:
        partes = [prod["marca"], prod["modelo"]] + pantalla + [prod["red"]]
        if prod["ram"]:
            partes.append(f"{prod['ram']} RAM")
        partes += [prod["almacenamiento"]] + otros
    prod["titulo"] = re.sub(r"\s+", " ", " ".join(p for p in partes if p)).strip()
    prod["id"] = slug(prod["titulo"] or prod["texto_origen"])
    if len(prod["titulo"]) > 70:
        prod["revisar"].append("título supera 70 caracteres")


def clave_dedupe(prod):
    modelo = (prod["modelo"] or "").upper()
    for sub in SUBMARCAS:
        modelo = modelo.replace(sub, " ")
    modelo = re.sub(r"[^A-Z0-9]", "", modelo)
    extra = re.sub(r"[^A-Z0-9]", "", "".join(a for a in prod["atributos"] if not es_atributo_sim(a)).upper())
    return f"{modelo}|{prod['almacenamiento'] or ''}|{extra}"


def es_atributo_sim(atributo):
    return "SIM" in atributo.upper()


def atributos_sim(prod):
    return {a.upper() for a in prod["atributos"] if es_atributo_sim(a)}


# --------------------------------------------------------------------------
# Recorrido del mensaje
# --------------------------------------------------------------------------

def es_encabezado(texto: str) -> bool:
    """
    WhatsApp marca los encabezados en *negrita*, pero el mensaje llega sin los
    asteriscos cuando se copia desde una vista que ya los renderizó. Una lista
    real llegó así (12/09/2026) y no se reconoció ni una sola sección: los
    productos quedaron sin la marca y sin la categoría que pone el encabezado,
    29 sin marca y 7 de marca excluida colándose al análisis.

    Por eso la línea sin marcadores también se acepta, con dos condiciones que un
    encabezado siempre cumple y una línea de producto casi nunca: no trae precio
    y es corta. Las reglas de ENCABEZADOS y MARCAS son de subcadena y de prefijo,
    lo bastante laxas como para tragarse un producto si no se acota el largo.
    """
    t = limpiar(sin_emojis(texto)).strip()
    if not t or "$" in texto:
        return False
    con_negrita = ((t.startswith("*") and t.rstrip().endswith("*")) or
                   (t.startswith("_") and t.rstrip().endswith("_")))
    contenido = normalizar(t.replace("*", " ").replace("_", " "))
    if any(x in contenido for x in TITULOS_LISTA) or contenido.startswith("LLEGANDO"):
        return True
    if not con_negrita and len(contenido.split()) > PALABRAS_MAX_ENCABEZADO_SIN_NEGRITA:
        return False
    if any(clave in contenido for clave, _ in ENCABEZADOS):
        return True
    if con_negrita:
        return any(contenido == mk or contenido.startswith(mk + " ") for mk in MARCAS)
    # Sin negrita, el encabezado de solo marca tiene que ser exactamente la marca:
    # "SAMSUNG" es la sección, "SAMSUNG BAND FIT 3" es un producto de esa sección.
    return contenido in MARCAS


def es_ruido(linea: str) -> bool:
    t = normalizar(sin_emojis(linea).replace("*", " ").replace("_", " "))
    if not t:
        return True
    if RE_TELEFONO.match(t):
        return True
    if RE_FECHA.search(t) and len(t.split()) <= 4:
        return True
    if "$" not in linea and not re.search(r"\d", t) and len(t.split()) >= 4:
        return True
    return False


def parsear(texto: str):
    lineas = texto.splitlines()
    productos, descartados, sin_clasificar = [], [], []
    categoria = marca = condicion = None
    seccion = "(sin sección)"
    fecha = None
    pendiente = None
    ultimo = None

    def clasificar(prod):
        nonlocal ultimo
        if prod["categoria"] not in CATEGORIAS_INCLUIDAS:
            descartados.append({**prod, "motivo": f"categoría no publicable: {prod['categoria']}"})
        elif prod["condicion"] not in CONDICIONES_PUBLICABLES:
            descartados.append({**prod, "motivo": f"condición no publicable: {prod['condicion']}"})
        else:
            productos.append(prod)
            ultimo = prod

    def cerrar_pendiente():
        nonlocal pendiente
        if pendiente:
            clasificar(pendiente)
            pendiente = None

    for i, cruda in enumerate(lineas, start=1):
        linea = RE_EXPORTADO.sub("", cruda.strip()).strip()
        if not linea or set(linea) <= {"-", "_", "'", "=", "*"}:
            continue

        if fecha is None:
            m = RE_FECHA.search(sin_emojis(linea).replace("*", ""))
            if m:
                mes = MESES.get(m.group(2).upper(), m.group(2))
                if str(mes).isdigit():
                    fecha = f"{m.group(3)}-{int(mes):02d}-{int(m.group(1)):02d}"

        emoji, cat_vineta, cond_vineta, resto = None, None, None, linea
        t = limpiar(linea).lstrip()
        for e in sorted(list(VINETAS_CATEGORIA) + list(VINETAS_CONDICION), key=len, reverse=True):
            if t.startswith(e):
                emoji, resto = e, t[len(e):].strip()
                cat_vineta = VINETAS_CATEGORIA.get(e)
                cond_vineta = VINETAS_CONDICION.get(e)
                break

        if es_encabezado(linea):
            cerrar_pendiente()
            enc = normalizar(sin_emojis(linea).replace("*", " ").replace("_", " "))
            if any(x in enc for x in TITULOS_LISTA) and not enc.startswith("LLEGANDO"):
                continue
            if enc.startswith("LLEGANDO"):
                anuncio = enc.replace("LLEGANDO", "", 1).strip()
                if any(re.search(rf"\b{mk}\b", anuncio) for mk in MARCAS) or \
                   any(re.search(rf"\b{sub}\b", anuncio) for sub in SUBMARCAS):
                    pendiente = construir_producto(
                        anuncio, categoria_por_palabras(anuncio) or "celulares",
                        None, "nuevo", "Anuncio de llegada", i)
                    continue
                categoria, marca, condicion = None, None, "nuevo"
                seccion = enc.title()
                continue
            seccion = enc.title()
            aplicado = False
            for clave, (c, mk, cond) in ENCABEZADOS:
                if clave in enc:
                    categoria, marca, condicion = c, mk, cond
                    aplicado = True
                    break
            for mk in MARCAS:
                if re.search(rf"\b{mk}\b", enc):
                    marca = CASING.get(mk.title(), mk.title())
                    if not aplicado:      # encabezado de solo marca: la categoría la ponen las líneas
                        categoria, condicion, aplicado = None, "nuevo", True
                    break
            if not aplicado:
                sin_clasificar.append({"linea": i, "texto": linea, "motivo": "encabezado no reconocido"})
            continue

        if RE_PORCENTAJE.search(linea) and not RE_PRECIO.search(linea):
            continue

        limpio = normalizar(sin_emojis(resto).replace("*", " ").replace("_", " "))
        if RE_ANOTACION.match(limpio) and ultimo is not None and not emoji:
            nota = titulo_bonito(limpio)
            if nota not in ultimo["atributos"]:
                ultimo["atributos"].append(nota)
                armar_titulo(ultimo)
            continue

        if es_ruido(resto):
            continue

        if emoji:
            cerrar_pendiente()
            cat = cat_vineta or categoria_por_palabras(resto) or categoria or "celulares"
            cond = cond_vineta or condicion or "nuevo"
            prod = construir_producto(resto, cat, marca, cond, seccion, i)
            if prod["precio_proveedor_cop"] is None:
                pendiente = prod
            else:
                clasificar(prod)
            continue

        # Línea sin viñeta
        if pendiente is not None and len(limpio.split()) <= 5:
            fusion = pendiente["texto_origen"] + " " + linea
            prod = construir_producto(fusion, pendiente["categoria"], pendiente["marca"],
                                      pendiente["condicion"], pendiente["seccion"], pendiente["linea"])
            pendiente = None
            if prod["precio_proveedor_cop"] is None:
                pendiente = prod
            else:
                clasificar(prod)
            continue

        cerrar_pendiente()
        tiene_marca = any(re.search(rf"\b{mk}\b", limpio) for mk in MARCAS) or \
                      any(re.search(rf"\b{sub}\b", limpio) for sub in SUBMARCAS)
        if categoria or tiene_marca or RE_PRECIO.search(linea):
            cat = categoria_por_palabras(resto) or categoria or ("celulares" if tiene_marca else None)
            if cat:
                prod = construir_producto(linea, cat, marca, condicion or "nuevo", seccion, i)
                prod["revisar"].append("línea sin viñeta: verificar que sea un producto")
                clasificar(prod)
                continue
        sin_clasificar.append({"linea": i, "texto": linea, "motivo": "no se pudo clasificar"})

    cerrar_pendiente()
    productos, duplicados = fusionar_duplicados(productos)
    productos = filtrar_por_precio(productos, descartados)
    return {
        "fecha_lista": fecha,
        "categorias_incluidas": sorted(CATEGORIAS_INCLUIDAS),
        "productos": productos,
        "descartados": descartados,
        "duplicados_fusionados": duplicados,
        "sin_clasificar": sin_clasificar,
    }


def compatibles(a, b):
    """Dos entradas son el mismo producto si ningún dato conocido se contradice."""
    for campo in ("red", "ram", "marca"):
        if a[campo] and b[campo] and a[campo] != b[campo]:
            return False
    # "1 SIM" y "DUAL SIM" son dos referencias; pero el aviso de llegada que no
    # menciona la SIM no contradice a la lista que sí la trae.
    if atributos_sim(a) and atributos_sim(b) and atributos_sim(a) != atributos_sim(b):
        return False
    return True


def fusionar_duplicados(productos):
    por_clave, salida, fusionados = {}, [], []
    for p in productos:
        k = clave_dedupe(p)
        grupo = por_clave.setdefault(k, [])
        base = next((b for b in grupo if compatibles(b, p)), None)
        if base is None:
            grupo.append(p)
            salida.append(p)
            continue
        for campo in ("red", "ram", "ram_virtual", "marca"):
            base[campo] = base[campo] or p[campo]
        for a in p["atributos"]:
            if a not in base["atributos"]:
                base["atributos"].append(a)
        for campo in ("supuestos", "revisar", "colores_familia", "colores_emoji"):
            base[campo] = list(dict.fromkeys(base[campo] + p[campo]))
        precios = [x for x in (base["precio_proveedor_cop"], p["precio_proveedor_cop"]) if x]
        if precios:
            base["precio_proveedor_cop"] = min(precios)
        if len(set(precios)) > 1:
            base["supuestos"].append(
                "aparece repetido con precios distintos ("
                + " y ".join(f"{x:,}".replace(",", ".") for x in sorted(set(precios)))
                + "): se tomó el menor, como está decidido")
        armar_titulo(base)
        fusionados.append({"titulo": p["titulo"], "linea": p["linea"], "fusionado_en": base["titulo"]})

    solo_modelo = {}
    for p in salida:
        if p["categoria"] not in ("celulares", "tablets"):
            continue
        base = clave_dedupe(p).split("|")[0]
        solo_modelo.setdefault(base, []).append(p)
    for base, grupo in solo_modelo.items():
        con_cap = [g for g in grupo if g["almacenamiento"]]
        sin_cap = [g for g in grupo if not g["almacenamiento"]]
        if not sin_cap or len(grupo) < 2:
            continue
        if len(con_cap) == 1 and all(
            compatibles(con_cap[0], g) and g["precio_proveedor_cop"] in (None, con_cap[0]["precio_proveedor_cop"])
            for g in sin_cap
        ):
            # Solo hay una variante con capacidad y el anuncio sin capacidad no la
            # contradice ni en precio: no hay duda, es el mismo equipo.
            destino = con_cap[0]
            for g in sin_cap:
                for campo in ("supuestos", "revisar", "colores_familia", "colores_emoji"):
                    destino[campo] = list(dict.fromkeys(destino[campo] + g[campo]))
                destino["supuestos"].append(
                    f"el aviso «{g['texto_origen'][:50]}» no trae capacidad; se unió a la única variante de la lista")
                fusionados.append({"titulo": g["titulo"], "linea": g["linea"], "fusionado_en": destino["titulo"]})
                salida.remove(g)
        else:
            for g in grupo:
                g["revisar"].append("posible duplicado: el mismo modelo aparece con y sin capacidad")
    return salida, fusionados


def filtrar_por_precio(productos, descartados):
    """Se aplica después de fusionar: el aviso de llegada puede traer el precio que la lista no trae."""
    salida = []
    minimo = f"{PRECIO_MINIMO_CELULAR_COP:,}".replace(",", ".")
    for p in productos:
        precio = p["precio_proveedor_cop"]
        if DESCARTAR_SIN_PRECIO and precio is None:
            descartados.append({**p, "motivo": "sin precio de proveedor"})
        elif p["categoria"] == "computadores" and DESCARTAR_COMPUTADOR_SIN_REFERENCIA and p["sin_datos"]:
            descartados.append({**p, "motivo": "computador sin " + " ni ".join(p["sin_datos"]) + " en la lista"})
        elif p["categoria"] == "celulares" and precio is not None and precio < PRECIO_MINIMO_CELULAR_COP:
            descartados.append({**p, "motivo": f"celular por debajo del mínimo de {minimo} COP"})
        elif (p.get("marca") or "").strip().lower() in MARCAS_EXCLUIDAS:
            descartados.append({**p, "motivo":
                                f"marca excluida ({p['marca']}): sin precio de "
                                f"mercado admisible en Colombia"})
        else:
            salida.append(p)
    return salida


def reporte(datos: dict) -> str:
    L = ["# Revisión de la lista", ""]
    L.append(f"- Fecha de la lista: {datos['fecha_lista'] or 'no detectada'}")
    L.append(f"- Productos para publicar: {len(datos['productos'])}")
    L.append(f"- Descartados: {len(datos['descartados'])}")
    L.append(f"- Duplicados fusionados: {len(datos['duplicados_fusionados'])}")
    L.append(f"- Productos con algún supuesto aplicado: "
             f"{sum(1 for p in datos['productos'] if p.get('supuestos'))}")
    L.append(f"- Líneas sin clasificar: {len(datos['sin_clasificar'])}")
    L.append("")
    L.append("## Productos para publicar")
    for p in datos["productos"]:
        precio = f"{p['precio_proveedor_cop']:,}".replace(",", ".") if p["precio_proveedor_cop"] else "sin precio"
        L.append(f"- **{p['titulo']}** — {p['categoria']} — {precio} COP"
                 + (f" — colores: {', '.join(p['colores_familia'])}" if p["colores_familia"] else ""))
        for a in p.get("supuestos", []):
            L.append(f"  - · asumido: {a}")
        for r in p["revisar"]:
            L.append(f"  - ⚠️ {r}")
    L.append("")
    L.append("## Descartados")
    for p in datos["descartados"]:
        L.append(f"- {p['texto_origen'][:60]} — {p['motivo']}")
    if datos["duplicados_fusionados"]:
        L.append("")
        L.append("## Duplicados fusionados")
        for d in datos["duplicados_fusionados"]:
            L.append(f"- L{d['linea']}: {d['titulo']} → {d['fusionado_en']}")
    if datos["sin_clasificar"]:
        L.append("")
        L.append("## Sin clasificar (revisar a mano)")
        for s in datos["sin_clasificar"]:
            L.append(f"- L{s['linea']}: {s['texto'][:70]} — {s['motivo']}")
    return "\n".join(L)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("entrada")
    ap.add_argument("--salida", default="productos.json")
    ap.add_argument("--reporte", default="revision.md")
    args = ap.parse_args()

    datos = parsear(Path(args.entrada).read_text(encoding="utf-8"))
    Path(args.salida).write_text(json.dumps(datos, ensure_ascii=False, indent=2), encoding="utf-8")
    Path(args.reporte).write_text(reporte(datos), encoding="utf-8")
    print(f"{len(datos['productos'])} productos | {len(datos['descartados'])} descartados "
          f"| {len(datos['duplicados_fusionados'])} duplicados | {len(datos['sin_clasificar'])} sin clasificar")


if __name__ == "__main__":
    main()
