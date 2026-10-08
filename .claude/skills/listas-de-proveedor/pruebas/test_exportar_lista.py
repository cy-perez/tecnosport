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

    def test_la_sim_esim_sola_tambien_se_lee(self):
        self.assertEqual("eSIM", exportar_lista.sim_de({"atributos": ["eSIM"]}))
        self.assertEqual("SIM + eSIM", exportar_lista.sim_de({"atributos": ["SIM + eSIM"]}))
        self.assertIsNone(exportar_lista.sim_de({"atributos": []}))


if __name__ == "__main__":
    unittest.main()
