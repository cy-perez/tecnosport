"""Pruebas del exportador: lo que la API recibe de cada lista."""

import sys
import unittest
from pathlib import Path

SKILL = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(SKILL / "scripts"))

import exportar_lista  # noqa: E402


def producto(id_, titulo, **cambios):
    p = {"id": id_, "titulo": titulo, "id_modelo": "samsung-galaxy-a17-5g", "titulo_modelo": "Samsung Galaxy A17 5G",
         "marca": "Samsung", "categoria": "celulares", "ram": "8GB", "almacenamiento": "256GB",
         "atributos": [], "precio_proveedor_cop": 675_000, "precio_mercado_cop": 849_900,
         "colores_oficiales": ["Negro", "Gris", "Azul"], "colores_sugeridos": ["Negro"],
         "descripcion": "El Galaxy A17 5G.\n", "meta_descripcion": "El Galaxy A17 5G."}
    p.update(cambios)
    return p


DATOS = {
    "fecha_lista": "2026-10-08", "bloques": ["ANDROID"], "comparacion": {"fecha": "2026-10-08"},
    "productos": [
        producto("samsung-galaxy-a17-5g-8gb-ram-256gb-1-sim", "Samsung Galaxy A17 5G 8GB RAM 256GB 1 SIM",
                 atributos=["1 SIM"]),
        producto("samsung-galaxy-a17-5g-8gb-ram-256gb-dual-sim", "Samsung Galaxy A17 5G 8GB RAM 256GB Dual SIM",
                 atributos=["Dual SIM"], precio_proveedor_cop=690_000),
        producto("jbl-flip-7", "JBL Flip 7", id_modelo="jbl-flip-7", titulo_modelo="JBL Flip 7", marca="JBL",
                 categoria="parlantes", ram=None, almacenamiento=None, descripcion=None),
    ],
    "desaparecidos": [{"id": "xiaomi-17t-5g-12gb-ram-256gb", "id_modelo": "xiaomi-17t-5g"}],
    "modelos_desaparecidos": [{"id_modelo": "xiaomi-17t-5g"}],
}


class Exportar(unittest.TestCase):
    def test_un_modelo_con_sus_configuraciones(self):
        lista, _ = exportar_lista.exportar(DATOS)
        [a17] = lista["modelos"]
        self.assertEqual(("samsung-galaxy-a17-5g", "Samsung Galaxy A17 5G", ["Negro", "Gris", "Azul"]),
                         (a17["idModelo"], a17["titulo"], a17["paleta"]))
        self.assertEqual([("1 SIM", 675_000), ("Dual SIM", 690_000)],
                         [(c["sim"], c["costoProveedor"]) for c in a17["configuraciones"]])
        self.assertEqual(["Negro"], a17["configuraciones"][0]["coloresSugeridos"])

    def test_un_modelo_sin_descripcion_no_se_exporta_y_se_dice(self):
        lista, fuera = exportar_lista.exportar(DATOS)
        self.assertNotIn("jbl-flip-7", [m["idModelo"] for m in lista["modelos"]])
        self.assertEqual([("JBL Flip 7", "sin descripción: falta terminar el paso 4")], fuera)

    def test_lleva_los_desaparecidos_que_decidio_la_comparacion(self):
        lista, _ = exportar_lista.exportar(DATOS)
        self.assertEqual(["xiaomi-17t-5g-12gb-ram-256gb"], lista["configuracionesDesaparecidas"])
        self.assertEqual(["xiaomi-17t-5g"], lista["modelosDesaparecidos"])
        self.assertEqual(("2026-10-08", ["ANDROID"]), (lista["fechaLista"], lista["bloques"]))

    def test_un_modelo_nuevo_lleva_los_colores_que_sugiere_la_lista(self):
        datos = {**DATOS, "productos": [producto(
            "samsung-galaxy-a17-5g-8gb-ram-256gb-1-sim", "Samsung Galaxy A17 5G 8GB RAM 256GB 1 SIM",
            atributos=["1 SIM"], colores_sugeridos=[], colores_familia=["Azul"],
            colores_oficiales=["Negro", "Azul claro"])]}
        lista, _ = exportar_lista.exportar(datos)
        self.assertEqual(["Azul claro"], lista["modelos"][0]["configuraciones"][0]["coloresSugeridos"])

    def test_un_id_que_no_cabe_se_queda_fuera_y_se_dice(self):
        largo = "x" * 61
        datos = {**DATOS, "productos": [producto(largo, "Equipo con nombre larguísimo")] + DATOS["productos"]}
        lista, fuera = exportar_lista.exportar(datos)
        skus = [c["sku"] for m in lista["modelos"] for c in m["configuraciones"]]
        self.assertNotIn(largo, skus)
        self.assertIn("Equipo con nombre larguísimo", [t for t, _ in fuera])

    def test_sin_fecha_no_se_exporta(self):
        with self.assertRaises(ValueError):
            exportar_lista.exportar({**DATOS, "fecha_lista": None})

    def test_la_sim_esim_sola_tambien_se_lee(self):
        self.assertEqual("eSIM", exportar_lista.sim_de({"atributos": ["eSIM"]}))
        self.assertEqual("SIM + eSIM", exportar_lista.sim_de({"atributos": ["SIM + eSIM"]}))
        self.assertIsNone(exportar_lista.sim_de({"atributos": []}))


