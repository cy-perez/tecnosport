#!/usr/bin/env python3
"""Paso de diferencias: qué trae la lista de hoy frente a lo que ya se investigó.

Corre entre el parser (paso 2) y la revisión con la persona (paso 3), y una sola
vez por lista:

    python3 scripts/comparar_lista.py catalogo/productos.json

Clasifica cada producto contra `referencias/conocidos.json`:

- **nuevo**: no está en la base. Se investiga entero en el paso 4.
- **sin_precio_vigente**: conocido, pero su precio de mercado pasó de los 7 días
  o nunca lo tuvo. Hereda descripción y colores; se investiga solo el precio.
- **costo_cambio**: conocido, precio vigente, y el proveedor cambió el costo.
  Hereda todo y el margen se recalcula con el costo de hoy.
- **sin_cambios**: conocido, precio vigente, mismo costo. Nada que hacer.

Y anota los **desaparecidos**: conocidos que no vinieron hoy aunque llegó el
mensaje de la lista en que suelen venir (Android, variedad, gama alta). Es la
señal de que el proveedor ya no los tiene.

Escribe de vuelta en productos.json —`estado_lista`, `pendiente`,
`costo_anterior_cop` y lo heredado en cada producto; `desaparecidos` y
`comparacion` arriba— y un `cambios.md` para mostrar en el paso 3. Los scripts
del paso 4 leen `pendiente` (ver pendientes.py) y no tocan lo que no les toca.
"""

import argparse
import json
import re
import sys
from copy import deepcopy
from datetime import date
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import conocidos  # noqa: E402
from asignar_precios import aplicar_margen  # noqa: E402
from pendientes import TAREAS  # noqa: E402

# Lo que se hereda siempre de un conocido, y lo que solo se hereda con el precio
# vigente. Los colores van aparte: solo se heredan si la lista marca los mismos.
HEREDADO = ("descripcion", "meta_titulo", "meta_descripcion")
PRECIO = ("precio_mercado_cop", "nivel_precio", "fuentes_precio", "notas_precio", "fecha_precio")
# Las alertas que pone aplicar_margen: se quitan antes de recalcular para que
# correr esto dos veces no las duplique.
ALERTAS_DE_MARGEN = ("POR DEBAJO DEL COSTO", "margen inusual")
ESTADOS = ("nuevo", "costo_cambio", "sin_cambios", "sin_precio_vigente")
# La tarea de colores no la hace ningún script: es confirmar contra la paleta
# oficial y escribir `colores_oficiales` a mano (regla 13).
COLORES = "colores"
# El sufijo de SIM del id: sin él, dos ids pueden ser el mismo equipo.
RE_SUFIJO_SIM = re.compile(r"-(1-sim|dual-sim|sim-esim|esim)$")


def comparar(datos: dict, base: dict, hoy: date) -> dict:
    """Escribe la comparación en `datos`, en su sitio, y lo devuelve."""
    for p in datos["productos"]:
        # Los supuestos que puso el parser, aparte de los que se heredan: al
        # consolidar solo se guardan los de la investigación.
        p.setdefault("supuestos_lista", list(p.get("supuestos") or []))
        e = base.get(p["id"])
        if e is None:
            p["estado_lista"], p["pendiente"], p["costo_anterior_cop"] = "nuevo", list(TAREAS), None
            continue

        for campo in HEREDADO:
            p[campo] = deepcopy(e.get(campo))
        p["costo_anterior_cop"] = e.get("ultimo_costo_cop")

        # Los colores dependen de la lista: se heredan solo si hoy marca los mismos
        # que la vez en que se decidieron. Si no, ni los oficiales ni la sección
        # de la descripción: el iPhone 17 Pro 256 salía en Azul con la línea en 🧡.
        hoy_marca = sorted(p.get("colores_familia") or [])
        mismos_colores = hoy_marca == sorted(e.get("colores_de_la_lista") or [])
        p["colores_oficiales"] = deepcopy(e.get("colores_oficiales")) if mismos_colores else []
        p["descripcion"] = conocidos.con_colores(p["descripcion"], p["colores_oficiales"])
        p["supuestos"] = list(dict.fromkeys(
            p["supuestos_lista"] + (e.get("supuestos_investigacion") or [])
            + ((e.get("supuestos_colores") or []) if mismos_colores else [])))
        p["revisar"] = [r for r in p.get("revisar") or [] if not r.startswith("los colores de la lista cambiaron")]
        if not mismos_colores:
            p["revisar"].append(
                f"los colores de la lista cambiaron (hoy: {', '.join(hoy_marca) or 'sin marcar'}; "
                f"la última vez: {', '.join(sorted(e.get('colores_de_la_lista') or [])) or 'sin marcar'}): "
                "confirmar contra la paleta oficial y escribir colores_oficiales; la sección de "
                "colores de la descripción se rehace al consolidar")

        if conocidos.precio_vigente(e, hoy):
            for campo in PRECIO:
                p[campo] = deepcopy(e.get(campo))
            p["pendiente"] = [] if mismos_colores else [COLORES]
            p["estado_lista"] = ("sin_cambios" if e.get("ultimo_costo_cop") == p["precio_proveedor_cop"]
                                 else "costo_cambio")
        else:
            # Un precio investigado en esta corrida trae la fecha de su consulta,
            # distinta de la de la base. Si ya está, no se borra: volver a correr
            # esto después del paso 4 no puede deshacer el paso 4.
            fresco = bool(p.get("precio_mercado_cop") and p.get("fecha_precio")
                          and p.get("fecha_precio") != e.get("fecha_precio"))
            if not fresco:
                for campo in PRECIO:
                    p[campo] = [] if campo in ("fuentes_precio", "notas_precio") else None
            p["pendiente"] = ([] if fresco else ["precio"]) + ([] if mismos_colores else [COLORES])
            p["estado_lista"] = "sin_precio_vigente"

        p["revisar"] = [r for r in p.get("revisar") or [] if not r.startswith(ALERTAS_DE_MARGEN)]
        aplicar_margen(p)

    presentes = {p["id"] for p in datos["productos"]}
    # Un conocido que vino pero quedó descartado —el S25 Ultra sin precio, el
    # 08/10/2026— no desapareció: el proveedor lo tiene y no entra por otra razón.
    descartados = {d.get("id"): d for d in datos.get("descartados") or []}
    datos["conocidos_descartados"] = [
        {"id": pid, "titulo": e["titulo"], "motivo": descartados[pid].get("motivo")}
        for pid, e in sorted(base.items()) if pid not in presentes and pid in descartados
    ]
    presentes |= set(descartados)
    llegaron = set(datos.get("bloques") or [])
    faltan = [
        {"id": pid, "titulo": e["titulo"], "bloques": e.get("bloques") or [],
         "visto_por_ultima_vez": e.get("visto_por_ultima_vez"),
         "ultimo_costo_cop": e.get("ultimo_costo_cop")}
        for pid, e in base.items()
        if pid not in presentes and set(e.get("bloques") or []) & llegaron
    ]
    faltan.sort(key=lambda d: (d["visto_por_ultima_vez"] or "", d["id"]), reverse=True)

    # Un nuevo y un faltante que solo difieren en la SIM son, casi siempre, el
    # mismo equipo con la anotación de la SIM pegada a otra línea: el Moto G17
    # Power salió como nuevo y como desaparecido el 08/10/2026. No se juntan
    # solos —puede ser otra referencia—: se muestran juntos para decidirlo.
    sin_sim = lambda pid: RE_SUFIJO_SIM.sub("", pid)  # noqa: E731
    datos["posibles_mismos"] = []
    for p in datos["productos"]:
        if p["estado_lista"] != "nuevo":
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
    for e in base.values():
        for b in e.get("bloques") or []:
            ultima_vez[b] = max(ultima_vez.get(b, ""), e.get("visto_por_ultima_vez") or "")
    datos["desaparecidos"] = [d for d in faltan
                              if any(d["visto_por_ultima_vez"] == ultima_vez.get(b) for b in d["bloques"])]
    datos["ausentes"] = [d for d in faltan if d not in datos["desaparecidos"]]

    resumen = {estado: sum(1 for p in datos["productos"] if p["estado_lista"] == estado)
               for estado in ESTADOS}
    resumen["desaparecidos"] = len(datos["desaparecidos"])
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
    return {"nuevo": "nuevo", "sin_cambios": "igual",
            "sin_precio_vigente": "precio por investigar"}.get(estado, "")


