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
             "OPPO A6C 4GB RAM 128GB",
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

    def test_un_emoji_suelto_entra_pero_pide_verificar(self):
        # Revisión del 08/10/2026: un domicilio con 🚚 se publicaba como producto.
        datos = parsear("*AUDIFONOS ORIGINALES*🎧\n🚚 DOMICILIO EN MEDELLIN $10.000\n")
        [p] = datos["productos"]
        self.assertIn("viñeta no reconocida (🚚): verificar que sea un producto", p["revisar"])

    def test_la_vineta_del_dia_tambien_vale_con_el_precio_abajo(self):
        datos = parsear(self.LISTA.replace("*SAMSUNG*\n", "*SAMSUNG*\n🎃A37 5G (6+128)\n$810\n"))
        self.assertIn(("Samsung Galaxy A37 5G 6GB RAM 128GB", 810_000),
                      [(p["titulo"], p["precio_proveedor_cop"]) for p in datos["productos"]])
        self.assertEqual([], datos["sin_clasificar"])

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
        self.assertEqual(["Motorola Edge 50 Fusion 5G 8GB RAM 256GB SIM + eSIM"], titulos(datos))

    def test_la_sim_dice_lo_mismo_en_la_linea_o_debajo(self):
        # Revisión del 08/10/2026: `*1SIM*` pegado daba «1Sim» debajo y «1 SIM»
        # en la línea, dos ids para el mismo equipo. `*1SIM*` existe en la lista 2.
        for texto in ("🎃A27 5G (8+256)$870 *1SIM*\n", "🎃A27 5G (8+256)$870\n*1SIM*\n"):
            with self.subTest(texto=texto):
                self.assertEqual(["Samsung Galaxy A27 5G 8GB RAM 256GB 1 SIM"],
                                 titulos(parsear("*SAMSUNG*\n" + texto)))
        for texto in ("🎃EDGE 50 FUSIÓN 5G (8+256)$735 *SIM/ESIM*\n",
                      "🎃EDGE 50 FUSIÓN 5G (8+256)$735\n*SIM / ESIM*\n"):
            with self.subTest(texto=texto):
                self.assertEqual(["Motorola Edge 50 Fusion 5G 8GB RAM 256GB SIM + eSIM"],
                                 titulos(parsear("*MOTOROLA*\n" + texto)))

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

    def test_un_ano_despues_de_otro_parentesis_no_es_un_precio(self):
        # Revisión del 08/10/2026: el patrón aceptaba cualquier «)».
        datos = parsear("*AUDIFONOS ORIGINALES*🎧\n🎄 *PROMOS (DICIEMBRE) 2026*\n")
        self.assertEqual([], datos["productos"])
        datos = parsear("*COMPUTADORES*\n💻MACBOOK AIR 13 (M4) 2025\n$4.500\n")
        self.assertEqual([4_500_000], [p["precio_proveedor_cop"]
                                       for p in datos["productos"] + datos["descartados"]])

    def test_con_la_sim_detras_o_con_punto_de_miles_tambien_es_el_precio(self):
        datos = parsear("*MOTOROLA*\n🎃MOTO G77 5G (8+256) 760 *1 SIM*\n🎃MOTO G86 5G (8+512) 1.690\n")
        self.assertEqual([("Motorola Moto G77 5G 8GB RAM 256GB 1 SIM", 760_000),
                          ("Motorola Moto G86 5G 8GB RAM 512GB", 1_690_000)],
                         [(p["titulo"], p["precio_proveedor_cop"]) for p in datos["productos"]])

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


