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



class LienzoPrevio(unittest.TestCase):
    def test_conserva_el_lienzo_con_que_ya_se_procesaron_las_fotos_del_producto(self):
        registros = [{"grupo": "iphone", "lienzo": [1200, 1200]}, {"grupo": "iphone", "lienzo": [1200, 1200]},
                     {"grupo": "iphone", "lienzo": [1600, 1600]}, {"grupo": "otro", "lienzo": [2000, 2000]}]
        self.assertEqual(1200, salidas.lienzo_previo(registros, "iphone"))

    def test_sin_fotos_previas_no_impone_nada(self):
        self.assertIsNone(salidas.lienzo_previo([{"grupo": "otro", "lienzo": [2000, 2000]}], "iphone"))



class VersionDelRetoque(unittest.TestCase):
    """Decisión del 20/10/2026: una marca a ojo vale para el retoque sobre el que se puso."""

    def test_una_marca_de_un_retoque_anterior_caduca(self):
        # Las Tab A11 seguían en REPETIR por «el recorte se comió la pantalla», que el retoque nuevo
        # ya no hacía.
        vieja = {"estado": "REPETIR", "motivo": "se comió la pantalla", "version_retoque": "2026-10-01"}
        sin_version = {"estado": "REVISAR", "motivo": "una mano"}
        vigente = {"estado": "REPETIR", "motivo": "marca de agua", "version_retoque": salidas.VERSION_RETOQUE}
        vigentes, caducadas = salidas.separar_marcas([vieja, sin_version, vigente])
        self.assertEqual([vigente], vigentes)
        self.assertEqual([vieja, sin_version], caducadas)

    def test_una_marca_caducada_no_desaparece_en_el_siguiente_reproceso(self):
        # La marca caducada quedaba aparte, y la vuelta siguiente ya no la veía: la foto podía salir
        # LISTA sin que nadie decidiera sobre el REPETIR viejo.
        caducada = {"estado": "REPETIR", "motivo": "se comió la pantalla", "version_retoque": "2026-10-01"}
        _, primera = salidas.separar_marcas([caducada])
        vigentes, segunda = salidas.separar_marcas([], primera)
        self.assertEqual([], vigentes)
        self.assertEqual([caducada], segunda)


class LoQueSobreviveAlReproceso(unittest.TestCase):
    """22/10/2026: reprocesar un modelo borraba su principal y sus aprobaciones a ojo.

    procesar.py armaba el registro nuevo con `revisada` en None y sin `principal`, así que un modelo
    que cambiaba —y se reprocesa entero— perdía lo que la persona ya había decidido sobre sus fotos."""

    PREVIO = {"huella_origen": "abc", "huella_config": "cfg-1200", "principal": {"fecha": "2026-10-22"},
              "revisada": {"fecha": "2026-10-22", "nota": "revisada al 100 %"}}

    def test_la_misma_foto_con_el_mismo_retoque_conserva_principal_y_aprobacion(self):
        heredado = salidas.heredado_del_previo(self.PREVIO, "abc", "cfg-1200")
        self.assertEqual(self.PREVIO["principal"], heredado["principal"])
        self.assertEqual(self.PREVIO["revisada"], heredado["revisada"])

    def test_otro_lienzo_o_otro_retoque_conserva_la_principal_y_no_la_aprobacion(self):
        # La aprobación miró un resultado; con otro lienzo o retoque el resultado es otro. La
        # principal es la toma, y la toma no cambió.
        heredado = salidas.heredado_del_previo(self.PREVIO, "abc", "cfg-1600")
        self.assertEqual(self.PREVIO["principal"], heredado["principal"])
        self.assertIsNone(heredado["revisada"])

    def test_otra_foto_con_el_mismo_nombre_no_hereda_nada(self):
        self.assertEqual({"principal": None, "revisada": None},
                         salidas.heredado_del_previo(self.PREVIO, "otra", "cfg-1200"))

    def test_sin_registro_previo_no_hereda_nada(self):
        self.assertEqual({"principal": None, "revisada": None}, salidas.heredado_del_previo({}, "abc", "cfg"))



class TamanoOriginal(unittest.TestCase):
    def test_una_foto_cargada_reducida_da_su_tamano_de_antes(self):
        # marcar.py --excluir recortaba contra 4000 una zona dada en px de la foto de 8000.
        self.assertEqual((8000, 6000), salidas.tamano_original({"ancho": 4000, "alto": 3000,
                                                                 "reducida_desde": [8000, 6000]}))
        self.assertEqual((1200, 900), salidas.tamano_original({"ancho": 1200, "alto": 900}))


class LadoDeCarga(unittest.TestCase):
    """Decisión del 20/10/2026: una foto enorme se carga reducida, pero nunca hasta tener que ampliar."""

    def test_una_foto_de_8000_con_el_producto_grande_se_carga_a_4000(self):
        # El Moto G17 _3: 8000 px, el teléfono a lo alto. El reproceso murió por memoria en ella.
        self.assertEqual(4000, salidas.lado_de_carga(8000, 7000, 2000, 0.85))

    def test_no_se_reduce_hasta_que_el_producto_quede_corto(self):
        # Un producto de 2800 px en una foto de 8000 necesita 2550 (1700 × 1,5): apenas se reduce.
        lado = salidas.lado_de_carga(8000, 2800, 2000, 0.85)
        self.assertIsNotNone(lado)
        self.assertGreaterEqual(2800 * lado / 8000, 2000 * 0.85 * salidas.MARGEN_DE_CARGA - 1)

    def test_si_reducir_obligaria_a_ampliar_se_carga_entera(self):
        self.assertIsNone(salidas.lado_de_carga(8000, 1800, 2000, 0.85))

    def test_sin_una_medida_fiable_del_producto_no_se_reduce(self):
        # Sobre una mesa el estimador no sabe: una foto de 8000 con el producto a 1600 salía 2,13×.
        self.assertIsNone(salidas.lado_de_carga(8000, None, 2000, 0.85))

    def test_una_foto_normal_se_carga_entera(self):
        self.assertIsNone(salidas.lado_de_carga(1200, 1000, 1200, 0.85))


if __name__ == "__main__":
    unittest.main()
