#!/usr/bin/env python3
"""La carpeta de cada modelo en `catalogo/entregables/fichas/`: todo lo del modelo en un sitio.

Decisión del 08/10/2026. Antes la información de un producto vivía repartida en
`catalogo/` —la ficha en `icecat/fichas/<id>.json`, las fotos al lado, la prosa
en `prosa.json`, lo de mi.com en `mi/`— y las fotos retocadas en otra carpeta
que terminó perdiéndose. Ahora cada modelo tiene una carpeta con nombre legible:

    fichas/Samsung Galaxy S25 Ultra/
    ├── samsung-galaxy-s25-ultra-ficha.txt   la genera la skill en cada corrida
    ├── Fotos originales/                    las descarga la persona: <id>_1.jpg, <id>_2.png…
    ├── Fotos procesadas/                    las escribe fotos-estudio-degradado, mismo nombre en .jpg
    └── Fuente de la marca/                  solo en marcas que bloquean la lectura automática

**La carpeta se reconoce por su archivo de ficha**, `<id del modelo>-ficha.txt`,
no por el nombre: si el título del modelo se corrige con una equivalencia, la
carpeta se sigue encontrando y las fotos que ya tiene no se pierden.

**Nada de esto borra.** La skill escribe la ficha y crea las carpetas que falten;
las fotos que dejó la persona no se tocan nunca.
"""

import re
from pathlib import Path

ORIGINALES = "Fotos originales"
PROCESADAS = "Fotos procesadas"
FUENTE_DE_LA_MARCA = "Fuente de la marca"

# Marcas cuyo sitio no se lee de forma automática y en las que la persona guarda
# la página de especificaciones en la carpeta del modelo. No es una preferencia:
# mi.com responde 403 a los agentes de IA, JBL pone un captcha y Amazon y
# Nintendo excluyen a Claude en su robots.txt (verificado el 08/10/2026). Esas
# barreras no se eluden; la página que guarda una persona desde su navegador sí
# se puede leer.
MARCAS_SIN_LECTURA_AUTOMATICA = {"Xiaomi", "JBL", "Amazon", "Nintendo"}


def nombre_de_carpeta(titulo: str) -> str:
    """El título del modelo como nombre de carpeta válido en Windows.

    Windows no admite `"` en un nombre: las pulgadas se escriben con la doble
    prima `″`, que se ve igual (decisión del 08/10/2026). Los demás caracteres
    prohibidos se cambian por un espacio.
    """
    nombre = titulo.replace('"', "″")
    nombre = re.sub(r'[<>:/\\|?*]', " ", nombre)
    return re.sub(r"\s+", " ", nombre).strip().rstrip(". ")


def archivo_de_ficha(id_modelo: str) -> str:
    return f"{id_modelo}-ficha.txt"


def carpeta_de_modelo(raiz: Path, id_modelo: str, titulo: str) -> Path:
    """La carpeta que ya tiene la ficha de este modelo, o la que le toca por título."""
    raiz = Path(raiz)
    if raiz.is_dir():
        for carpeta in raiz.iterdir():
            if (carpeta / archivo_de_ficha(id_modelo)).is_file():
                return carpeta
    return raiz / nombre_de_carpeta(titulo)


def id_de_carpeta(carpeta: Path):
    """El id del modelo de una carpeta, por su archivo de ficha; None si no tiene."""
    for f in Path(carpeta).glob("*-ficha.txt"):
        return f.name[: -len("-ficha.txt")]
    return None


def pesos(valor) -> str:
    return f"{valor:,}".replace(",", ".") if valor is not None else "por definir"