class ProcesadorDelComputador(unittest.TestCase):
    """15/10/2026: dos portátiles sin referencia entraron al análisis (regla 11).

    `RYZEN 3 (` no traía el código del chip y `CORE i 5` traía un espacio: el
    procesador no se reconocía, quedaba en la línea y pasaba por referencia.
    """

    def computador(self, linea):
        datos = parsear(" 💻🖥️ *COMPUTADORES*\n\n" + linea + "\n")
        return datos["productos"] + datos["descartados"]

    def test_el_ryzen_sin_codigo_no_es_una_referencia(self):
        [p] = self.computador('💻*LAPTOP ASUS RYZEN 3 ( 8 RAM + 512 GB SSD) 15.6" $1.320*')
        self.assertEqual(["referencia"], p["sin_datos"])
        self.assertIn("Ryzen 3", p["atributos"])

    def test_el_core_con_espacio_y_su_codigo_no_son_una_referencia(self):
        [p] = self.computador("💻 *LAPTOP ASUS CORE i 5 7520U (12 RAM DDR5 +512GB SSD)  $1.850+ BOLSO*")
        self.assertEqual(["referencia"], p["sin_datos"])
        self.assertIn("Core i5 7520U", p["atributos"])

    def test_sin_referencia_va_a_descartados(self):
        datos = parsear(" 💻🖥️ *COMPUTADORES*\n\n"
                        '💻*LAPTOP ASUS RYZEN 3 ( 8 RAM + 512 GB SSD) 15.6" $1.320*\n')
        self.assertEqual([], datos["productos"])
        self.assertEqual(["computador sin referencia en la lista"],
                         [d["motivo"] for d in datos["descartados"]])

    def test_con_referencia_sigue_entrando_sin_el_procesador_en_el_titulo(self):
        [p] = self.computador('💻*LAPTOP ASUS VIVOBOOK X1504 CORE I5-1235U (8 RAM + 512 GB SSD) 15.6" $1.900*')
        self.assertEqual([], p["sin_datos"])
        self.assertEqual("Vivobook X1504", p["referencia"])


