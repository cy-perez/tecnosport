"""Pruebas de la fuente de la marca: qué página pedir y si lo guardado es la de especificaciones.

El 22/10/2026 llegaron primero las páginas «General» de dos modelos de mi.com, sin peso, caja ni
configuraciones. Las páginas de prueba son mínimas y sintéticas: solo lo que se clasifica.
"""

import sys
import tempfile
import unittest
from pathlib import Path

SKILL = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(SKILL / "scripts"))

import fichas  # noqa: E402
import fuente_de_marca  # noqa: E402

GENERAL = ('<html><head><title>POCO F9 Ultra | Xiaomi Colombia | Todas las especificaciones y características'
           '</title><link rel="canonical" href="https://www.mi.com/co/product/poco-f9-ultra/"></head></html>')
SPECS = ('<html><head><title>POCO F9 Ultra Especificaciones, Características | Xiaomi Colombia</title>'
         '<link rel="canonical" href="https://www.mi.com/co/product/poco-f9-ultra/specs/"></head></html>')
GUARDADA_SIN_CANONICA = ('<!-- saved from url=(0063)https://www.mi.com/co/product/redmi-note-17-pro-max-5g/specs/ -->'
                         '<html><head><title>REDMI Note 17 Pro Max 5G</title></head></html>')
MOTOROLA = '<html><head><title>Especificaciones - moto g15 | Motorola Support LATAM</title></head></html>'


class Slugs(unittest.TestCase):
    def test_los_slugs_conocidos_de_mi_com(self):
        # Los de fichas-tecnicas.md y los dos que se guardaron el 22/10/2026.
        self.assertEqual("redmi-note-15-pro", fichas.slugs_de_mi("Xiaomi Redmi Note 15 Pro 5G")[0])
        self.assertEqual("poco-x8-pro-max", fichas.slugs_de_mi("Xiaomi POCO X8 Pro Max 5G")[0])
        self.assertEqual(["redmi-pad-2-9-7-inch"], fichas.slugs_de_mi('Xiaomi Redmi Pad 2 9.7" WiFi'))
        self.assertEqual(["xiaomi-smart-projector-l1"], fichas.slugs_de_mi("Xiaomi Smart Projector L1"))
        self.assertEqual("poco-f9-ultra", fichas.slugs_de_mi("Xiaomi POCO F9 Ultra 5G")[0])

    def test_con_la_red_va_como_alternativa(self):
        self.assertEqual(["redmi-note-17-pro-max", "redmi-note-17-pro-max-5g"],
                         fichas.slugs_de_mi("Xiaomi Redmi Note 17 Pro Max 5G"))

    def test_la_ficha_de_un_xiaomi_dice_la_url_y_la_pestana(self):
        texto = fichas.texto_de_ficha([{"id": "x", "titulo": "Xiaomi POCO F9 Ultra 5G 12GB RAM 256GB",
                                        "titulo_modelo": "Xiaomi POCO F9 Ultra 5G", "marca": "Xiaomi"}])
        self.assertIn("https://www.mi.com/co/product/poco-f9-ultra/specs/", texto)
        self.assertIn("prueba https://www.mi.com/co/product/poco-f9-ultra-5g/specs/", texto)
        self.assertIn("no «General»", texto)

    def test_la_de_jbl_pide_el_specsheet(self):
        texto = fichas.texto_de_ficha([{"id": "jbl-go-5", "titulo": "JBL Go 5", "marca": "JBL"}])
        self.assertIn("«JBL Go 5 Specsheet» (PDF)", texto)

    def test_motorola_lleva_la_carpeta_por_si_la_ficha_de_producto_no_basta(self):
        datos = {"productos": [{"id": "motorola-moto-g15-4g-4gb-ram-512gb", "id_modelo": "motorola-moto-g15-4g",
                                "titulo": "Motorola Moto G15 4G 4GB RAM 512GB", "titulo_modelo": "Motorola Moto G15 4G",
                                "marca": "Motorola", "precio_proveedor_cop": 530_000, "precio_mercado_cop": 593_357}]}
        with tempfile.TemporaryDirectory() as tmp:
            [carpeta] = fichas.escribir(datos, Path(tmp))
            self.assertTrue((carpeta / "Fuente de la marca").is_dir())
            self.assertIn("soporte LATAM", (carpeta / "motorola-moto-g15-4g-ficha.txt").read_text(encoding="utf-8"))


