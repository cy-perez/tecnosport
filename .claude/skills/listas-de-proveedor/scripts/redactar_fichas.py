#!/usr/bin/env python3
"""
Compone la descripción de cada **modelo** con la estructura de
`referencias/descripciones.md` y la escribe en todos sus productos de productos.json.

Uso:
    python3 redactar_fichas.py catalogo/productos.json --prosa catalogo/3-redaccion/prosa.json \
        --marca catalogo/3-redaccion/fichas-marca.json --icecat catalogo/3-redaccion/icecat/fichas

Una descripción por modelo, no por SKU: el Galaxy A57 de 256 GB y el de 512 GB
comparten ficha, y la memoria y el color son variantes que el cliente elige al
comprar. Por eso la descripción no lleva fila de memoria, ni sección de colores,
ni las notas de RAM virtual y de SIM (decisión del 08/10/2026).

La ficha técnica sale, en este orden (decisión del 08/10/2026):

1. **El sitio oficial de la marca**, en `--marca`: un JSON por id de modelo con
   la fuente, la URL, la fecha de consulta y las filas.
2. **Open Icecat**, en `--icecat`, y solo por código: la tabla la arma este
   script y la prosa no se escribe nunca a partir de ella. La licencia anula el
   permiso si los datos se usan para «automated synthetic content creation»
   (cláusula 10), y exige el aviso de derechos, el descargo y la nota de lo que
   se modificó (cláusulas 1 y 2): ver `aviso_icecat`.

Reparte el trabajo como lo reparte la skill: **la estructura y la tabla las
arma el script** con los datos de la ficha oficial, y **la prosa la escribe una
persona** en `prosa.json` —la apertura, las viñetas de beneficio, el contenido
de la caja y las notas—. Así las 96 descripciones salen iguales de forma y
distintas de fondo, que es lo que pide el estándar.

Lo que este script NO hace, a propósito:

- **No inventa.** Si un atributo no está en la ficha, la fila no aparece. Un
  producto sin prosa autorada queda sin descripción y marcado, no con una
  descripción genérica.
- **No promete garantía en plazos.** El texto remite a la garantía legal
  colombiana y a la política de la tienda, porque el plazo es un dato del
  negocio. Nunca un marcador `[[ ]]`, que la regla 4 del proyecto prohíbe en
  texto publicado.
- **No omite el aviso de Icecat.** Cuando la ficha sale de Open Icecat, el
  bloque de fuentes lleva el aviso literal, el enlace a la licencia y la nota de
  la modificación: son condiciones de la licencia, no un crédito opcional.

Sin dependencias: solo biblioteca estándar.
"""

import argparse
import json
import re
import sys
from datetime import date
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import descripciones  # noqa: E402
from colores import SUPUESTO_PALETA_COMPLETA  # noqa: E402
from pendientes import COMPLETAR, necesita  # noqa: E402

# Lo que comparar_lista.py anota de una descripción heredada: se recalcula al completarla.
ALERTA_DESCRIPCION = "descripción de la base:"

LIMITE_META_TITULO = 60
LIMITE_META_DESCRIPCION = 155

# Atributo de Icecat -> fila de la tabla. El orden de esta lista es el orden de
# la tabla, y la primera coincidencia gana: un producto no repite fila.
FILAS = [
    ("Pantalla", ["Diagonal de la pantalla", "Resolución de la pantalla",
                  "Tipo de pantalla", "Máxima velocidad de actualización"]),
    ("Procesador", ["Familia de procesador", "Modelo del procesador",
                    "Número de núcleos de procesador"]),
    # Sin RAM ni almacenamiento: son variantes. Sí la tarjeta, que es del equipo.
    ("Expansión", ["Tarjetas de memoria compatibles"]),
    ("Cámara", ["Resolución de la cámara trasera (numérica)",
                "Tipo de cámara trasera",
                "Resolución de la cámara frontal (numérica)"]),
    ("Batería", ["Capacidad de batería", "Potencia de carga requerida (máx.)",
                 "Adaptador AC incluido"]),
    ("Conectividad", ["Estándar Wi-Fi", "Versión de Bluetooth",
                      "Comunicación de Campo Cercano (NFC)", "Conector USB"]),
    ("Sistema operativo", ["Sistema operativo instalado", "Plataforma"]),
    ("Resistencia", ["Código IP (International Protection)"]),
    ("Dimensiones y peso", ["Altura", "Ancho", "Profundidad", "Peso"]),
]


