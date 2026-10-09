#!/usr/bin/env python3
"""Paso de diferencias: qué trae la lista de hoy frente a lo que ya se investigó.

Corre entre el parser (paso 2) y la revisión con la persona (paso 3), y una sola
vez por lista:

    python3 scripts/comparar_lista.py catalogo/productos.json

Clasifica cada producto (cada configuración: modelo + memoria + SIM) contra
`referencias/conocidos.json`:

- **nuevo**: el modelo no está en la base. Se investiga entero en el paso 4.
- **configuracion_nueva**: el modelo es conocido y esta memoria no. Hereda la
  ficha del modelo; se investiga solo su precio.
- **sin_precio_vigente**: configuración conocida, pero su precio de mercado pasó
  de los 7 días o nunca lo tuvo. Hereda la ficha; se investiga solo el precio.
- **costo_cambio**: conocida, precio vigente, y el proveedor cambió el costo.
  Hereda todo y el margen se recalcula con el costo de hoy.
- **sin_cambios**: conocida, precio vigente, mismo costo. Nada que hacer.

Y anota los **desaparecidos** —configuraciones que vinieron en la última lista de
su mensaje y hoy no, aunque el mensaje llegó—, los **modelos desaparecidos**
—todas sus configuraciones faltan: en el sitio es ocultar el producto, no una
variante—, los **posibles el mismo** —un nuevo y un faltante que solo difieren
en la SIM— y los conocidos que vinieron pero no entran.

Los colores ya no se heredan como texto de la ficha: son una variante que el
cliente elige (decisión del 08/10/2026). Cada producto lleva la paleta del
modelo en `colores_oficiales` y, en `colores_sugeridos`, los de esa paleta que
coinciden con los emojis de la lista, para premarcarlos en el panel.

Escribe de vuelta en productos.json y un `cambios.md` para mostrar en el paso 3.
Los scripts del paso 4 leen `pendiente` (ver pendientes.py).
"""

import argparse
import json
import re
import sys
import unicodedata
from copy import deepcopy
from datetime import date
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import conocidos  # noqa: E402
from asignar_precios import aplicar_margen  # noqa: E402
from pendientes import TAREAS  # noqa: E402

PRECIO = conocidos.PRECIO
# Las alertas que pone aplicar_margen: se quitan antes de recalcular para que
# correr esto dos veces no las duplique.
ALERTAS_DE_MARGEN = ("POR DEBAJO DEL COSTO", "margen inusual")
ALERTA_CAPACIDAD = "la descripción del modelo nombra un almacenamiento"
ESTADOS = ("nuevo", "configuracion_nueva", "costo_cambio", "sin_cambios", "sin_precio_vigente")
# El sufijo de SIM del id: sin él, dos ids pueden ser el mismo equipo.
RE_SUFIJO_SIM = re.compile(r"-(1-sim|dual-sim|sim-esim|esim)$")
# Una descripción de modelo que nombra un almacenamiento se escribió para una sola
# configuración: con una segunda, dice algo falso de ella.
RE_ALMACENAMIENTO = re.compile(r"\d+\s?(?:GB|TB) de almacenamiento|\bde \d+\s?(?:GB|TB)\b")


def sin_tildes(texto: str) -> str:
    return unicodedata.normalize("NFKD", texto).encode("ascii", "ignore").decode().lower()


def colores_sugeridos(familias, paleta):
    """Los colores de la paleta que nombran alguna de las familias que marcó la lista.

    Una sugerencia, no una decisión: la persona confirma en el panel contra el
    mensaje del proveedor. Sin emojis no se sugiere nada.
    """
    claves = [sin_tildes(f).split()[0] for f in familias or []]
    return [c for c in paleta or [] if any(k in sin_tildes(c) for k in claves)]


