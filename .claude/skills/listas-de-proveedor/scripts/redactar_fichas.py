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

La ficha técnica sale, en este orden (decisión del 22/10/2026, que invierte la
del 08/10/2026):

1. **Open Icecat**, en `--icecat`, y solo por código: la tabla la arma este
   script y la prosa no se escribe nunca a partir de ella. La licencia anula el
   permiso si los datos se usan para «automated synthetic content creation»
   (cláusula 10), y exige el aviso de derechos, el descargo y la nota de lo que
   se modificó (cláusulas 1 y 2). **Desde el 23/10/2026 ese aviso no se publica**:
   la descripción no dice de dónde salió un dato, por decisión del negocio, y el
   riesgo de la licencia está en `docs/14-consultas-al-abogado.md`. La fuente de
   cada sección queda en `fuentes_ficha`, que no se publica.
2. **El sitio oficial de la marca**, en `--marca`: un JSON por id de modelo con
   la fuente, la URL, la fecha de consulta, las filas y, si los da, la caja y la
   paleta. Va si Icecat no dio filas, **y también si las dos se contradicen** en la
   resistencia, el cargador incluido o el Bluetooth (`cotejar`): la de Icecat puede
   ser de otro mercado, como la del Moto G15 el 22/10/2026.

Y completa las descripciones heredadas que la comparación dejó con la tarea
`completar`: solo la ficha y la caja que les faltan (`completar`).

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
AVISO_COTEJO = "la ficha de Icecat no coincide con la de la marca"

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


RE_IP = re.compile(r"\bIP\s?(\d{2}K?)\b", re.I)
RE_BLUETOOTH = re.compile(r"Bluetooth\s*[®™]?\s*v?(\d+(?:\.\d+)?)", re.I)
# Solo un cargador afirmado: «Adaptador USB-C a 3,5 mm», «Adaptador de SIM» o «Cargador no
# incluido» no son un cargador en la caja (revisión del PR #268).
RE_CARGADOR = re.compile(r"\bcargador\b|\badaptador (?:de corriente|de pared|de carga|AC)\b", re.I)
RE_NO_INCLUIDO = re.compile(r"\bno\s+(?:se\s+)?inclu", re.I)


def _version(v):
    """«5» y «5.0» son la misma versión de Bluetooth."""
    return v and str(float(v))


def _trae_cargador(caja):
    return any(RE_CARGADOR.search(c) and not RE_NO_INCLUIDO.search(c) for c in caja)


def _datos_de_icecat(ficha):
    por = {limpiar(e["atributo"]): limpiar(e["valor"]) for e in (ficha or {}).get("especificaciones") or []}
    ip = RE_IP.search(por.get("Código IP (International Protection)", ""))
    bt = re.match(r"v?(\d+(?:\.\d+)?)", por.get("Versión de Bluetooth", ""))
    cargador = por.get("Adaptador AC incluido")
    return {"IP": ip and f"IP{ip.group(1).upper()}",
            "Bluetooth": bt and _version(bt.group(1)),
            "cargador incluido": {"si": "sí", "sí": "sí", "no": "no"}.get((cargador or "").lower())}


def _datos_de_marca(ficha, caja):
    texto = " · ".join(limpiar(v) for v in ((ficha or {}).get("f") or {}).values())
    ips = sorted({f"IP{m.upper()}" for m in RE_IP.findall(texto)})
    bt = RE_BLUETOOTH.search(texto)
    # La caja que publica la marca manda; la de la prosa, si la marca no la da.
    caja = list((ficha or {}).get("caja") or caja or [])
    return {"IP": ips, "Bluetooth": bt and _version(bt.group(1)),
            "cargador incluido": (("sí" if _trae_cargador(caja) else "no") if caja else None)}