# Atributos cuyo valor no se entiende suelto en la tabla. Sin esto, un parlante
# publicaba «Batería: 17,28 Wh · No» (el «No» era el adaptador), «Conectividad:
# 5.4» (la versión de Bluetooth) y tres medidas sin decir cuál es cuál.
ROTULOS = {
    "Número de núcleos de procesador": "{} núcleos",
    "Resolución de la cámara trasera (numérica)": "trasera {}",
    "Resolución de la cámara frontal (numérica)": "frontal {}",
    "Adaptador AC incluido": "adaptador incluido: {}",
    "Versión de Bluetooth": "Bluetooth {}",
    "Comunicación de Campo Cercano (NFC)": "NFC: {}",
    "Altura": "alto {}",
    "Ancho": "ancho {}",
    "Profundidad": "fondo {}",
    "Peso": "peso {}",
}


def limpiar(valor):
    return re.sub(r"\s+", " ", str(valor)).strip()


def rotular(atributo, valor):
    return ROTULOS.get(atributo, "{}").format(valor)


def tabla_icecat(ficha):
    """Las filas que la ficha soporta, en el orden del estándar."""
    por_atributo = {}
    for e in ficha.get("especificaciones") or []:
        por_atributo.setdefault(limpiar(e["atributo"]), limpiar(e["valor"]))
    filas = []
    for etiqueta, atributos in FILAS:
        partes = [rotular(a, por_atributo[a]) for a in atributos if por_atributo.get(a)]
        if partes:
            filas.append((etiqueta, " · ".join(dict.fromkeys(partes))))
    return filas


# Lo que en una ficha de marca es de la configuración y no del modelo.
FILAS_DE_CONFIGURACION = re.compile(r"^(memoria|ram|almacenamiento|capacidad|memoria y almacenamiento)$", re.I)


def tabla_mi(ficha):
    """
    La ficha de mi.com ya viene por secciones y redactada a mano en
    `mi-fichas.json`, así que aquí no se reformatea el valor: solo se quita la
    nota al pie que el fabricante cuelga con asterisco.

    No intentes separar palabras pegadas con una regla de mayúsculas: rompe
    justamente los datos que importan —«1.5K» queda «1.5 · K», «MediaTek» queda
    «Media · Tek» y «200MP» queda «200 · MP»—.
    """
    filas = []
    for etiqueta, valor in (ficha.get("f") or {}).items():
        v = re.sub(r"\*.*$", "", limpiar(valor)).strip()
        if v and not FILAS_DE_CONFIGURACION.match(limpiar(etiqueta)):
            filas.append((limpiar(etiqueta), v[:300]))
    return filas


def aviso_icecat(fecha: str) -> str:
    """El aviso que exige la Open Content License de Icecat (v1.4, 11/02/2026).

    Cláusula 1: aviso de derechos literal y descargo de garantía, con la licencia
    a mano de quien recibe el contenido. Cláusula 2: nota visible de que se
    modificó, en qué y cuándo. El aviso de derechos va en inglés porque así lo
    fija la licencia.
    """
    return (f"Ficha técnica: Database Right data-sheet {fecha[:4]} Icecat. All rights reserved. "
            "Datos de Open Icecat (https://icecat.biz) bajo la Open Content License "
            "(https://iceclog.com/open-content-license-opl/), sin garantía de ningún tipo. "
            f"Modificado por TecnoSport el {fecha}: selección de atributos, traducción de rótulos "
            "y formato de la tabla.")


def meta_titulo(producto):
    t = producto.get("titulo_modelo") or producto["titulo"]
    return t if len(t) <= LIMITE_META_TITULO else t[:LIMITE_META_TITULO].rstrip(" ,·-")


def meta_descripcion(producto, prosa):
    base = prosa.get("meta") or prosa.get("apertura", "")
    base = re.sub(r"\s+", " ", base).strip()
    if len(base) <= LIMITE_META_DESCRIPCION:
        return base
    corte = base[:LIMITE_META_DESCRIPCION].rsplit(" ", 1)[0]
    return corte.rstrip(" ,.;:") + "."