def comparar(datos: dict, base: dict, hoy: date) -> dict:
    """Escribe la comparación en `datos`, en su sitio, y lo devuelve."""
    modelos, configuraciones = base["modelos"], base["configuraciones"]
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

        p["descripcion"] = m["descripcion"]
        p["meta_titulo"] = m["meta_titulo"]
        p["meta_descripcion"] = m["meta_descripcion"]
        p["colores_oficiales"] = deepcopy(m["paleta"])
        p["colores_sugeridos"] = colores_sugeridos(p.get("colores_familia"), m["paleta"])
        p["fuentes_ficha"] = deepcopy(m["fuentes_ficha"])
        p["supuestos"] = list(dict.fromkeys(
            p["supuestos_lista"] + m["supuestos_investigacion"] + m["supuestos_paleta"]))
        p["revisar"] = [r for r in p.get("revisar") or []
                        if not r.startswith(ALERTAS_DE_MARGEN + (ALERTA_CAPACIDAD,))]

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
            if e is None and RE_ALMACENAMIENTO.search(m["descripcion"]):
                p["revisar"].append(
                    f"{ALERTA_CAPACIDAD} y llegó otra configuración: quitar la capacidad de la descripción "
                    "para que valga para todas")

        aplicar_margen(p)

    presentes = {p["id"] for p in datos["productos"]}
    # Un conocido que vino pero quedó descartado —el S25 Ultra sin precio, el
    # 08/10/2026— no desapareció: el proveedor lo tiene y no entra por otra razón.
    descartados = {d.get("id"): d for d in datos.get("descartados") or []}
    datos["conocidos_descartados"] = [
        {"id": cid, "titulo": c["titulo"], "motivo": descartados[cid].get("motivo")}
        for cid, c in sorted(configuraciones.items()) if cid not in presentes and cid in descartados
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

    # Un nuevo y un faltante que solo difieren en la SIM son, casi siempre, el
    # mismo equipo con la anotación de la SIM pegada a otra línea: el Moto G17
    # Power salió como nuevo y como desaparecido el 08/10/2026. No se juntan
    # solos —puede ser otra referencia—: se muestran juntos para decidirlo.
    sin_sim = lambda pid: RE_SUFIJO_SIM.sub("", pid)  # noqa: E731
    datos["posibles_mismos"] = []
    for p in datos["productos"]:
        if p["estado_lista"] not in ("nuevo", "configuracion_nueva"):
            continue
        pareja = next((d for d in faltan if sin_sim(d["id"]) == sin_sim(p["id"])), None)
        if pareja:
            faltan.remove(pareja)
            datos["posibles_mismos"].append({"nuevo": p["id"], "titulo_nuevo": p["titulo"],
                                             "conocido": pareja["id"], "titulo_conocido": pareja["titulo"]})

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
    datos["comparacion"] = {"fecha": hoy.isoformat(), "resumen": resumen}
    return datos


def pesos(valor) -> str:
    return f"{valor:,}".replace(",", ".") if valor is not None else "—"


def fecha_corta(iso) -> str:
    return "/".join(reversed(iso.split("-"))) if iso else "—"


def texto_de_cambio(p: dict) -> str:
    """La columna «Cambio» del Excel. Vive aquí y no en construir_entregables.py
    porque aquel importa openpyxl, que integración continua no instala."""
    estado = p.get("estado_lista")
    if estado == "costo_cambio":
        delta = p["precio_proveedor_cop"] - p["costo_anterior_cop"]
        return f"costo {'+' if delta > 0 else '−'}{pesos(abs(delta))}"
    return {"nuevo": "nuevo", "configuracion_nueva": "memoria nueva", "sin_cambios": "igual",
            "sin_precio_vigente": "precio por investigar"}.get(estado, "")


def reporte(datos: dict) -> str:
    r = datos["comparacion"]["resumen"]
    por = {estado: [p for p in datos["productos"] if p["estado_lista"] == estado] for estado in ESTADOS}
    solo_precio = [p for p in por["sin_precio_vigente"] + por["configuracion_nueva"] if p["pendiente"]]
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
          + ("" if p["pendiente"] else " (ya investigado en esta corrida)")
          for p in por["configuracion_nueva"]] or ["Ninguna."]

    L += ["", "## Sin precio de mercado vigente — investigar solo el precio", ""]
    L += [f"- **{p['titulo']}** — costo {pesos(p['precio_proveedor_cop'])}"
          + ("" if p["pendiente"] else " (ya investigado en esta corrida)")
          for p in por["sin_precio_vigente"]] or ["Ninguno."]

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

    L += ["", "## Posibles el mismo — cambió solo la SIM", ""]
    L += [f"- **{d['titulo_nuevo']}** (nuevo) y **{d['titulo_conocido']}** (conocido): si es el mismo "
          f"equipo, agrega la equivalencia `{d['nuevo']}` → `{d['conocido']}` y vuelve a parsear"
          for d in datos["posibles_mismos"]] or ["Ninguno."]
    if datos["ausentes"]:
        L += ["", f"{len(datos['ausentes'])} conocidos siguen ausentes desde antes (ya se reportaron como "
              "desaparecidos en su momento)."]

    L += ["", "## Conocidos que vinieron pero no entran", ""]
    L += [f"- **{d['titulo']}** — {d['motivo']}" for d in datos["conocidos_descartados"]] or ["Ninguno."]

    L += ["", "## Sin cambios", "", f"{r['sin_cambios']} productos con el mismo costo y el precio vigente."]
    return "\n".join(L) + "\n"


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("productos", help="productos.json del parser; se escribe en su sitio")
    ap.add_argument("--base", default=str(conocidos.BASE))
    ap.add_argument("--hoy", default=date.today().isoformat(), help="fecha para la vigencia del precio")
    ap.add_argument("--cambios", help="reporte; por omisión cambios.md junto a productos.json")
    args = ap.parse_args()

    ruta = Path(args.productos)
    datos = json.loads(ruta.read_text(encoding="utf-8"))
    comparar(datos, conocidos.cargar(args.base), date.fromisoformat(args.hoy))
    ruta.write_text(json.dumps(datos, ensure_ascii=False, indent=2), encoding="utf-8")
    cambios = Path(args.cambios) if args.cambios else ruta.with_name("cambios.md")
    cambios.write_text(reporte(datos), encoding="utf-8", newline="")
    r = datos["comparacion"]["resumen"]
    print(f"{r['nuevo']} nuevos | {r['configuracion_nueva']} memoria nueva | {r['costo_cambio']} costo cambiado | "
          f"{r['sin_cambios']} sin cambios | {r['sin_precio_vigente']} sin precio vigente | "
          f"{r['desaparecidos']} desaparecidos ({r['modelos_desaparecidos']} modelos)")
    print(f"Reporte en {cambios}")


if __name__ == "__main__":
    main()
