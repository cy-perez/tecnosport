"""Lo que una descripción ya escrita tiene que cumplir antes de volver a publicarse.

`redactar_fichas.py` arma la descripción de un modelo nuevo y nunca escribe una
sección vacía. Pero un modelo conocido no pasa por ahí: `comparar_lista.py` copia
la de `referencias/conocidos.json`, y `conocidos.descripcion_de_modelo` le quita
antes lo que depende de la configuración —la fila «Memoria», entre otras—. Si esa
era la única fila de la tabla, queda el encabezado solo, y así se guardaba y se
volvía a publicar en cada lista: el 21/10/2026 había 15 en la base, y 11 de ellas
iban en los borradores de la lista del 15/10.

Dos cosas distintas, a propósito:

- `sanear` quita una sección que quedó sin contenido. Publicarla vacía nunca está
  bien, y quitarla no inventa nada, así que se hace sola.
- `defectos` dice lo que no se arregla sin datos: una ficha que falta, un peso que
  no está, un marcador que alguien dejó. Esos se informan; solo el marcador deja
  al modelo fuera de la exportación (`BLOQUEAN`), porque la plantilla lo pintaría
  tal cual (`ADR-0025`).
"""

import re

RE_ENCABEZADO = re.compile(r"^## (.+)$", re.M)
RE_FILA_DE_TABLA = re.compile(r"^\|(.+)\|\s*$")
RE_SEPARADOR_DE_TABLA = re.compile(r"^\|(\s*:?-+:?\s*\|)+\s*$")
RE_MARCADOR = re.compile(r"\[\[|\bTODO\b")
RE_PESO_O_MEDIDAS = re.compile(r"peso|dimensiones|medidas", re.I)
# Las frases con que una descripción avisa un dato pendiente del proveedor. Acotadas a propósito:
# «pendiente» a secas marcaba el «posicionamiento GNSS independiente» de la Smart Band 10 Pro.
RE_NOTA_PENDIENTE = re.compile(
    r"se le pidió al proveedor|por confirmar|queda(?:n)? pendientes?\b|no se afirma(?:n)? aquí", re.I)

# La descripción es del modelo y vale para todas sus memorias: una frase que nombra una
# configuración —«Esta versión trae 6GB de RAM física y 256GB de almacenamiento»— es falsa para
# las demás. El 22/10/2026 el Redmi 17 decía 6GB también en su versión de 4GB, y nada lo avisó
# porque la alerta solo saltaba cuando llegaba una memoria nueva. La RAM sola no cuenta: casi
# siempre es del modelo entero (el Moto G15 trae 4 GB en las tres memorias).
RE_CONFIGURACION = re.compile(
    r"Esta versi[oó]n trae[^.\n]*|\d+\s?(?:GB|TB) de almacenamiento|\bde \d+\s?(?:GB|TB)\b", re.I)

CARACTERISTICAS = "Características principales"
FICHA = "Ficha técnica"
GARANTIA = "Garantía y notas"
CAJA = "Contenido de la caja"
# El orden de las secciones de `referencias/descripciones.md`: una sección que se completa
# entra en su sitio, no al final.
ORDEN = (CARACTERISTICAS, FICHA, CAJA, GARANTIA)

MARCADOR = "tiene un marcador [[ ]] o un TODO: se publicaría tal cual"
SIN_GARANTIA = f"le falta la sección «{GARANTIA}», con las declaraciones que no se pueden omitir"
SIN_FICHA = f"no tiene «{FICHA}»: falta la ficha oficial del fabricante"
SIN_PESO = "la ficha técnica no dice peso ni dimensiones"
SIN_CAJA = f"no tiene «{CAJA}»"
NOMBRA_CONFIGURACION = "nombra una configuración"

# Lo que deja al modelo fuera de la exportación. El resto se informa y sigue.
BLOQUEAN = (MARCADOR,)


def _partes(texto: str) -> tuple[str, list[tuple[str, str]], str]:
    """(lo de antes del primer encabezado, [(título, cuerpo)], el pie de fuentes).

    El pie es lo que va después de la última línea `---`: las fuentes de la ficha.
    """
    cuerpo, pie = texto, ""
    corte = texto.rfind("\n---\n")
    if corte >= 0:
        cuerpo, pie = texto[:corte], texto[corte + len("\n---\n"):]
    encabezados = list(RE_ENCABEZADO.finditer(cuerpo))
    if not encabezados:
        return cuerpo, [], pie
    inicio = cuerpo[:encabezados[0].start()]
    secciones = []
    for i, m in enumerate(encabezados):
        fin = encabezados[i + 1].start() if i + 1 < len(encabezados) else len(cuerpo)
        secciones.append((m.group(1).strip(), cuerpo[m.end():fin]))
    return inicio, secciones, pie


def _filas(cuerpo: str) -> list[str]:
    """Las filas de datos de la tabla de una sección: sin el encabezado ni el separador."""
    lineas = [l.strip() for l in cuerpo.splitlines() if RE_FILA_DE_TABLA.match(l.strip())]
    datos = [l for l in lineas if not RE_SEPARADOR_DE_TABLA.match(l)]
    # La primera es el encabezado de la tabla («| Atributo | Detalle |») si va antes del separador.
    if any(RE_SEPARADOR_DE_TABLA.match(l) for l in lineas) and datos:
        datos = datos[1:]
    return datos


def _vacia(titulo: str, cuerpo: str) -> bool:
    if titulo == FICHA:
        return not _filas(cuerpo)
    return not cuerpo.strip()