class Bloques(unittest.TestCase):
    """La lista llega en tres mensajes, y la ausencia de un producto solo dice algo
    si llegó el mensaje en que suele venir."""

    def test_cada_producto_sabe_de_que_bloque_viene(self):
        datos = parsear(leer(EJEMPLOS / "lista-ejemplo-3.txt"))
        bloque = {p["titulo"]: p["bloques"] for p in datos["productos"]}
        self.assertEqual(["ANDROID"], bloque["Samsung Galaxy A57 5G 8GB RAM 256GB"])
        self.assertEqual(["VARIEDAD"], bloque["JBL Flip 7"])
        self.assertEqual(["GAMA ALTA"], bloque["Apple iPhone 17 Pro 256GB eSIM"])

    def test_la_lista_dice_que_bloques_llegaron(self):
        self.assertEqual(["ANDROID", "VARIEDAD", "GAMA ALTA"],
                         parsear(leer(EJEMPLOS / "lista-ejemplo-3.txt"))["bloques"])
        self.assertEqual(["GAMA ALTA", "VARIEDAD"],
                         parsear(leer(EJEMPLOS / "lista-ejemplo.txt"))["bloques"])

    def test_el_titulo_trunco_es_el_bloque_android(self):
        # «12 SEPTIEMBRE/2026 LISTADO DE» y nada más: así llegó el de Android el
        # 12/09 y el 02/10, y la lista del 08/10 lo nombra.
        datos = parsear("🙏🏼  *12 SEPTIEMBRE/2026 LISTADO DE*\n *SAMSUNG*\n🎃A57 5G (8+256)$1.290\n")
        self.assertEqual(["ANDROID"], datos["bloques"])
        self.assertEqual([["ANDROID"]], [p["bloques"] for p in datos["productos"]])

    def test_un_aviso_de_llegada_no_es_un_bloque(self):
        datos = parsear("*LLEGANDO MERCANCÍA*\nPoco x8 pro Max 256 $1.830\n")
        self.assertEqual([], datos["bloques"])
        self.assertEqual([[]], [p["bloques"] for p in datos["productos"]])

    def test_el_nombre_del_bloque_no_depende_de_como_se_escriba_la_fecha(self):
        # Revisión del 08/10/2026: cada variación daba otro nombre, y un nombre
        # nuevo cada día hace que nada se reporte como desaparecido.
        casos = {
            "*08 OCTUBRE 2026 LISTADO DE*": "ANDROID",
            "*08/10/26 LISTADO DE*": "ANDROID",
            "*OCTUBRE 08/2026 LISTADO DE*": "ANDROID",
            "‼️*LISTADO DE ANDROID* ACTUALIZADO": "ANDROID",
            "*LISTA DE GAMA ALTA* 🍎": "GAMA ALTA",
            "*LISTADO DE VARIEDAD*": "VARIEDAD",
            "*LISTA DE TECNOLOGÍA*": "TECNOLOGIA",
        }
        for encabezado, nombre in casos.items():
            with self.subTest(encabezado=encabezado):
                self.assertEqual([nombre], parsear(encabezado + "\n")["bloques"])

    def test_un_bloque_que_no_se_conoce_se_avisa_en_la_revision(self):
        datos = parsear("*LISTA DE TECNOLOGÍA*\n")
        self.assertIn("mensaje de la lista no reconocido: TECNOLOGIA", parsear_lista.reporte(datos))

    def test_despues_de_un_aviso_la_seccion_siguiente_vuelve_al_bloque(self):
        datos = parsear("*LISTADO DE ANDROID*\n *SAMSUNG*\n🎃A57 5G (8+256)$1.290\n"
                        "*LLEGANDO MERCANCÍA*\n *SAMSUNG*\n📲A37 5G (6+128)$810\n")
        self.assertEqual([["ANDROID"], ["ANDROID"]], [p["bloques"] for p in datos["productos"]])

    def test_la_fusion_por_capacidad_faltante_tambien_suma_los_bloques(self):
        datos = parsear("*LLEGANDO MERCANCÍA*\nPoco x8 pro 256 $1.250\n"
                        "*LISTADO DE ANDROID*\n *XIAOMI* \n✔️POCO X8 PRO 5G $1.250\n")
        self.assertEqual(1, len(datos["productos"]), titulos(datos))
        self.assertEqual(["ANDROID"], datos["productos"][0]["bloques"])

    def test_al_fusionar_se_suman_los_bloques(self):
        # Como el 02/10: el aviso de llegada y la lista traen el mismo equipo.
        datos = parsear("*LLEGANDO MERCANCÍA*\nPoco x8 pro Max 256 $1.830\n"
                        "*LISTADO DE ANDROID*\n *XIAOMI* \n🎃X8 PRO MAX 5G (12+256GB) $1.830\n")
        self.assertEqual(1, len(datos["duplicados_fusionados"]))
        self.assertEqual([["ANDROID"]], [p["bloques"] for p in datos["productos"]])


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

    def test_la_sim_que_corrige_la_equivalencia_corrige_tambien_el_atributo(self):
        # 15/10/2026: el A27 5G vino «1 SIM» y es Dual SIM. La API lee la SIM del atributo:
        # sin esto salía titulado Dual SIM y vendido como 1 SIM.
        eq = {"samsung-galaxy-a27-5g-8gb-ram-256gb-1-sim": equivalencia(
            "samsung-galaxy-a27-5g-8gb-ram-256gb-dual-sim", "Samsung Galaxy A27 5G 8GB RAM 256GB Dual SIM",
            "2026-10-15")}
        [p] = parsear(" *SAMSUNG*\n🎃A27 5G (8+256)$870 *1 SIM* \n", eq)["productos"]
        self.assertEqual(("samsung-galaxy-a27-5g-8gb-ram-256gb-dual-sim", ["Dual SIM"]), (p["id"], p["atributos"]))

    def test_una_equivalencia_sin_sim_en_el_titulo_no_toca_la_sim(self):
        eq = {"samsung-galaxy-a27-5g-8gb-ram-256gb-1-sim": equivalencia(
            "samsung-galaxy-a27-5g-8gb-ram-256gb", "Samsung Galaxy A27 5G 8GB RAM 256GB", "2026-10-15")}
        [p] = parsear(" *SAMSUNG*\n🎃A27 5G (8+256)$870 *1 SIM* \n", eq)["productos"]
        self.assertEqual(["1 SIM"], p["atributos"])

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

    def test_los_descartados_tambien_toman_el_id_definitivo(self):
        # Para que la comparación reconozca a un conocido que hoy quedó fuera.
        eq = {"jbl-extreme-4": equivalencia("jbl-xtreme-4", "JBL Xtreme 4")}
        datos = parsear("*PARLANTE ORIGINALES*🔊\n🔊JBL EXTREME 4\n", eq)
        self.assertEqual(["jbl-xtreme-4"], [d["id"] for d in datos["descartados"]])

    def test_dos_lineas_con_el_mismo_id_final_se_fusionan_al_menor_precio_y_avisan(self):
        # Revisión del 08/10/2026: quedaban dos productos con el mismo id, y en la
        # base y en la comparación uno pisaba al otro.
        eq = {"jbl-extreme-4": equivalencia("jbl-flip-7", "JBL Flip 7")}
        datos = parsear(self.LISTA, eq)
        [p] = datos["productos"]
        self.assertEqual(450_000, p["precio_proveedor_cop"])
        self.assertTrue(any(r.startswith("dos líneas de la lista quedaron con el mismo id (jbl-flip-7)")
                            for r in p["revisar"]), p["revisar"])
        self.assertEqual(1, len(datos["duplicados_fusionados"]))