class Validar(unittest.TestCase):
    def carpeta(self, tmp, nombre, marca, archivos):
        c = Path(tmp) / nombre
        (c / "Fuente de la marca").mkdir(parents=True)
        (c / f"{nombre.lower().replace(' ', '-')}-ficha.txt").write_text(f"MODELO: {nombre}\nMARCA: {marca}\n",
                                                                       encoding="utf-8")
        for archivo, texto in archivos.items():
            (c / "Fuente de la marca" / archivo).write_text(texto, encoding="utf-8")
        return c

    def test_la_pestana_general_de_mi_com_se_rechaza_aunque_el_titulo_diga_especificaciones(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.carpeta(tmp, "Xiaomi POCO F9 Ultra 5G", "Xiaomi", {"POCO F9 Ultra.html": GENERAL})
            [(_, clase, detalle)] = fuente_de_marca.validar(Path(tmp))
        self.assertEqual(fuente_de_marca.GENERAL, clase)
        self.assertIn("https://www.mi.com/co/product/poco-f9-ultra/specs/", detalle)

    def test_la_de_especificaciones_pasa_por_la_canonica_o_por_la_marca_de_guardado(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.carpeta(tmp, "Xiaomi POCO F9 Ultra 5G", "Xiaomi", {"a.html": SPECS})
            self.carpeta(tmp, "Xiaomi Redmi Note 17 Pro Max 5G", "Xiaomi", {"b.html": GUARDADA_SIN_CANONICA})
            clases = [c for _, c, _ in fuente_de_marca.validar(Path(tmp))]
        self.assertEqual([fuente_de_marca.ESPECIFICACIONES] * 2, clases)

    def test_lo_guardado_manda_sobre_la_canonica(self):
        # Revisión del PR #268: si la /specs/ apunta su canónica a la página de producto, una página
        # buena salía GENERAL. Lo que Chrome anota que guardó es lo que se guardó.
        pagina = ('<!-- saved from url=(0046)https://www.mi.com/co/product/poco-f9-ultra/specs/ -->'
                  '<html><head><title>POCO F9 Ultra Especificaciones</title>'
                  '<link rel="canonical" href="https://www.mi.com/co/product/poco-f9-ultra/"></head></html>')
        with tempfile.TemporaryDirectory() as tmp:
            self.carpeta(tmp, "Xiaomi POCO F9 Ultra 5G", "Xiaomi", {"a.html": pagina})
            [(_, clase, _)] = fuente_de_marca.validar(Path(tmp))
        self.assertEqual(fuente_de_marca.ESPECIFICACIONES, clase)

    def test_un_mhtml_se_lee_por_su_ubicacion_guardada(self):
        # «Página web, un solo archivo» no deja el comentario «saved from url».
        mhtml = ("From: <Saved by Blink>\r\nSnapshot-Content-Location: https://www.mi.com/co/product/poco-f9-ultra/specs/"
                 "\r\nSubject: POCO F9 Ultra Especificaciones\r\n")
        with tempfile.TemporaryDirectory() as tmp:
            self.carpeta(tmp, "Xiaomi POCO F9 Ultra 5G", "Xiaomi", {"a.mhtml": mhtml})
            [(_, clase, _)] = fuente_de_marca.validar(Path(tmp))
        self.assertEqual(fuente_de_marca.ESPECIFICACIONES, clase)

    def test_el_specsheet_de_jbl_y_la_de_motorola(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.carpeta(tmp, "JBL Go 5", "JBL", {"JBL_GO_5_Specsheet_EN.pdf": "%PDF-1.7"})
            self.carpeta(tmp, "Motorola Moto G15 4G", "Motorola", {"g15.html": MOTOROLA})
            clases = sorted(c for _, c, _ in fuente_de_marca.validar(Path(tmp)))
        self.assertEqual([fuente_de_marca.ESPECIFICACIONES, fuente_de_marca.SPECSHEET], clases)

    def test_una_carpeta_vacia_de_xiaomi_falta_y_una_de_motorola_no(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.carpeta(tmp, "Xiaomi Smart Band 11", "Xiaomi", {})
            self.carpeta(tmp, "Motorola Moto G15 4G", "Motorola", {})
            resultado = fuente_de_marca.validar(Path(tmp))
        self.assertEqual([("Xiaomi Smart Band 11", fuente_de_marca.FALTA, "la carpeta está vacía")], resultado)


if __name__ == "__main__":
    unittest.main()
