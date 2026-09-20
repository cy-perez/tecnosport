#!/usr/bin/env python3
"""
Decide el precio de mercado de cada producto a partir del corpus cosechado y lo
escribe en productos.json.

Uso:
    python3 asignar_precios.py productos.json \
        --vtex precios-vtex.json --alkosto alkosto-vitrina.json

Aplica, tal cual, las reglas de `referencias/precios.md`:

1. **Si hay vitrina, manda.** El inventario propio de la tienda y el marketplace
   no se promedian nunca: los terceros tiran el precio muy por debajo y mezclar
   los dos niveles hunde categorías enteras.
2. **Mediana, no promedio.** Una promoción agresiva o un listado inflado mueven
   el promedio; la mediana los resiste.
3. **Se descarta lo que se salga más de un 35 % de la mediana**, y queda anotado.
4. **Menos de tres fuentes es poca evidencia**, y el producto queda marcado.
5. **Cada precio guarda su fuente.** Un precio sin fuente no se puede defender
   cuando el negocio pregunte de dónde salió.

Y una regla que salió de esta lista: cuando el catálogo trae el mismo modelo en
4G y en 5G, las ofertas cuyo nombre no declara la red **no se pueden atribuir**
a ninguno de los dos y quedan fuera del cálculo. Un A17 4G y un A17 5G se
venden a precios distintos, y el retail escribe «A17 256GB» a secas.

Sin dependencias: solo biblioteca estándar.
"""

import argparse
import importlib.util
import json
import pathlib
import re
import statistics
import unicodedata

# El mismo emparejador que usa la cosecha. Se reusa aquí para poder apretarlo sin
# volver a consultar las tiendas: el corpus guarda el nombre de cada oferta.
_spec = importlib.util.spec_from_file_location(
    "precios", pathlib.Path(__file__).with_name("precios.py"))
_precios = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_precios)
coincide = _precios.coincide

DESVIACION_MAXIMA = 0.35
FUENTES_MINIMAS = 3
MARGEN_SOSPECHOSO = 0.60

RE_RED = re.compile(r"\b(4G|5G)\b", re.I)


def normalizar(texto):
    t = unicodedata.normalize("NFKD", texto or "").encode("ascii", "ignore").decode()
    return re.sub(r"\s+", " ", t).strip().lower()


def base_sin_red(titulo):
    """El título sin la red, para detectar que el mismo modelo viene en 4G y 5G."""
    return normalizar(RE_RED.sub("", titulo))


def dedupe(filas):
    """
    VTEX devuelve una fila por color. Cuenta una vez por (tienda, vendedor,
    precio): el Redmi Pad 2 Pro en negro y en gris, al mismo precio y en la
    misma tienda, es una observación, no dos. Contarlas por separado le daba
    a Éxito dos votos contra uno de Alkosto y movía la mediana.
    """
    vistas, salida = set(), []
    for f in filas:
        k = (f["tienda"], f.get("vendedor"), f["precio_cop"])
        if k not in vistas:
            vistas.add(k)
            salida.append(f)
    return salida


