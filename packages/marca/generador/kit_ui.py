#!/usr/bin/env python3
"""Genera el kit de interfaz a partir de tokens.json.

Uso:
    python3 kit_ui.py tokens.json --out kit --marca "Mi Marca" --logo logo.svg

Produce:
    kit/tokens.css   variables listas para el proyecto, con modo oscuro
    kit/index.html   guia visual completa: paleta, tipografia, componentes,
                     y maqueta de header, body y footer
    kit/contraste.md informe WCAG de los pares que se usan de verdad

Los estados (hover, pressed, disabled, foco) y el color de texto sobre cada
fondo se calculan aqui: son derivados, no decisiones sueltas, y calcularlos
evita que en la maqueta aparezca un boton con texto ilegible.
"""
import argparse, json, shutil, sys
from pathlib import Path

# ---------- color ----------

def rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i+2], 16) for i in (0, 2, 4))

def hexa(t):
    return "#{:02X}{:02X}{:02X}".format(*[max(0, min(255, int(round(c)))) for c in t])

def mezclar(a, b, p):
    """Mezcla a con b en proporcion p (0 = a, 1 = b)."""
    ra, rb = rgb(a), rgb(b)
    return hexa(tuple(ra[i] + (rb[i] - ra[i]) * p for i in range(3)))

def luminancia(h):
    f = lambda c: c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4
    r, g, b = [c / 255 for c in rgb(h)]
    return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b)

def contraste(a, b):
    la, lb = luminancia(a), luminancia(b)
    hi, lo = max(la, lb), min(la, lb)
    return round((hi + 0.05) / (lo + 0.05), 2)

def texto_sobre(fondo, claro="#FFFFFF", oscuro="#111111"):
    """Elige el color de texto legible sobre un fondo dado."""
    return claro if contraste(fondo, claro) >= contraste(fondo, oscuro) else oscuro

def nivel(r, grande=False):
    if grande:
        return "AAA" if r >= 4.5 else "AA" if r >= 3 else "FALLA"
    return "AAA" if r >= 7 else "AA" if r >= 4.5 else "FALLA"

# ---------- tokens ----------

BASE = {
    "primario": "#0F3D2E", "acento": "#8CE0B0", "fondo": "#FBFAF7",
    "superficie": "#FFFFFF", "texto": "#161A18", "texto_suave": "#5C6560",
    "borde": "#E4E7E5", "exito": "#1E7B4D", "aviso": "#B4690E", "error": "#B3261E",
}

def estados_primario(base, claro="#FFFFFF", oscuro="#000000"):
    """Hover y pressed de un color de fondo, en la direccion que se note.

    Oscurecer un 12% un color casi negro no produce ningun cambio perceptible:
    el boton parece roto porque no responde. Cuando el color base ya es muy
    oscuro, el hover ACLARA y el pressed vuelve a hundirse por debajo del base.
    Con un color claro se mantiene el comportamiento clasico.
    """
    if luminancia(base) < 0.18:
        return mezclar(base, claro, 0.14), mezclar(base, oscuro, 0.35)
    return mezclar(base, oscuro, 0.12), mezclar(base, oscuro, 0.22)


def borde_de_control(borde, superficie):
    """Oscurece (o aclara, sobre fondo oscuro) el borde hasta llegar a 3:1.

    El divisor decorativo puede ser sutil; el borde de un campo es el unico
    indicador visual del control y sin 3:1 no se ve donde escribir.
    """
    bc = hacia = borde
    hacia = "#000000" if luminancia(superficie) > 0.4 else "#FFFFFF"
    for _ in range(40):
        if contraste(bc, superficie) >= 3:
            break
        bc = mezclar(bc, hacia, 0.10)
    return bc


