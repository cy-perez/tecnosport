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
  no está, un marcador que alguien dejó. Esos se informan; el marcador y la frase
  que nombra una fuente dejan al modelo fuera de la exportación (`BLOQUEAN`): la
  plantilla pintaría el primero tal cual (`ADR-0025`), y la segunda rompe la regla
  del 23/10/2026.

**La descripción no dice de dónde salió un dato** (decisión del negocio del
23/10/2026). Ni el pie de fuentes tras `---` ni notas como «La tabla técnica sale
de Open Icecat…» o «según la ficha de JBL». Las fuentes se guardan en
`fuentes_ficha` del modelo, que no se publica. Eso incluye el aviso de Open
Icecat aunque su licencia lo exija (cláusulas 1 y 2): el riesgo lo asumió el
negocio y está en `docs/14-consultas-al-abogado.md`.
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
# «de N GB» solo fija la configuración si no es la RAM ni una tarjeta: «Memoria RAM de 8 GB»,
# «microSD de 1 TB», «la versión de 4 GB de RAM» o «de hasta 2 TB» son del modelo entero o de lo que
# admite (revisión del PR #268).
RE_NO_ES_CONFIGURACION_ANTES = re.compile(r"(?:RAM|microSD|micro SD|tarjetas?|expandible|ampliable)\s*$", re.I)
RE_NO_ES_CONFIGURACION_DESPUES = re.compile(r"^\s*(?:de\s+)?RAM\b", re.I)

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
SIN_PLAZO_DE_GARANTIA = (f"la última nota de «{GARANTIA}» no dice el plazo de garantía "
                         "(«Un año de garantía.» o «6 meses de garantía.»)")
# La última viñeta de «Garantía y notas» desde el 27/10/2026: la escribe `redactar_fichas.nota_de_garantia`.
RE_NOTA_DE_GARANTIA = re.compile(r"^- (?:Un año|1 mes|(?:[2-9]|1[01]) meses) de garantía\.$")
NOMBRA_CONFIGURACION = "nombra una configuración"
HABLA_DE_LA_FUENTE = "nombra de dónde salió un dato"

# Una frase de la prosa que cita su fuente. Sin `re.I` a propósito: «Ficha de JBL» es una cita y
# «ficha de la» no. «ficha técnica» tampoco cuenta: «La ficha técnica completa se le pidió al
# proveedor» es una nota de dato pendiente (`notas_pendientes`), y esa sí se publica. Y «según
# Apple» o «según el fabricante» tampoco: dicen quién declara una cifra —la autonomía es la que
# declara el fabricante, no una promesa de la tienda—, no qué página se consultó.
RE_FUENTE = re.compile(
    r"Icecat|[Tt]abla técnica|\bficha (?:oficial|del fabricante|de [A-ZÁÉÍÓÚ])"
    r"|\b[Ss]egún (?:la|el) (?:ficha|sitio|página|proveedor|línea del proveedor)"
    r"|\bconsultad[oa] el\b|\bmi\.com\b|[Pp]ágina oficial")
FORMATO_NO_SE_PINTA = "usa un formato que la ficha del sitio no pinta"

# La ficha del sitio pinta párrafos, subtítulos «## », viñetas «- » y tablas de dos columnas
# (`apps/web/src/app/shared/ui/descripcion/leer-descripcion.ts`) —el pie tras «---» lo descarta—, y nada
# más: no hay librería de Markdown detrás. Lo demás sale tal cual, con los asteriscos a la vista.
FORMATOS_QUE_NO_SE_PINTAN = (
    (re.compile(r"^(?:#|#{3,6}) ", re.M), "un subtítulo que no es «## »"),
    (re.compile(r"\*\*|__"), "negrita con ** o __"),
    (re.compile(r"^\d+[.)] ", re.M), "una lista numerada"),
    (re.compile(r"^> ", re.M), "una cita con >"),
    (re.compile(r"\[[^\]\n]+\]\([^)\n]+\)"), "un enlace [texto](dirección)"),
)

# Lo que deja al modelo fuera de la exportación. El resto se informa y sigue.
BLOQUEAN = (MARCADOR, HABLA_DE_LA_FUENTE)


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


def _sin_vinetas_de_icecat(cuerpo: str) -> str:
    """El cuerpo de una sección sin las viñetas que nombran Icecat: hablan de la fuente y nada más
    —«La tabla técnica sale de Open Icecat y describe una versión regional…»—. Una que cita otra
    fuente lleva casi siempre un dato del producto, y esa la reescribe una persona (`defectos`)."""
    return "\n".join(l for l in cuerpo.split("\n")
                     if not (l.lstrip().startswith("- ") and "Icecat" in l))


