#!/usr/bin/env python3
"""
Marcas a ojo sobre el reporte de fotos-estudio-degradado.

    python marcar.py SALIDA --lista [--zip]                 # --zip rehace el ZIP de entrega
    python marcar.py SALIDA NOMBRE [NOMBRE ...] --repetir "motivo"
    python marcar.py SALIDA NOMBRE --revisar "motivo"
    python marcar.py SALIDA NOMBRE --aprobar ["nota"]
    python marcar.py SALIDA NOMBRE --excluir X0,Y0,X1,Y1 [--excluir ...]
    python marcar.py SALIDA NOMBRE --limpiar                # quita marcas y aprobación
    python marcar.py SALIDA NOMBRE --limpiar-exclusiones

Lo que el script no detecta y hay que marcar a ojo:
  REVISAR  manos, ganchos, maniquíes o soportes visibles (se conservan, no se borran);
           restos del fondo original; varias piezas que quizá no son un solo producto.
  REPETIR  marca de agua o etiqueta de precio encima del producto; foto ajena sin
           autorización; productos distintos en la misma foto; partes perdidas.

--aprobar sólo levanta los motivos REVISAR automáticos que ya se revisaron a tamaño real
(p. ej. una ampliación de 1,6× que se ve nítida). Un REPETIR no se aprueba.

--excluir quita del recorte un rectángulo en píxeles de la foto original ya girada
(ampliar.py --original muestra las coordenadas). Sirve para objetos separados del
producto: una etiqueta colgante, un logo flotante que el modelo conservó. Nunca para
algo encima del producto, porque eso sería borrar parte del producto. Se aplica al
reprocesar:  python procesar.py -o SALIDA --solo NOMBRE

Las fotos REPETIR salen de las carpetas de entrega (quedan en .trabajo/retenidas) y
vuelven si se quita la marca. Después de cada cambio se regeneran las hojas.
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import entorno  # noqa: E402  (sólo biblioteca estándar)

entorno.exigir()

import hojas  # noqa: E402
import reporte  # noqa: E402


def utf8() -> None:
    for flujo in (sys.stdout, sys.stderr):
        try:
            flujo.reconfigure(encoding="utf-8", errors="replace")
        except Exception:
            pass


def leer_zona(texto: str, entrada: dict) -> list[int]:
    try:
        x0, y0, x1, y1 = [int(round(float(v))) for v in texto.replace(";", ",").split(",")]
    except ValueError:
        raise ValueError(f"zona no válida «{texto}»: usa X0,Y0,X1,Y1 en píxeles")
    x0, x1 = sorted((x0, x1))
    y0, y1 = sorted((y0, y1))
    ancho, alto = entrada.get("ancho"), entrada.get("alto")
    if ancho and alto:
        x0, x1 = max(0, x0), min(int(ancho), x1)
        y0, y1 = max(0, y0), min(int(alto), y1)
    if x1 - x0 < 2 or y1 - y0 < 2:
        raise ValueError(f"la zona «{texto}» queda vacía dentro de la foto ({ancho}×{alto} px)")
    return [x0, y0, x1, y1]


def listar(rep: dict) -> None:
    for f in reporte.ordenar(rep["fotos"]):
        estado = reporte.estado_final(f)
        extra = []
        if f.get("revisada") and estado != "REPETIR":
            extra.append("revisada a ojo")
        if f.get("exclusiones"):
            extra.append(f"{len(f['exclusiones'])} exclusión(es)")
        print(f"{estado:8} {f['nombre']}" + (f"  ({', '.join(extra)})" if extra else ""))
        for m in f.get("motivos", []):
            print(f"         • {m}")
    print("\n" + reporte.texto_resumen(rep))


def main() -> int:
    utf8()
    p = argparse.ArgumentParser(description="Marcas a ojo para fotos-estudio-degradado",
                                formatter_class=argparse.RawDescriptionHelpFormatter, epilog=__doc__)
    p.add_argument("salida", help="carpeta de salida (la que tiene reporte.json)")
    p.add_argument("nombres", nargs="*", help="nombres de las fotos (como aparecen en el reporte)")
    g = p.add_mutually_exclusive_group()
    g.add_argument("--lista", action="store_true", help="muestra el estado de cada foto")
    g.add_argument("--repetir", metavar="MOTIVO")
    g.add_argument("--revisar", metavar="MOTIVO")
    g.add_argument("--aprobar", nargs="?", const="", metavar="NOTA")
    g.add_argument("--excluir", action="append", metavar="X0,Y0,X1,Y1")
    g.add_argument("--limpiar", action="store_true", help="quita las marcas a ojo y la aprobación")
    g.add_argument("--limpiar-exclusiones", action="store_true")
    p.add_argument("--zip", action="store_true", help="vuelve a generar el ZIP de entrega")
    a = p.parse_args()

    raiz = Path(a.salida)
    rep = reporte.cargar(raiz)
    if not rep:
        print(f"No hay {reporte.NOMBRE} en {raiz}")
        return 1
    fotos = {f["nombre"]: f for f in rep["fotos"]}
    accion = a.repetir or a.revisar or a.aprobar is not None or a.excluir or a.limpiar or a.limpiar_exclusiones
    if a.lista or not accion:
        listar(rep)
        if a.zip:
            print(f"ZIP: {reporte.empaquetar(raiz, rep)}")
        return 0
    if not a.nombres:
        p.error("indica al menos un NOMBRE")
    desconocidos = [n for n in a.nombres if n not in fotos]
    if desconocidos:
        print(f"No están en el reporte: {', '.join(desconocidos)}\nNombres: {', '.join(sorted(fotos))}")
        return 1

    cambios, reprocesar = [], []
    for n in a.nombres:
        f = fotos[n]
        antes = reporte.estado_final(f)
        if a.repetir:
            f.setdefault("marcas", []).append({"estado": "REPETIR", "motivo": a.repetir.strip(), "fecha": reporte.ahora()})
        elif a.revisar:
            f.setdefault("marcas", []).append({"estado": "REVISAR", "motivo": a.revisar.strip(), "fecha": reporte.ahora()})
        elif a.aprobar is not None:
            if antes == "REPETIR":
                print(f"{n}: está en REPETIR y eso no se aprueba a ojo; hay que repetir la foto.")
                continue
            if any(m["estado"] == "REVISAR" for m in f.get("marcas", [])):
                print(f"{n}: conserva marcas REVISAR puestas a ojo; quítalas con --limpiar si ya no aplican.")
            f["revisada"] = {"fecha": reporte.ahora(), "nota": a.aprobar.strip()}
        elif a.excluir:
            try:
                zonas = [leer_zona(z, f.get("entrada") or {}) for z in a.excluir]
            except ValueError as e:
                print(f"{n}: {e}")
                return 1
            f.setdefault("exclusiones", []).extend(zonas)
            reprocesar.append(n)
        elif a.limpiar:
            f["marcas"], f["revisada"] = [], None
        elif a.limpiar_exclusiones:
            if f.get("exclusiones"):
                reprocesar.append(n)
            f["exclusiones"] = []
        despues = reporte.estado_final(f)
        if despues == "REPETIR" and f.get("salidas"):
            k = reporte.retirar_salidas(raiz, f)
            print(f"{n}: {k} archivo(s) retirados de las carpetas de entrega.")
        elif despues != "REPETIR" and f.get("salidas_retenidas"):
            k = reporte.restaurar_salidas(raiz, f)
            print(f"{n}: {k} archivo(s) devueltos a las carpetas de entrega.")
        cambios.append((n, antes, despues))

    reporte.guardar(raiz, rep)
    rutas, ubicacion = hojas.generar(raiz, rep, int(rep.get("configuracion", {}).get("fotos_por_hoja", 8)))
    rep["hojas_revision"] = [r.name for r in rutas]
    reporte.guardar(raiz, rep)
    for n, antes, despues in cambios:
        flecha = f"{antes} → {despues}" if antes != despues else despues
        print(f"{n}: {flecha} · {raiz / ubicacion.get(n, '?')}")
    if reprocesar:
        script = Path(__file__).resolve().parent / "procesar.py"
        print(f"Para aplicar las exclusiones: python {script} -o \"{raiz}\" --solo {','.join(reprocesar)}")
    print(reporte.texto_resumen(rep))
    if a.zip:
        print(f"ZIP: {reporte.empaquetar(raiz, rep)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