def sanear(texto: str) -> str:
    """La descripción sin las secciones que quedaron vacías.

    Si la que se va es la ficha técnica, se va también la línea de su fuente en el
    pie —«Ficha técnica: …»—: atribuir datos que no se publican es decir algo falso.
    """
    if not texto:
        return texto
    inicio, secciones, pie = _partes(texto)
    quedan = [(t, c) for t, c in secciones if not _vacia(t, c)]
    if len(quedan) == len(secciones):
        return texto
    lineas_pie = pie.splitlines()
    if not any(t == FICHA for t, _ in quedan):
        lineas_pie = [l for l in lineas_pie if not l.startswith(f"{FICHA}:")]
    partes = [inicio.rstrip("\n")] + [f"## {t}{c.rstrip()}" for t, c in quedan]
    resultado = "\n\n".join(p for p in partes if p.strip())
    if any(l.strip() for l in lineas_pie):
        resultado += "\n\n---\n" + "\n".join(lineas_pie).rstrip()
    return resultado.rstrip("\n") + "\n"


def defectos(texto: str) -> list[str]:
    """Lo que la descripción no cumple y no se arregla sin datos, en el orden en que importa."""
    if not texto or not texto.strip():
        return []
    _, secciones, _ = _partes(texto)
    por_titulo = dict(secciones)
    hallados = []
    if RE_MARCADOR.search(texto):
        hallados.append(MARCADOR)
    if GARANTIA not in por_titulo:
        hallados.append(SIN_GARANTIA)
    ficha = por_titulo.get(FICHA)
    if ficha is None or not _filas(ficha):
        hallados.append(SIN_FICHA)
    elif not any(RE_PESO_O_MEDIDAS.search(f) for f in _filas(ficha)):
        hallados.append(SIN_PESO)
    if CAJA not in por_titulo or not por_titulo[CAJA].strip():
        hallados.append(SIN_CAJA)
    frase = frase_de_configuracion(texto)
    if frase:
        hallados.append(f"{NOMBRA_CONFIGURACION} («{frase}»): la descripción es del modelo y vale para "
                        "todas sus memorias")
    return hallados


def frase_de_configuracion(texto: str):
    """La primera frase de la prosa que nombra una configuración, o None.

    Se busca fuera de la tabla y del pie: la ficha técnica sí puede decir «microSD de hasta 1 TB».
    """
    if not texto:
        return None
    inicio, secciones, _ = _partes(texto)
    prosa = [inicio] + [c for t, c in secciones if t != FICHA]
    lineas = [l for parte in prosa for l in parte.splitlines() if not l.strip().startswith("|")]
    m = RE_CONFIGURACION.search("\n".join(lineas))
    return m.group(0).strip()[:80] if m else None


def faltantes(texto: str) -> list[str]:
    """Las secciones que se pueden completar con datos de la marca o de Icecat: la ficha y la caja.

    Las demás faltas —la garantía, una frase de configuración, un marcador— no se arreglan
    insertando una sección: las corrige quien redacta.
    """
    if not texto or not texto.strip():
        return []
    _, secciones, _ = _partes(texto)
    por_titulo = dict(secciones)
    faltan = []
    if FICHA not in por_titulo or not _filas(por_titulo[FICHA]):
        faltan.append(FICHA)
    if CAJA not in por_titulo or not por_titulo[CAJA].strip():
        faltan.append(CAJA)
    return faltan


def insertar(texto: str, titulo: str, cuerpo: str, linea_de_pie: str | None = None) -> str:
    """La descripción con la sección `titulo` en su sitio del ORDEN, y su fuente al pie.

    No toca la apertura, las viñetas ni las demás secciones. Si la sección ya existía vacía, se
    reemplaza. La línea de pie va después de las que ya había.
    """
    inicio, secciones, pie = _partes(texto)
    secciones = [(t, c) for t, c in secciones if t != titulo]
    nueva = (titulo, "\n\n" + cuerpo.strip("\n") + "\n\n")
    posicion = ORDEN.index(titulo) if titulo in ORDEN else len(ORDEN)
    i = next((k for k, (t, _) in enumerate(secciones)
              if (ORDEN.index(t) if t in ORDEN else len(ORDEN)) > posicion), len(secciones))
    secciones.insert(i, nueva)
    lineas_pie = [l for l in pie.splitlines() if l.strip()]
    if linea_de_pie and linea_de_pie not in lineas_pie:
        lineas_pie.append(linea_de_pie)
    partes = [inicio.rstrip("\n")] + [f"## {t}\n\n{c.strip(chr(10))}" for t, c in secciones]
    resultado = "\n\n".join(p for p in partes if p.strip()) + "\n"
    if lineas_pie:
        resultado += "\n---\n\n" + "\n".join(lineas_pie) + "\n"
    return resultado


def bloqueantes(texto: str) -> list[str]:
    return [d for d in defectos(texto) if d in BLOQUEAN]


def notas_pendientes(texto: str) -> list[str]:
    """Las líneas de la descripción que avisan un dato pendiente del proveedor.

    Son honestas a propósito —«la ficha técnica completa se le pidió al proveedor»— y se publican
    así (un producto sin ficha oficial no se inventa una). Pero quien aprueba el borrador tiene que
    verlas: es justo el momento en que puede resolverlas, y `npm run marcadores` no las ve porque
    no son `[[ ]]`."""
    if not texto:
        return []
    return [linea.strip().lstrip("- ").strip()
            for linea in texto.splitlines() if RE_NOTA_PENDIENTE.search(linea)]
