#!/usr/bin/env python3
"""
Cliente de Open Icecat. SE EJECUTA EN EL COMPUTADOR DEL USUARIO, no en la skill:
el entorno de la skill no alcanza data.icecat.biz.

No necesita instalar nada, solo Python 3.

Credenciales (nunca en el código, ni en el repositorio, ni en el chat).
Se buscan en este orden:
    1. variables ya definidas en la sesión
    2. un archivo .env en la carpeta actual o hasta cuatro niveles arriba
    3. ~/.icecat.env
El archivo lleva dos líneas:
    ICECAT_USER=tu-usuario
    ICECAT_PASSWORD=tu-clave

Tres pasos, en orden:

  1) python icecat_local.py indice --productos productos.json
     Baja el índice de Open Icecat y deja un índice local solo con las marcas
     que vendemos. Es la descarga pesada; se hace una vez cada tanto.

  2) python icecat_local.py buscar --productos productos.json
     Empareja cada producto de la lista con el índice y escribe
     coincidencias.csv con el candidato y su puntaje. Se revisa a mano.

  3) python icecat_local.py traer --productos productos.json
     Con las coincidencias confirmadas, baja la ficha de cada producto y deja
     los enlaces de fotos en fotos/urls.csv y el contenido en icecat/<id>.json

Condiciones de uso de Open Icecat que hay que respetar al publicar:
  - citar "Specs Icecat" con enlace a Icecat.biz en la ficha del producto
  - incluir el descargo de responsabilidad (AS IS)
  - no usar los datos de Icecat para entrenar modelos de IA
"""

import argparse
import base64
import csv
import gzip
import io
import shutil
import zlib
import json
import os
import re
import sys
import time
import unicodedata
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from difflib import SequenceMatcher
from pathlib import Path

BASE = "https://data.icecat.biz"
INDICE_URL = f"{BASE}/export/freexml/EN/files.index.xml.gz"
PROVEEDORES_URL = f"{BASE}/export/freexml/refs/SuppliersList.xml.gz"
FICHA_URL = BASE + "/export/freexml/{lang}/{icecat_id}.xml"
XMLS3_URL = BASE + "/xml_s3/xml_server3.cgi"
PAUSA = 0.25          # segundos entre peticiones; el límite es 100 por IP
MIN_PUNTAJE = 0.62    # por debajo de esto no se propone candidato
AUTO_CONFIRMA = 0.90  # por encima de esto se marca confirmado solo
FOTOS_POR_PRODUCTO = 4


# --------------------------------------------------------------------------

# Las URL de icecat.biz llevan todos los identificadores:
# /p/<marca>/<codigo>/<slug>-<gtin>-<nombre>-<icecat_id>.html
RE_URL_ICECAT = re.compile(
    r"icecat\.[a-z.]+/[^\s]*?/p/([^/]+)/([^/]+)/(.*?)-(\d{6,})\.html", re.I)


def identificadores(valor):
    """Acepta un icecat_id suelto o una URL de icecat.biz. Devuelve un dict."""
    valor = (valor or "").strip()
    m = RE_URL_ICECAT.search(valor)
    if m:
        marca, codigo, resto, icecat_id = m.groups()
        gtin = re.search(r"(?<!\d)(\d{8,14})(?!\d)", resto)
        return {"icecat_id": icecat_id, "marca": marca.title(),
                "prod_id": urllib.parse.unquote(codigo).upper(),
                "gtin": gtin.group(1) if gtin else ""}
    if valor.isdigit():
        return {"icecat_id": valor, "marca": "", "prod_id": "", "gtin": ""}
    return {"icecat_id": "", "marca": "", "prod_id": "", "gtin": ""}


def cmd_id(args):
    for valor in args.valores:
        d = identificadores(valor)
        if d["icecat_id"]:
            print(f"icecat_id={d['icecat_id']}  marca={d['marca'] or '?'}  "
                  f"codigo={d['prod_id'] or '?'}  gtin={d['gtin'] or '?'}")
        else:
            print(f"no se reconoció un identificador en: {valor[:60]}")