def reporte(datos: dict) -> str:
    r = datos["comparacion"]["resumen"]
    por = {estado: [p for p in datos["productos"] if p["estado_lista"] == estado] for estado in ESTADOS}
    investigar = len(por["nuevo"]) + sum(1 for p in por["sin_precio_vigente"] if p["pendiente"])
    L = [
        "# Cambios frente a lo conocido", "",
        f"- Lista del {fecha_corta(datos.get('fecha_lista'))}, comparada el "
        f"{fecha_corta(datos['comparacion']['fecha'])} con referencias/conocidos.json",
        f"- Nuevos: {r['nuevo']} · costo cambiado: {r['costo_cambio']} · sin cambios: "
        f"{r['sin_cambios']} · sin precio vigente: {r['sin_precio_vigente']} · "
        f"desaparecidos: {r['desaparecidos']}",
        f"- Para el paso 4: {investigar} productos ({len(por['nuevo'])} enteros, "
        f"{investigar - len(por['nuevo'])} solo el precio)",
        "", "## Nuevos — investigar todo", "",
    ]
    L += [f"- **{p['titulo']}** — costo {pesos(p['precio_proveedor_cop'])}" for p in por["nuevo"]] or ["Ninguno."]

    L += ["", "## Sin precio de mercado vigente — investigar solo el precio", ""]
    L += [f"- **{p['titulo']}** — costo {pesos(p['precio_proveedor_cop'])}"
          + ("" if p["pendiente"] else " (ya investigado en esta corrida)")
          for p in por["sin_precio_vigente"]] or ["Ninguno."]

    L += ["", "## Costo cambiado", ""]
    for p in sorted(por["costo_cambio"], key=lambda x: x["precio_proveedor_cop"] - (x["costo_anterior_cop"] or 0)):
        delta = p["precio_proveedor_cop"] - p["costo_anterior_cop"]
        margen = f"{p['margen']:.1%}".replace(".", ",") if p.get("margen") is not None else "—"
        alerta = " · ⚠️ POR DEBAJO DEL COSTO" if (p.get("margen") or 0) < 0 else ""
        ganancia = p.get("ganancia_cop")
        ganancia = "—" if ganancia is None else ("−" if ganancia < 0 else "") + pesos(abs(ganancia))
        L.append(f"- **{p['titulo']}** — {pesos(p['costo_anterior_cop'])} → {pesos(p['precio_proveedor_cop'])} "
                 f"({'+' if delta > 0 else '−'}{pesos(abs(delta))}) · ganancia {ganancia} · margen {margen}{alerta}")
    if not por["costo_cambio"]:
        L.append("Ninguno.")

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
    print(f"{r['nuevo']} nuevos | {r['costo_cambio']} costo cambiado | {r['sin_cambios']} sin cambios | "
          f"{r['sin_precio_vigente']} sin precio vigente | {r['desaparecidos']} desaparecidos")
    print(f"Reporte en {cambios}")


if __name__ == "__main__":
    main()
