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


class SimEnLaMismaLinea(unittest.TestCase):
    """08/10/2026: «1 SIM» y «DUAL SIM» al final de la línea se ignoraban.

    Solo se leían cuando venían en la línea siguiente. Sin el atributo, los dos
    A17 5G no se contradecían en nada y se fusionaban en uno, al menor precio:
    se perdía una referencia y el título del que quedaba no decía cuál era.
    """

    LISTA = (
        "*SAMSUNG*\n"
        "🎃A17 5G (8+256)$675 *1 SIM*\n"
        "🎃A17 5G (8+256)$690 *DUAL SIM* \n"
    )

    def test_una_sim_y_dos_sim_son_dos_referencias(self):
        datos = parsear(self.LISTA)
        self.assertEqual([], datos["duplicados_fusionados"])
        self.assertEqual(
            [("Samsung Galaxy A17 5G 8GB RAM 256GB 1 SIM", 675_000),
             ("Samsung Galaxy A17 5G 8GB RAM 256GB Dual SIM", 690_000)],
            [(p["titulo"], p["precio_proveedor_cop"]) for p in datos["productos"]])

    def test_sim_y_esim_en_la_linea_no_deja_un_sim_suelto_en_el_modelo(self):
        datos = parsear("*MOTOROLA*\n🎃EDGE 50 FUSIÓN 5G (8+256)$735 *SIM / ESIM*\n")
        self.assertEqual(["Motorola Edge 50 Fusion 5G 8GB RAM 256GB SIM / eSIM"], titulos(datos))

    def test_la_sim_en_la_linea_siguiente_sigue_leyendose(self):
        datos = parsear("*MOTOROLA*\n🎃MOTO G17 4G (4+4+256)$505\n*1 SIM*\n")
        self.assertEqual(["Motorola Moto G17 4G 4GB RAM 256GB 1 SIM"], titulos(datos))


class PrecioSinSigno(unittest.TestCase):
    """08/10/2026: `MOTO G77 5G (8+256) 760` se descartó «sin precio de proveedor»."""

    def test_un_numero_despues_de_la_memoria_al_final_de_la_linea_es_el_precio(self):
        datos = parsear("*MOTOROLA*\n🎃MOTO G77 5G (8+256) 760\n")
        self.assertEqual([], datos["descartados"])
        [producto] = datos["productos"]
        self.assertEqual("Motorola Moto G77 5G 8GB RAM 256GB", producto["titulo"])
        self.assertEqual(760_000, producto["precio_proveedor_cop"])

    def test_queda_dicho_que_el_precio_se_leyo_sin_signo(self):
        [producto] = parsear("*MOTOROLA*\n🎃MOTO G77 5G (8+256) 760\n")["productos"]
        self.assertTrue(any("sin «$»" in s for s in producto["supuestos"]), producto["supuestos"])

    def test_las_pulgadas_despues_de_la_memoria_no_son_un_precio(self):
        datos = parsear("*COMPUTADORES*\n💻 *COMPUTADOR TODO EN UNO RYZEN 3 7520U (8 RAM + 512 SSD) 24\"*\n")
        self.assertIsNone((datos["productos"] + datos["descartados"])[0]["precio_proveedor_cop"])

    def test_sin_numero_al_final_sigue_sin_precio(self):
        datos = parsear("*XIAOMI*\n🎃POCO F8 PRO 12+256\n")
        self.assertEqual(["sin precio de proveedor"], [d["motivo"] for d in datos["descartados"]])


if __name__ == "__main__":
    unittest.main()
