#!/usr/bin/env python3
"""Paso de diferencias: qué trae la lista de hoy frente a lo que ya se investigó.

Corre entre el parser (paso 2) y la revisión con la persona (paso 3). Se puede
volver a correr: parte siempre de lo que dejó el parser (`1-lista/productos-del-parser.json`)
y aplica antes las decisiones de la persona (`1-lista/decisiones.json`, ver decisiones.py):

    python3 scripts/comparar_lista.py catalogo/productos.json

Clasifica cada producto (cada configuración: modelo + memoria + SIM) contra
`referencias/conocidos.json`:

- **nuevo**: el modelo no está en la base. Se investiga entero en el paso 4.
- **configuracion_nueva**: el modelo es conocido y esta memoria no. Hereda la
  ficha del modelo; se investiga solo su precio.
- **sin_precio_vigente**: configuración conocida, pero su precio de mercado pasó
  de los 7 días o nunca lo tuvo. Hereda la ficha; se investiga solo el precio.
  Si el costo cambió, el reporte lo dice igual que en costo_cambio.
- **costo_cambio**: conocida, precio vigente, y el proveedor cambió el costo.
  Hereda todo y el margen se recalcula con el costo de hoy.
- **sin_cambios**: conocida, precio vigente, mismo costo. Nada que hacer.

Y anota los **desaparecidos** —configuraciones que vinieron en la última lista de
su mensaje y hoy no, aunque el mensaje llegó—, los **modelos desaparecidos**
—todas sus configuraciones faltan: en el sitio es ocultar el producto, no una
variante—, los **posibles el mismo** —un nuevo y un faltante que solo difieren
en la SIM: el nuevo se descarta por falta de información del proveedor y se
pregunta— y los conocidos que vinieron pero no entran.

Los colores ya no se heredan como texto de la ficha: son una variante que el
cliente elige (decisión del 08/10/2026). Cada producto lleva la paleta del
modelo en `colores_oficiales` y, en `colores_sugeridos`, los de esa paleta que
coinciden con los emojis de la lista —o la paleta entera si no marca ninguno,
regla 13—, para premarcarlos en el panel (`colores.py`).

Escribe de vuelta en productos.json y un `cambios.md`, en `1-lista/` junto a la lista,
para mostrar en el paso 3.
Los scripts del paso 4 leen `pendiente` (ver pendientes.py).
"""

import argparse
import json
import re
import sys
from copy import deepcopy
from datetime import date
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from colores import SUPUESTO_PALETA_COMPLETA, colores_sugeridos, sin_tildes  # noqa: E402,F401
import conocidos  # noqa: E402
import decisiones as decisiones_de_la_persona  # noqa: E402
import descripciones  # noqa: E402
from asignar_precios import ALERTAS_DE_MARGEN, aplicar_margen  # noqa: E402
from pendientes import COMPLETAR, TAREAS  # noqa: E402
import rutas  # noqa: E402

PRECIO = conocidos.PRECIO
# Una alerta de versiones anteriores: hoy la frase de configuración es un defecto de la
# descripción (`descripciones.NOMBRA_CONFIGURACION`) y se avisa siempre. Se sigue filtrando
# para que un productos.json viejo no la arrastre.
ALERTA_CAPACIDAD = "la descripción del modelo nombra un almacenamiento"
# Lo que la descripción guardada no cumple: se recalcula en cada comparación, como las de margen.
ALERTA_DESCRIPCION = "descripción de la base:"
# El prefijo del motivo con que se descarta un modelo que el negocio decidió no vender
# (referencias/excluidos.json, escrito por `conocidos.py excluir`).
MOTIVO_EXCLUIDO = "excluido del catálogo"
ESTADOS = ("nuevo", "configuracion_nueva", "costo_cambio", "sin_cambios", "sin_precio_vigente")
# El sufijo de SIM del id: sin él, dos ids pueden ser el mismo equipo.
RE_SUFIJO_SIM = re.compile(r"-(1-sim|dual-sim|sim-esim|esim)$")


