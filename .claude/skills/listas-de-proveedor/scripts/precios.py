#!/usr/bin/env python3
"""
Reúne precios de mercado colombiano desde los catálogos VTEX de Éxito, Olímpica
y Jumbo, separando el inventario propio de la tienda —la vitrina— de los
vendedores del marketplace.

Uso:
    python3 precios.py productos.json --salida precios.json
    python3 precios.py productos.json --salida precios.json --solo "JBL Xtreme 5"

La distinción vitrina/marketplace no es un adorno: los terceros tiran el precio
muy por debajo de la vitrina y mezclarlos hunde categorías enteras. En la lista
del 12/09/2026 los parlantes JBL daban mediana −1 % con precios de marketplace y
+14 % contra la vitrina. Por eso cada precio sale etiquetado con su nivel y este
script NO promedia nada: solo cosecha. Ver `referencias/precios.md`.

Las tres tiendas traen sobre todo marketplace en tecnología, así que un corpus
armado solo con esto queda flojo. Alkosto —que es la vitrina que corrige el
sesgo— no es VTEX y se consulta por navegador, no desde aquí.

Sin dependencias: solo biblioteca estándar.
"""

import argparse
import json
import re
import sys
import time
import unicodedata
import urllib.error
import urllib.parse
import urllib.request

# El catálogo VTEX de estas tres responde sin credenciales.
TIENDAS = [
    ("Éxito", "https://www.exito.com"),
    ("Olímpica", "https://www.olimpica.com"),
    ("Jumbo", "https://www.tiendasjumbo.co"),
]

# En VTEX el vendedor con id "1" es el inventario propio de la tienda; cualquier
# otro es un tercero del marketplace.
SELLER_VITRINA = "1"

AGENTE = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
          "(KHTML, like Gecko) Chrome/125.0 Safari/537.36")

PAUSA_SEGUNDOS = 0.4
RESULTADOS_POR_CONSULTA = 20

RE_RAM = re.compile(r"\b\d{1,2}\s*GB\s*RAM\b", re.I)
RE_CAPACIDAD = re.compile(r"\b(\d{2,4}\s*GB|\d\s*TB)\b", re.I)
RE_PULGADAS = re.compile(r"\b\d{1,2}([.,]\d)?\"")


def normalizar(texto):
    t = unicodedata.normalize("NFKD", texto).encode("ascii", "ignore").decode()
    return re.sub(r"\s+", " ", t).strip().lower()


def sanear(consulta):
    """
    VTEX responde 400 si el texto de búsqueda trae comillas o un más: las
    pulgadas (`8.7"`) y los modelos con plus (`A11+`) tumbaban la consulta de
    las tres tiendas a la vez. Se quitan los dos caracteres, no el número.
    """
    return re.sub(r"\s+", " ", consulta.replace('"', " ").replace("+", " ")).strip()


def consultas(titulo):
    """
    De más específica a más general. VTEX busca por texto completo: demasiados
    términos devuelven cero resultados, así que se afloja por pasos en vez de
    mandar el título entero una sola vez.
    """
    base = RE_RAM.sub("", titulo)
    base = re.sub(r"\s+", " ", base).strip()
    salida = [base]

    sin_capacidad = RE_CAPACIDAD.sub("", base).strip()
    sin_capacidad = re.sub(r"\s+", " ", sin_capacidad)
    if sin_capacidad and sin_capacidad != base:
        salida.append(sin_capacidad)

    sin_pulgadas = RE_PULGADAS.sub("", sin_capacidad or base).strip()
    sin_pulgadas = re.sub(r"\s+", " ", sin_pulgadas)
    if sin_pulgadas and sin_pulgadas not in salida:
        salida.append(sin_pulgadas)

    corto = " ".join((sin_pulgadas or base).split()[:3])
    if corto and corto not in salida:
        salida.append(corto)

    vistas, limpias = set(), []
    for c in salida:
        c = sanear(c)
        if c and len(c) >= 3 and c not in vistas:
            vistas.add(c)
            limpias.append(c)
    return limpias


def pedir(url):
    pedido = urllib.request.Request(url, headers={
        "User-Agent": AGENTE,
        "Accept": "application/json",
    })
    with urllib.request.urlopen(pedido, timeout=30) as r:
        return json.loads(r.read().decode("utf-8", "replace"))