def notas_obligatorias(producto):
    """
    Las declaraciones que `descripciones.md` no deja omitir, cuando el dato del
    producto las dispara.
    """
    # La RAM virtual y la SIM son de la configuración: van en la variante que el
    # cliente elige, no en la descripción del modelo.
    notas = []
    if producto.get("compatible_con"):
        notas.append(
            f"Es un accesorio de la marca {producto.get('marca') or 'indicada'}, "
            f"compatible con equipos {producto['compatible_con']}. No es un producto "
            f"original de {producto['compatible_con']}.")
    notas.append(
        "Producto nuevo, sellado y sin activar. Aplica la garantía legal que la ley "
        "colombiana reconoce para bienes nuevos; el plazo y el procedimiento son los "
        "de la política de garantías de la tienda.")
    return notas


def elegir_tabla(ficha_icecat, ficha_marca):
    """(filas, origen): Icecat primero (22/10/2026); la marca solo si Icecat no dio filas.

    `origen` es «Open Icecat», «marca» o None.
    """
    filas = tabla_icecat(ficha_icecat) if ficha_icecat else []
    if filas:
        return filas, "Open Icecat"
    filas = tabla_mi(ficha_marca) if ficha_marca else []
    return filas, ("marca" if filas else None)


def texto_de_tabla(filas):
    return "\n".join(["| Atributo | Detalle |", "|---|---|"] + [f"| {a} | {b} |" for a, b in filas])


def linea_de_fuente(origen, ficha_marca, fecha):
    """La línea del pie que atribuye la ficha técnica."""
    if origen == "Open Icecat":
        return aviso_icecat(fecha)
    if origen == "marca":
        donde = ficha_marca.get("fuente") or "el sitio oficial del fabricante"
        cuando = ficha_marca.get("fecha")
        return f"Ficha técnica: {donde}" + (f", consultado el {cuando}." if cuando else ".")
    return None


def componer(producto, prosa, ficha_icecat, ficha_marca, fecha):
    partes = [prosa["apertura"].strip(), ""]

    vinetas = prosa.get("vinetas") or []
    if vinetas:
        partes += ["## Características principales", ""]
        partes += [f"- {v}" for v in vinetas] + [""]

    filas, origen = elegir_tabla(ficha_icecat, ficha_marca)
    if filas:
        partes += ["## Ficha técnica", "", texto_de_tabla(filas), ""]

    caja = prosa.get("caja") or []
    if caja:
        partes += ["## Contenido de la caja", ""]
        partes += [f"- {c}" for c in caja] + [""]

    partes += ["## Garantía y notas", ""]
    partes += [f"- {n}" for n in (prosa.get("notas") or []) + notas_obligatorias(producto)]
    partes += [""]

    fuente = linea_de_fuente(origen, ficha_marca, fecha)
    if fuente:
        partes += ["---", "", fuente]

    return "\n".join(partes).strip() + "\n"


def completar(descripcion, ficha_icecat, ficha_marca, caja, fecha):
    """(descripción, origen de la ficha, secciones agregadas) de una descripción heredada.

    Inserta en su sitio solo lo que le falta y se puede afirmar con la fuente: la ficha técnica
    —con el mismo orden de fuentes que `componer`— y el contenido de la caja. La apertura, las
    viñetas y las notas no se tocan: las escribió una persona en otra lista, y reescribirlas sin
    prosa nueva sería inventar.
    """
    faltan = descripciones.faltantes(descripcion)
    agregadas, origen = [], None
    if descripciones.FICHA in faltan:
        filas, origen = elegir_tabla(ficha_icecat, ficha_marca)
        if filas:
            descripcion = descripciones.insertar(descripcion, descripciones.FICHA, texto_de_tabla(filas),
                                                 linea_de_fuente(origen, ficha_marca, fecha))
            agregadas.append(descripciones.FICHA)
    if descripciones.CAJA in faltan and caja:
        pie = None
        if ficha_marca and ficha_marca.get("caja"):
            donde = ficha_marca.get("fuente") or "el sitio oficial del fabricante"
            cuando = ficha_marca.get("fecha")
            pie = f"Contenido de la caja: {donde}" + (f", consultado el {cuando}." if cuando else ".")
        descripcion = descripciones.insertar(descripcion, descripciones.CAJA,
                                             "\n".join(f"- {c}" for c in caja), pie)
        agregadas.append(descripciones.CAJA)
    return descripcion, (origen if descripciones.FICHA in agregadas else None), agregadas