def derivar(t):
    c = dict(BASE)
    c.update({k.replace("-", "_"): v for k, v in (t.get("color") or {}).items()
              if isinstance(v, str)})
    for k, v in (t.get("color") or {}).items():
        if isinstance(v, dict) and "hex" in v:
            c[k.replace("-", "_")] = v["hex"]
    d = dict(c)
    # El texto sobre un fondo se elige entre los DOS colores de la marca, no
    # entre blanco y negro genericos: un #111111 suelto no es de nadie.
    def sobre(fondo):
        return texto_sobre(fondo, c["superficie"], c["texto"])
    d["primario_hover"], d["primario_pressed"] = estados_primario(c["primario"])
    d["primario_suave"]    = mezclar(c["primario"], "#FFFFFF", 0.90)
    d["sobre_primario"]    = sobre(c["primario"])
    d["sobre_acento"]      = sobre(c["acento"])
    d["acento_hover"], d["acento_pressed"] = estados_primario(c["acento"])
    d["deshabilitado"]     = mezclar(c["texto_suave"], c["fondo"], 0.55)
    d["sobre_deshabilitado"] = sobre(d["deshabilitado"])
    d["foco"]              = c["acento"] if contraste(c["acento"], c["fondo"]) >= 3 else c["primario"]
    d["borde_control"]     = borde_de_control(c["borde"], c["superficie"])
    # Superficie neutra: fondo de encabezado de tabla, de bloque de codigo y de
    # marco de imagen. Sale del borde, NO del primario: un "primario suave"
    # derivado de un color senal tine de ese color media interfaz, justo lo
    # contrario de reservar el acento para una sola cosa.
    d["superficie_alt"] = mezclar(c["fondo"], c["borde"], 0.55)
    # Superficie de marca: las franjas grandes (hero, ejes, menu movil, footer).
    # NO es el color del boton principal, aunque en modo claro coincidan. Cuando
    # el primario del modo oscuro es un color senal —aqui el ambar—, usarlo como
    # fondo de bloque tine media pagina y el acento deja de ser una sola cosa
    # por pantalla. Por eso es un rol propio, y en oscuro se declara aparte.
    d["marca"]        = c["primario"]
    d["marca_alt"]    = estados_primario(c["primario"])[0]
    d["marca_fuerte"] = estados_primario(c["primario"])[1]
    d["sobre_marca"]  = sobre(c["primario"])
    # modo oscuro derivado
    d["o_fondo"]      = mezclar(c["texto"], "#000000", 0.35)
    d["o_superficie"] = mezclar(c["texto"], "#FFFFFF", 0.10)
    d["o_texto"]      = mezclar(c["fondo"], "#FFFFFF", 0.30)
    d["o_texto_suave"]= mezclar(c["fondo"], "#000000", 0.35)
    d["o_borde"]      = mezclar(c["texto"], "#FFFFFF", 0.22)
    d["o_acento"]     = c["acento"]
    d["o_marca"]      = d["o_superficie"]
    d["o_exito"]      = c["exito"]
    d["o_aviso"]      = c["aviso"]
    d["o_error"]      = c["error"]
    op = c["primario"]
    while contraste(op, d["o_fondo"]) < 4.5 and luminancia(op) < 0.75:
        op = mezclar(op, "#FFFFFF", 0.12)
    d["o_primario"]   = op

    # --- decisiones explicitas del manual de marca ---
    # Hay colores que no se derivan bien: el modo oscuro de una marca monocroma,
    # o un borde de control que debe conservar el tinte de la marca. Cuando
    # tokens.json los declara, mandan sobre lo derivado. Todo lo demas se sigue
    # calculando, asi que cambiar el primario sigue recalculando el sistema.
    for k, v in (t.get("color") or {}).items():
        if isinstance(v, str):
            d[k.replace("-", "_")] = v
    for k, v in (t.get("modo_oscuro") or {}).items():
        if isinstance(v, str):
            d["o_" + k.replace("-", "_")] = v

    # Estados del modo oscuro, derivados del primario oscuro ya resuelto.
    d["o_primario_hover"], d["o_primario_pressed"] = estados_primario(d["o_primario"])
    d["o_primario_suave"]  = mezclar(d["o_primario"], d["o_fondo"], 0.86)
    d["o_sobre_primario"]  = texto_sobre(d["o_primario"], d["o_texto"], c["texto"])
    d["o_sobre_acento"]    = texto_sobre(d["o_acento"], d["o_texto"], c["texto"])
    d["o_borde_control"]   = borde_de_control(d["o_borde"], d["o_superficie"])
    d["o_deshabilitado"]   = mezclar(d["o_texto_suave"], d["o_fondo"], 0.55)
    d["o_foco"] = (d["o_acento"] if contraste(d["o_acento"], d["o_fondo"]) >= 3
                   else d["o_texto"])
    # Despues de aplicar los valores declarados: si se calculara antes, saldria
    # de los derivados y no de los que de verdad manda el manual.
    d["o_superficie_alt"] = mezclar(d["o_superficie"], d["o_borde"], 0.65)
    d["o_marca_alt"]    = estados_primario(d["o_marca"])[0]
    d["o_marca_fuerte"] = estados_primario(d["o_marca"])[1]
    d["o_sobre_marca"]  = texto_sobre(d["o_marca"], d["o_texto"], c["texto"])
    for k, v in (t.get("modo_oscuro") or {}).items():
        if isinstance(v, str):
            d["o_" + k.replace("-", "_")] = v
    return d

def escala(t):
    base = {"escala_px": {"xs":12,"sm":14,"base":16,"lg":20,"xl":26,"2xl":34,"3xl":46,"4xl":62}}
    tip = t.get("tipografia") or {}
    return {**base, **tip}

def escapar(s):
    """Escapa para HTML. Ojo: esc() en este archivo es la escala tipografica."""
    return (str(s).replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace('"', "&quot;"))

PLACEHOLDERS = ("NOMBRE", "XXX", "TODO", "PENDIENTE", "TU_", "FAMILIA")

def sin_definir(nombre):
    if not nombre or not str(nombre).strip():
        return True
    n = str(nombre).upper()
    return any(pl in n for pl in PLACEHOLDERS)

def pila(familia, tipo):
    """Pila de respaldo. Si la fuente no carga, que caiga en algo del mismo genero."""
    if tipo == "mono":
        return '"{}", ui-monospace, SFMono-Regular, Menlo, monospace'.format(familia)
    if tipo == "display":
        return '"{}", Georgia, "Times New Roman", serif'.format(familia)
    return '"{}", system-ui, -apple-system, "Segoe UI", Roboto, sans-serif'.format(familia)

def url_google(familias_pesos):
    """URL de Google Fonts para las familias pedidas."""
    partes = []
    for fam, pesos in familias_pesos:
        ps = ";".join(str(x) for x in sorted(set(pesos)))
        partes.append("family=" + fam.replace(" ", "+") + ":wght@" + ps)
    return "https://fonts.googleapis.com/css2?" + "&".join(partes) + "&display=swap"

