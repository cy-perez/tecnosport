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


class VinetaDeTemporada(unittest.TestCase):
    """08/10/2026: la lista llegó con 🎃 de viñeta y se perdieron 52 líneas.

    Bajo un encabezado de solo marca (`*SAMSUNG*`), una línea sin viñeta
    reconocida y sin la marca escrita caía en `sin_clasificar`: el A57, los Oppo
    A6 y el Magic 8 Lite no llegaban ni a la hoja de descartados.
    """

    LISTA = (
        "*SAMSUNG*\n"
        "🎃A57 5G (8+256)$1.290\n"
        "\n"
        " *OPPO* \n"
        "🎃A6C (4+128)$575 \n"
        "\n"
        " *HONOR*\n"
        "🎃MAGIC 8 LITE 5G (8+512)$1.190\n"
    )

    def test_un_emoji_que_no_esta_en_las_tablas_es_una_vineta(self):
        datos = parsear(self.LISTA)
        self.assertEqual([], datos["sin_clasificar"])
        self.assertEqual(
            ["Samsung Galaxy A57 5G 8GB RAM 256GB",
             "Oppo A6C 4GB RAM 128GB",
             "Honor Magic 8 Lite 5G 8GB RAM 512GB"],
            titulos(datos))

    def test_la_vineta_generica_no_pide_verificar_que_sea_un_producto(self):
        # Esa alerta es para la línea que llega sin viñeta; esta sí la trae.
        productos = parsear(self.LISTA)["productos"]
        self.assertEqual(3, len(productos))
        for p in productos:
            self.assertNotIn("línea sin viñeta: verificar que sea un producto", p["revisar"])

    def test_la_vineta_generica_no_decide_categoria_ni_condicion(self):
        datos = parsear("*RELOJES ORIGINALES*\n🎃XIAOMI WATCH S4 41MM $490\n")
        self.assertEqual(["relojes"], [p["categoria"] for p in datos["productos"]])
        self.assertEqual(["nuevo"], [p["condicion"] for p in datos["productos"]])

    def test_un_corazon_al_inicio_es_un_color_y_no_una_vineta(self):
        # En el bloque de usados los corazones abren la línea; son el color.
        datos = parsear("*SAMSUNG*\n💙A57 5G (8+256)$1.290\n")
        self.assertEqual([], datos["productos"])
        self.assertEqual(1, len(datos["sin_clasificar"]))


if __name__ == "__main__":
    unittest.main()