def comparar(datos: dict, base: dict, hoy: date, excluidos: dict | None = None) -> dict:
    """Escribe la comparación en `datos`, en su sitio, y lo devuelve."""
    modelos, configuraciones = base["modelos"], base["configuraciones"]
    # Lo que el negocio decidió no vender se descarta antes de clasificar: no se investiga, no
    # recibe carpeta y no vuelve a proponerse. La decisión la registró una persona con
    # `conocidos.py excluir`, con su motivo, y aquí solo se aplica.
    for p in list(datos["productos"]):
        ex = (excluidos or {}).get(p["id_modelo"])
        if ex:
            datos["productos"].remove(p)
            datos.setdefault("descartados", []).append({
                **p, "motivo": f"{MOTIVO_EXCLUIDO} ({fecha_corta(ex.get('fecha'))}): {ex.get('motivo')}"})
    datos["excluidos_del_catalogo"] = [
        {"id": d["id"], "titulo": d.get("titulo"), "motivo": d["motivo"]}
        for d in datos.get("descartados") or []
        if str(d.get("motivo") or "").startswith(MOTIVO_EXCLUIDO)]
    for p in datos["productos"]:
        # Los supuestos que puso el parser, aparte de los que se heredan: al
        # consolidar solo se guardan los de la investigación.
        p.setdefault("supuestos_lista", list(p.get("supuestos") or []))
        m = modelos.get(p["id_modelo"])
        e = configuraciones.get(p["id"])
        p["costo_anterior_cop"] = e.get("ultimo_costo_cop") if e else None
        if m is None:
            p["estado_lista"], p["pendiente"] = "nuevo", list(TAREAS)
            continue

        # La de la base puede venir de una versión vieja de la skill: lo que quedó vacío se
        # quita, y lo que falta se dice para el paso 4, en vez de volver a publicarlo en silencio.
        p["descripcion"] = descripciones.sanear(m["descripcion"])
        p["meta_titulo"] = m["meta_titulo"]
        p["meta_descripcion"] = m["meta_descripcion"]
        p["colores_oficiales"] = deepcopy(m["paleta"])
        p["colores_sugeridos"] = colores_sugeridos(p.get("colores_familia"), m["paleta"])
        if not p.get("colores_familia") and m["paleta"] and SUPUESTO_PALETA_COMPLETA not in p["supuestos_lista"]:
            # En los de la lista y no en los de la investigación: es un hecho de esta lista, y
            # consolidar no lo lleva a la base.
            p["supuestos_lista"].append(SUPUESTO_PALETA_COMPLETA)
        p["fuentes_ficha"] = deepcopy(m["fuentes_ficha"])
        p["garantia_mercado"] = deepcopy(m.get("garantia"))
        p["supuestos"] = list(dict.fromkeys(
            p["supuestos_lista"] + m["supuestos_investigacion"] + m["supuestos_paleta"]))
        p["revisar"] = [r for r in p.get("revisar") or []
                        if not r.startswith(ALERTAS_DE_MARGEN + (ALERTA_CAPACIDAD, ALERTA_DESCRIPCION))]
        p["revisar"] += [f"{ALERTA_DESCRIPCION} {d}" for d in descripciones.defectos(p["descripcion"])]
        # Lo que le falta y se puede completar con la ficha oficial —la tabla, la caja— queda para
        # el paso 4, sin reescribir la prosa: si no, el borrador llegaba al panel así, lista tras
        # lista. `ficha` además para que Icecat la busque, porque va primero (22/10/2026).
        faltan = descripciones.faltantes(p["descripcion"])
        por_completar = (["ficha"] if descripciones.FICHA in faltan else []) + ([COMPLETAR] if faltan else [])

        if e is not None and conocidos.precio_vigente(e, hoy):
            for campo in PRECIO:
                p[campo] = deepcopy(e.get(campo))
            p["pendiente"] = []
            p["estado_lista"] = ("sin_cambios" if e.get("ultimo_costo_cop") == p["precio_proveedor_cop"]
                                 else "costo_cambio")
        else:
            # Un precio investigado en esta corrida trae la fecha de su consulta,
            # distinta de la de la base. Si ya está, no se borra: volver a correr
            # esto después del paso 4 no puede deshacer el paso 4.
            fresco = bool(p.get("precio_mercado_cop") and p.get("fecha_precio")
                          and p.get("fecha_precio") != (e or {}).get("fecha_precio"))
            if not fresco:
                for campo in PRECIO:
                    p[campo] = [] if campo in ("fuentes_precio", "notas_precio") else None
            p["pendiente"] = [] if fresco else ["precio"]
            p["estado_lista"] = "configuracion_nueva" if e is None else "sin_precio_vigente"
        p["pendiente"] += por_completar

        aplicar_margen(p)

    presentes = {p["id"] for p in datos["productos"]}
    # Un conocido que vino pero quedó descartado —el S25 Ultra sin precio, el
    # 08/10/2026— no desapareció: el proveedor lo tiene y no entra por otra razón.
    descartados = {d.get("id"): d for d in datos.get("descartados") or []}
    datos["conocidos_descartados"] = [
        {"id": cid, "titulo": c["titulo"], "motivo": descartados[cid].get("motivo")}
        for cid, c in sorted(configuraciones.items()) if cid not in presentes and cid in descartados
        # Los excluidos del catálogo tienen su propia sección: aquí repetirían el mismo motivo.
        and not str(descartados[cid].get("motivo") or "").startswith(MOTIVO_EXCLUIDO)
    ]
    presentes |= set(descartados)
    llegaron = set(datos.get("bloques") or [])
    faltan = [
        {"id": cid, "id_modelo": c["id_modelo"], "titulo": c["titulo"], "bloques": c.get("bloques") or [],
         "visto_por_ultima_vez": c.get("visto_por_ultima_vez"),
         "ultimo_costo_cop": c.get("ultimo_costo_cop")}
        for cid, c in configuraciones.items()
        if cid not in presentes and set(c.get("bloques") or []) & llegaron
    ]
    faltan.sort(key=lambda d: (d["visto_por_ultima_vez"] or "", d["id"]), reverse=True)

    # Un nuevo y un faltante que solo difieren en la SIM pueden ser el mismo
    # equipo con la anotación pegada a otra línea —el Moto G17 Power, 08/10/2026—
    # o dos referencias —el A17 5G llegó «1 SIM» el 02/10 y «DUAL SIM» el 15/10—.
    # La lista no permite decirlo, así que el nuevo se descarta por falta de
    # información del proveedor (regla 10) y se le pregunta a la persona. El
    # conocido sigue faltando: si se confirma el descarte, deja de ofrecerse;
    # si la persona tiene el dato, la equivalencia lo resuelve al volver a parsear.
    # Una línea con equivalencia ya la miró una persona y no se pregunta otra vez.
    sin_sim = lambda pid: RE_SUFIJO_SIM.sub("", pid)  # noqa: E731
    datos["posibles_mismos"] = []
    for p in list(datos["productos"]):
        if p["estado_lista"] not in ("nuevo", "configuracion_nueva") or p.get("confirmado_el"):
            continue
        pareja = next((d for d in faltan if sin_sim(d["id"]) == sin_sim(p["id"])), None)
        if pareja:
            datos["productos"].remove(p)
            datos.setdefault("descartados", []).append({
                **p, "motivo": f"falta información del proveedor: si es la misma referencia que "
                               f"{pareja['titulo']} (solo cambió la SIM)",
                "falta_informacion": "la SIM", "posible_mismo_de": pareja["id"]})
    # Desde los descartados, para que correrlo dos veces dé lo mismo.
    for d in datos.get("descartados") or []:
        if d.get("posible_mismo_de") in configuraciones:
            datos["posibles_mismos"].append({
                "nuevo": d["id"], "titulo_nuevo": d["titulo"], "conocido": d["posible_mismo_de"],
                "titulo_conocido": configuraciones[d["posible_mismo_de"]]["titulo"]})
    datos["falta_informacion"] = [
        {"id": d["id"], "titulo": d.get("titulo") or d.get("texto_origen"), "motivo": d["motivo"],
         "posible_mismo_de": d.get("posible_mismo_de"),
         "resolver": d.get("resolver") or "equivalencia con el título completo"}
        for d in datos.get("descartados") or [] if d.get("falta_informacion")]

    # Desaparecido es el que vino en la última lista de su bloque y hoy no. El
    # que ya faltaba entonces se reportó esa vez: queda como ausente, para que la
    # sección no repita en cada lista a todos los que alguna vez faltaron.
    ultima_vez = {}
    for c in configuraciones.values():
        for b in c.get("bloques") or []:
            ultima_vez[b] = max(ultima_vez.get(b, ""), c.get("visto_por_ultima_vez") or "")
    datos["desaparecidos"] = [d for d in faltan
                              if any(d["visto_por_ultima_vez"] == ultima_vez.get(b) for b in d["bloques"])]
    datos["ausentes"] = [d for d in faltan if d not in datos["desaparecidos"]]

    # Un modelo desaparece cuando ninguna de sus configuraciones vino: en el sitio
    # eso es ocultar el producto, y una configuración que falta es una variante.
    con_presencia = {p["id_modelo"] for p in datos["productos"]}
    con_presencia |= {configuraciones[cid]["id_modelo"] for cid in descartados if cid in configuraciones}
    desaparecidos_por_modelo = {}
    for d in datos["desaparecidos"]:
        desaparecidos_por_modelo.setdefault(d["id_modelo"], []).append(d["id"])
    datos["modelos_desaparecidos"] = [
        {"id_modelo": mid, "titulo": modelos[mid]["titulo"], "configuraciones": cids}
        for mid, cids in sorted(desaparecidos_por_modelo.items()) if mid not in con_presencia and mid in modelos
    ]

    resumen = {estado: sum(1 for p in datos["productos"] if p["estado_lista"] == estado)
               for estado in ESTADOS}
    resumen["desaparecidos"] = len(datos["desaparecidos"])
    resumen["modelos_desaparecidos"] = len(datos["modelos_desaparecidos"])
    resumen["ausentes"] = len(datos["ausentes"])
    resumen["posibles_mismos"] = len(datos["posibles_mismos"])
    resumen["falta_informacion"] = len(datos["falta_informacion"])
    resumen["excluidos"] = len(datos["excluidos_del_catalogo"])
    datos["comparacion"] = {"fecha": hoy.isoformat(), "resumen": resumen}
    return datos