def resolver_tipografia(t):
    """Devuelve la especificacion tipografica y avisa de lo que falte.

    El fallo que esto evita: que el kit se entregue con NOMBRE_DISPLAY escrito
    literalmente, o con la fuente correcta en el CSS pero sin cargarla, de modo
    que el cliente abre el entregable y no ve la tipografia por ningun lado.
    """
    tip = t.get("tipografia") or {}
    pesos = list((tip.get("pesos") or {"regular": 400, "medio": 500, "fuerte": 700}).values())
    spec = {"avisos": [], "familias": [], "pendiente": False}
    for rol, tipo in (("texto", "texto"), ("display", "display"), ("mono", "mono")):
        d = tip.get(rol) or {}
        fam = d.get("familia")
        if rol == "mono" and not fam:
            continue
        if sin_definir(fam):
            spec["pendiente"] = True
            spec["avisos"].append(
                "La familia de '{}' no esta definida (aparece como '{}').".format(rol, fam))
            fam = "Inter" if rol != "display" else "Fraunces"
            spec["avisos"].append("  Se uso '{}' de forma provisional. Cambiala antes de entregar.".format(fam))
        # La pila de respaldo por defecto asume que la display es serif. Si la
        # marca usa una sans para titulares, un respaldo serif descuadra la
        # maqueta el dia que la fuente no cargue: por eso tokens.json puede
        # declarar su propia pila en "respaldo".
        spec[rol] = {
            "familia": fam,
            "pila": d.get("respaldo") or pila(fam, tipo),
            "licencia": d.get("licencia", "por confirmar"),
            "origen": d.get("origen", "google"),
            "uso": d.get("uso", ""),
            "pesos": d.get("pesos", pesos if rol != "display" else [p for p in pesos if p >= 500] or [700]),
        }
        spec["familias"].append((fam, spec[rol]["pesos"]))
    if tip.get("confirmada") is False:
        spec["pendiente"] = True
        spec["avisos"].append("tokens.json marca la tipografia como no confirmada ('confirmada': false).")
    spec["url_google"] = url_google([(f, p) for f, p in spec["familias"]
                                     if spec.get("texto", {}).get("origen", "google") == "google"])
    spec["pesos"] = pesos
    return spec

# ---------- salidas ----------