def _candidatos_env(explicito=None):
    """Dónde se busca el .env, en orden de prioridad."""
    if explicito:
        return [Path(explicito)]
    rutas = []
    actual = Path.cwd().resolve()
    for carpeta in [actual, *list(actual.parents)[:4]]:
        rutas.append(carpeta / ".env")
    rutas.append(Path.home() / ".icecat.env")
    return rutas


def cargar_env(explicito=None):
    """
    Lee un .env sencillo sin dependencias. Las variables que ya existen en el
    entorno mandan sobre el archivo: así una sesión puede pisar el valor de
    forma puntual sin editar nada.
    Devuelve la ruta del archivo que se usó, o None.
    """
    for ruta in _candidatos_env(explicito):
        if not ruta.is_file():
            continue
        usado = False
        for linea in ruta.read_text(encoding="utf-8-sig", errors="replace").splitlines():
            linea = linea.strip()
            if not linea or linea.startswith("#") or "=" not in linea:
                continue
            clave, _, valor = linea.partition("=")
            clave = clave.strip().removeprefix("export ").strip()
            valor = valor.strip().strip('"').strip("'")
            if clave.startswith("ICECAT_"):
                usado = True
                os.environ.setdefault(clave, valor)
        if usado:
            return ruta
    return None


def credenciales(args):
    archivo = cargar_env(getattr(args, "env", None))
    usuario = getattr(args, "usuario", None) or os.environ.get("ICECAT_USER")
    clave = os.environ.get("ICECAT_PASSWORD")
    if usuario and clave:
        if archivo:
            print(f"(credenciales leídas de {archivo})")
        return usuario, clave

    revisados = "\n  ".join(str(r) for r in _candidatos_env(getattr(args, "env", None)))
    sys.exit(
        "Faltan las credenciales de Icecat. Tres maneras, en orden de preferencia:\n\n"
        "  1. Un archivo .env en la raíz del proyecto, con estas dos líneas:\n"
        "         ICECAT_USER=tu-usuario\n"
        "         ICECAT_PASSWORD=tu-clave\n"
        "     Agrega .env al .gitignore ANTES de crearlo.\n\n"
        "  2. Un archivo ~/.icecat.env con lo mismo, fuera del repositorio.\n\n"
        "  3. Variables de la sesión de PowerShell:\n"
        "         $env:ICECAT_USER=\"tu-usuario\"\n"
        "         $env:ICECAT_PASSWORD=\"tu-clave\"\n\n"
        f"Se buscó un .env en:\n  {revisados}"
    )


def pedir(url, usuario=None, clave=None, binario=False):
    req = urllib.request.Request(url, headers={
        "User-Agent": "catalogo-tecnosport/1.0",
        "Accept-Encoding": "gzip",
    })
    if usuario:
        token = base64.b64encode(f"{usuario}:{clave}".encode()).decode()
        req.add_header("Authorization", f"Basic {token}")
    with urllib.request.urlopen(req, timeout=120) as r:
        datos = r.read()
    if datos[:2] == b"\x1f\x8b":
        datos = gzip.decompress(datos)
    return datos if binario else datos.decode("utf-8", errors="replace")


# Palabras que Icecat agrega al nombre y no distinguen un modelo de otro.
RUIDO = {"DUAL", "SIM", "ESIM", "ANDROID", "SMARTPHONE", "CM", "MM", "MAH", "USB",
         "TYPE", "C", "HD", "IPS", "OLED", "AMOLED", "NFC", "GPS", "WIFI", "RAM",
         "SSD", "PULGADAS", "TABLET", "NEGRO", "BLACK", "BLUE", "WHITE", "GREEN"}

# Palabras que sí distinguen variantes: si una está en un lado y no en el otro,
# casi seguro son productos distintos.
CALIFICADORES = {"PRO", "MAX", "PLUS", "ULTRA", "LITE", "MINI", "FE", "NEO",
                 "POWER", "ACTIVE", "CLASSIC", "PRIME", "PREMIUM", "ESPECIAL"}


def normalizar(texto):
    t = unicodedata.normalize("NFKD", texto or "").encode("ascii", "ignore").decode()
    t = re.sub(r"(\d+)\s*(GB|TB)\b", r"\1\2", t.upper())     # "256 GB" -> "256GB"
    t = re.sub(r"[^A-Z0-9]+", " ", t)
    return " ".join(w for w in t.split() if w not in RUIDO)