def pesos(valor) -> str:
    return f"{valor:,}".replace(",", ".") if valor is not None else "—"


def fecha_corta(iso) -> str:
    return "/".join(reversed(iso.split("-"))) if iso else "—"


def cambio_de_costo(p: dict):
    """Cuánto subió (o bajó) el costo frente a la última lista, o None si es igual o no se sabe."""
    antes = p.get("costo_anterior_cop")
    if antes is None or antes == p["precio_proveedor_cop"]:
        return None
    return p["precio_proveedor_cop"] - antes


def costo_con_cambio(p: dict) -> str:
    delta = cambio_de_costo(p)
    if delta is None:
        return pesos(p["precio_proveedor_cop"])
    return (f"{pesos(p['costo_anterior_cop'])} → {pesos(p['precio_proveedor_cop'])} "
            f"({'+' if delta > 0 else '−'}{pesos(abs(delta))})")


def texto_de_cambio(p: dict) -> str:
    """La columna «Cambio» del Excel. Vive aquí y no en construir_entregables.py
    porque aquel importa openpyxl, que integración continua no instala."""
    estado = p.get("estado_lista")
    if estado == "costo_cambio":
        delta = p["precio_proveedor_cop"] - p["costo_anterior_cop"]
        return f"costo {'+' if delta > 0 else '−'}{pesos(abs(delta))}"
    delta = cambio_de_costo(p) if estado == "sin_precio_vigente" else None
    if delta is not None:
        return f"precio por investigar · costo {'+' if delta > 0 else '−'}{pesos(abs(delta))}"
    return {"nuevo": "nuevo", "configuracion_nueva": "memoria nueva", "sin_cambios": "igual",
            "sin_precio_vigente": "precio por investigar"}.get(estado, "")