def css(d, tip, esp, rad, tipo, extra=None, fuentes_ok=False):
    extra = extra or {}
    L = ["/* Generado por kit_ui.py. Los estados son derivados: no los edites a mano,",
         "   cambia el color base y vuelve a generar. */",
         ""]
    if fuentes_ok:
        L += ["/* TIPOGRAFIA — viene autoalojada en este mismo kit. En el <head>,",
              "   ANTES de esta hoja de estilos:",
              "",
              '   <link rel="stylesheet" href="fuentes.css">',
              "",
              "   Copia la carpeta fuentes/ completa, con sus licencias dentro:",
              "   distribuirlas es condicion de la licencia OFL. */"]
    else:
        L += ["/* TIPOGRAFIA — la fuente debe cargarse o el navegador usara la de respaldo.",
              "   Pega esto en el <head>, ANTES de esta hoja de estilos:",
              "",
              '   <link rel="preconnect" href="https://fonts.googleapis.com">',
              '   <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>',
              '   <link rel="stylesheet" href="' + tipo["url_google"] + '">',
              "",
              "   Alternativa autoalojada (mas rapida y sin dependencia externa):",
              "   python3 generador/fuentes.py tokens.json --out fuentes */"]
    L += ["", ":root {"]
    for k in ["primario","primario_hover","primario_pressed","primario_suave","sobre_primario",
              "acento","acento_hover","acento_pressed","sobre_acento",
              "marca","marca_alt","marca_fuerte","sobre_marca",
              "fondo","superficie","superficie_alt","texto","texto_suave","borde",
              "exito","aviso","error","deshabilitado","sobre_deshabilitado",
              "foco","borde_control"]:
        L.append("  --color-{}: {};".format(k.replace("_","-"), d[k]))
    L.append("  --fuente-texto: {};".format(tipo["texto"]["pila"]))
    L.append("  --fuente-display: {};".format(tipo["display"]["pila"]))
    if tipo.get("mono"):
        L.append("  --fuente-mono: {};".format(tipo["mono"]["pila"]))
    for k, v in (tip.get("pesos") or {"regular":400,"medio":500,"fuerte":700}).items():
        L.append("  --peso-{}: {};".format(k, v))
    il = tip.get("interlineado") or {"titulares": 1.15, "texto": 1.55}
    L.append("  --interlineado-titulares: {};".format(il.get("titulares", 1.15)))
    L.append("  --interlineado-texto: {};".format(il.get("texto", 1.55)))
    for k, v in escala(tip)["escala_px"].items():
        L.append("  --texto-{}: {}px;".format(k, v))
    for v in esp:
        L.append("  --esp-{}: {}px;".format(v, v))
    for k, v in rad.items():
        L.append("  --radio-{}: {}px;".format(k, v))
    for k, v in (extra.get("chaflan_px") or {}).items():
        if isinstance(v, int) and not isinstance(v, bool):
            L.append("  --chaflan-{}: {}px;".format(k, v))
    L += ["  --sombra-sm: 0 1px 2px rgba(0,0,0,.06);",
          "  --sombra-md: 0 4px 12px rgba(0,0,0,.08);",
          "  --sombra-lg: 0 12px 32px rgba(0,0,0,.12);"]
    for k, v in (extra.get("breakpoints_px") or {}).items():
        L.append("  --bp-{}: {}px;".format(k, v))
    for k, v in (extra.get("header") or {}).items():
        if isinstance(v, int) and not isinstance(v, bool):
            nombre = k[:-3] if k.endswith("_px") else k
            L.append("  --header-{}: {}px;".format(nombre.replace("_", "-"), v))
    L += ["  --ancho-max: {}px;".format(extra.get("ancho_max_px", 1140)), "}", "",
          '[data-tema="oscuro"] {']
    for k, v in [("fondo","o_fondo"),("superficie","o_superficie"),
                 ("superficie-alt","o_superficie_alt"),("texto","o_texto"),
                 ("texto-suave","o_texto_suave"),("borde","o_borde"),("borde-control","o_borde_control"),
                 ("primario","o_primario"),("primario-hover","o_primario_hover"),
                 ("primario-pressed","o_primario_pressed"),("primario-suave","o_primario_suave"),
                 ("sobre-primario","o_sobre_primario"),("acento","o_acento"),
                 ("marca","o_marca"),("marca-alt","o_marca_alt"),
                 ("marca-fuerte","o_marca_fuerte"),("sobre-marca","o_sobre_marca"),
                 ("sobre-acento","o_sobre_acento"),("exito","o_exito"),("aviso","o_aviso"),
                 ("error","o_error"),("deshabilitado","o_deshabilitado"),("foco","o_foco")]:
        L.append("  --color-{}: {};".format(k, d[v]))
    L += ["}", ""]
    if extra.get("chaflan_px"):
        L += ["/* LA FIRMA DE LA MARCA. Ninguna esquina se redondea: se corta a 45 grados,",
              "   siempre en la superior izquierda y la inferior derecha. Aplicala a",
              "   botones, tarjetas, etiquetas de precio y recortes de fotografia. */",
              ".chaflan {",
              "  --ch: var(--chaflan-md);",
              "  border-radius: 0;",
              "  clip-path: polygon(",
              "    var(--ch) 0, 100% 0,",
              "    100% calc(100% - var(--ch)), calc(100% - var(--ch)) 100%,",
              "    0 100%, 0 var(--ch)",
              "  );",
              "}",
              ".chaflan--sm { --ch: var(--chaflan-sm); }",
              ".chaflan--lg { --ch: var(--chaflan-lg); }",
              ".chaflan--xl { --ch: var(--chaflan-xl); }",
              "",
              "/* clip-path recorta tambien el anillo de foco. Mientras el elemento esta",
              "   enfocado con teclado se renuncia al chaflan y se muestra el rectangulo",
              "   completo con su outline: el foco visible pesa mas que la esquina. */",
              ".chaflan:focus-visible { clip-path: none; }", ""]
    if extra.get("mono"):
        L += ["/* Precios, referencias y cantidades: monoespaciada con cifras tabulares,",
              "   para que las columnas de una lista de precios alineen solas. */",
              ".precio, .sku, .cantidad {",
              "  font-family: var(--fuente-mono);",
              "  font-variant-numeric: tabular-nums;",
              "}", ""]
    L += ["/* Respeta a quien pide menos movimiento */",
          "@media (prefers-reduced-motion: reduce) {",
          "  * { animation-duration: .01ms !important; transition-duration: .01ms !important; }", "}"]
    return "\n".join(L) + "\n"

PARES = [("texto","fondo","Texto principal sobre fondo",False),
         ("texto","superficie","Texto sobre tarjeta",False),
         ("texto_suave","fondo","Texto secundario sobre fondo",False),
         ("texto_suave","superficie","Texto secundario sobre tarjeta",False),
         ("sobre_primario","primario","Texto del boton principal",False),
         ("primario","fondo","Primario como texto o icono sobre fondo",True),
         ("sobre_primario","primario_hover","Texto del boton principal en hover",False),
         ("sobre_acento","acento","Texto del boton de acento",False),
         ("exito","superficie","Etiqueta 'En stock' sobre tarjeta",False),
         ("aviso","superficie","Aviso sobre tarjeta",False),
         ("error","fondo","Mensaje de error sobre fondo",False),
         ("borde_control","superficie","Borde de campo de formulario",True),
         ("foco","fondo","Anillo de foco sobre fondo",True),
         ("sobre_marca","marca","Texto sobre la franja de marca",False),
         ("sobre_marca","marca_fuerte","Texto sobre la franja de datos",False),
         ("texto","superficie_alt","Texto sobre encabezado de tabla o codigo",False)]

def _tabla(d, pares, prefijo=""):
    fil, fallas = [], 0
    for a, b, desc, grande in pares:
        ka, kb = prefijo + a, prefijo + b
        if ka not in d or kb not in d:
            continue
        r = contraste(d[ka], d[kb])
        n = nivel(r, grande)
        if n == "FALLA":
            fallas += 1
        fil.append((desc, d[ka], d[kb], r, n, grande))
    L = ["| Par | Frente | Fondo | Ratio | Nivel |", "|---|---|---|---|---|"]
    for desc, fa, fb, r, n, g in fil:
        L.append("| {} | `{}` | `{}` | {}:1 | {}{} |".format(
            desc, fa, fb, r, n, " (texto grande)" if g else ""))
    return L, fallas