def fuente_ficha(origen_ficha, ficha_marca, fecha):
    if origen_ficha == "marca":
        return {"fuente": ficha_marca.get("fuente"), "url": ficha_marca.get("url"), "fecha": ficha_marca.get("fecha")}
    if origen_ficha == "icecat":
        return {"fuente": "Open Icecat", "url": "https://icecat.biz", "fecha": fecha}
    return None


def llevar_paletas(productos, marca) -> int:
    """La paleta de `fichas-marca.json` a `colores_oficiales` de los modelos que no la tienen.

    Es la paleta que publica la marca: la que la base guarda del modelo y la que el panel ofrece.
    Antes se escribía a mano en el paso 4; el 22/10/2026 hubo que hacerlo en cuatro modelos con un
    script suelto. No pisa una paleta que ya existe. Y si la lista no marca colores, deja dicho que
    se sugiere la paleta completa (regla 13): `exportar_lista.py` la manda así al panel.
    """
    llevadas = 0
    for p in productos:
        entrada = marca.get(p.get("id_modelo") or p["id"]) or {}
        if entrada.get("paleta") and not p.get("colores_oficiales"):
            p["colores_oficiales"] = list(entrada["paleta"])
            donde = entrada.get("fuente") or "el sitio oficial del fabricante"
            nota = f"colores: la paleta que publica {donde}" + (f" ({entrada['fecha']})" if entrada.get("fecha") else "")
            p["supuestos"] = list(dict.fromkeys((p.get("supuestos") or []) + [nota]))
            llevadas += 1
        if p.get("colores_oficiales") and not p.get("colores_familia"):
            lista = p.setdefault("supuestos_lista", [])
            if SUPUESTO_PALETA_COMPLETA not in lista:
                lista.append(SUPUESTO_PALETA_COMPLETA)
            if SUPUESTO_PALETA_COMPLETA not in (p.get("supuestos") or []):
                p["supuestos"] = (p.get("supuestos") or []) + [SUPUESTO_PALETA_COMPLETA]
    return llevadas