def buscar(base_url, consulta):
    url = (f"{base_url}/api/catalog_system/pub/products/search"
           f"?ft={urllib.parse.quote(consulta)}&_from=0&_to={RESULTADOS_POR_CONSULTA - 1}")
    try:
        return pedir(url)
    except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError,
            json.JSONDecodeError) as e:
        print(f"    ! {base_url}: {e}", file=sys.stderr)
        return []


def ofertas(producto, tienda, base_url):
    """Una fila por vendedor con stock y precio real."""
    filas = []
    enlace = f"{base_url}/{producto.get('linkText', '')}/p"
    for item in producto.get("items", []):
        for vendedor in item.get("sellers", []):
            oferta = vendedor.get("commertialOffer") or {}
            precio = oferta.get("Price") or 0
            if precio <= 0 or (oferta.get("AvailableQuantity") or 0) <= 0:
                continue
            propio = str(vendedor.get("sellerId")) == SELLER_VITRINA
            filas.append({
                "tienda": tienda,
                "nivel": "inventario propio" if propio else "marketplace",
                "vendedor": vendedor.get("sellerName"),
                "nombre": producto.get("productName"),
                "marca": producto.get("brand"),
                "precio_cop": int(precio),
                "precio_lista_cop": int(oferta.get("ListPrice") or 0) or None,
                "enlace": enlace,
                "red_nombre": red_de(producto.get("productName") or ""),
            })
    return filas


# Palabras que delatan un accesorio del producto, no el producto. Solo descartan
# cuando el título buscado no las trae: «Samsung Cargador 25W» sí es un cargador.
ACCESORIOS = (
    "estuche", "forro", "funda", "case", "protector", "soporte", "correa",
    "vidrio", "lamina", "cover", "bolso", "maleta", "adaptador", "repuesto",
    "kit de", "combo",
)

# Un calificador cambia el producto y el precio: el Note 15 y el Note 15 Pro no
# son el mismo equipo. Tiene que estar en los dos lados o en ninguno.
CALIFICADORES = {
    "pro", "plus", "max", "ultra", "lite", "mini", "active", "classic",
    "fe", "se", "neo", "power", "air", "bundle",
}


RE_RED_NOMBRE = re.compile(r"\b(5\s?G|4\s?G|LTE)\b", re.I)


def red_de(texto):
    """4G y 5G son dos referencias con dos precios. El retail escribe LTE por 4G."""
    m = RE_RED_NOMBRE.search(texto)
    if not m:
        return None
    v = re.sub(r"\s", "", m.group(1)).upper()
    return "4G" if v in ("4G", "LTE") else "5G"


def tokens_de(texto):
    return [x for x in re.split(r"[^\w.]+", normalizar(texto)) if x]