def cotejar(ficha_icecat, ficha_marca, caja=None) -> list[str]:
    """Lo que la ficha de Icecat dice distinto que la de la marca: resistencia, cargador y Bluetooth.

    El 22/10/2026 la ficha de Icecat del Moto G15 era la de otro mercado —IP54 contra el IP52 de
    Motorola Colombia, «adaptador incluido: No» cuando la caja trae cargador, Bluetooth 5.0— y
    con Icecat primero se habría publicado una descripción que se contradice. Solo compara lo que
    las dos fichas dicen.
    """
    if not ficha_icecat or not ficha_marca:
        return []
    ice, marca = _datos_de_icecat(ficha_icecat), _datos_de_marca(ficha_marca, caja)
    difieren = []
    if ice["IP"] and marca["IP"] and ice["IP"] not in marca["IP"]:
        difieren.append(f"resistencia {ice['IP']} contra {', '.join(marca['IP'])}")
    for dato in ("Bluetooth", "cargador incluido"):
        if ice[dato] and marca[dato] and ice[dato] != marca[dato]:
            difieren.append(f"{dato} {ice[dato]} contra {marca[dato]}")
    return difieren


def elegir_tabla(ficha_icecat, ficha_marca, caja=None):
    """(filas, origen, avisos): Icecat primero (22/10/2026); la marca si Icecat no dio filas.

    Si las dos existen y se contradicen (`cotejar`), manda la de la marca —es la del mercado que
    se vende— y el aviso dice qué difería. `origen` es «Open Icecat», «marca» o None.
    """
    filas = tabla_icecat(ficha_icecat) if ficha_icecat else []
    difieren = cotejar(ficha_icecat, ficha_marca, caja) if filas else []
    filas_marca = tabla_mi(ficha_marca) if ficha_marca else []
    if filas and not (difieren and filas_marca):
        return filas, "Open Icecat", []
    if filas_marca:
        avisos = [f"{AVISO_COTEJO} ({'; '.join(difieren)}): se publicó la de la marca; la de Icecat puede ser "
                  "de otro mercado"] if difieren else []
        return filas_marca, "marca", avisos
    return [], None, []


def _celda(texto):
    """El texto de una celda sin barras: cada una abriría otra columna en la ficha del sitio.

    Icecat escribe «GPS | Galileo | Glonass» dentro del valor de Conectividad, y así llegaron cuatro
    relojes al 23/10/2026 con cinco columnas bajo un encabezado de dos.
    """
    return re.sub(r"\s*\|\s*", ", ", str(texto))


def texto_de_tabla(filas):
    return "\n".join(["| Atributo | Detalle |", "|---|---|"]
                     + [f"| {_celda(a)} | {_celda(b)} |" for a, b in filas])


def componer(producto, prosa, ficha_icecat, ficha_marca, fecha):
    partes = [prosa["apertura"].strip(), ""]

    vinetas = prosa.get("vinetas") or []
    if vinetas:
        partes += ["## Características principales", ""]
        partes += [f"- {v}" for v in vinetas] + [""]

    filas, origen, _ = elegir_tabla(ficha_icecat, ficha_marca, prosa.get("caja"))
    if filas:
        partes += ["## Ficha técnica", "", texto_de_tabla(filas), ""]

    caja = prosa.get("caja") or []
    if caja:
        partes += ["## Contenido de la caja", ""]
        partes += [f"- {c}" for c in caja] + [""]

    partes += ["## Garantía y notas", ""]
    partes += [f"- {n}" for n in (prosa.get("notas") or []) + notas_obligatorias(producto)]
    partes += [""]

    return "\n".join(partes).strip() + "\n"