class MasPegado(unittest.TestCase):
    """Revisión del 08/10/2026: `PRO+` pegado se fundía con el Pro de la misma lista."""

    def test_el_pro_y_el_pro_mas_son_dos_productos(self):
        datos = parsear(" *XIAOMI* \n✔️NOTE 15 PRO 4G (8+256) $860\n✔️NOTE 15 PRO+ (8+256) $1.310\n")
        self.assertEqual([("xiaomi-redmi-note-15-pro-4g-8gb-ram-256gb", 860_000),
                          ("xiaomi-redmi-note-15-pro-plus-8gb-ram-256gb", 1_310_000)],
                         [(p["id"], p["precio_proveedor_cop"]) for p in datos["productos"]])

    def test_la_memoria_no_se_toca(self):
        datos = parsear(" *XIAOMI* \n✔️NOTE 15 PRO 4G (8+256) $860\n")
        self.assertEqual(["Xiaomi Redmi Note 15 Pro 4G 8GB RAM 256GB"], titulos(datos))


class FaltaInformacionDelProveedor(unittest.TestCase):
    """Regla 10, 15/10/2026: lo que la lista no permite identificar se descarta
    por falta de información del proveedor, y la persona confirma o da el dato."""

    WATCH = "*APPLE WATCH* 💥\n⌚WATCH  SE 44MM $1.080 🖤\n"

    def test_el_apple_watch_se_sin_generacion_se_descarta(self):
        datos = parsear(self.WATCH)
        self.assertEqual([], datos["productos"])
        [d] = datos["descartados"]
        self.assertEqual("falta información del proveedor: la generación del Apple Watch SE (SE 2 o SE 3)",
                         d["motivo"])

    def test_con_la_generacion_en_la_lista_entra(self):
        datos = parsear("*APPLE WATCH* 💥\n⌚WATCH SE 3 44MM $1.080\n")
        self.assertEqual(["Apple Watch SE 3 44mm"], titulos(datos))

    def test_la_equivalencia_con_el_titulo_completo_es_tener_el_dato(self):
        eq = {"apple-watch-se-44mm": equivalencia("apple-watch-se-3-44mm", "Apple Watch SE 3 44mm", "2026-10-15")}
        self.assertEqual(["Apple Watch SE 3 44mm"], titulos(parsear(self.WATCH, eq)))

    def test_los_airpods_pro_sin_generacion_tambien(self):
        datos = parsear("*APPLE WATCH* 💥\n🎧AIRPODS PRO $840\n🎧AIRPODS PRO 3 $840\n")
        self.assertEqual(["Apple AirPods Pro 3"], titulos(datos))
        self.assertIn("AirPods Pro", datos["descartados"][0]["motivo"])

    def test_la_equivalencia_deja_la_fecha_en_que_la_persona_confirmo(self):
        eq = {"nintendo-switch-2": equivalencia("nintendo-switch-2", "Nintendo Switch 2", "2026-10-15")}
        [p] = parsear("*COSOLAS Y ACCESORIOS*\n👾NINTENDO SWITCH 2 $2100\n", eq)["productos"]
        self.assertEqual("2026-10-15", p["confirmado_el"])


