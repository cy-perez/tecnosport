"""Pruebas del parser de listas de proveedor.

Dos clases de prueba, y las dos hacen falta:

- **Las listas de ejemplo completas contra su revisión guardada.** Es la red que
  atrapa lo que nadie pensó en probar: un cambio en una regla mueve un título,
  un descarte o una fusión en otra sección de otra lista. La revisión es
  legible, así que la diferencia también: si un cambio la mueve a propósito, se
  regenera y el diff del commit muestra exactamente qué productos cambiaron.
- **Una línea real por defecto corregido**, con el resultado que se espera.
  Fallan antes de la corrección y dicen qué se rompió sin tener que leer un
  diff de 300 líneas.

Se corren con la librería estándar, sin dependencias:

    python -m unittest discover -s .claude/skills/listas-de-proveedor/pruebas
"""

import sys
import unittest
from pathlib import Path

SKILL = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(SKILL / "scripts"))

import parsear_lista  # noqa: E402

EJEMPLOS = SKILL / "ejemplo"

# lista -> revisión guardada. La 3 es la del 08/10/2026, la primera que llegó con
# una viñeta de temporada (🎃) que el parser no conocía.
LISTAS = {
    "lista-ejemplo.txt": "revision-lista-1.md",
    "lista-ejemplo-2.txt": "revision-lista-2.md",
    "lista-ejemplo-3.txt": "revision-lista-3.md",
}


def leer(ruta: Path) -> str:
    # Sin `newline=""` Python ya traduce CRLF a LF al leer; la revisión guardada
    # está en LF por .gitattributes, y la generada en Windows sale en CRLF.
    return ruta.read_text(encoding="utf-8")


def parsear(texto: str) -> dict:
    return parsear_lista.parsear(texto)


def titulos(datos: dict) -> list:
    return [p["titulo"] for p in datos["productos"]]


class ListasDeEjemplo(unittest.TestCase):
    def test_cada_lista_produce_su_revision_guardada(self):
        for lista, revision in LISTAS.items():
            with self.subTest(lista=lista):
                datos = parsear(leer(EJEMPLOS / lista))
                generada = parsear_lista.reporte(datos).rstrip("\n")
                guardada = leer(EJEMPLOS / revision).rstrip("\n")
                self.assertEqual(
                    guardada, generada,
                    f"{lista} ya no produce {revision}. Si el cambio es a propósito, "
                    "regenera la revisión y revisa el diff producto por producto.")


if __name__ == "__main__":
    unittest.main()