def completar(descripcion, ficha_icecat, ficha_marca, caja, fecha):
    """(descripción, origen de la ficha, secciones agregadas, avisos del cotejo) de una heredada.

    Inserta en su sitio solo lo que le falta y se puede afirmar con la fuente: la ficha técnica
    —con el mismo orden de fuentes que `componer`— y el contenido de la caja. La apertura, las
    viñetas y las notas no se tocan: las escribió una persona en otra lista, y reescribirlas sin
    prosa nueva sería inventar.
    """
    faltan = descripciones.faltantes(descripcion)
    agregadas, origen, avisos = [], None, []
    if descripciones.FICHA in faltan:
        filas, origen, avisos = elegir_tabla(ficha_icecat, ficha_marca, caja)
        if filas:
            descripcion = descripciones.insertar(descripcion, descripciones.FICHA, texto_de_tabla(filas))
            agregadas.append(descripciones.FICHA)
    if descripciones.CAJA in faltan and caja:
        descripcion = descripciones.insertar(descripcion, descripciones.CAJA,
                                             "\n".join(f"- {c}" for c in caja))
        agregadas.append(descripciones.CAJA)
    return descripcion, (origen if descripciones.FICHA in agregadas else None), agregadas, avisos


def _quitar_pendientes(grupo, faltan) -> None:
    """Saca `completar` —y `ficha`, si ya hay ficha— del pendiente de lo que ya no le falta nada de
    eso: una segunda corrida lo reportaba «sin fuente para completar» y icecat_local lo volvía a
    buscar (revisión del PR #268)."""
    for p in grupo:
        pendiente = list(p.get("pendiente") or [])
        if not faltan and COMPLETAR in pendiente:
            pendiente.remove(COMPLETAR)
        if descripciones.FICHA not in faltan and "ficha" in pendiente and p.get("descripcion"):
            pendiente.remove("ficha")
        p["pendiente"] = pendiente


def fuente_caja(ficha_marca):
    """La fuente del contenido de la caja, para `fuentes_ficha`; None si no salió de la marca.

    La caja puede venir de otra página que la ficha —el 22/10/2026 la del Watch8 salió de
    samsung.com/es porque la colombiana no la publica—: entonces lleva su propia fuente.
    """
    if not (ficha_marca and ficha_marca.get("caja")):
        return None
    return {"seccion": "caja",
            "fuente": ficha_marca.get("fuente_caja") or ficha_marca.get("fuente")
            or "el sitio oficial del fabricante",
            "url": ficha_marca.get("url_caja") or ficha_marca.get("url"),
            "fecha": ficha_marca.get("fecha_caja") or ficha_marca.get("fecha")}


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
        _, origen, avisos = elegir_tabla(ficha_ice, ficha_marca, prosa.get("caja"))
        fuente = fuente_ficha({"Open Icecat": "icecat"}.get(origen, origen), ficha_marca or {}, a.fecha)
        for p in grupo:
            p["revisar"] = [r for r in p["revisar"] if not r.startswith(AVISO_COTEJO)] + avisos
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
        if not descripciones.faltantes(grupo[0]["descripcion"]):
            # Ya completa —una corrida anterior la completó—: no hay nada que buscar (revisión del PR #268).
            _quitar_pendientes(grupo, descripciones.faltantes(grupo[0]["descripcion"]))
            continue
        texto, origen, agregadas, avisos = completar(grupo[0]["descripcion"], ficha_ice, ficha_marca, caja, a.fecha)
        if not agregadas:
            sin_fuente.append(grupo[0].get("titulo_modelo") or grupo[0]["titulo"])
            continue
        fuentes = [fuente_ficha({"Open Icecat": "icecat"}.get(origen, origen), ficha_marca, a.fecha),
                   fuente_caja(ficha_marca) if descripciones.CAJA in agregadas else None]
        for p in grupo:
            p["descripcion"] = texto
            for fuente in fuentes:
                if fuente and fuente not in (p.get("fuentes_ficha") or []):
                    p["fuentes_ficha"] = list(p.get("fuentes_ficha") or []) + [fuente]
            p["revisar"] = [r for r in p["revisar"] if not r.startswith((ALERTA_DESCRIPCION, AVISO_COTEJO))]
            p["revisar"] += [f"{ALERTA_DESCRIPCION} {d}" for d in descripciones.defectos(texto)] + avisos
        _quitar_pendientes(grupo, descripciones.faltantes(texto))
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