def capacidades(texto):
    return set(re.findall(r"\d+(?:GB|TB)", normalizar(texto)))


def puntaje(a, b):
    na, nb = normalizar(a), normalizar(b)
    if not na or not nb:
        return 0.0
    base = SequenceMatcher(None, na, nb).ratio()
    ta, tb = set(na.split()), set(nb.split())
    comunes = len(ta & tb) / max(1, len(ta))
    s = (base + comunes) / 2

    # Una capacidad distinta es otro producto, no un parecido.
    ca, cb = capacidades(a), capacidades(b)
    if ca and cb and not (ca & cb):
        s *= 0.45
    # Pro, Max, Plus y compañía separan variantes que se escriben casi igual.
    if (ta & CALIFICADORES) ^ (tb & CALIFICADORES):
        s *= 0.7
    return round(s, 3)


def cargar_productos(ruta):
    return json.loads(Path(ruta).read_text(encoding="utf-8"))["productos"]


def texto_producto(p):
    partes = [p.get("modelo"), p.get("ram"), p.get("almacenamiento"), p.get("red")]
    return " ".join(x for x in partes if x)


# --------------------------------------------------------------------------
# Paso 0: diagnóstico
# --------------------------------------------------------------------------

def _texto_respuesta(flujo, limite=400):
    """Lee un cuerpo que puede venir comprimido y sin terminar."""
    try:
        datos = flujo.read(2_000_000)
    except Exception as e:
        return f"(no se pudo leer el cuerpo: {e})"
    if datos[:2] == b"\x1f\x8b":
        try:
            datos = zlib.decompressobj(16 + zlib.MAX_WBITS).decompress(datos)
        except Exception:
            return "(cuerpo comprimido ilegible)"
    texto = datos.decode("utf-8", errors="replace")
    error = (re.search(r'ErrorMessage="([^"]+)"', texto)
             or re.search(r'ErrorMessage="([^"]{3,200})', texto))
    if error:
        return f"ErrorMessage: {error.group(1)}"
    return re.sub(r"\s+", " ", texto)[:limite]


def _sondear(url, usuario, clave, metodo="GET", limite=400):
    """Devuelve (codigo, detalle) sin lanzar excepción."""
    req = urllib.request.Request(url, method=metodo, headers={
        "User-Agent": "catalogo-tecnosport/1.0", "Accept-Encoding": "gzip"})
    token = base64.b64encode(f"{usuario}:{clave}".encode()).decode()
    req.add_header("Authorization", f"Basic {token}")
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            if metodo == "HEAD":
                mb = r.headers.get("Content-Length")
                mb = f"{int(mb) / 1_048_576:.0f} MB" if mb else "tamaño no informado"
                return r.status, mb
            return r.status, _texto_respuesta(r, limite)
    except urllib.error.HTTPError as e:
        return e.code, f"{e.reason} — {_texto_respuesta(e, 240)}"
    except Exception as e:
        return 0, f"{type(e).__name__}: {e}"