class BloqueDeIphoneSinCondicion(unittest.TestCase):
    """El bloque *IPH NUEVOS* que llegó al final de la lista del 15/10/2026."""

    BLOQUE = ("*IPH NUEVOS* \n✔️18 PRO MAX 256 *ESIM* 🖤🤍$4.980 \n15 128 *$1.950*\n"
              "18 pm 256 *$4.850*\nS26 u 256 *$3.150*\n")

    def test_un_iphone_sin_vineta_ni_esim_ni_activo_se_descarta_por_la_condicion(self):
        datos = parsear(self.BLOQUE)
        faltan = [d["texto_origen"] for d in datos["descartados"] if d.get("falta_informacion")]
        self.assertEqual(["15 128 *$1.950*", "18 pm 256 *$4.850*"], faltan)

    def test_no_le_baja_el_precio_al_sellado_del_mismo_modelo(self):
        [p] = [p for p in parsear(self.BLOQUE)["productos"] if "iPhone 18" in p["titulo"]]
        self.assertEqual(4_980_000, p["precio_proveedor_cop"])

    def test_con_equivalencia_la_persona_dijo_que_es_nuevo(self):
        eq = {"apple-iphone-15-128gb": equivalencia("apple-iphone-15-128gb", "Apple iPhone 15 128GB", "2026-10-15")}
        self.assertIn("Apple iPhone 15 128GB", titulos(parsear(self.BLOQUE, eq)))

    def test_la_vineta_no_dice_la_condicion(self):
        # La misma lista llegó después con ✔️ en esas líneas: entraban como sellados.
        datos = parsear(self.BLOQUE.replace("\n15 128", "\n✔️15 128").replace("\n18 pm", "\n✔️18 pm"))
        faltan = [d["texto_origen"] for d in datos["descartados"] if d.get("falta_informacion")]
        self.assertEqual(["15 128 *$1.950*", "18 pm 256 *$4.850*"], faltan)
        [p] = [p for p in datos["productos"] if "iPhone 18" in p["titulo"]]
        self.assertEqual(4_980_000, p["precio_proveedor_cop"])

    def test_un_s26_dentro_de_la_seccion_de_iphone_es_samsung_y_la_u_es_ultra(self):
        self.assertIn("Samsung Galaxy S26 Ultra 256GB", titulos(parsear(self.BLOQUE)))

    def test_y_se_junta_con_el_s26_ultra_de_gama_alta_que_venia_sin_precio(self):
        datos = parsear("*SAMSUNG* ✈️\n🚀S26 ULTRA 12/256🖤💜\n" + self.BLOQUE)
        [p] = [p for p in datos["productos"] if "S26" in p["titulo"]]
        self.assertEqual(("Samsung Galaxy S26 Ultra 12GB RAM 256GB", 3_150_000),
                         (p["titulo"], p["precio_proveedor_cop"]))


