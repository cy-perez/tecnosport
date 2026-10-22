"""Pruebas del emparejador de ofertas de `precios.py`.

`coincide()` decide qué oferta del retail es el mismo producto de la lista. Un
vecino que se cuela no hace ruido: el precio sale razonable y es de otro equipo.
"""

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "scripts"))

from precios import coincide  # noqa: E402


class GeneracionConLetra(unittest.TestCase):
    """22/10/2026: «15C» y «17T» no se miraban, y cualquier Xiaomi de 256GB pasaba.

    El Redmi 15C 8/256 tomó precio del Redmi 15, del Redmi 17 y del Note 17; el
    Xiaomi 17T, del Note 17 5G y del Note 15 5G, y quedó a −73,8 % del costo.
    """

    def test_el_15_no_es_el_15c(self):
        self.assertFalse(coincide("Celular Xiaomi Redmi 15 8Gb 256Gb Negro",
                                  "Xiaomi Redmi 15C 4G 8GB RAM 256GB"))

    def test_el_17_no_es_el_15c(self):
        self.assertFalse(coincide("Celular Xiaomi Redmi 17 6GB/256GB Black",
                                  "Xiaomi Redmi 15C 4G 8GB RAM 256GB"))

    def test_el_note_17_no_es_el_17t(self):
        self.assertFalse(coincide("Celular XIAOMI NOTE 17 5G 256 GB 6 GB RAM Negro",
                                  "Xiaomi 17T 5G 12GB RAM 256GB"))

    def test_el_15c_sigue_siendo_el_15c(self):
        self.assertTrue(coincide("Celular Xiaomi Redmi 15C 8GB Ram / 256GB Negro",
                                 "Xiaomi Redmi 15C 4G 8GB RAM 256GB"))

    def test_la_letra_separada_tambien_cuenta(self):
        self.assertTrue(coincide("Celular Xiaomi Redmi 15 C 256GB 8GB Verde",
                                 "Xiaomi Redmi 15C 4G 8GB RAM 256GB"))

    def test_la_red_no_es_una_generacion(self):
        # «4G» también empieza por número y termina en letra, y no lo trae
        # cualquier oferta: sin declarar la red, la oferta sigue pasando.
        self.assertTrue(coincide("Celular Samsung Galaxy A17 256GB 8GB Gris",
                                 "Samsung Galaxy A17 4G 8GB RAM 256GB"))


class MasPegado(unittest.TestCase):
    """22/10/2026: la Tab A11 de 8.7" entraba como Tab A11+ y movía el margen a −0,5 %."""

    def test_la_a11_no_es_la_a11_mas(self):
        self.assertFalse(coincide("Tablet SAMSUNG GALAXY A11 8.7 pulgadas Wifi RAM 8 GB 128 GB Gris",
                                  'Samsung Galaxy Tab A11+ 11" WiFi 6GB RAM 128GB'))

    def test_la_a11_mas_es_la_a11_mas(self):
        self.assertTrue(coincide("Tablet SAMSUNG GALAXY A11+ 11 pulgadas Wifi RAM 6 GB 128 GB Gris",
                                 'Samsung Galaxy Tab A11+ 11" WiFi 6GB RAM 128GB'))

    def test_escrito_plus_tambien_es_la_a11_mas(self):
        self.assertTrue(coincide("Tablet Samsung Galaxy Tab A11 Plus 128GB 6GB Gris",
                                 'Samsung Galaxy Tab A11+ 11" WiFi 6GB RAM 128GB'))

    def test_la_a11_mas_no_es_la_a11(self):
        self.assertFalse(coincide("Tablet SAMSUNG GALAXY A11+ 11 pulgadas Wifi RAM 6 GB 128 GB Gris",
                                  'Samsung Galaxy Tab A11 8.7" WiFi 8GB RAM 128GB'))


class WifiEsUnaRed(unittest.TestCase):
    """22/10/2026: la Redmi Pad 2 4G tomaba el precio de la WiFi."""

    def test_la_wifi_no_es_la_4g(self):
        self.assertFalse(coincide("Tablet XIAOMI Redmi Pad 2 11 pulgadas Wifi RAM 4 GB 128 GB Gris",
                                  'Xiaomi Redmi Pad 2 11" 4G 4GB RAM 128GB'))

    def test_la_wifi_es_la_wifi(self):
        self.assertTrue(coincide("Tablet XIAOMI Redmi Pad 2 11 pulgadas Wi-Fi RAM 8 GB 256 GB Gris",
                                 'Xiaomi Redmi Pad 2 11" WiFi 8GB RAM 256GB'))

    def test_la_4g_con_wifi_sigue_siendo_4g(self):
        self.assertTrue(coincide("Tablet Xiaomi Redmi Pad 2 11 4G LTE WiFi 4GB 128GB",
                                 'Xiaomi Redmi Pad 2 11" 4G 4GB RAM 128GB'))


class GeneracionConNumero(unittest.TestCase):
    """Lo que ya cubría la regla 6 no se mueve."""

    def test_el_xtreme_4_no_es_el_xtreme_5(self):
        self.assertFalse(coincide("Parlante JBL Xtreme 4 Negro", "JBL Xtreme 5"))

    def test_el_xtreme_5_es_el_xtreme_5(self):
        self.assertTrue(coincide("Parlante JBL Xtreme 5 Negro", "JBL Xtreme 5"))


if __name__ == "__main__":
    unittest.main()