def cmd_diagnostico(args):
    usuario, clave = credenciales(args)
    print("=== DIAGNÓSTICO OPEN ICECAT ===")
    print(f"usuario: {usuario}  (la clave no se imprime)")
    print()

    pruebas = [
        ("marcas (SuppliersList)", PROVEEDORES_URL, "GET"),
        ("índice Open (peso)", INDICE_URL, "HEAD"),
        ("repositorio Full (debe fallar)", f"{BASE}/export/level4/EN/refs/", "HEAD"),
    ]
    for nombre, url, metodo in pruebas:
        codigo, detalle = _sondear(url, usuario, clave, metodo)
        print(f"[{codigo}] {nombre}\n      {detalle}\n")

    d = identificadores(args.icecat_id)
    if not d["icecat_id"]:
        print("Sin --icecat-id no se puede probar una ficha de producto.")
        print("Pega la URL completa de la ficha en icecat.biz o el número del id.")
        return

    print(f"--- producto de prueba: id={d['icecat_id']} codigo={d['prod_id'] or '?'} "
          f"gtin={d['gtin'] or '?'} marca={d['marca'] or '?'} ---\n")

    print("Repositorio abierto (carpeta por idioma):")
    for lang in ("ES", "EN", "INT"):
        codigo, detalle = _sondear(FICHA_URL.format(lang=lang, icecat_id=d["icecat_id"]),
                                   usuario, clave, "GET")
        print(f"  [{codigo}] lang={lang} -> {detalle[:200]}")

    # XML_s3 responde 200 con el motivo escrito, aunque el producto no esté
    print("\nAPI XML_s3 (devuelve el motivo en texto):")
    consultas = [("por icecat_id", f"lang=ES&icecat_id={d['icecat_id']}")]
    if d["gtin"]:
        consultas.append(("por GTIN", f"lang=ES&ean_upc={d['gtin']}"))
    if d["prod_id"] and d["marca"]:
        consultas.append(("por codigo+marca",
                          f"lang=ES&prod_id={urllib.parse.quote(d['prod_id'])}"
                          f"&vendor={urllib.parse.quote(d['marca'])}"))
    for nombre, query in consultas:
        codigo, detalle = _sondear(f"{XMLS3_URL}?{query}&output=productxml",
                                   usuario, clave, "GET")
        print(f"  [{codigo}] {nombre} -> {detalle[:240]}")

    print("\nCopia y pega todo este bloque tal cual.")


# --------------------------------------------------------------------------
# Paso 1: índice local
# --------------------------------------------------------------------------

def cmd_indice(args):
    usuario, clave = credenciales(args)
    if args.marcas:
        marcas = {m.strip().upper() for m in args.marcas.split(",") if m.strip()}
    else:
        marcas = {(p.get("marca") or "").upper() for p in cargar_productos(args.productos)}
    marcas.discard("")
    print(f"Marcas buscadas: {', '.join(sorted(marcas))}")

    print("Bajando lista de marcas de Icecat...")
    xml_prov = pedir(PROVEEDORES_URL, usuario, clave)
    ids_marca, equivalencias = {}, {}
    for prov in ET.fromstring(xml_prov).iter("Supplier"):
        nombre = (prov.get("Name") or "").strip()
        arriba = nombre.upper()
        for marca in marcas:
            # exacta, o el nombre de Icecat empieza por la marca ("Xiaomi Inc.")
            if arriba == marca or re.match(rf"^{re.escape(marca)}\b", arriba):
                ids_marca[prov.get("ID")] = nombre
                equivalencias.setdefault(marca, set()).add(nombre)
    sin_equivalencia = sorted(marcas - set(equivalencias))
    print(f"Marcas reconocidas en Icecat: "
          f"{', '.join(sorted({n for v in equivalencias.values() for n in v})) or 'ninguna'}")
    if sin_equivalencia:
        print(f"Sin equivalencia por nombre: {', '.join(sin_equivalencia)}")
    if not ids_marca:
        sys.exit("Ninguna de las marcas está en Icecat. No hay nada que indexar.")

    salida = Path(args.salida)
    salida.parent.mkdir(parents=True, exist_ok=True)
    comprimido = salida.with_name("files.index.xml.gz")

    if comprimido.exists() and comprimido.stat().st_size > 1_000_000 and not args.refrescar:
        print(f"Reutilizando {comprimido} ({comprimido.stat().st_size / 1_048_576:.0f} MB). "
              "Usa --refrescar para volver a bajarlo.")
    else:
        print("Bajando el índice de Open Icecat. Son unos 290 MB: puede tardar.")
        _descargar(INDICE_URL, usuario, clave, comprimido)

    print("Leyendo el índice (se procesa por partes, no se carga entero en memoria)...")
    filas = 0
    with salida.open("w", newline="", encoding="utf-8") as f, \
            gzip.open(comprimido, "rb") as gz:
        w = csv.writer(f)
        w.writerow(["icecat_id", "marca", "prod_id", "modelo", "gtin", "foto_principal"])
        for _, elem in ET.iterparse(gz, events=("end",)):
            if elem.tag != "file":
                continue
            marca = ids_marca.get(elem.get("Supplier_id"))
            if marca:
                gtins = [g.get("Value") for g in elem.iter("EAN_UPC")]
                w.writerow([elem.get("Product_ID"), marca, elem.get("Prod_ID"),
                            elem.get("Model_Name"), ";".join(filter(None, gtins)),
                            elem.get("HighPic")])
                filas += 1
            elem.clear()
    print(f"{filas} productos indexados -> {salida}")
    if filas:
        conteo = {}
        with salida.open(encoding="utf-8") as f:
            for fila in csv.DictReader(f):
                conteo[fila["marca"]] = conteo.get(fila["marca"], 0) + 1
        print("\nProductos en el catálogo abierto, por marca:")
        for marca, n in sorted(conteo.items(), key=lambda x: -x[1]):
            print(f"  {marca:16} {n}")
        vacias = sorted({n for v in equivalencias.values() for n in v} - set(conteo))
        if vacias:
            print("\nExisten en Icecat pero sin nada en el catálogo abierto "
                  "(su contenido es de pago):")
            print("  " + ", ".join(vacias))
    if sin_equivalencia:
        print("\nNo aparecen en la lista de marcas de Icecat con ese nombre:")
        print("  " + ", ".join(sin_equivalencia))
        print("  Puede ser que Icecat las escriba distinto. Si alguna te importa,")
        print("  búscala en icecat.biz y dime cómo la escriben ellos.")