class MargenMinimo(unittest.TestCase):
    """Regla 18, 15/10/2026: con menos de un 5 % sobre el costo no se ofrece."""

    def a17(self, sim, costo, mercado):
        return producto(f"samsung-galaxy-a17-5g-8gb-ram-256gb-{sim}", f"Samsung Galaxy A17 5G 8GB RAM 256GB {sim}",
                        precio_proveedor_cop=costo, precio_mercado_cop=mercado)

    def exportar(self, *productos):
        return exportar_lista.exportar({**DATOS, "productos": list(productos)})

    def test_por_debajo_del_cinco_por_ciento_deja_de_ofrecerse_y_se_dice(self):
        lista, fuera = self.exportar(self.a17("1-sim", 1_000_000, 1_049_000), self.a17("dual-sim", 690_000, 849_900))
        self.assertEqual(["samsung-galaxy-a17-5g-8gb-ram-256gb-dual-sim"],
                         [c["sku"] for c in lista["modelos"][0]["configuraciones"]])
        self.assertIn("samsung-galaxy-a17-5g-8gb-ram-256gb-1-sim", lista["configuracionesDesaparecidas"])
        self.assertNotIn("samsung-galaxy-a17-5g", lista["modelosDesaparecidos"])
        self.assertIn("margen de 4.9 %", dict(fuera)["Samsung Galaxy A17 5G 8GB RAM 256GB 1-sim"])

    def test_el_cinco_por_ciento_justo_se_ofrece(self):
        lista, _ = self.exportar(self.a17("1-sim", 1_000_000, 1_050_000))
        self.assertEqual(1, len(lista["modelos"]))

    def test_por_debajo_del_costo_tambien(self):
        lista, _ = self.exportar(self.a17("1-sim", 840_000, 799_010))
        self.assertEqual([], lista["modelos"])

    def test_si_ninguna_configuracion_alcanza_el_modelo_deja_de_ofrecerse(self):
        lista, _ = self.exportar(self.a17("1-sim", 1_000_000, 1_000_000), self.a17("dual-sim", 1_000_000, 990_000))
        self.assertEqual([], lista["modelos"])
        self.assertIn("samsung-galaxy-a17-5g", lista["modelosDesaparecidos"])

    def test_sin_precio_de_mercado_no_hay_margen_que_medir(self):
        lista, _ = self.exportar(self.a17("1-sim", 1_000_000, None))
        self.assertEqual(1, len(lista["modelos"]))


class DescripcionQueSePublica(unittest.TestCase):
    """La última compuerta antes de la API: lo vacío no se publica, y un marcador tampoco."""

    def exportar(self, descripcion):
        datos = {"fecha_lista": "2026-10-15", "productos": [
            producto("samsung-galaxy-a17-5g-8gb-ram-256gb", "Samsung Galaxy A17 5G 8GB RAM 256GB",
                     descripcion=descripcion)]}
        return exportar_lista.exportar(datos)

    def test_la_ficha_vacia_no_llega_a_la_api(self):
        lista, _ = self.exportar("El Galaxy A17 5G.\n\n## Ficha técnica\n\n| Atributo | Detalle |\n|---|---|\n\n"
                                 "## Garantía y notas\n\n- Producto nuevo.\n")
        self.assertEqual("El Galaxy A17 5G.\n\n## Garantía y notas\n\n- Producto nuevo.\n",
                         lista["modelos"][0]["descripcion"])

    def test_un_marcador_deja_el_modelo_fuera_y_se_dice(self):
        lista, fuera = self.exportar("El Galaxy A17 5G.\n\n## Garantía y notas\n\n- Llega en [[PLAZO]] días.\n")
        self.assertEqual([], lista["modelos"])
        self.assertIn("marcador", dict(fuera)["Samsung Galaxy A17 5G"])


class LaQueEntro(unittest.TestCase):
    """Una exportación no pisa la que ya entró como inventario (15/10/2026)."""

    LISTA = '{\n  "fechaLista": "2026-10-15",\n  "modelos": []\n}'

    def por_proponer(self, contenido, fecha="2026-10-15"):
        return {"fechaLista": fecha, "huellaLista": exportar_lista.huella(contenido), "modelos": []}

    def test_otra_exportacion_de_la_misma_lista_se_niega(self):
        ya_entro = self.por_proponer('{\n  "fechaLista": "2026-10-15",\n  "modelos": [1]\n}')
        motivo = exportar_lista.choque_con_lo_que_entro(self.LISTA, ya_entro)
        self.assertIn("ya entró con otra exportación", motivo)
        self.assertIn("--reemplazar-la-que-entro", motivo)

    def test_la_misma_exportacion_otra_vez_pasa(self):
        self.assertIsNone(exportar_lista.choque_con_lo_que_entro(self.LISTA, self.por_proponer(self.LISTA)))

    def test_una_lista_de_otra_fecha_pasa(self):
        de_ayer = self.por_proponer('{"fechaLista": "2026-10-14"}', fecha="2026-10-14")
        self.assertIsNone(exportar_lista.choque_con_lo_que_entro(self.LISTA, de_ayer))

    def test_sin_inventario_no_hay_con_que_chocar(self):
        self.assertIsNone(exportar_lista.choque_con_lo_que_entro(self.LISTA, None))
        sin_huella = {"fechaLista": "2026-10-15", "huellaLista": None}
        self.assertIsNone(exportar_lista.choque_con_lo_que_entro(self.LISTA, sin_huella))

    def test_la_huella_es_la_del_importador(self):
        # El SHA-256 del texto tal cual: si cambiara, toda exportación chocaría con su propio inventario.
        self.assertEqual("44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a",
                         exportar_lista.huella("{}"))


if __name__ == "__main__":
    unittest.main()