class EncabezadosDel15DeOctubre(unittest.TestCase):
    """Secciones que la lista del 15/10/2026 trajo y el parser no conocía."""

    def test_consolas_mal_escrito_abre_consolas_y_el_control_no_se_publica(self):
        datos = parsear("*COSOLAS Y ACCESORIOS*\nCONTROL $300\n👾NINTENDO SWITCH 2 $2100\n")
        self.assertEqual([("Nintendo Switch 2", "consolas")],
                         [(p["titulo"], p["categoria"]) for p in datos["productos"]])
        self.assertEqual(["accesorios_consola"], [d["categoria"] for d in datos["descartados"]])

    def test_el_vapeador_no_hereda_la_seccion_anterior(self):
        datos = parsear("*ROUTERS* 📡\n📡TP-LINK AX12 $125\n*VAPER*\n✔️50.000 PUFF $35\n")
        self.assertEqual(["routers", "vapeadores"], [d["categoria"] for d in datos["descartados"]])

    def test_el_encabezado_de_ups_no_es_un_producto(self):
        datos = parsear("🪫*UPS*🪫\n\n📍8.800mah $110\n")
        self.assertEqual(["8.800mah $110"], [d["texto_origen"] for d in datos["descartados"]])

    def test_el_tono_de_piel_de_la_vineta_no_queda_en_el_texto(self):
        [d] = parsear("✍🏽PENCIL TÁCTIL XIAOMI $140\n")["descartados"]
        self.assertEqual("PENCIL TÁCTIL XIAOMI $140", d["texto_origen"])

    def test_se_y_airpods_con_las_mayusculas_de_apple(self):
        self.assertEqual("Apple Watch SE 3 AirPods", parsear_lista.titulo_bonito("APPLE WATCH SE 3 AIRPODS"))


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


class Modelo(unittest.TestCase):
    """08/10/2026: la base pasa a ser por modelo. El modelo es el equipo que el
    cliente ve en una ficha; la memoria y la SIM son variantes que elige. La red
    sí es del modelo: el A17 4G y el A17 5G llevan chips distintos, y Alkosto los
    publica como productos aparte."""

    def test_el_modelo_es_el_titulo_sin_memoria_ni_sim(self):
        casos = {
            "Samsung Galaxy A17 5G 8GB RAM 256GB 1 SIM": "Samsung Galaxy A17 5G",
            "Samsung Galaxy A17 5G 8GB RAM 256GB Dual SIM": "Samsung Galaxy A17 5G",
            "Apple iPhone 17 Pro 256GB eSIM": "Apple iPhone 17 Pro",
            "Xiaomi Redmi Note 15 Pro+ 5G 8GB RAM 256GB": "Xiaomi Redmi Note 15 Pro+ 5G",
            'Lenovo Tab One 8.7" 4G 4GB RAM 128GB con mouse': 'Lenovo Tab One 8.7" 4G con mouse',
            "Motorola Edge 50 Fusion 5G 8GB RAM 256GB SIM + eSIM": "Motorola Edge 50 Fusion 5G",
            "Xiaomi Watch S4 41mm": "Xiaomi Watch S4 41mm",
            "JBL Flip 7": "JBL Flip 7",
        }
        for sku, modelo in casos.items():
            with self.subTest(sku=sku):
                self.assertEqual(modelo, parsear_lista.titulo_de_modelo(sku))

    def test_cada_producto_lleva_su_modelo_y_dos_configuraciones_lo_comparten(self):
        datos = parsear("*SAMSUNG*\n🎃A57 5G (8+256)$1.290\n🎃A57 5G (12+512)$1.700\n🎃A17 4G (8+256)$620\n"
                        "🎃A17 5G (8+256)$675 *1 SIM*\n")
        self.assertEqual(
            [("samsung-galaxy-a57-5g", "Samsung Galaxy A57 5G")] * 2
            + [("samsung-galaxy-a17-4g", "Samsung Galaxy A17 4G"),
               ("samsung-galaxy-a17-5g", "Samsung Galaxy A17 5G")],
            [(p["id_modelo"], p["titulo_modelo"]) for p in datos["productos"]])

    def test_el_modelo_sale_del_titulo_definitivo(self):
        eq = {"jbl-extreme-4": equivalencia("jbl-xtreme-4", "JBL Xtreme 4")}
        [p] = parsear("*PARLANTE ORIGINALES*🔊\n🔊JBL EXTREME 4 $1.100\n", eq)["productos"]
        self.assertEqual(("jbl-xtreme-4", "JBL Xtreme 4"), (p["id_modelo"], p["titulo_modelo"]))

    def test_la_marca_se_escribe_siempre_igual(self):
        # La base tenía «OPPO» y la lista del 08/10 «Oppo»: con la base por
        # modelo, una mayúscula partía la misma marca en dos.
        [p] = parsear(" *OPPO* \n🎃A6C (4+128)$575 \n🎃A6K (4+256)$850\n🎃A6C (4+64)$500\n")["productos"][:1]
        self.assertEqual(("OPPO", "OPPO A6C 4GB RAM 128GB"), (p["marca"], p["titulo"]))