def resumir(filas):
    """Mediana del grupo, quitando lo que se aleje más del umbral. Devuelve
    (precio, usadas, descartadas)."""
    precios = [f["precio_cop"] for f in filas]
    if not precios:
        return None, [], []
    m = statistics.median(precios)
    usadas = [f for f in filas if abs(f["precio_cop"] - m) / m <= DESVIACION_MAXIMA]
    fuera = [f for f in filas if f not in usadas]
    if not usadas:
        return int(m), filas, []
    return int(statistics.median([f["precio_cop"] for f in usadas])), usadas, fuera


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("productos")
    ap.add_argument("--vtex", required=True)
    ap.add_argument("--alkosto", required=True)
    ap.add_argument("--descartar",
                    help="JSON de id -> motivo: referencias que una revision a mano "
                         "rechazo pese a pasar el emparejador")
    ap.add_argument("--fecha", default="2026-09-19",
                    help="fecha de consulta, que queda en cada fuente")
    args = ap.parse_args()

    datos = json.loads(open(args.productos, encoding="utf-8").read())
    vtex = json.loads(open(args.vtex, encoding="utf-8").read())
    alkosto = json.loads(open(args.alkosto, encoding="utf-8").read())
    descartar = (json.loads(open(args.descartar, encoding="utf-8").read())
                 if args.descartar else {})

    # ¿Qué modelos vienen en las dos redes? Ahí las ofertas sin red no sirven.
    bases = {}
    for p in datos["productos"]:
        red = (RE_RED.search(p["titulo"]) or [None])[0]
        if red:
            bases.setdefault(base_sin_red(p["titulo"]), set()).add(red.upper())
    ambiguas = {b for b, redes in bases.items() if len(redes) > 1}

    for p in datos["productos"]:
        pid = p["id"]
        fuentes, notas = [], []

        alk = alkosto.get(pid) or {}
        if alk.get("precio"):
            fuentes.append({
                "tienda": "Alkosto", "nivel": "inventario propio",
                "nombre": alk["nombre"], "precio_cop": alk["precio"],
                "precio_antes_cop": alk.get("antes") or None,
                "enlace": "https://www.alkosto.com/search?text="
                          + alk["nombre"].replace(" ", "+"),
                "consultado": args.fecha,
            })
        elif alk.get("sin_vitrina"):
            notas.append("sin vitrina en Alkosto: " + alk["sin_vitrina"])
        if alk.get("reparo"):
            notas.append(alk["reparo"])

        crudas = (vtex.get(pid) or {}).get("ofertas", [])

        # Lo que una revision a mano rechazo no vuelve a entrar por la puerta de
        # atras: el corpus entero del producto se tira, con el motivo escrito.
        motivo = descartar.get(pid)
        if motivo:
            crudas = []
            notas.append("corpus descartado a mano: " + motivo)

        # La cosecha guardó el nombre de cada oferta, así que el emparejador se
        # puede apretar sin volver a consultar las tiendas. Aquí se vuelve a
        # pasar: así una regla nueva —la de vatios y miliamperios, por ejemplo—
        # limpia el corpus que ya está en disco.
        antes_filtro = len(crudas)
        crudas = [f for f in crudas if coincide(f["nombre"], p["titulo"])]
        if antes_filtro != len(crudas):
            notas.append(f"se descartaron {antes_filtro - len(crudas)} ofertas al "
                         "revalidar el nombre contra el titulo")
        crudas = dedupe(crudas)

        red_producto = (RE_RED.search(p["titulo"]) or [None])[0]
        if red_producto and base_sin_red(p["titulo"]) in ambiguas:
            antes = len(crudas)
            crudas = [f for f in crudas if f.get("red_nombre")]
            if antes != len(crudas):
                notas.append(
                    f"se dejaron fuera {antes - len(crudas)} ofertas cuyo nombre no dice "
                    "si es 4G o 5G: la lista trae el mismo modelo en las dos redes y "
                    "un precio sin red no se puede atribuir")

        propias = [f for f in crudas if f["nivel"] == "inventario propio"]
        mercado = [f for f in crudas if f["nivel"] == "marketplace"]

        # El precio de Alkosto se emparejó a mano, uno por uno; los de VTEX los
        # emparejó una expresión regular. Cuando hay Alkosto, ancla: una oferta
        # que se aleje más del umbral es casi siempre otro producto —la power
        # bank Xiaomi de 165W emparejaba con una Awei y con una Magnetic de
        # 5000 mAh— y no debe votar en la mediana.
        if fuentes and propias:
            ancla = fuentes[0]["precio_cop"]
            cerca = [f for f in propias
                     if abs(f["precio_cop"] - ancla) / ancla <= DESVIACION_MAXIMA]
            if len(cerca) != len(propias):
                notas.append(
                    f"se dejaron fuera {len(propias) - len(cerca)} ofertas de vitrina "
                    f"que se alejan mas de {int(DESVIACION_MAXIMA * 100)} % del precio "
                    "verificado a mano en Alkosto: casi siempre son otra referencia")
            propias = cerca

        vitrina = fuentes + [dict(f, consultado=args.fecha) for f in propias]

        if vitrina:
            nivel = "inventario propio"
            precio, usadas, fuera = resumir(vitrina)
        elif mercado:
            nivel = "marketplace"
            precio, usadas, fuera = resumir([dict(f, consultado=args.fecha)
                                             for f in mercado])
            notas.append("precio de marketplace: ninguna vitrina tiene la referencia, "
                         "asi que este numero vale menos que uno de vitrina")
        else:
            precio, usadas, fuera = None, [], []

        for f in fuera:
            notas.append(f"descartado {f['precio_cop']:,} COP de {f['tienda']}: "
                         f"se aleja mas de {int(DESVIACION_MAXIMA * 100)} % de la mediana")

        p["precio_mercado_cop"] = precio
        p["nivel_precio"] = nivel if precio else None
        p["fuentes_precio"] = usadas

        if precio is None:
            p["revisar"].append(
                "sin precio de mercado: no se encontro la referencia en ninguna "
                "tienda admisible; el margen no se puede calcular")
            p["ganancia_cop"] = None
            p["margen"] = None
        else:
            p["ganancia_cop"] = precio - p["precio_proveedor_cop"]
            p["margen"] = round(p["ganancia_cop"] / p["precio_proveedor_cop"], 4)
            tiendas = {f["tienda"] for f in usadas}
            if len(tiendas) < FUENTES_MINIMAS:
                notas.append(f"precio con poca evidencia: {len(tiendas)} tienda(s)")
            if p["margen"] < 0:
                p["revisar"].append(
                    f"POR DEBAJO DEL COSTO: el mercado paga {precio:,} COP y el "
                    f"proveedor cobra {p['precio_proveedor_cop']:,} COP "
                    f"({p['margen']:.1%}). No publicar sin decision del negocio")
            elif p["margen"] > MARGEN_SOSPECHOSO:
                p["revisar"].append(
                    f"margen inusual de {p['margen']:.1%}: revisar que el precio de "
                    "lista no tenga un error de lectura (el x1.000) y que la "
                    "referencia comparada sea la misma")

        p["notas_precio"] = notas

    json.dump(datos, open(args.productos, "w", encoding="utf-8"),
              ensure_ascii=False, indent=2)

    con = [p for p in datos["productos"] if p["precio_mercado_cop"]]
    vit = [p for p in con if p["nivel_precio"] == "inventario propio"]
    bajo = [p for p in con if p["margen"] < 0]
    print(f"{len(con)}/{len(datos['productos'])} con precio de mercado "
          f"| {len(vit)} de vitrina | {len(con) - len(vit)} de marketplace "
          f"| {len(bajo)} por debajo del costo")
    if con:
        print(f"margen mediano: {statistics.median(p['margen'] for p in con):.1%}")


if __name__ == "__main__":
    main()
