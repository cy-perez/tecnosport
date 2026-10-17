"""Pruebas de `salidas.py`: qué lienzo lleva cada producto y qué archivos web salen de cada foto.

Los lados de producto de los casos son los medidos en `catalogo/4-entregables/fichas/` el
16/10/2026, cuando la regla de la mejor foto dejaba fuera de la tanda a modelos que en 1200
reunían de sobra sus cuatro LISTA."""

import json
import sys
import unittest
from pathlib import Path

SKILL = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(SKILL / "scripts"))

import procesar_fichas as pf  # noqa: E402
import salidas  # noqa: E402

CFG = {"lienzos_escala": [2000, 1600, 1200, 1000, 800, 600, 480, 400, 320], "ocupacion": 0.85,
       "ampliacion_revisar": 1.6, "ampliacion_tolerada": 1.25, "lienzo": [2000, 2000]}


def config_real(variantes=False) -> dict:
    """El config.json de la skill, como lo deja `procesar.py` con y sin `--variantes`."""
    cfg = json.loads((SKILL / "config.json").read_text(encoding="utf-8"))
    if variantes:
        cfg["anchos_variantes"] = cfg["anchos_variantes_propuestos"]
        cfg["formatos_web"] = cfg["formatos_web_propuestos"]
        cfg["formatos_por_ancho"] = cfg["formatos_por_ancho_propuestos"]
    return cfg


def tanda(lados):
    return salidas.elegir_lienzo_tanda(lados, CFG, 1200, 4)


class LienzoDeLaTanda(unittest.TestCase):
    def test_una_foto_grande_no_deja_a_las_demas_en_revisar(self):
        a57 = [1194, 797, 797, 797, 796, 796]
        self.assertEqual(1600, salidas.elegir_lienzo(a57, CFG), "la regla de la mejor foto")
        self.assertEqual(1, salidas.listas_en(1600, a57, CFG))
        self.assertEqual(1200, tanda(a57))
        self.assertEqual(6, salidas.listas_en(1200, a57, CFG))

    def test_un_modelo_que_da_para_2000_se_queda_en_2000(self):
        self.assertEqual(2000, tanda([6463, 6474, 6715, 6963, 6963]))

    def test_sube_a_1200_aunque_la_mejor_foto_no_llegue_con_la_ampliacion_tolerada(self):
        a27 = [800] * 6
        self.assertEqual(1000, salidas.elegir_lienzo(a27, CFG))
        self.assertEqual(1200, tanda(a27), "a 1,28× son LISTA")

    def test_los_iphone_de_650_px_entran_en_1200_con_el_umbral_en_1_6(self):
        self.assertEqual(1200, tanda([717, 658, 658, 658, 658, 658]))
        con_1_5 = dict(CFG, ampliacion_revisar=1.5)
        self.assertEqual(1, salidas.listas_en(1200, [717, 658, 658, 658, 658, 658], con_1_5))

    def test_elige_el_mayor_que_cumple_y_no_el_que_mas_deja(self):
        # En 1600 caben cuatro (1360/1000 = 1,36) y en 1200, las seis: se queda en 1600.
        self.assertEqual(1600, tanda([1000, 1000, 1000, 1000, 700, 700]))

    def test_sin_cuatro_en_ningun_lado_elige_el_que_mas_deja_y_a_igualdad_el_mayor(self):
        # Dos LISTA tanto en 1600 como en 1200; en 2000, ninguna.
        self.assertEqual(1600, tanda([1000, 1000, 500, 500]))

    def test_si_ni_en_1200_sale_una_vuelve_a_la_regla_de_siempre(self):
        xtreme = [459, 459, 458, 457, 456]
        self.assertEqual(salidas.elegir_lienzo(xtreme, CFG), tanda(xtreme))
        self.assertEqual(600, tanda(xtreme))

    def test_las_fotos_que_no_se_midieron_no_cuentan(self):
        self.assertEqual(0, salidas.listas_en(1200, [0, None, 0], CFG))


class AnchosWeb(unittest.TestCase):
    def test_con_variantes_salen_el_carrusel_y_la_miniatura(self):
        cfg = config_real(variantes=True)
        esperado = [(1200, ["avif", "jpg"]), (480, ["avif"])]
        self.assertEqual(esperado, salidas.anchos_web(cfg, 2000, True), "2000 se queda en la maestra")
        self.assertEqual(esperado, salidas.anchos_web(cfg, 1600, True))
        self.assertEqual(esperado, salidas.anchos_web(cfg, 1200, True))

    def test_un_lienzo_menor_que_el_carrusel_ocupa_su_lugar(self):
        cfg = config_real(variantes=True)
        self.assertEqual([(1000, ["avif", "jpg"]), (480, ["avif"])], salidas.anchos_web(cfg, 1000, True))
        self.assertEqual([(400, ["avif", "jpg"])], salidas.anchos_web(cfg, 400, True))

    def test_sin_variantes_el_modo_plano_sigue_igual(self):
        self.assertEqual([(2000, ["avif"])], salidas.anchos_web(config_real(), 2000, False))

    def test_el_modo_plano_no_agrega_el_lienzo(self):
        self.assertEqual([(480, ["avif"])], salidas.anchos_web(config_real(variantes=True), 1000, False))


class ConfigDeLaSkill(unittest.TestCase):
    """Lo que dice el config.json que se entrega, no el de una prueba."""

    def test_el_umbral_de_revisar_esta_en_1_6(self):
        self.assertEqual(1.6, config_real()["ampliacion_revisar"])

    def test_la_foto_minima_antes_de_procesar_baja_a_638(self):
        # 1200 × 0,85 / 1,6 = 637,5
        self.assertEqual(638, pf.lado_minimo(pf.cargar_config()))


if __name__ == "__main__":
    unittest.main()
