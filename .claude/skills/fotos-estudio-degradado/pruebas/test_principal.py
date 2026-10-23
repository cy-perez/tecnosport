"""La principal de varios modelos en una sola llamada (22/10/2026).

`marcar.py --principal` aceptaba una foto por llamada, y cada llamada regenera las ~45 hojas de
revisión (unos 4 minutos): elegir las 56 portadas de una tanda eran casi cuatro horas. Ahora acepta
varias, una por modelo. Solo biblioteca estándar: `reporte.py` no pide OpenCV ni Pillow."""

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "scripts"))

import reporte  # noqa: E402


def foto(nombre, grupo, estado="LISTA", principal=False):
    auto = [{"estado": "REPETIR", "codigo": "x", "texto": "rota"}] if estado == "REPETIR" else []
    return {"nombre": nombre, "grupo": grupo, "motivos_auto": auto, "marcas": [],
            "principal": {"fecha": "2026-10-21"} if principal else None}


class Principales(unittest.TestCase):
    def test_varias_fotos_de_modelos_distintos_se_aceptan(self):
        fotos = [foto("a-1", "a"), foto("a-2", "a"), foto("b-1", "b")]
        self.assertEqual([], reporte.problemas_de_principales(fotos, ["a-2", "b-1"]))

    def test_dos_fotos_del_mismo_modelo_no(self):
        fotos = [foto("a-1", "a"), foto("a-2", "a")]
        problemas = reporte.problemas_de_principales(fotos, ["a-1", "a-2"])
        self.assertEqual(1, len(problemas))
        self.assertIn("a-1", problemas[0])
        self.assertIn("a-2", problemas[0])

    def test_una_repetir_no_puede_ser_principal(self):
        fotos = [foto("a-1", "a", "REPETIR")]
        self.assertIn("REPETIR", reporte.problemas_de_principales(fotos, ["a-1"])[0])

    def test_poner_la_principal_le_quita_la_marca_a_la_anterior_del_mismo_modelo(self):
        fotos = [foto("a-1", "a", principal=True), foto("a-2", "a"), foto("b-1", "b", principal=True)]
        reporte.poner_principal(fotos, fotos[1])
        self.assertIsNone(fotos[0]["principal"])
        self.assertTrue(fotos[1]["principal"])
        self.assertTrue(fotos[2]["principal"], "la de otro modelo no se toca")


if __name__ == "__main__":
    unittest.main()