def _descargar(url, usuario, clave, destino):
    """Baja a disco por trozos: el índice no cabe cómodo en memoria."""
    req = urllib.request.Request(url, headers={"User-Agent": "catalogo-tecnosport/1.0"})
    token = base64.b64encode(f"{usuario}:{clave}".encode()).decode()
    req.add_header("Authorization", f"Basic {token}")
    parcial = destino.with_suffix(destino.suffix + ".parcial")
    with urllib.request.urlopen(req, timeout=900) as r, parcial.open("wb") as f:
        total = int(r.headers.get("Content-Length") or 0)
        bajado = 0
        while True:
            trozo = r.read(1 << 20)
            if not trozo:
                break
            f.write(trozo)
            bajado += len(trozo)
            if total:
                print(f"\r  {bajado * 100 / total:5.1f}%  "
                      f"({bajado / 1_048_576:.0f} de {total / 1_048_576:.0f} MB)", end="")
    print()
    shutil.move(str(parcial), str(destino))


# --------------------------------------------------------------------------
# Paso 2: emparejar
# --------------------------------------------------------------------------

def cmd_buscar(args):
    indice = list(csv.DictReader(Path(args.indice).open(encoding="utf-8")))
    por_marca = {}
    for fila in indice:
        por_marca.setdefault(fila["marca"].upper(), []).append(fila)

    filas = []
    for p in cargar_productos(args.productos):
        marca = (p.get("marca") or "").upper()
        candidatos = por_marca.get(marca, [])
        alm = (p.get("almacenamiento") or "").upper().replace(" ", "")
        mejor, mejor_p = None, 0.0
        for c in candidatos:
            s = puntaje(texto_producto(p), c["modelo"])
            # el almacenamiento es el dato que separa variantes del mismo modelo
            if alm and alm not in normalizar(c["modelo"]).split():
                s *= 0.5
            if s > mejor_p:
                mejor, mejor_p = c, round(s, 3)
        filas.append({
            "id_producto": p["id"],
            "titulo": p["titulo"],
            "icecat_id": mejor["icecat_id"] if mejor and mejor_p >= MIN_PUNTAJE else "",
            "modelo_icecat": mejor["modelo"][:90] if mejor and mejor_p >= MIN_PUNTAJE else "",
            "gtin": mejor["gtin"] if mejor and mejor_p >= MIN_PUNTAJE else "",
            "puntaje": mejor_p if mejor else 0.0,
            "confirmado": "si" if mejor_p >= AUTO_CONFIRMA else "",
        })

    ruta = Path(args.coincidencias)
    with ruta.open("w", newline="", encoding="utf-8-sig") as f:
        w = csv.DictWriter(f, fieldnames=list(filas[0].keys()))
        w.writeheader()
        w.writerows(filas)

    con = sum(1 for f in filas if f["icecat_id"])
    autos = sum(1 for f in filas if f["confirmado"])
    print(f"{con} de {len(filas)} productos con candidato; "
          f"{autos} confirmados automáticamente por puntaje alto")
    print(f"-> {ruta}")
    print("Revisa la columna modelo_icecat. Escribe 'si' en confirmado para los que")
    print("estén bien, corrige el icecat_id de los dudosos y deja vacíos los que no.")
    print("En icecat_id puedes pegar la URL completa de icecat.biz: se extrae el id sola.")


