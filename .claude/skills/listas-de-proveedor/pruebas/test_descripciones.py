"""Pruebas de lo que una descripción ya escrita tiene que cumplir antes de volver a publicarse."""

import sys
import unittest
from pathlib import Path

SKILL = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(SKILL / "scripts"))

import descripciones  # noqa: E402

APERTURA = "El iPhone 16 lleva el chip A18.\n\n"
VINETAS = "## Características principales\n\n- Pantalla de 6,1 pulgadas.\n\n"
CAJA = "## Contenido de la caja\n\n- iPhone 16\n- Cable USB-C\n\n"
GARANTIA = "## Garantía y notas\n\n- Producto nuevo, sellado y sin activar.\n"
FICHA_VACIA = "## Ficha técnica\n\n| Atributo | Detalle |\n|---|---|\n\n"
FICHA = ("## Ficha técnica\n\n| Atributo | Detalle |\n|---|---|\n| Pantalla | OLED 6,1\" |\n"
         "| Dimensiones y peso | 147,6 x 71,6 x 7,8 mm · 170 g |\n\n")
PIE_ICECAT = "\n---\n\nFicha técnica: Database Right data-sheet 2026 Icecat. All rights reserved.\n"


class Sanear(unittest.TestCase):
    def test_la_ficha_tecnica_sin_filas_se_quita(self):
        # El caso del 21/10/2026: 12 modelos de la lista del 15/10 llevaban así la tabla.
        texto = APERTURA + VINETAS + FICHA_VACIA + CAJA + GARANTIA
        self.assertEqual(APERTURA + VINETAS + CAJA + GARANTIA, descripciones.sanear(texto))

    def test_una_descripcion_completa_no_cambia_ni_un_caracter(self):
        texto = APERTURA + VINETAS + FICHA + CAJA + GARANTIA + PIE_ICECAT
        self.assertEqual(texto, descripciones.sanear(texto))

    def test_sanear_dos_veces_es_sanear_una(self):
        una = descripciones.sanear(APERTURA + FICHA_VACIA + CAJA + GARANTIA)
        self.assertEqual(una, descripciones.sanear(una))

    def test_sin_la_ficha_se_va_tambien_su_fuente(self):
        # Atribuir a Icecat una tabla que no se publica es decir algo falso.
        saneada = descripciones.sanear(APERTURA + FICHA_VACIA + GARANTIA + PIE_ICECAT)
        self.assertNotIn("Icecat", saneada)
        self.assertNotIn("---", saneada)
        self.assertTrue(saneada.endswith("sin activar.\n"))

    def test_una_lista_sin_vinetas_tambien_se_quita(self):
        texto = APERTURA + "## Contenido de la caja\n\n" + GARANTIA
        self.assertEqual(APERTURA + GARANTIA, descripciones.sanear(texto))

    def test_sin_texto_no_hay_nada_que_sanear(self):
        self.assertIsNone(descripciones.sanear(None))
        self.assertEqual("", descripciones.sanear(""))


class Defectos(unittest.TestCase):
    def test_una_descripcion_completa_no_tiene_defectos(self):
        self.assertEqual([], descripciones.defectos(APERTURA + VINETAS + FICHA + CAJA + GARANTIA))

    def test_dice_lo_que_falta_sin_inventarlo(self):
        hallados = descripciones.defectos(APERTURA + VINETAS + GARANTIA)
        self.assertEqual([descripciones.SIN_FICHA, descripciones.SIN_CAJA], hallados)

    def test_una_ficha_sin_peso_ni_medidas_se_dice(self):
        ficha = "## Ficha técnica\n\n| Atributo | Detalle |\n|---|---|\n| Pantalla | OLED |\n\n"
        self.assertIn(descripciones.SIN_PESO, descripciones.defectos(APERTURA + ficha + CAJA + GARANTIA))

    def test_sin_garantia_y_notas_se_dice(self):
        self.assertIn(descripciones.SIN_GARANTIA, descripciones.defectos(APERTURA + FICHA + CAJA))

    def test_solo_el_marcador_bloquea(self):
        con_marcador = APERTURA + FICHA + CAJA + "## Garantía y notas\n\n- Plazo de [[PLAZO]] días.\n"
        self.assertEqual([descripciones.MARCADOR], descripciones.bloqueantes(con_marcador))
        self.assertEqual([], descripciones.bloqueantes(APERTURA + GARANTIA))