def leer_icecat(dir_ice, ids):
    for i in ids:
        ruta = dir_ice / f"{i}.json"
        if ruta.is_file():
            return json.loads(ruta.read_text(encoding="utf-8"))
    return None


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("productos")
    ap.add_argument("--prosa", required=True, help="JSON de prosa por id de modelo")
    ap.add_argument("--marca", help="JSON de fichas del sitio oficial de la marca, por id de modelo")
    ap.add_argument("--icecat", help="carpeta de fichas de icecat_local.py (primera opción)")
    ap.add_argument("--mi", help="(compatibilidad) JSON de fichas de mi.com, por la clave `mi` de la prosa")
    ap.add_argument("--fecha", default=date.today().isoformat(), help="fecha de la composición")
    a = ap.parse_args()

    datos = json.loads(Path(a.productos).read_text(encoding="utf-8"))
    prosas = json.loads(Path(a.prosa).read_text(encoding="utf-8"))
    dir_ice = Path(a.icecat) if a.icecat else None
    marca = json.loads(Path(a.marca).read_text(encoding="utf-8")) if a.marca else {}
    mi = json.loads(Path(a.mi).read_text(encoding="utf-8")) if a.mi else {}

    # Una descripción por modelo, para todos sus productos. Solo los modelos que
    # la tienen pendiente: la de un conocido la copió comparar_lista.py de la base.
    por_modelo = {}
    for p in datos["productos"]:
        if necesita(p, "descripcion"):
            por_modelo.setdefault(p.get("id_modelo") or p["id"], []).append(p)

    hechas, sin_prosa = 0, []
    for mid, grupo in por_modelo.items():
        ids = [mid] + [p["id"] for p in grupo]
        prosa = next((prosas[i] for i in ids if prosas.get(i, {}).get("apertura")), None)
        if not prosa:
            sin_prosa.extend(p["titulo"] for p in grupo)
            continue
        ficha_marca = marca.get(mid) or mi.get(prosa.get("mi") or "")
        # `"icecat": false` descarta la ficha entera. El indice mezcla productos
        # que comparten nombre: la del `Honor Pad X8b` (tablet de 11") es la del
        # `Honor X8b`, que es un celular de 6,7", y la del bundle de Switch 2 es
        # la del juego Mario Kart World, que pesa 10 gramos. Si la ficha
        # contradice la categoria del producto, no se usa ni un dato de ella.
        ficha_ice = leer_icecat(dir_ice, ids) if dir_ice and prosa.get("icecat") is not False else None
        referencia = grupo[0]
        descripcion = componer(referencia, prosa, ficha_ice, ficha_marca, a.fecha)
        _, origen = elegir_tabla(ficha_ice, ficha_marca)
        fuente = fuente_ficha({"Open Icecat": "icecat"}.get(origen, origen), ficha_marca or {}, a.fecha)
        for p in grupo:
            p["descripcion"] = descripcion
            p["meta_titulo"] = meta_titulo(p)
            p["meta_descripcion"] = meta_descripcion(p, prosa)
            p["fuentes_ficha"] = [fuente] if fuente else []
            p["revisar"] = [r for r in p["revisar"] if "sin descripcion" not in r]
        hechas += 1

    paletas = llevar_paletas(datos["productos"], marca)

    # Las heredadas que comparar_lista.py dejó por completar: solo las secciones que faltan.
    por_completar = {}
    for p in datos["productos"]:
        if necesita(p, COMPLETAR) and not necesita(p, "descripcion") and p.get("descripcion"):
            por_completar.setdefault(p.get("id_modelo") or p["id"], []).append(p)
    completadas, sin_fuente = 0, []
    for mid, grupo in por_completar.items():
        ids = [mid] + [p["id"] for p in grupo]
        prosa = next((prosas[i] for i in ids if i in prosas), {})
        ficha_marca = marca.get(mid) or {}
        ficha_ice = leer_icecat(dir_ice, ids) if dir_ice and prosa.get("icecat") is not False else None
        caja = ficha_marca.get("caja") or prosa.get("caja") or []
        texto, origen, agregadas = completar(grupo[0]["descripcion"], ficha_ice, ficha_marca, caja, a.fecha)
        if not agregadas:
            sin_fuente.append(grupo[0].get("titulo_modelo") or grupo[0]["titulo"])
            continue
        fuente = fuente_ficha({"Open Icecat": "icecat"}.get(origen, origen), ficha_marca, a.fecha)
        for p in grupo:
            p["descripcion"] = texto
            if fuente and fuente not in (p.get("fuentes_ficha") or []):
                p["fuentes_ficha"] = list(p.get("fuentes_ficha") or []) + [fuente]
            p["revisar"] = [r for r in p["revisar"] if not r.startswith(ALERTA_DESCRIPCION)]
            p["revisar"] += [f"{ALERTA_DESCRIPCION} {d}" for d in descripciones.defectos(texto)]
        completadas += 1

    for p in datos["productos"]:
        if p["titulo"] in sin_prosa and "sin descripcion" not in " ".join(p["revisar"]):
            p["revisar"].append(
                "sin descripcion: falta la ficha oficial del fabricante, asi que no "
                "hay de donde sacar los datos sin inventarlos")

    Path(a.productos).write_text(json.dumps(datos, ensure_ascii=False, indent=2),
                                 encoding="utf-8")
    print(f"{hechas} modelos con descripción | {len(sin_prosa)} productos sin prosa autorada")
    if paletas:
        print(f"{paletas} productos con la paleta de fichas-marca.json")
    if por_completar:
        print(f"{completadas} descripciones heredadas completadas | {len(sin_fuente)} sin ficha ni caja con qué "
              "completarlas")
        for t in sin_fuente:
            print("   sin fuente para completar:", t)
    largos = [p["titulo"] for p in datos["productos"]
              if len(p.get("meta_titulo") or "") > LIMITE_META_TITULO
              or len(p.get("meta_descripcion") or "") > LIMITE_META_DESCRIPCION]
    if largos:
        print("metadatos fuera de limite:", largos)
    for t in sin_prosa:
        print("   sin prosa:", t)


if __name__ == "__main__":
    main()