# --------------------------------------------------------------------------
# Paso 3: traer contenido
# --------------------------------------------------------------------------

def extraer_ficha(xml_texto):
    raiz = ET.fromstring(xml_texto)
    prod = raiz.find(".//Product")
    if prod is None:
        return None
    if prod.get("ErrorMessage"):
        return {"error": prod.get("ErrorMessage")}

    fotos = []
    for pic in prod.iter("ProductPicture"):
        url = pic.get("Pic") or pic.get("Pic500x500") or pic.get("Original")
        if url:
            fotos.append({"url": url, "orden": int(pic.get("No") or 99),
                          "tipo": pic.get("Type") or "", "principal": pic.get("IsMain") == "Y"})
    fotos.sort(key=lambda x: (not x["principal"], x["orden"]))

    desc = prod.find(".//ProductDescription")
    resumen = prod.find(".//LongSummaryDescription")
    seo = prod.find(".//SEO")

    especificaciones = []
    for f in prod.iter("ProductFeature"):
        nombre = f.find(".//Name")
        valor = f.get("Presentation_Value") or f.get("Value")
        if nombre is not None and valor:
            especificaciones.append({"atributo": nombre.get("Value"), "valor": valor})

    return {
        "icecat_id": prod.get("ID"),
        "prod_id": prod.get("Prod_id"),
        "titulo_icecat": prod.get("Title") or prod.get("Name"),
        # la documentación escribe la etiqueta como EanCode y como EANCode
        "gtins": [e.get("EAN") or e.get("Value") for e in prod.iter()
                  if e.tag.lower() == "eancode" and (e.get("EAN") or e.get("Value"))],
        "descripcion_larga": (desc.get("LongDesc") if desc is not None else "") or "",
        "descripcion_corta": (desc.get("ShortDesc") if desc is not None else "") or "",
        "garantia": (desc.get("WarrantyInfo") if desc is not None else "") or "",
        "resumen": (resumen.text or "") if resumen is not None else "",
        "vinetas": [b.get("Value") for b in prod.iter("GeneratedBulletPoint") if b.get("Value")],
        "meta_titulo": (seo.get("Title") if seo is not None else "") or "",
        "meta_descripcion": (seo.get("Description") if seo is not None else "") or "",
        "especificaciones": especificaciones,
        "fotos": fotos,
        "atribucion": "Specs Icecat — https://icecat.biz",
    }