def texto_de_ficha(productos: list) -> str:
    """La ficha de un modelo: los datos del equipo y una línea por configuración."""
    m = productos[0]
    paleta = ", ".join(m.get("colores_oficiales") or []) or "por confirmar"
    L = [
        f"MODELO: {m.get('titulo_modelo') or m['titulo']}",
        f"ID DEL MODELO: {m.get('id_modelo') or m['id']}",
        f"CATEGORÍA: {m.get('categoria', '')}",
        f"MARCA: {m.get('marca') or 'por confirmar'}",
        f"PALETA OFICIAL: {paleta}",
        "",
        "CONFIGURACIONES DE ESTA LISTA",
        "-" * 60,
    ]
    for p in productos:
        sugeridos = ", ".join(p.get("colores_sugeridos") or []) or "sin emojis en la lista"
        L.append(f"- {p['titulo']} (SKU {p['id']})")
        L.append(f"  costo {pesos(p.get('precio_proveedor_cop'))} · mercado {pesos(p.get('precio_mercado_cop'))}"
                 f" · colores sugeridos: {sugeridos}")
    L += ["", f"META TÍTULO: {m.get('meta_titulo', '')}", f"META DESCRIPCIÓN: {m.get('meta_descripcion', '')}",
          "", "DESCRIPCIÓN", "-" * 60, m.get("descripcion") or "(pendiente de redactar)"]

    if m.get("marca") in MARCAS_SIN_LECTURA_AUTOMATICA:
        L += ["", "FICHA DE LA MARCA", "-" * 60,
              f"El sitio de {m['marca']} no se puede leer de forma automática. Guarda la página de "
              f"especificaciones del modelo (Ctrl+S, «solo HTML») en la carpeta «{FUENTE_DE_LA_MARCA}»."]

    supuestos = list(dict.fromkeys(s for p in productos for s in p.get("supuestos") or []))
    if supuestos:
        L += ["", "SUPUESTOS", "-" * 60] + [f"- {s}" for s in supuestos]
    revisar = [f"- {p['titulo']}: {r}" for p in productos for r in p.get("revisar") or []]
    if revisar:
        L += ["", "PENDIENTES ANTES DE PUBLICAR", "-" * 60] + revisar
    fuentes = [f for p in productos[:1] for f in p.get("fuentes_ficha") or []]
    if fuentes:
        L += ["", "FUENTES DE LA FICHA", "-" * 60]
        L += [f"- {f.get('fuente')}" + (f" — {f['url']}" if f.get("url") else "")
              + (f" (consultado el {f['fecha']})" if f.get("fecha") else "") for f in fuentes]
    con_fuentes = [p for p in productos if p.get("fuentes_precio")]
    if con_fuentes:
        L += ["", "FUENTES DEL PRECIO DE MERCADO", "-" * 60]
        for p in con_fuentes:
            L.append(f"{p['titulo']}:")
            for f in p["fuentes_precio"]:
                precio = f.get("precio_cop") or f.get("precio")
                enlace = f.get("enlace") or f.get("url") or ""
                nivel = f" ({f['nivel']})" if f.get("nivel") else ""
                L.append(f"- {f.get('tienda', '?')}{nivel}: {pesos(precio) if precio else '?'} — {enlace}")
    return "\n".join(L) + "\n"


def escribir(datos: dict, raiz: Path) -> list:
    """Escribe la ficha de cada modelo de la lista y crea las carpetas que falten.

    Devuelve las carpetas, en el orden de la lista.
    """
    raiz = Path(raiz)
    raiz.mkdir(parents=True, exist_ok=True)
    por_modelo = {}
    for p in datos["productos"]:
        por_modelo.setdefault(p.get("id_modelo") or p["id"], []).append(p)
    carpetas = []
    for mid, productos in por_modelo.items():
        carpeta = carpeta_de_modelo(raiz, mid, productos[0].get("titulo_modelo") or productos[0]["titulo"])
        (carpeta / ORIGINALES).mkdir(parents=True, exist_ok=True)
        if productos[0].get("marca") in MARCAS_SIN_LECTURA_AUTOMATICA:
            (carpeta / FUENTE_DE_LA_MARCA).mkdir(exist_ok=True)
        (carpeta / archivo_de_ficha(mid)).write_text(texto_de_ficha(productos), encoding="utf-8", newline="")
        carpetas.append(carpeta)
    return carpetas