def informe(d, prohibidos=None):
    L = ["# Informe de contraste", "",
         "Umbrales WCAG 2.1: 4.5:1 para texto normal, 3:1 para texto grande (24px o",
         "19px en negrita), iconos y bordes de control.", "",
         "## Modo claro", ""]
    tc, fallas = _tabla(d, PARES)
    L += tc + ["", "## Modo oscuro", "",
               "El modo oscuro no es invertir colores: se verifica aparte, porque el gris",
               "suave que funciona sobre blanco casi nunca funciona sobre negro.", ""]
    to, fo = _tabla(d, PARES, "o_")
    L += to
    fallas += fo
    L.append("")
    if prohibidos:
        L += ["## Pares prohibidos por el manual de marca", "",
              "No son fallas del kit: son combinaciones que el sistema **no debe**",
              "producir. Se listan con su ratio real para que nadie las reintroduzca",
              "por descuido.", "",
              "| Combinacion | Ratio | Por que se prohibe |", "|---|---|---|"]
        for a, b, motivo in prohibidos:
            ha = d.get(a.replace("-", "_"), a)
            hb = d.get(b.replace("-", "_"), b)
            L.append("| `{}` sobre `{}` | {}:1 | {} |".format(ha, hb, contraste(ha, hb), motivo))
        L.append("")
    if fallas:
        L += ["## {} par(es) no pasan".format(fallas), "",
              "No lo entregues asi. Opciones, de menos a mas invasiva:", "",
              "1. Oscurece el color de texto, no aclares el fondo: preserva mejor la marca.",
              "2. Usa la variante oscura del primario para texto y deja el primario",
              "   original solo como fondo de boton.",
              "3. Si el acento de marca no alcanza, no lo uses para texto: reservalo",
              "   para fondos, subrayados y detalles graficos.", "",
              "El color de marca no es excusa: un texto que no se lee no comunica la marca."]
    else:
        L.append("Todos los pares en uso cumplen el umbral que les corresponde.")
    return "\n".join(L) + "\n", fallas

def ficha_tipografia(tipo, tip, marca, fuentes_ok=False):
    e = escala(tip)["escala_px"]
    mapa = [("3xl", "H1 — titular de portada", "display", "titulares"),
            ("2xl", "H2 — titulo de seccion", "display", "titulares"),
            ("xl",  "H3 — subtitulo", "display", "titulares"),
            ("lg",  "Entradilla", "texto", "texto"),
            ("base","Cuerpo de texto", "texto", "texto"),
            ("sm",  "Texto secundario, etiquetas", "texto", "texto"),
            ("xs",  "Leyendas, avisos legales", "texto", "texto")]
    il = tip.get("interlineado") or {"titulares": 1.15, "texto": 1.55}
    L = ["# Tipografia del sitio — " + marca, ""]
    if tipo["pendiente"]:
        L += ["> **PENDIENTE DE DEFINIR.** " + " ".join(tipo["avisos"]),
              "> No entregues el kit asi.", ""]
    L += ["## Familias", "",
          "| Rol | Familia | Pesos | Licencia | Uso |", "|---|---|---|---|---|"]
    for rol in ("display", "texto", "mono"):
        if rol not in tipo:
            continue
        f = tipo[rol]
        L.append("| {} | **{}** | {} | {} | {} |".format(
            rol, f["familia"], ", ".join(str(p) for p in f["pesos"]),
            f["licencia"], f["uso"] or "-"))
    L += ["", "## Como se instala", ""]
    if fuentes_ok:
        L += ["Las tipografias vienen **autoalojadas en el kit**, en `fuentes/`.",
              "El sitio no depende de ningun servicio externo.", "",
              "```html",
              '<link rel="stylesheet" href="fuentes.css">',
              '<link rel="stylesheet" href="tokens.css">',
              "```", "",
              "Copia la carpeta `fuentes/` completa, con las licencias que trae dentro:",
              "distribuirlas es condicion de la licencia OFL.", ""]
    else:
        L += ["En el `<head>`, **antes** de la hoja de estilos del sitio:", "",
              "```html",
              '<link rel="preconnect" href="https://fonts.googleapis.com">',
              '<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>',
              '<link rel="stylesheet" href="' + tipo["url_google"] + '">',
              "```", "",
              "**Para entregar el kit sin dependencias externas**, autoaloja las",
              "tipografias: cargan antes y el sitio no se rompe si el servicio falla.", "",
              "```bash",
              "python3 generador/fuentes.py tokens.json --out fuentes",
              "```", "",
              "Eso descarga los `.woff2`, sus licencias y genera `fuentes.css`.", ""]
    L += [
          "## Escala y jerarquia", "",
          "| Token | px | Donde se usa | Familia | Interlineado | Peso |",
          "|---|---|---|---|---|---|"]
    for k, desc, fam, iln in mapa:
        if k not in e:
            continue
        L.append("| `--texto-{}` | {} | {} | {} | {} | {} |".format(
            k, e[k], desc, tipo[fam]["familia"], il.get(iln, 1.4),
            700 if fam == "display" else 400))
    L += ["", "## Reglas", "",
          "- El cuerpo **nunca baja de 16px**. Por debajo, los navegadores moviles",
          "  hacen zoom automatico al enfocar un campo y descuadran la maqueta.",
          "- Ancho de linea de 60 a 75 caracteres en texto corrido.",
          "- Interlineado de {} en texto y {} en titulares.".format(
              il.get("texto", 1.55), il.get("titulares", 1.15)),
          "- Maximo tres pesos en todo el sitio.",
          "- Usa siempre `var(--fuente-texto)` y `var(--fuente-display)`,",
          "  nunca el nombre de la familia escrito a mano en el CSS.", ""]
    if tipo["texto"]["licencia"] == "por confirmar":
        L += ["> Confirma la licencia antes de publicar. Si la tipografia es de pago,",
              "> el cliente necesita comprar la licencia web, que se cobra aparte de",
              "> la de escritorio y suele depender de las visitas del sitio.", ""]
    return "\n".join(L)