def cmd_traer(args):
    usuario, clave = credenciales(args)
    coincidencias = list(csv.DictReader(Path(args.coincidencias).open(encoding="utf-8-sig")))
    for c in coincidencias:
        c["icecat_id"] = identificadores(c["icecat_id"])["icecat_id"]
    elegidas = [c for c in coincidencias
                if c["icecat_id"] and (not args.solo_confirmados
                                       or c["confirmado"].strip().lower() in ("si", "sí", "x", "1"))]
    if not elegidas:
        sys.exit("No hay coincidencias confirmadas en el CSV.")

    dir_icecat = Path(args.salida_contenido)
    dir_icecat.mkdir(parents=True, exist_ok=True)
    filas_fotos, ok, errores, de_pago = [], 0, [], []

    for c in elegidas:
        url = FICHA_URL.format(lang=args.lang, icecat_id=c["icecat_id"])
        try:
            ficha = extraer_ficha(pedir(url, usuario, clave))
        except urllib.error.HTTPError as e:
            if e.code == 404:
                de_pago.append(c["id_producto"])
            else:
                errores.append(f"{c['id_producto']}: HTTP {e.code} — {e.reason}")
            continue
        except Exception as e:
            errores.append(f"{c['id_producto']}: {e}")
            continue
        time.sleep(PAUSA)

        motivo = (ficha or {}).get("error", "")
        if re.search(r"full icecat", motivo, re.I):
            de_pago.append(c["id_producto"])   # no está en el catálogo abierto
            continue
        if not ficha or motivo:
            errores.append(f"{c['id_producto']}: {motivo or 'ficha vacía'}")
            continue

        (dir_icecat / f"{c['id_producto']}.json").write_text(
            json.dumps(ficha, ensure_ascii=False, indent=2), encoding="utf-8")
        for n, foto in enumerate(ficha["fotos"][:FOTOS_POR_PRODUCTO], start=1):
            filas_fotos.append({
                "id_producto": c["id_producto"], "titulo": c["titulo"], "n_foto": n,
                "encuadre": foto["tipo"] or "", "url": foto["url"],
                "fuente": "Open Icecat", "licencia": "Open Icecat Content License",
            })
        ok += 1
        print(f"  ok: {c['id_producto']} ({len(ficha['fotos'])} fotos)")

    ruta_fotos = Path(args.salida_fotos)
    ruta_fotos.parent.mkdir(parents=True, exist_ok=True)
    with ruta_fotos.open("w", newline="", encoding="utf-8-sig") as f:
        w = csv.DictWriter(f, fieldnames=["id_producto", "titulo", "n_foto",
                                          "encuadre", "url", "fuente", "licencia"])
        w.writeheader()
        w.writerows(filas_fotos)

    print(f"\n{ok} fichas traídas, {len(filas_fotos)} fotos listadas.")
    print(f"-> {dir_icecat}/  y  {ruta_fotos}")
    if de_pago:
        print(f"\n{len(de_pago)} productos están solo en el catálogo de pago. "
              "No son un error: van al pedido de fotos del proveedor.")
    if errores:
        print(f"\n{len(errores)} con error:")
        for e in errores[:20]:
            print("  " + e)
        print("\nSi el error se repite igual en todos, es de credenciales o de acceso,")
        print("no del producto. Pásame el mensaje tal cual y lo ajustamos.")


# --------------------------------------------------------------------------

def main():
    ap = argparse.ArgumentParser(description="Cliente local de Open Icecat")
    ap.add_argument("--usuario", help="usuario de Icecat (o variable ICECAT_USER)")
    ap.add_argument("--env", help="ruta a un .env concreto; por defecto se busca solo")
    sub = ap.add_subparsers(dest="cmd", required=True)

    d = sub.add_parser("diagnostico", help="comprueba credenciales, acceso e idiomas")
    d.add_argument("--icecat-id", help="id o URL de icecat.biz de un producto tuyo")
    d.set_defaults(func=cmd_diagnostico)

    i = sub.add_parser("id", help="extrae icecat_id, código y GTIN de una URL de icecat.biz")
    i.add_argument("valores", nargs="+")
    i.set_defaults(func=cmd_id)

    a = sub.add_parser("indice", help="baja y filtra el índice de Open Icecat")
    a.add_argument("--productos", default="productos.json")
    a.add_argument("--salida", default="icecat/indice.csv")
    a.add_argument("--refrescar", action="store_true", help="vuelve a bajar el índice")
    a.add_argument("--marcas", help="lista separada por comas, en vez de productos.json")
    a.set_defaults(func=cmd_indice)

    b = sub.add_parser("buscar", help="empareja la lista con el índice")
    b.add_argument("--productos", default="productos.json")
    b.add_argument("--indice", default="icecat/indice.csv")
    b.add_argument("--coincidencias", default="icecat/coincidencias.csv")
    b.set_defaults(func=cmd_buscar)

    c = sub.add_parser("traer", help="baja fichas y enlaces de fotos")
    c.add_argument("--coincidencias", default="icecat/coincidencias.csv")
    c.add_argument("--salida-contenido", default="icecat/fichas")
    c.add_argument("--salida-fotos", default="fotos/urls-icecat.csv")
    c.add_argument("--lang", default="ES", help="ES, EN, es_CO segun la tabla de locales")
    c.add_argument("--solo-confirmados", action="store_true",
                   help="usa solo las filas marcadas en la columna confirmado")
    c.set_defaults(func=cmd_traer)

    args = ap.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