def sanear(texto: str) -> str:
    """La descripción sin pie de fuentes, sin las viñetas sobre Icecat y sin secciones vacías.

    El pie y esas viñetas se van siempre (decisión del 23/10/2026: la ficha no dice de dónde
    salió un dato); las fuentes se guardan en `fuentes_ficha`. Una sección que se queda sin
    contenido se va también: publicarla vacía nunca está bien.
    """
    if not texto:
        return texto
    inicio, secciones, pie = _partes(texto)
    limpias = [(t, c if t == FICHA else _sin_vinetas_de_icecat(c)) for t, c in secciones]
    quedan = [(t, c) for t, c in limpias if not _vacia(t, c)]
    if quedan == secciones and not pie and "\n---\n" not in texto:
        return texto
    partes = [inicio.rstrip("\n")] + [f"## {t}{c.rstrip()}" for t, c in quedan]
    resultado = "\n\n".join(p for p in partes if p.strip())
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
    cita = frase_de_fuente(texto)
    if cita:
        hallados.append(f"{HABLA_DE_LA_FUENTE} («{cita}»): la ficha no dice de dónde salió un dato; "
                        "se reescribe con el dato solo y la fuente queda en fuentes_ficha")
    if GARANTIA not in por_titulo:
        hallados.append(SIN_GARANTIA)
    elif not ultima_nota_es_el_plazo(por_titulo[GARANTIA]):
        # No bloquea: el bloqueo saca al modelo entero de la lista para la API, y con él el costo y la
        # existencia de lo que ya se vende. Las publicadas antes del 27/10/2026 no lo dicen todavía.
        hallados.append(SIN_PLAZO_DE_GARANTIA)
    ficha = por_titulo.get(FICHA)
    if ficha is None or not _filas(ficha):
        hallados.append(SIN_FICHA)
    elif not any(RE_PESO_O_MEDIDAS.search(f) for f in _filas(ficha)):
        hallados.append(SIN_PESO)
    if CAJA not in por_titulo or not por_titulo[CAJA].strip():
        hallados.append(SIN_CAJA)
    formatos = formatos_que_no_se_pintan(texto)
    if formatos:
        hallados.append(f"{FORMATO_NO_SE_PINTA}: {'; '.join(formatos)}")
    frase = frase_de_configuracion(texto)
    if frase:
        hallados.append(f"{NOMBRA_CONFIGURACION} («{frase}»): la descripción es del modelo y vale para "
                        "todas sus memorias")
    return hallados


def ultima_nota_es_el_plazo(cuerpo: str) -> bool:
    vinetas = [l.strip() for l in cuerpo.splitlines() if l.strip().startswith("- ")]
    return bool(vinetas) and bool(RE_NOTA_DE_GARANTIA.match(vinetas[-1]))


def formatos_que_no_se_pintan(texto: str) -> list[str]:
    """Lo que la descripción escribe y la ficha del sitio mostraría con su sintaxis a la vista."""
    if not texto:
        return []
    hallados = [nombre for patron, nombre in FORMATOS_QUE_NO_SE_PINTAN if patron.search(texto)]
    for linea in texto.splitlines():
        linea = linea.strip()
        if RE_FILA_DE_TABLA.match(linea) and not RE_SEPARADOR_DE_TABLA.match(linea):
            celdas = re.split(r"(?<!\\)\|", linea[1:-1])
            if len(celdas) > 2:
                hallados.append(f"una barra dentro de una celda («{linea[:60]}»)")
                break
    return hallados


def _prosa(texto: str) -> str:
    """La prosa de la descripción: sin la tabla técnica, sin los subtítulos y sin el pie."""
    inicio, secciones, _ = _partes(texto)
    partes = [inicio] + [c for t, c in secciones if t != FICHA]
    return "\n".join(l for parte in partes for l in parte.splitlines() if not l.strip().startswith("|"))


def frase_de_fuente(texto: str):
    """La primera línea de la prosa que nombra de dónde salió un dato, o None."""
    if not texto:
        return None
    for linea in _prosa(texto).splitlines():
        if RE_FUENTE.search(linea):
            return linea.strip().lstrip("- ").strip()[:80]
    return None


def frase_de_configuracion(texto: str):
    """La primera frase de la prosa que nombra una configuración, o None.

    Se busca fuera de la tabla y del pie: la ficha técnica sí puede decir «microSD de hasta 1 TB».
    """
    if not texto:
        return None
    inicio, secciones, _ = _partes(texto)
    prosa = [inicio] + [c for t, c in secciones if t != FICHA]
    lineas = [l for parte in prosa for l in parte.splitlines() if not l.strip().startswith("|")]
    prosa_plana = "\n".join(lineas)
    for m in RE_CONFIGURACION.finditer(prosa_plana):
        if m.group(0).lower().startswith("de "):
            antes = prosa_plana[max(0, m.start() - 20):m.start()]
            despues = prosa_plana[m.end():m.end() + 12]
            if RE_NO_ES_CONFIGURACION_ANTES.search(antes) or RE_NO_ES_CONFIGURACION_DESPUES.match(despues):
                continue
        return m.group(0).strip()[:80]
    return None


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


def insertar(texto: str, titulo: str, cuerpo: str) -> str:
    """La descripción con la sección `titulo` en su sitio del ORDEN.

    No toca la apertura, las viñetas ni las demás secciones. Si la sección ya existía vacía, se
    reemplaza. Un pie de fuentes que traiga se va: desde el 23/10/2026 no se publica.
    """
    inicio, secciones, _ = _partes(texto)
    secciones = [(t, c) for t, c in secciones if t != titulo]
    nueva = (titulo, "\n\n" + cuerpo.strip("\n") + "\n\n")
    posicion = ORDEN.index(titulo) if titulo in ORDEN else len(ORDEN)
    i = next((k for k, (t, _) in enumerate(secciones)
              if (ORDEN.index(t) if t in ORDEN else len(ORDEN)) > posicion), len(secciones))
    secciones.insert(i, nueva)
    partes = [inicio.rstrip("\n")] + [f"## {t}\n\n{c.strip(chr(10))}" for t, c in secciones]
    return "\n\n".join(p for p in partes if p.strip()) + "\n"


def bloqueantes(texto: str) -> list[str]:
    return [d for d in defectos(texto) if es_bloqueante(d)]


def es_bloqueante(defecto: str) -> bool:
    """Si el defecto deja al modelo fuera de la exportación. Por prefijo: el de la fuente lleva la
    frase que lo disparó."""
    return any(defecto.startswith(b) for b in BLOQUEAN)


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