class LineasExcluidas(unittest.TestCase):
    # Decisión del 18/10/2026: la Honor Band no se vende; las demás bandas, sí.
    LISTA = "*RELOJES ORIGINALES*\n⌚HONOR 5i $155\n⌚HONOR BAND 9 $180\n⌚HONOR 2i $130\n" \
            "⌚XIAOMI SMART BAND 10 $160\n"

    def test_la_honor_band_queda_por_fuera_con_su_motivo(self):
        datos = parsear(self.LISTA, parsear_lista.cargar_equivalencias())
        fuera = {d["titulo"]: d["motivo"] for d in datos["descartados"]}
        self.assertEqual({"Honor 5i", "Honor Band 9"}, set(fuera))
        self.assertIn("Honor Band", fuera["Honor 5i"])

    def test_el_honor_2i_es_un_reloj_y_la_xiaomi_smart_band_sigue_entrando(self):
        datos = parsear(self.LISTA, parsear_lista.cargar_equivalencias())
        self.assertEqual(["Honor Watch Choice 2i", "Xiaomi Smart Band 10"], sorted(titulos(datos)))


class NombresDel22DeOctubre(unittest.TestCase):
    """22/10/2026: dos equipos ya conocidos salían con otro nombre.

    Un nombre distinto es un id distinto: la comparación los daba por nuevos y
    retiraba del sitio al conocido, que sí había venido en la lista.
    """

    def test_el_mi_que_xiaomi_dejo_en_la_serie_11_no_va_en_el_titulo(self):
        datos = parsear(" *XIAOMI* \n🎃MI 17T 5G (12+256) $1.900\n🎃MI 11T 5G (8+128) $900\n")
        self.assertEqual(["Xiaomi 17T 5G 12GB RAM 256GB", "Xiaomi Mi 11T 5G 8GB RAM 128GB"],
                         titulos(datos))
        self.assertIn("Xiaomi dejó el prefijo «Mi» desde la serie 12: se leyó como Xiaomi 17T",
                      datos["productos"][0]["supuestos"])

    def test_la_serie_fusion_de_motorola_es_edge(self):
        datos = parsear(" *MOTOROLA*\n🎃50 FUSIÓN 5G (8+256) $755\n🎃MOTO G67 4G (8+256) $730\n")
        self.assertEqual(["Motorola Edge 50 Fusion 5G 8GB RAM 256GB",
                          "Motorola Moto G67 4G 8GB RAM 256GB"], titulos(datos))
        self.assertIn("la sección Motorola abrevia la serie Edge: se leyó como Edge 50 Fusion",
                      datos["productos"][0]["supuestos"])

    def test_el_fuego_delante_de_un_macbook_no_lo_vuelve_cable(self):
        # El 🔥 es la viñeta de los cables, pero también adorno: aquí abre un
        # MacBook reacondicionado (CPO), sin precio y en tres líneas.
        datos = parsear(" *TABLET XIAOMI* \n📲XIAOMI PAD 2 PRO 11\" WIFI (8+256) $920\n"
                        "------\n\n🔥MacBook Neo 2\nCPO\n256\n")
        [d] = [d for d in datos["descartados"] if "MacBook" in d["texto_origen"]]
        self.assertEqual(("computadores", "reacondicionado"), (d["categoria"], d["condicion"]))

    def test_el_fuego_delante_de_un_cable_sigue_siendo_cable(self):
        [d] = parsear("*CABLE*\n🔥TIPO C - LIGHTNING $45\n")["descartados"]
        self.assertEqual("cables", d["categoria"])


if __name__ == "__main__":
    unittest.main()
