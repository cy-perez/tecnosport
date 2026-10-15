"""Pruebas de la carpeta de cada modelo en catalogo/4-entregables/fichas/."""

import sys
import tempfile
import unittest
from pathlib import Path

SKILL = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(SKILL / "scripts"))

import fichas  # noqa: E402


def producto(id_, titulo, **cambios):
    p = {"id": id_, "titulo": titulo, "id_modelo": "samsung-galaxy-tab-a11-8-7-wifi",
         "titulo_modelo": 'Samsung Galaxy Tab A11 8.7" WiFi', "categoria": "tablets", "marca": "Samsung",
         "precio_proveedor_cop": 455_000, "precio_mercado_cop": 599_900,
         "colores_oficiales": ["Gris", "Plata"], "colores_sugeridos": ["Gris"],
         "descripcion": "La Galaxy Tab A11.\n", "supuestos": [], "revisar": []}
    p.update(cambios)
    return p


DOS = {"productos": [producto("samsung-galaxy-tab-a11-8-7-wifi-8gb-ram-128gb",
                              'Samsung Galaxy Tab A11 8.7" WiFi 8GB RAM 128GB'),
                     producto("samsung-galaxy-tab-a11-8-7-wifi-4gb-ram-64gb",
                              'Samsung Galaxy Tab A11 8.7" WiFi 4GB RAM 64GB', precio_proveedor_cop=380_000)]}


class NombreDeCarpeta(unittest.TestCase):
    def test_las_pulgadas_van_con_doble_prima(self):
        # Windows no admite «"» en un nombre (decisión del 08/10/2026).
        self.assertEqual('Samsung Galaxy Tab A11 8.7″ WiFi', fichas.nombre_de_carpeta('Samsung Galaxy Tab A11 8.7" WiFi'))

    def test_los_demas_caracteres_prohibidos_se_van(self):
        self.assertEqual("JBL Cinema SB180 2.1", fichas.nombre_de_carpeta("JBL Cinema SB180: 2.1?"))


class Escribir(unittest.TestCase):
    def test_una_carpeta_por_modelo_con_su_ficha_y_la_de_fotos_originales(self):
        with tempfile.TemporaryDirectory() as tmp:
            [carpeta] = fichas.escribir(DOS, Path(tmp))
            self.assertEqual('Samsung Galaxy Tab A11 8.7″ WiFi', carpeta.name)
            self.assertTrue((carpeta / "samsung-galaxy-tab-a11-8-7-wifi-ficha.txt").is_file())
            self.assertTrue((carpeta / "Fotos originales").is_dir())
            self.assertFalse((carpeta / "Fuente de la marca").exists())

    def test_la_ficha_lista_las_dos_configuraciones(self):
        with tempfile.TemporaryDirectory() as tmp:
            [carpeta] = fichas.escribir(DOS, Path(tmp))
            texto = (carpeta / "samsung-galaxy-tab-a11-8-7-wifi-ficha.txt").read_text(encoding="utf-8")
            self.assertIn('MODELO: Samsung Galaxy Tab A11 8.7" WiFi', texto)
            self.assertIn("8GB RAM 128GB", texto)
            self.assertIn("4GB RAM 64GB", texto)
            self.assertIn("costo 380.000", texto)
            self.assertIn("PALETA OFICIAL: Gris, Plata", texto)

    def test_no_borra_las_fotos_que_dejo_la_persona(self):
        with tempfile.TemporaryDirectory() as tmp:
            [carpeta] = fichas.escribir(DOS, Path(tmp))
            foto = carpeta / "Fotos originales" / "samsung-galaxy-tab-a11-8-7-wifi_1.jpg"
            foto.write_bytes(b"foto")
            fichas.escribir(DOS, Path(tmp))
            self.assertEqual(b"foto", foto.read_bytes())

    def test_la_carpeta_se_reconoce_por_su_ficha_aunque_cambie_el_titulo(self):
        with tempfile.TemporaryDirectory() as tmp:
            [antes] = fichas.escribir(DOS, Path(tmp))
            renombrado = {"productos": [dict(p, titulo_modelo="Samsung Galaxy Tab A11 8,7 pulgadas WiFi")
                                        for p in DOS["productos"]]}
            [despues] = fichas.escribir(renombrado, Path(tmp))
            self.assertEqual(antes, despues)
            self.assertEqual("samsung-galaxy-tab-a11-8-7-wifi", fichas.id_de_carpeta(despues))

    def test_en_una_marca_que_bloquea_la_lectura_pide_guardar_la_pagina(self):
        xiaomi = {"productos": [producto("xiaomi-redmi-15-4g-8gb-ram-256gb", "Xiaomi Redmi 15 4G 8GB RAM 256GB",
                                         id_modelo="xiaomi-redmi-15-4g", titulo_modelo="Xiaomi Redmi 15 4G",
                                         marca="Xiaomi")]}
        with tempfile.TemporaryDirectory() as tmp:
            [carpeta] = fichas.escribir(xiaomi, Path(tmp))
            self.assertTrue((carpeta / "Fuente de la marca").is_dir())
            self.assertIn("Fuente de la marca", (carpeta / "xiaomi-redmi-15-4g-ficha.txt").read_text(encoding="utf-8"))