def html(d, tip, marca, logo_rel, informe_md, tipo, fuentes_ok=False,
         logo_neg_rel=None, isotipo_rel=None, isotipo_neg_rel=None):
    e = escala(tip)["escala_px"]
    fam_t = tipo["texto"]["familia"]
    fam_d = tipo["display"]["familia"]
    filas_pal = "".join(
        '<div class="ficha"><div class="muestra" style="background:{h}"></div>'
        '<b>{k}</b><code>{h}</code></div>'.format(k=k.replace("_","-"), h=d[k])
        for k in ["primario","primario_hover","acento","fondo","superficie","texto",
                  "texto_suave","borde","exito","aviso","error"])
    usos = {"3xl":("H1 · titular de portada","display"),"2xl":("H2 · titulo de seccion","display"),
            "xl":("H3 · subtitulo","display"),"lg":("Entradilla","texto"),
            "base":("Cuerpo de texto","texto"),"sm":("Secundario, etiquetas","texto"),
            "xs":("Leyendas, legales","texto"),"4xl":("Titular grande","display")}
    filas_tipo = "".join(
        '<tr><td><code>--texto-{k}</code></td><td>{v}px</td><td>{u}</td>'
        '<td style="font-size:{vv}px;line-height:1.2;font-family:var(--fuente-{f});'
        'font-weight:{w}">{m}</td></tr>'.format(
            k=k, v=v, u=usos.get(k, ("-", "texto"))[0], f=usos.get(k, ("-", "texto"))[1],
            w=700 if usos.get(k, ("-", "texto"))[1] == "display" else 400,
            vv=min(v, 40), m="Diseño con carácter" if v >= 20 else "Texto de muestra")
        for k, v in e.items())
    aviso_tipo = ("" if not tipo["pendiente"] else
        '<div style="background:#B3261E;color:#fff;padding:16px 20px;border-radius:10px;'
        'margin-bottom:20px"><b>Tipografia pendiente de definir.</b><br>' +
        "<br>".join(escapar(a) for a in tipo["avisos"]) +
        '<br>No entregues el kit en este estado.</div>')
    filas_fam = "".join(
        '<tr><td>{r}</td><td style="font-family:var(--fuente-{r2});font-size:22px;'
        'font-weight:{w}">{f}</td><td><code>{p}</code></td><td>{l}</td></tr>'.format(
            r=r, r2=r if r != "mono" else "mono", f=tipo[r]["familia"],
            w=700 if r == "display" else 400,
            p=", ".join(str(x) for x in tipo[r]["pesos"]), l=tipo[r]["licencia"])
        for r in ("display", "texto", "mono") if r in tipo)
    if fuentes_ok:
        enlace_fuente = '<link rel="stylesheet" href="fuentes.css">'
        codigo_carga = escapar('<link rel="stylesheet" href="fuentes.css">')
    else:
        enlace_fuente = (
            '<link rel="preconnect" href="https://fonts.googleapis.com">'
            '<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>'
            '<link rel="stylesheet" href="{}">'.format(tipo["url_google"]))
        codigo_carga = escapar('<link rel="stylesheet" href="' + tipo["url_google"] + '">')
    # Sin estilos en linea: la altura la fija la hoja de estilos con los tokens
    # del header, y asi el logo puede encogerse en movil sin pelear con !important.
    def img(ruta, clase, alt=True):
        return '<img src="{}" class="{}" alt="{}">'.format(
            ruta, clase, "Logo de " + marca if alt else "")

    def par(positivo, negativo, clase):
        """Las dos versiones del logo, y el CSS decide cual se ve.

        Un logo monocromo oscuro desaparece sobre el fondo oscuro. La variante
        negativa existe para eso, pero solo sirve si la pagina la lleva puesta:
        por eso van las dos y se alternan con el tema.
        """
        if not positivo:
            return '<span class="logotexto">{}</span>'.format(marca)
        if not negativo:
            return img(positivo, clase)
        return ('<span class="logo-par">' + img(positivo, clase + " logo-pos")
                + img(negativo, clase + " logo-neg", alt=False) + '</span>')

    marca_logo = par(logo_rel, logo_neg_rel, "logo-marca")
    marca_logo_neg = (img(logo_neg_rel, "logo-marca") if logo_neg_rel
                      else marca_logo)  # el pie SIEMPRE va en negativo
    marca_isotipo = par(isotipo_rel, isotipo_neg_rel, "logo-isotipo")
    def filas_de(prefijo, rotulo):
        L, _ = _tabla(d, PARES, prefijo)
        out = ["<tr class='grupo'><td colspan='5'><b>{}</b></td></tr>".format(rotulo)]
        for l in L[2:]:
            c = [x.strip() for x in l.strip("|").split("|")]
            if len(c) >= 5:
                estado = "falla" if "FALLA" in c[4] else "ok"
                out.append("<tr class='{}'><td>{}</td><td><code>{}</code></td>"
                           "<td><code>{}</code></td><td>{}</td><td>{}</td></tr>".format(
                               estado, c[0], c[1].strip("`"), c[2].strip("`"), c[3], c[4]))
        return out
    filas = filas_de("", "Modo claro") + filas_de("o_", "Modo oscuro")
    plantilla = Path(__file__).with_name("_plantilla_kit.html").read_text(encoding="utf-8")
    for k, v in {
        "__MARCA__": marca, "__LOGO__": marca_logo, "__PALETA__": filas_pal,
        "__TIPO__": filas_tipo, "__CONTRASTE__": "".join(filas),
        "__FAM_TEXTO__": fam_t, "__FAM_DISPLAY__": fam_d,
        "__ENLACE_FUENTE__": enlace_fuente, "__AVISO_TIPO__": aviso_tipo,
        "__FAMILIAS__": filas_fam, "__CODIGO_CARGA__": codigo_carga,
        "__LOGO_NEG__": marca_logo_neg, "__ISOTIPO__": marca_isotipo,
    }.items():
        plantilla = plantilla.replace(k, v)
    return plantilla