def coincide(nombre, titulo):
    """
    La búsqueda por texto trae vecinos, y tomarlos por buenos es el error que
    `precios.md` llama «referencia distinta a la que se comparó»: al pedir
    «JBL Xtreme 5» VTEX devuelve el Xtreme 4, el Xtreme 3 y un estuche de
    $94.990, y los tres mueven la mediana.
    """
    n, t = normalizar(nombre), normalizar(titulo)
    n_junto = re.sub(r"[^\w]", "", n)
    tn, tt = tokens_de(nombre), tokens_de(titulo)

    # 1. Un accesorio del producto no es el producto.
    if any(a in n and a not in t for a in ACCESORIOS):
        return False

    # 2. Ni un reacondicionado ni un usado: `precios.md` los deja fuera porque
    #    el mercado los paga menos y no son lo que se va a publicar.
    if any(x in n for x in ("reacondicionado", "refurbished", "seminuevo",
                            "open box", "usado")):
        return False

    # 3. Un combo no es el producto: Alkosto vende «Note 15 Pro + Power Bank
    #    165W» y ese precio no es el del celular solo.
    if re.search(r"\s\+\s", n) and not re.search(r"\s\+\s", t):
        return False

    # 4. La capacidad decide el precio: un 256GB y un 512GB no lo comparten.
    #    Hay que quitar la RAM antes de buscarla, o la primera coincidencia de
    #    «POCO F8 Pro 5G 12GB RAM 256GB» es los 12GB de RAM y entonces el filtro
    #    deja pasar el 512GB, que tambien dice 12GB. Con la RAM fuera, lo que
    #    queda a cada lado es el almacenamiento.
    titulo_sr = RE_RAM.sub(" ", titulo)
    nombre_sr = RE_RAM.sub(" ", nombre)
    cap = RE_CAPACIDAD.search(titulo_sr)
    if cap:
        pedida = normalizar(cap.group(1)).replace(" ", "")
        halladas = [normalizar(m.group(1)).replace(" ", "")
                    for m in RE_CAPACIDAD.finditer(nombre_sr)]
        if halladas and pedida not in halladas:
            return False

    # 4.b Hay productos donde el número que identifica no es una capacidad de
    #     disco sino la potencia o los miliamperios. Se descubrió en las power
    #     bank —«Xiaomi Power Bank 10.000 mAh 165W» emparejaba con una Awei de
    #     10000 mAh 22.5W y con una Xiaomi Magnetic de 5000 mAh, y la mediana
    #     terminaba en 119.900 cuando la vitrina la vende a 249.900—, que desde
    #     el 24/09/2026 ya no se publican. La regla se queda porque nunca fue de
    #     esa categoría: es genérica, y los vatios siguen identificando a un
    #     parlante igual que identificaban a un cargador.
    for patron in (r"(\d{2,3})\s*W\b", r"([\d.]+)\s*mAh\b"):
        pedidos = {m.group(1).replace(".", "") for m in re.finditer(patron, t, re.I)}
        if not pedidos:
            continue
        hallados = {m.group(1).replace(".", "") for m in re.finditer(patron, n, re.I)}
        if hallados and not (pedidos & hallados):
            return False
        if not hallados:
            return False

    # 5. El código de modelo —a17, g67, sb180, x8b— no se negocia.
    codigos = [x for x in tt
               if x[0].isalpha() and any(c.isdigit() for c in x) and len(x) >= 2]
    if any(c not in n_junto for c in codigos):
        return False

    # 6. El número que sigue al nombre de la línea distingue generaciones:
    #    «xtreme5» no está en «parlante jbl xtreme 4 negro».
    for a, b in zip(tt, tt[1:]):
        if a.isalpha() and len(a) >= 3 and b.isdigit():
            if (a + b) not in n_junto:
                return False

    # 7. 4G y 5G son dos productos. Si el nombre declara una red distinta a la
    #    del título, no es el mismo equipo; si no declara ninguna, pasa pero
    #    queda marcado para pesarlo distinto (ver `red_sin_declarar`).
    red_t, red_n = red_de(titulo), red_de(nombre)
    if red_t and red_n and red_t != red_n:
        return False

    # 8. Los calificadores tienen que estar en los dos lados o en ninguno.
    for q in CALIFICADORES:
        if (q in tt) != (q in tn):
            return False

    # 9. Y que el modelo esté de verdad, no solo la marca.
    largos = [x for x in tt if len(x) >= 3 and not x.isdigit()]
    if not largos:
        return True
    return sum(1 for x in largos if x in n) >= max(2, len(largos) // 2)


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("productos", help="productos.json del parser")
    ap.add_argument("--salida", required=True)
    ap.add_argument("--solo", help="procesar solo los títulos que contengan este texto")
    args = ap.parse_args()

    datos = json.loads(open(args.productos, encoding="utf-8").read())
    items = datos["productos"]
    if args.solo:
        aguja = normalizar(args.solo)
        items = [p for p in items if aguja in normalizar(p["titulo"])]

    corpus = {}
    for i, prod in enumerate(items, start=1):
        titulo = prod["titulo"]
        print(f"[{i}/{len(items)}] {titulo}")
        filas = []
        for tienda, base_url in TIENDAS:
            for consulta in consultas(titulo):
                encontrados = buscar(base_url, consulta)
                time.sleep(PAUSA_SEGUNDOS)
                nuevas = [f for p in encontrados
                          if coincide(p.get("productName", ""), titulo)
                          for f in ofertas(p, tienda, base_url)]
                if nuevas:
                    filas.extend(nuevas)
                    break          # esta tienda ya respondió: no aflojar más
        vitrina = [f for f in filas if f["nivel"] == "inventario propio"]
        print(f"    {len(filas)} ofertas | vitrina: {len(vitrina)}")
        corpus[prod["id"]] = {"titulo": titulo, "ofertas": filas}

    with open(args.salida, "w", encoding="utf-8") as f:
        json.dump(corpus, f, ensure_ascii=False, indent=2)

    con_vitrina = sum(1 for v in corpus.values()
                      if any(o["nivel"] == "inventario propio" for o in v["ofertas"]))
    sin_nada = sum(1 for v in corpus.values() if not v["ofertas"])
    print(f"\n{len(corpus)} productos | con vitrina: {con_vitrina} | sin ninguna oferta: {sin_nada}")
    print("Falta Alkosto, que es la vitrina que corrige el sesgo: va por navegador.")


if __name__ == "__main__":
    main()