def reporte(datos: dict) -> str:
    r = datos["comparacion"]["resumen"]
    por = {estado: [p for p in datos["productos"] if p["estado_lista"] == estado] for estado in ESTADOS}
    solo_precio = [p for p in por["sin_precio_vigente"] + por["configuracion_nueva"] if "precio" in p["pendiente"]]
    por_completar = {}
    for p in datos["productos"]:
        if COMPLETAR in (p.get("pendiente") or []):
            por_completar.setdefault(p["id_modelo"], (p.get("titulo_modelo") or p["titulo"],
                                                      descripciones.faltantes(p.get("descripcion"))))
    modelos_nuevos = {p["id_modelo"] for p in por["nuevo"]}
    L = [
        "# Cambios frente a lo conocido", "",
        f"- Lista del {fecha_corta(datos.get('fecha_lista'))}, comparada el "
        f"{fecha_corta(datos['comparacion']['fecha'])} con referencias/conocidos.json",
        f"- Nuevos: {r['nuevo']} ({len(modelos_nuevos)} modelos) · memoria nueva de un modelo conocido: "
        f"{r['configuracion_nueva']} · costo cambiado: {r['costo_cambio']} · sin cambios: {r['sin_cambios']} · "
        f"sin precio vigente: {r['sin_precio_vigente']} · desaparecidos: {r['desaparecidos']} "
        f"({r['modelos_desaparecidos']} modelos completos)",
        f"- Para el paso 4: {len(modelos_nuevos)} modelos por investigar enteros y "
        f"{len(solo_precio)} configuraciones solo por el precio",
        "", "## Nuevos — investigar todo", "",
    ]
    L += [f"- **{p['titulo']}** — costo {pesos(p['precio_proveedor_cop'])}" for p in por["nuevo"]] or ["Ninguno."]

    L += ["", "## Memoria nueva de un modelo conocido — investigar solo el precio", ""]
    L += [f"- **{p['titulo']}** — costo {pesos(p['precio_proveedor_cop'])}"
          + ("" if "precio" in p["pendiente"] else " (ya investigado en esta corrida)")
          for p in por["configuracion_nueva"]] or ["Ninguna."]

    # El costo anterior va aquí también: si no, una lista que llega después de
    # los 7 días de vigencia esconde todos los cambios de costo —el 15/10/2026
    # fueron 19, con «Costo cambiado: Ninguno»—.
    L += ["", "## Sin precio de mercado vigente — investigar solo el precio", ""]
    L += [f"- **{p['titulo']}** — costo {costo_con_cambio(p)}"
          + ("" if "precio" in p["pendiente"] else " (ya investigado en esta corrida)")
          for p in por["sin_precio_vigente"]] or ["Ninguno."]
    cambiaron = [p for p in por["sin_precio_vigente"] if cambio_de_costo(p)]
    if cambiaron:
        L += ["", f"{len(cambiaron)} de ellos cambiaron de costo frente a la última lista."]

    L += ["", "## Descripciones heredadas por completar — solo la ficha técnica o la caja", ""]
    L += [f"- **{t}** — falta {' y '.join(f'«{s}»' for s in faltan)}"
          for t, faltan in por_completar.values()] or ["Ninguna."]

    L += ["", "## Costo cambiado", ""]
    for p in sorted(por["costo_cambio"], key=lambda x: x["precio_proveedor_cop"] - (x["costo_anterior_cop"] or 0)):
        delta = p["precio_proveedor_cop"] - (p["costo_anterior_cop"] or 0)
        margen = f"{p['margen']:.1%}".replace(".", ",") if p.get("margen") is not None else "—"
        alerta = " · ⚠️ POR DEBAJO DEL COSTO" if (p.get("margen") or 0) < 0 else ""
        ganancia = p.get("ganancia_cop")
        ganancia = "—" if ganancia is None else ("−" if ganancia < 0 else "") + pesos(abs(ganancia))
        L.append(f"- **{p['titulo']}** — {pesos(p['costo_anterior_cop'])} → {pesos(p['precio_proveedor_cop'])} "
                 f"({'+' if delta > 0 else '−'}{pesos(abs(delta))}) · ganancia {ganancia} · margen {margen}{alerta}")
    if not por["costo_cambio"]:
        L.append("Ninguno.")

    L += ["", "## Modelos que desaparecieron completos — ocultarlos en el sitio", ""]
    L += [f"- **{d['titulo']}** ({len(d['configuraciones'])} "
          f"configuraci{'ón' if len(d['configuraciones']) == 1 else 'ones'})"
          for d in datos["modelos_desaparecidos"]] or ["Ninguno."]

    L += ["", "## Desaparecidos — no vinieron en un mensaje que sí llegó", ""]
    L += [f"- **{d['titulo']}** — visto el {fecha_corta(d['visto_por_ultima_vez'])} "
          f"a {pesos(d['ultimo_costo_cop'])} ({', '.join(d['bloques'])})"
          for d in datos["desaparecidos"]] or ["Ninguno."]

    L += ["", "## Falta información del proveedor — confirmar el descarte con la persona", ""]
    for d in datos["falta_informacion"]:
        if d["posible_mismo_de"]:
            como = (f"si es el mismo equipo, equivalencia `{d['id']}` → `{d['posible_mismo_de']}`; "
                    f"si es otra referencia, equivalencia `{d['id']}` → sí mismo")
        else:
            como = d["resolver"]
        L.append(f"- **{d['titulo']}** — {d['motivo']}. Con el dato: {como}, y volver a parsear")
    if not datos["falta_informacion"]:
        L.append("Ninguno.")
    if datos["ausentes"]:
        L += ["", f"{len(datos['ausentes'])} conocidos siguen ausentes desde antes (ya se reportaron como "
              "desaparecidos en su momento)."]

    if datos.get("excluidos_del_catalogo"):
        L += ["", "## Excluidos del catálogo — decisión del negocio, no se investigan", ""]
        L += [f"- **{d['titulo']}** — {d['motivo']}" for d in datos["excluidos_del_catalogo"]]
        L += ["", "Para readmitir uno: `python scripts/conocidos.py excluir <id del modelo> --quitar` "
                  "y volver a correr la comparación."]

    L += ["", "## Conocidos que vinieron pero no entran", ""]
    L += [f"- **{d['titulo']}** — {d['motivo']}" for d in datos["conocidos_descartados"]] or ["Ninguno."]

    L += ["", "## Sin cambios", "", f"{r['sin_cambios']} productos con el mismo costo y el precio vigente."]
    return "\n".join(L) + "\n"


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("productos", help="productos.json del parser; se escribe en su sitio")
    ap.add_argument("--base", default=str(conocidos.BASE))
    ap.add_argument("--excluidos", default=str(conocidos.EXCLUIDOS))
    ap.add_argument("--hoy", default=date.today().isoformat(), help="fecha para la vigencia del precio")
    ap.add_argument("--cambios", help="reporte; por omisión 1-lista/cambios.md junto a productos.json")
    ap.add_argument("--decisiones", help="decisiones de la persona; por omisión 1-lista/decisiones.json")
    args = ap.parse_args()

    ruta = Path(args.productos)
    datos = json.loads(ruta.read_text(encoding="utf-8"))
    # Repetible (22/10/2026): la comparación hereda datos al escribir, así que correrla otra vez
    # sobre su propia salida no da lo mismo —ese día cada exclusión obligó a rehacer la corrida
    # desde el parser—. La primera vez guarda lo que dejó el parser; las siguientes parten de ahí.
    copia = ruta.parent / rutas.LISTA.name / "productos-del-parser.json"
    if "comparacion" in datos:
        if not copia.is_file():
            raise SystemExit(f"{ruta} ya pasó por la comparación y no está {copia.name}, la copia de lo que dejó "
                             "el parser: vuelve a correr el parser.")
        datos = json.loads(copia.read_text(encoding="utf-8"))
        print(f"Parto de {copia.name}, lo que dejó el parser: si ya hiciste el paso 4, vuelve a correr "
              "asignar_precios.py y redactar_fichas.py, que leen lo que está en disco.")
    else:
        copia.parent.mkdir(parents=True, exist_ok=True)
        copia.write_text(json.dumps(datos, ensure_ascii=False, indent=2), encoding="utf-8")
    ruta_decisiones = Path(args.decisiones) if args.decisiones else ruta.parent / rutas.LISTA.name / "decisiones.json"
    for aviso in decisiones_de_la_persona.aplicar(datos, decisiones_de_la_persona.cargar(ruta_decisiones)):
        print("decisión sin aplicar:", aviso)
    comparar(datos, conocidos.cargar(args.base), date.fromisoformat(args.hoy),
             conocidos.cargar_excluidos(args.excluidos))
    ruta.write_text(json.dumps(datos, ensure_ascii=False, indent=2), encoding="utf-8")
    cambios = Path(args.cambios) if args.cambios else ruta.parent / rutas.LISTA.name / "cambios.md"
    cambios.parent.mkdir(parents=True, exist_ok=True)
    cambios.write_text(reporte(datos), encoding="utf-8", newline="")
    r = datos["comparacion"]["resumen"]
    print(f"{r['nuevo']} nuevos | {r['configuracion_nueva']} memoria nueva | {r['costo_cambio']} costo cambiado | "
          f"{r['sin_cambios']} sin cambios | {r['sin_precio_vigente']} sin precio vigente | "
          f"{r['desaparecidos']} desaparecidos ({r['modelos_desaparecidos']} modelos)")
    if r.get("excluidos"):
        print(f"{r['excluidos']} excluidos del catálogo por decisión del negocio (ver el reporte)")
    print(f"Reporte en {cambios}")


if __name__ == "__main__":
    main()