def main():
    p = argparse.ArgumentParser(description="Genera el kit de interfaz desde tokens.json")
    p.add_argument("tokens")
    p.add_argument("--out", default="kit")
    p.add_argument("--marca", default=None)
    p.add_argument("--logo", default=None, help="SVG o PNG del logo, se copia al kit")
    p.add_argument("--logo-negativo", default=None,
                   help="Variante monocroma negativa, para el footer y el modo oscuro")
    p.add_argument("--isotipo", default=None, help="Simbolo solo, para el header en movil")
    p.add_argument("--isotipo-negativo", default=None,
                   help="Simbolo en negativo, para el header en movil en modo oscuro")
    p.add_argument("--fuentes", action="store_true",
                   help="Descarga las tipografias y las autoaloja en el kit (necesita red)")
    p.add_argument("--sin-generador", action="store_true",
                   help="No incluir el generador en el kit (no recomendado)")
    args = p.parse_args()

    t = json.loads(Path(args.tokens).read_text(encoding="utf-8"))
    marca = args.marca or t.get("marca", "Marca")
    d = derivar(t)
    tip = t.get("tipografia") or {}
    esp = t.get("espaciado_px") or [4, 8, 12, 16, 24, 32, 48, 64, 96]
    rad = t.get("radio_px") or {"sm": 6, "md": 10, "lg": 16, "completo": 9999}

    tipo = resolver_tipografia(t)

    extra = {"chaflan_px": {k: v for k, v in (t.get("chaflan_px") or {}).items()
                            if isinstance(v, int)},
             "breakpoints_px": t.get("breakpoints_px") or {},
             "header": t.get("header") or {},
             "ancho_max_px": t.get("ancho_max_px", 1140),
             "mono": bool((t.get("tipografia") or {}).get("mono"))}
    extra["chaflan_px"] = {k: v for k, v in extra["chaflan_px"].items()
                           if not isinstance(v, bool)}

    out = Path(args.out); out.mkdir(parents=True, exist_ok=True)
    inf, fallas = informe(d, t.get("pares_prohibidos"))
    (out / "contraste.md").write_text(inf, encoding="utf-8")

    # --- el kit tiene que poder regenerarse solo ---
    # Sin tokens.json y sin el generador, cambiar un color obliga a editar a mano
    # el CSS ya generado, que es justo lo que el sistema de tokens evita.
    aqui = Path(__file__).parent
    def copiar(origen, destino):
        """Copia salvo que sea el mismo archivo.

        Al regenerar el kit dentro de su propia carpeta —que es lo normal para
        quien lo recibe— el generador se copiaria sobre si mismo.
        """
        origen, destino = Path(origen), Path(destino)
        if destino.exists() and origen.resolve() == destino.resolve():
            return
        destino.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy(origen, destino)

    if not args.sin_generador:
        gen = out / "generador"
        gen.mkdir(parents=True, exist_ok=True)
        for f in ("kit_ui.py", "_plantilla_kit.html", "fuentes.py"):
            if (aqui / f).exists():
                copiar(aqui / f, gen / f)
        copiar(args.tokens, out / "tokens.json")

    fuentes_ok = False
    if args.fuentes:
        try:
            sys.path.insert(0, str(aqui))
            import fuentes as MF
            caras = []
            for rol in ("display", "texto", "mono"):
                if rol in tipo and tipo[rol].get("origen", "google") == "google":
                    print("Descargando {}...".format(tipo[rol]["familia"]))
                    caras += MF.procesar(tipo[rol]["familia"], tipo[rol]["pesos"],
                                         out / "fuentes", MF.hay_brotli())
            if caras:
                (out / "fuentes.css").write_text(MF.css_fuentes(caras), encoding="utf-8")
                fuentes_ok = True
        except Exception as e:
            print("  AVISO: no se pudieron autoalojar las fuentes ({}).".format(type(e).__name__))
            print("         El kit queda enlazando a Google Fonts.")

    # Si el kit ya trae las tipografias dentro, se siguen usando aunque se
    # regenere sin --fuentes. Sin esto, quien recibe el kit cambia un color,
    # regenera, y el CSS vuelve a apuntar a Google Fonts en silencio: pierde el
    # autoalojado teniendo los .woff2 delante.
    if not fuentes_ok and (out / "fuentes.css").exists() and (out / "fuentes").is_dir():
        fuentes_ok = True
        print("  Tipografias ya autoalojadas en el kit: se conservan.")

    (out / "tokens.css").write_text(
        css(d, tip, esp, rad, tipo, extra, fuentes_ok), encoding="utf-8")
    (out / "tipografia.md").write_text(
        ficha_tipografia(tipo, tip, marca, fuentes_ok), encoding="utf-8")

    def traer_logo(ruta):
        if not ruta or not Path(ruta).exists():
            return None
        destino = out / "logo" / Path(ruta).name
        copiar(ruta, destino)
        return "logo/" + destino.name

    logo_rel = traer_logo(args.logo)
    logo_neg_rel = traer_logo(args.logo_negativo)
    isotipo_rel = traer_logo(args.isotipo)
    isotipo_neg_rel = traer_logo(args.isotipo_negativo)

    (out / "index.html").write_text(
        html(d, tip, marca, logo_rel, inf, tipo, fuentes_ok, logo_neg_rel,
             isotipo_rel, isotipo_neg_rel),
        encoding="utf-8")

    leeme = ["# Kit de interfaz — " + marca, "",
        "Todo lo que necesita quien programa el sitio. **El kit se regenera solo**",
        "si cambia una decision de diseno; no edites a mano los archivos generados.", "",
        "## Que hay aqui", "",
        "```",
        "kit/",
        "├── index.html       guia visual — abrela en el navegador",
        "├── tokens.css       variables para el proyecto (GENERADO, no editar)",
        "├── tokens.json      las decisiones. ESTE es el archivo que se edita",
        "├── tipografia.md    familias, pesos, licencia e instalacion",
        "├── contraste.md     informe WCAG",
        ("├── fuentes.css      @font-face de las tipografias autoalojadas" if fuentes_ok
         else "│                 (las tipografias se cargan desde Google Fonts)"),
        ("├── fuentes/         los .woff2 y sus licencias" if fuentes_ok else "│"),
        "└── generador/       kit_ui.py y sus plantillas",
        "```", "",
        "## Como cambiar algo", "",
        "1. Edita `tokens.json` — por ejemplo el color primario o un tamano de texto.",
        "2. Vuelve a generar:", "",
        "```bash",
        "python3 generador/kit_ui.py tokens.json --out . " + ("--fuentes" if fuentes_ok else ""),
        "```", "",
        "Los estados (hover, pressed, foco, texto sobre cada fondo) y el modo oscuro",
        "se recalculan solos, y el informe de contraste se rehace. Por eso no se",
        "editan a mano: el proximo regenerado borraria el cambio.", "",
        "## Como se enlaza en el sitio", "",
        "```html"]
    if fuentes_ok:
        leeme += ['<link rel="stylesheet" href="fuentes.css">',
                  '<link rel="stylesheet" href="tokens.css">', "```", "",
                  "Copia tambien la carpeta `fuentes/`. Las licencias que van dentro",
                  "deben distribuirse con los archivos: es condicion de la licencia OFL."]
    else:
        leeme += ['<link rel="preconnect" href="https://fonts.googleapis.com">',
                  '<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>',
                  '<link rel="stylesheet" href="' + tipo["url_google"] + '">',
                  '<link rel="stylesheet" href="tokens.css">', "```", "",
                  "Para autoalojar las tipografias y no depender de Google:", "",
                  "```bash", "python3 generador/fuentes.py tokens.json --out fuentes", "```"]
    leeme += ["", "## Reglas", "",
        "- Usa siempre las variables de `tokens.css`, nunca un HEX o un px suelto.",
        "- Modo oscuro: `data-tema=\"oscuro\"` en el `<html>`.",
        "- Los textos del kit son de muestra: reemplazalos por los definitivos.", ""]
    (out / "LEEME.md").write_text("\n".join(leeme), encoding="utf-8")

    print("Kit generado en " + str(out))
    listado = ["index.html", "tokens.css", "tokens.json", "tipografia.md",
               "contraste.md", "LEEME.md"]
    if fuentes_ok:
        listado += ["fuentes.css", "fuentes/"]
    if not args.sin_generador:
        listado += ["generador/"]
    for f in listado:
        print("  - " + f)
    if not fuentes_ok:
        print("\n  Tipografias enlazadas a Google Fonts. Para entregar el kit")
        print("  completo y sin dependencias externas, usa --fuentes.")
    print("\n  Tipografia: {} (titulares) / {} (texto)".format(
        tipo["display"]["familia"], tipo["texto"]["familia"]))
    if tipo["pendiente"]:
        print("\n  ATENCION: la tipografia no esta definida.")
        for a in tipo["avisos"]:
            print("  " + a)
        print("  Revisa tipografia.md y corrigelo antes de entregar.")
    if fallas:
        print("\n  ATENCION: {} par(es) de color no pasan contraste.".format(fallas))
        print("  Revisa contraste.md y corrige antes de entregar.")
    else:
        print("\n  Contraste: todos los pares en uso cumplen.")

if __name__ == "__main__":
    main()
