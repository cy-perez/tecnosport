"""Pruebas de la paleta del fabricante: qué modelos no la tienen y fijarla con su fuente (ADR-0086)."""

import sys
import unittest
from pathlib import Path

SKILL = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(SKILL / "scripts"))

import conocidos  # noqa: E402
import paletas  # noqa: E402

VIEJO = "colores: Apple no publica variantes de color para los AirPods Pro 3."
DE_LA_LISTA = "colores sugeridos: la lista no marca colores, así que se sugiere la paleta completa"


def base():
    b = conocidos.base_vacia()
    b["modelos"]["apple-airpods-pro-3"] = {
        "titulo": "Apple AirPods Pro 3", "marca": "Apple", "categoria": "audifonos",
        "paleta": [], "supuestos_paleta": [VIEJO]}
    b["modelos"]["samsung-galaxy-a57-5g"] = {
        "titulo": "Samsung Galaxy A57 5G", "marca": "Samsung", "categoria": "celulares",
        "paleta": ["Azul", "Gris"], "supuestos_paleta": []}
    return b


def corrida():
    return {"productos": [
        {"id": "apple-airpods-pro-3", "id_modelo": "apple-airpods-pro-3",
         "titulo_modelo": "Apple AirPods Pro 3", "colores_oficiales": [],
         "supuestos": [VIEJO, DE_LA_LISTA, "ficha: apple.com"], "supuestos_lista": [DE_LA_LISTA]},
        {"id": "fly-go-pad-10", "id_modelo": "fly-go-pad-10", "titulo_modelo": "Fly Go Pad 10",
         "colores_oficiales": [], "supuestos": []},
    ]}


class Pendientes(unittest.TestCase):
    def test_lista_los_sin_paleta_de_la_base_y_de_la_corrida(self):
        filas = paletas.pendientes(base(), corrida())

        self.assertEqual(["apple-airpods-pro-3", "fly-go-pad-10"], [f["id_modelo"] for f in filas])
        airpods, fly = filas
        self.assertTrue(airpods["en_la_base"] and airpods["en_la_corrida"])
        self.assertEqual([VIEJO], airpods["supuestos_paleta"])
        self.assertEqual((False, True), (fly["en_la_base"], fly["en_la_corrida"]))

    def test_sin_corrida_mira_solo_la_base(self):
        self.assertEqual(["apple-airpods-pro-3"],
                         [f["id_modelo"] for f in paletas.pendientes(base(), None)])


class Fijar(unittest.TestCase):
    def test_fija_la_paleta_en_la_base_y_en_la_corrida_y_reemplaza_el_supuesto_viejo(self):
        b, d = base(), corrida()

        tocado = paletas.fijar(b, d, "apple-airpods-pro-3", ["Blanco"], "apple.com, especificaciones",
                               "https://support.apple.com/es-lamr/125135", "2026-10-25")

        self.assertEqual({"base": True, "configuraciones": 1}, tocado)
        modelo = b["modelos"]["apple-airpods-pro-3"]
        self.assertEqual(["Blanco"], modelo["paleta"])
        self.assertEqual(["colores: según apple.com, especificaciones "
                          "(https://support.apple.com/es-lamr/125135), consultado el 2026-10-25."],
                         modelo["supuestos_paleta"])
        producto = d["productos"][0]
        self.assertEqual(["Blanco"], producto["colores_oficiales"])
        self.assertNotIn(VIEJO, producto["supuestos"])
        self.assertIn(DE_LA_LISTA, producto["supuestos"], "lo que es de la lista se queda")
        self.assertIn("ficha: apple.com", producto["supuestos"])

    def test_un_solo_acabado_es_un_color_y_no_una_paleta_vacia(self):
        with self.assertRaises(ValueError):
            paletas.fijar(base(), corrida(), "apple-airpods-pro-3", [" ", ""], "apple.com", None,
                          "2026-10-25")

    def test_sin_fuente_no_se_fija(self):
        with self.assertRaises(ValueError):
            paletas.fijar(base(), corrida(), "apple-airpods-pro-3", ["Blanco"], " ", None,
                          "2026-10-25")

    def test_limpia_como_la_api_y_rechaza_un_salto_de_linea(self):
        self.assertEqual(["Negro", "Azul"], paletas.limpiar_colores([" Negro", "negro", "Azul "]))
        with self.assertRaises(ValueError):
            paletas.limpiar_colores(["Negro\nAzul"])

    def test_un_modelo_que_no_esta_en_ninguna_parte_se_dice(self):
        with self.assertRaises(ValueError):
            paletas.fijar(base(), corrida(), "no-existe", ["Negro"], "x", None, "2026-10-25")

    def test_consolidar_despues_no_devuelve_el_supuesto_viejo(self):
        """El supuesto viejo vivía en los dos lados; quitándolo de uno solo, `consolidar` lo
        sumaba otra vez desde el otro."""
        b, d = base(), corrida()
        paletas.fijar(b, d, "apple-airpods-pro-3", ["Blanco"], "apple.com", None, "2026-10-25")
        investigados = [s for s in d["productos"][0]["supuestos"]
                        if s not in set(d["productos"][0]["supuestos_lista"])]
        self.assertEqual([], [s for s in investigados if s == VIEJO])


if __name__ == "__main__":
    unittest.main()
