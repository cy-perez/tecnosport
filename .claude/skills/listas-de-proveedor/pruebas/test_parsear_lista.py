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


def parsear(texto: str, equivalencias=None) -> dict:
    return parsear_lista.parsear(texto, equivalencias)


def titulos(datos: dict) -> list:
    return [p["titulo"] for p in datos["productos"]]


class ListasDeEjemplo(unittest.TestCase):
    def test_cada_lista_produce_su_revision_guardada(self):
        for lista, revision in LISTAS.items():
            with self.subTest(lista=lista):
                # Con las equivalencias de verdad: la revisión es lo que se ve al
                # correr el parser, y un cambio en el archivo también se ve aquí.
                datos = parsear(leer(EJEMPLOS / lista), parsear_lista.cargar_equivalencias())
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


class SerieFDePoco(unittest.TestCase):
    """08/10/2026: `F8 ULTRA` y `F9 ULTRA` salían como «Xiaomi F8 Ultra».

    La serie X ya se leía como POCO; la F es de la misma línea y el proveedor la
    abrevia igual. El 02/10/2026 hubo que corregir el título a mano.
    """

    LISTA = (
        " *XIAOMI* \n"
        "🎃F8 ULTRA 5G (12+256)$2.250 \n"
        "🎃POCO F8 PRO 12+256 $1.770\n"
        "🎃17T PRO 5G(12+512)$2.750 \n"
    )

    def test_la_serie_f_bajo_xiaomi_es_poco(self):
        self.assertEqual(
            ["Xiaomi POCO F8 Ultra 5G 12GB RAM 256GB",
             "Xiaomi POCO F8 Pro 12GB RAM 256GB",
             "Xiaomi 17T Pro 5G 12GB RAM 512GB"],
            titulos(parsear(self.LISTA)))

    def test_queda_el_rastro_en_supuestos(self):
        [ultra, *_] = parsear(self.LISTA)["productos"]
        self.assertIn("la sección Xiaomi abrevia la serie F: se leyó como POCO", ultra["supuestos"])


def equivalencia(id_, titulo, fecha="2026-10-02"):
    return {"id": id_, "titulo": titulo, "fecha": fecha, "motivo": "prueba"}


class Equivalencias(unittest.TestCase):
    """La corrección de un título se escribe una vez y vale para todas las listas.

    Hasta el 08/10/2026 vivía solo en el productos.json de la corrida en que se
    hizo: la lista siguiente volvía a traer `jbl-extreme-4` y había que volver a
    descubrir que es `jbl-xtreme-4`, investigarlo y redactarlo como si fuera nuevo.
    """

    LISTA = "*PARLANTE ORIGINALES*🔊\n🔊JBL EXTREME 4 $1.100\n🔊JBL FLIP 7 $450\n"
    EQUIVALENCIAS = {"jbl-extreme-4": equivalencia("jbl-xtreme-4", "JBL Xtreme 4")}

    def test_el_producto_toma_el_id_y_el_titulo_definitivos(self):
        xtreme, flip = parsear(self.LISTA, self.EQUIVALENCIAS)["productos"]
        self.assertEqual(("jbl-xtreme-4", "JBL Xtreme 4"), (xtreme["id"], xtreme["titulo"]))
        self.assertEqual(("jbl-flip-7", "JBL Flip 7"), (flip["id"], flip["titulo"]))

    def test_el_id_que_produjo_la_lista_queda_a_la_vista(self):
        xtreme, flip = parsear(self.LISTA, self.EQUIVALENCIAS)["productos"]
        self.assertEqual("jbl-extreme-4", xtreme["id_lista"])
        self.assertEqual("jbl-flip-7", flip["id_lista"])

    def test_queda_dicho_de_donde_salio_el_titulo(self):
        [xtreme, _] = parsear(self.LISTA, self.EQUIVALENCIAS)["productos"]
        self.assertIn("título confirmado el 02/10/2026: la lista lo trae como «JBL Extreme 4»",
                      xtreme["supuestos"])

    def test_un_nombre_confirmado_no_se_vuelve_a_pedir_confirmar(self):
        xtreme, flip = parsear(self.LISTA, self.EQUIVALENCIAS)["productos"]
        self.assertNotIn("confirmar nombre comercial oficial del modelo", xtreme["revisar"])
        self.assertIn("confirmar nombre comercial oficial del modelo", flip["revisar"])

    def test_sin_equivalencias_el_parser_sigue_igual(self):
        self.assertEqual(["JBL Extreme 4", "JBL Flip 7"], titulos(parsear(self.LISTA)))

    def test_dos_productos_con_el_mismo_id_final_se_marcan(self):
        eq = {"jbl-extreme-4": equivalencia("jbl-flip-7", "JBL Flip 7")}
        for p in parsear(self.LISTA, eq)["productos"]:
            self.assertIn("otro producto de la lista quedó con el mismo id (jbl-flip-7): "
                          "revisar las equivalencias", p["revisar"])


class ValidacionDeEquivalencias(unittest.TestCase):
    """El archivo se valida al cargarlo: un error ahí renombra productos en silencio."""

    def validar(self, eq):
        return parsear_lista.validar_equivalencias(eq)

    def test_el_archivo_guardado_es_valido(self):
        self.assertGreater(len(parsear_lista.cargar_equivalencias()), 0)

    def test_el_id_tiene_que_salir_del_titulo(self):
        # Así se colaron dos entradas viejas de catalogo/ids.json: apuntaban a un
        # id que ya no correspondía al título que se entregó.
        with self.assertRaisesRegex(ValueError, "no corresponde al título"):
            self.validar({"x": equivalencia("samsung-galaxy-tab-a11-11-wifi",
                                            "Samsung Galaxy Tab A11+ 11\" WiFi")})

    def test_el_mas_pegado_se_escribe_plus_y_el_suelto_separa(self):
        self.validar({
            "a": equivalencia("samsung-galaxy-tab-a11-plus-11-wifi", "Samsung Galaxy Tab A11+ 11\" WiFi"),
            "b": equivalencia("nintendo-switch-2-mario-kart-world", "Nintendo Switch 2 + Mario Kart World"),
        })

    def test_una_entrada_no_puede_apuntar_a_otra_clave(self):
        with self.assertRaisesRegex(ValueError, "también es una clave"):
            self.validar({"a": equivalencia("b", "B"), "b": equivalencia("c", "C")})

    def test_cada_entrada_dice_cuando_y_por_que(self):
        with self.assertRaisesRegex(ValueError, "faltan motivo"):
            self.validar({"a": {"id": "b", "titulo": "B", "fecha": "2026-10-02"}})


if __name__ == "__main__":
    unittest.main()