class NombraUnaConfiguracion(unittest.TestCase):
    """22/10/2026: el Redmi 17 decía «6GB de RAM física» también en su versión de 4GB, y la alerta
    solo saltaba cuando llegaba una memoria nueva. Ahora es un defecto, y se dice siempre."""

    def test_esta_version_trae_se_dice(self):
        texto = ("El Redmi 17 es un celular 4G. Esta versión trae 6GB de RAM física y 256GB de "
                 "almacenamiento, ampliable con microSD.\n\n" + FICHA + CAJA + GARANTIA)
        [d] = descripciones.defectos(texto)
        self.assertTrue(d.startswith(descripciones.NOMBRA_CONFIGURACION), d)
        self.assertIn("Esta versión trae 6GB de RAM física", d)

    def test_el_almacenamiento_en_la_prosa_se_dice(self):
        texto = "El Galaxy A57 de 256 GB es un celular 5G.\n\n" + FICHA + CAJA + GARANTIA
        self.assertEqual("de 256 GB", descripciones.frase_de_configuracion(texto))

    def test_la_ram_sola_no_es_una_configuracion(self):
        # El Moto G15 trae 4 GB de RAM física en las tres memorias: es del modelo.
        texto = "El Moto G15 tiene 4 GB de RAM física.\n\n" + FICHA + CAJA + GARANTIA
        self.assertIsNone(descripciones.frase_de_configuracion(texto))

    def test_la_tabla_puede_nombrar_capacidades(self):
        ficha = ("## Ficha técnica\n\n| Atributo | Detalle |\n|---|---|\n| Expansión | microSD de 1 TB |\n"
                 "| Dimensiones y peso | 170 g |\n\n")
        self.assertEqual([], descripciones.defectos(APERTURA + ficha + CAJA + GARANTIA))


class Completar(unittest.TestCase):
    """Lo que le falta a una descripción heredada se inserta en su sitio, sin tocar la prosa."""

    def test_faltantes_son_solo_la_ficha_y_la_caja(self):
        self.assertEqual([descripciones.FICHA, descripciones.CAJA],
                         descripciones.faltantes(APERTURA + VINETAS + "## Garantía y notas\n\n- Nuevo.\n"))
        self.assertEqual([], descripciones.faltantes(APERTURA + VINETAS + FICHA + CAJA + GARANTIA))
        self.assertEqual([descripciones.FICHA], descripciones.faltantes(APERTURA + FICHA_VACIA + CAJA + GARANTIA))

    def test_la_ficha_entra_despues_de_las_vinetas_y_antes_de_la_caja(self):
        texto = descripciones.insertar(APERTURA + VINETAS + CAJA + GARANTIA, descripciones.FICHA,
                                       "| Atributo | Detalle |\n|---|---|\n| Peso | 170 g |",
                                       "Ficha técnica: apple.com/co, consultado el 2026-10-22.")
        orden = [texto.index(s) for s in ("El iPhone 16", "## Características", "## Ficha técnica",
                                          "## Contenido de la caja", "## Garantía y notas", "\n---\n")]
        self.assertEqual(sorted(orden), orden)
        self.assertTrue(texto.endswith("Ficha técnica: apple.com/co, consultado el 2026-10-22.\n"))
        self.assertTrue(texto.startswith(APERTURA + VINETAS.rstrip("\n")))

    def test_la_caja_va_antes_de_la_garantia_y_conserva_el_pie(self):
        texto = descripciones.insertar(APERTURA + FICHA + GARANTIA + PIE_ICECAT, descripciones.CAJA,
                                       "- iPhone 16\n- Cable USB-C")
        self.assertLess(texto.index("## Contenido de la caja"), texto.index("## Garantía y notas"))
        self.assertIn("Database Right data-sheet 2026 Icecat", texto.split("\n---\n")[1])
        self.assertEqual([], descripciones.defectos(texto))


class NotasPendientes(unittest.TestCase):
    """Las notas honestas de un dato pendiente del proveedor: se publican, pero quien aprueba el
    borrador tiene que verlas, y `npm run marcadores` no las ve porque no son `[[ ]]`."""

    def test_encuentra_las_frases_de_dato_pendiente(self):
        texto = (APERTURA + "## Garantía y notas\n\n"
                 "- La ficha técnica completa se le pidió al proveedor.\n"
                 "- El contenido del paquete está por confirmar con el proveedor, y no se afirman aquí.\n"
                 "- Producto nuevo, sellado y sin activar.\n")
        notas = descripciones.notas_pendientes(texto)
        self.assertEqual(2, len(notas))
        self.assertEqual("La ficha técnica completa se le pidió al proveedor.", notas[0])

    def test_un_pendiente_que_no_es_nota_no_marca(self):
        # «posicionamiento GNSS independiente» contiene «pendiente» y no avisa nada.
        texto = APERTURA + "- Marco de aluminio y posicionamiento GNSS independiente.\n" + GARANTIA
        self.assertEqual([], descripciones.notas_pendientes(texto))

    def test_sin_texto_no_hay_notas(self):
        self.assertEqual([], descripciones.notas_pendientes(None))
        self.assertEqual([], descripciones.notas_pendientes(""))


if __name__ == "__main__":
    unittest.main()