class SoloLoQueSeOfrece(unittest.TestCase):
    """Regla 18, 15/10/2026: no se le crea carpeta —donde se buscan fotos— a lo que no va a entrar."""

    GRIP = {"productos": [producto("jbl-grip", "JBL Grip", id_modelo="jbl-grip", titulo_modelo="JBL Grip",
                                   marca="Samsung", precio_proveedor_cop=290_000, precio_mercado_cop=280_900)]}

    def test_un_modelo_sin_ninguna_configuracion_con_margen_no_recibe_carpeta(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.assertEqual([], fichas.escribir(self.GRIP, Path(tmp)))
            self.assertEqual([], list(Path(tmp).iterdir()))

    def test_la_configuracion_sin_margen_va_aparte_en_la_ficha_del_modelo(self):
        datos = {"productos": [DOS["productos"][0],
                               {**DOS["productos"][1], "precio_proveedor_cop": 590_000}]}
        with tempfile.TemporaryDirectory() as tmp:
            [carpeta] = fichas.escribir(datos, Path(tmp))
            texto = (carpeta / "samsung-galaxy-tab-a11-8-7-wifi-ficha.txt").read_text(encoding="utf-8")
        ofrecidas, retiradas = texto.split("NO SE OFRECEN")
        self.assertIn("8GB RAM 128GB (SKU", ofrecidas)
        self.assertNotIn("4GB RAM 64GB", ofrecidas)
        self.assertIn("4GB RAM 64GB: costo 590.000 · mercado 599.900 · margen 1.7 %", retiradas)

    def test_sin_precio_de_mercado_si_tiene_carpeta(self):
        datos = {"productos": [producto("tcl-pad-lugh", "TCL Pad Lugh", id_modelo="tcl-pad-lugh",
                                        titulo_modelo="TCL Pad Lugh", precio_mercado_cop=None)]}
        with tempfile.TemporaryDirectory() as tmp:
            self.assertEqual(1, len(fichas.escribir(datos, Path(tmp))))

    def test_la_carpeta_de_una_lista_anterior_no_se_borra_y_se_informa(self):
        con_margen = {"productos": [{**self.GRIP["productos"][0], "precio_mercado_cop": 349_900}]}
        with tempfile.TemporaryDirectory() as tmp:
            [carpeta] = fichas.escribir(con_margen, Path(tmp))
            (carpeta / "Fotos originales" / "jbl-grip_1.jpg").write_bytes(b"foto")
            self.assertEqual([], fichas.escribir(self.GRIP, Path(tmp)))
            self.assertTrue((carpeta / "Fotos originales" / "jbl-grip_1.jpg").is_file())
            self.assertEqual([("JBL Grip", carpeta)], fichas.retirados_con_carpeta(self.GRIP, Path(tmp)))


if __name__ == "__main__":
    unittest.main()
