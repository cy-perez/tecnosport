#!/usr/bin/env python3
"""
Arma los entregables finales a partir de productos.json ya enriquecido.

Uso:
    python3 construir_entregables.py productos.json --salida catalogo/entregables

Produce:
    entregables/fichas/<Modelo>/           una carpeta por modelo, que se queda: su
                                           ficha, «Fotos originales» y, cuando se
                                           procesan, «Fotos procesadas» (ver fichas.py)
    entregables/comparativo-<fecha>.xlsx   título / precio lista / precio mercado / ganancia

Hasta el 08/10/2026 salía un ZIP con una carpeta por SKU que se rehacía en cada
corrida. La carpeta por modelo lo reemplaza porque es donde viven también las
fotos: un ZIP que se borra y se vuelve a armar no puede guardar lo que la
persona descarga a mano.
"""

import argparse
import json
from datetime import date
from pathlib import Path

from openpyxl import Workbook
from openpyxl.styles import Alignment, Font, PatternFill
from openpyxl.utils import get_column_letter

import sys  # noqa: E402
sys.path.insert(0, str(Path(__file__).resolve().parent))
from comparar_lista import texto_de_cambio  # noqa: E402
import fichas  # noqa: E402


def construir(datos: dict, salida: Path):
    fecha = datos.get("fecha_lista") or date.today().isoformat()
    salida.mkdir(parents=True, exist_ok=True)
    carpetas = fichas.escribir(datos, salida / "fichas")
    ruta_xlsx = salida / f"comparativo-{fecha}.xlsx"
    escribir_excel(datos, ruta_xlsx, fecha)
    sin_fotos = [c.name for c in carpetas
                 if not any((c / fichas.ORIGINALES).iterdir())]
    return carpetas, ruta_xlsx, sin_fotos


def escribir_excel(datos: dict, ruta: Path, fecha: str):
    wb = Workbook()
    ws = wb.active
    ws.title = "Comparativo"
    encabezados = [
        "Título del producto",
        "Precio lista proveedor (COP)",
        "Precio promedio mercado Colombia (COP)",
        "Ganancia (COP)",
    ]
    # Con la comparación corrida, una quinta columna dice qué cambió frente a lo
    # que ya se conocía; sin ella, el comparativo queda como siempre.
    comparada = bool(datos.get("comparacion"))
    if comparada:
        encabezados.append("Cambio frente a lo conocido")
    ws.append(encabezados)
    relleno = PatternFill("solid", fgColor="1F3A5F")
    for c in ws[1]:
        c.font = Font(bold=True, color="FFFFFF")
        c.fill = relleno
        c.alignment = Alignment(vertical="center", wrap_text=True)

    for p in datos["productos"]:
        costo = p.get("precio_proveedor_cop")
        mercado = p.get("precio_mercado_cop")
        ganancia = (mercado - costo) if (costo and mercado) else None
        fila = [p["titulo"], costo, mercado, ganancia]
        ws.append(fila + [texto_de_cambio(p)] if comparada else fila)

    for fila in ws.iter_rows(min_row=2, min_col=2, max_col=4):
        for c in fila:
            c.number_format = '#,##0'
    for col, ancho in zip("ABCDE", (52, 26, 32, 20, 24)):
        ws.column_dimensions[col].width = ancho
    ws.freeze_panes = "A2"

    ws2 = wb.create_sheet("Detalle")
    ws2.append(["Título", "Categoría", "Marca", "Condición", "Colores",
                "Margen %", "Fuentes del precio", "Pendientes", "Supuestos"])
    for c in ws2[1]:
        c.font = Font(bold=True)
    for p in datos["productos"]:
        costo, mercado = p.get("precio_proveedor_cop"), p.get("precio_mercado_cop")
        margen = round((mercado - costo) / mercado * 100, 1) if (costo and mercado) else None
        ws2.append([
            p["titulo"], p.get("categoria"), p.get("marca"), p.get("condicion"),
            ", ".join(p.get("colores_oficiales") or p.get("colores_familia") or []),
            margen,
            " | ".join(f.get("tienda", "") for f in p.get("fuentes_precio", [])),
            " | ".join(p.get("revisar", [])),
            " | ".join(p.get("supuestos", [])),
        ])
    for i, ancho in enumerate((46, 16, 14, 14, 26, 10, 40, 60, 50), start=1):
        ws2.column_dimensions[get_column_letter(i)].width = ancho

    ws3 = wb.create_sheet("Descartados")
    ws3.append(["Texto original", "Motivo"])
    for c in ws3[1]:
        c.font = Font(bold=True)
    for p in datos.get("descartados", []):
        ws3.append([p.get("texto_origen", ""), p.get("motivo", "")])
    ws3.column_dimensions["A"].width = 50
    ws3.column_dimensions["B"].width = 40

    if comparada:
        # Lo que el proveedor tenía y hoy no trae, aunque llegó el mensaje en
        # que suele venir: es la señal para dejar de ofrecerlo.
        ws4 = wb.create_sheet("Desaparecidos")
        ws4.append(["Título", "Visto por última vez", "Último costo (COP)", "Mensaje de la lista"])
        for c in ws4[1]:
            c.font = Font(bold=True)
        for d in datos.get("desaparecidos", []):
            ws4.append([d["titulo"], d.get("visto_por_ultima_vez"), d.get("ultimo_costo_cop"),
                        ", ".join(d.get("bloques") or [])])
        for c in ws4["C"][1:]:
            c.number_format = '#,##0'
        for col, ancho in zip("ABCD", (52, 20, 20, 20)):
            ws4.column_dimensions[col].width = ancho

    wb.save(ruta)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("productos")
    ap.add_argument("--salida", default="catalogo/entregables")
    args = ap.parse_args()

    datos = json.loads(Path(args.productos).read_text(encoding="utf-8"))
    carpetas, xlsx, sin_fotos = construir(datos, Path(args.salida))
    print(f"FICHAS -> {Path(args.salida) / 'fichas'} ({len(carpetas)} modelos)")
    print(f"XLSX   -> {xlsx}")
    if sin_fotos:
        print(f"\n{len(sin_fotos)} modelos sin fotos en «{fichas.ORIGINALES}»:")
        for nombre in sin_fotos[:20]:
            print(f"  - {nombre}")


if __name__ == "__main__":
    main()
