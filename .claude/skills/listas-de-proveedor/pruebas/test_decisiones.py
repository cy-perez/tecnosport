"""Pruebas de las decisiones de la persona sobre una lista (`catalogo/1-lista/decisiones.json`).

22/10/2026: el iPhone 18 Pro 256GB y el 17 Pro Max 256GB vinieron sin precio, la persona decidió
incluirlos con el costo de la lista anterior y la versión eSIM, y eso vivió en un script suelto que
había que correr a mano entre el parser y la comparación.
"""

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SKILL = Path(__file__).resolve().parent.parent
SCRIPTS = SKILL / "scripts"
sys.path.insert(0, str(SCRIPTS))

import decisiones  # noqa: E402

IPHONE = {"id": "apple-iphone-18-pro-256gb", "id_lista": "apple-iphone-18-pro-256gb",
          "titulo": "Apple iPhone 18 Pro 256GB", "titulo_modelo": "Apple iPhone 18 Pro",
          "id_modelo": "apple-iphone-18-pro", "categoria": "celulares", "marca": "Apple",
          "atributos": [], "precio_proveedor_cop": None, "condicion": "nuevo", "revisar": [], "supuestos": [],
          "motivo": "sin precio de proveedor", "bloques": ["GAMA ALTA"]}
DECISION = {"id_lista": "apple-iphone-18-pro-256gb", "costo_cop": 4_300_000,
            "titulo": "Apple iPhone 18 Pro 256GB eSIM", "atributos": ["eSIM"],
            "motivo": "la lista no trae precio; se incluye con el costo de la lista del 15/10/2026",
            "fecha": "2026-10-22"}


def lista():
    return {"fecha_lista": "2026-10-22", "bloques": ["GAMA ALTA"], "productos": [], "descartados": [dict(IPHONE)]}


class Aplicar(unittest.TestCase):
    def test_un_descartado_sin_precio_vuelve_con_el_costo_el_titulo_y_la_sim(self):
        datos = lista()
        self.assertEqual([], decisiones.aplicar(datos, [DECISION]))
        [p] = datos["productos"]
        self.assertEqual([], datos["descartados"])
        self.assertEqual(4_300_000, p["precio_proveedor_cop"])
        self.assertEqual(("apple-iphone-18-pro-256gb-esim", "Apple iPhone 18 Pro 256GB eSIM"), (p["id"], p["titulo"]))
        self.assertEqual(("apple-iphone-18-pro", "Apple iPhone 18 Pro"), (p["id_modelo"], p["titulo_modelo"]))
        self.assertEqual(["eSIM"], p["atributos"])
        self.assertNotIn("motivo", p)
        self.assertIn("decisión de la persona (22/10/2026): la lista no trae precio; se incluye con el costo de la "
                      "lista del 15/10/2026", p["supuestos"])

    def test_una_decision_sin_producto_se_avisa(self):
        [aviso] = decisiones.aplicar(lista(), [dict(DECISION, id_lista="apple-iphone-99-256gb")])
        self.assertIn("no está en la lista de hoy", aviso)

    def test_sin_motivo_o_sin_fecha_no_se_carga(self):
        with tempfile.TemporaryDirectory() as tmp:
            ruta = Path(tmp) / "decisiones.json"
            ruta.write_text(json.dumps({"decisiones": [{"id_lista": "x", "costo_cop": 1}]}), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "faltan motivo, fecha"):
                decisiones.cargar(ruta)

    def test_sin_archivo_no_hay_decisiones(self):
        self.assertEqual([], decisiones.cargar(Path("no-existe.json")))


class ComparacionRepetible(unittest.TestCase):
    """Correr la comparación dos veces da lo mismo, y la decisión se aplica en las dos."""

    def correr(self, tmp):
        subprocess.run([sys.executable, "-B", str(SCRIPTS / "comparar_lista.py"), str(tmp / "productos.json"),
                        "--hoy", "2026-10-22", "--cambios", str(tmp / "cambios.md")],
                       check=True, capture_output=True)
        return (tmp / "productos.json").read_text(encoding="utf-8"), (tmp / "cambios.md").read_text(encoding="utf-8")

    def test_dos_corridas_dan_lo_mismo_y_parten_de_la_copia(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            (tmp / "productos.json").write_text(json.dumps(lista()), encoding="utf-8")
            (tmp / "1-lista").mkdir()
            (tmp / "1-lista" / "decisiones.json").write_text(json.dumps({"decisiones": [DECISION]}), encoding="utf-8")
            una = self.correr(tmp)
            self.assertTrue((tmp / "1-lista" / "productos-del-parser.json").is_file())
            dos = self.correr(tmp)
            self.assertEqual(una, dos)
            [p] = json.loads(una[0])["productos"]
            self.assertEqual("apple-iphone-18-pro-256gb-esim", p["id"])

    def test_sin_copia_y_ya_comparado_pide_volver_a_parsear(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            (tmp / "productos.json").write_text(json.dumps({**lista(), "comparacion": {}}), encoding="utf-8")
            r = subprocess.run([sys.executable, "-B", str(SCRIPTS / "comparar_lista.py"), str(tmp / "productos.json")],
                               capture_output=True, text=True, encoding="utf-8")
            self.assertNotEqual(0, r.returncode)
            self.assertIn("vuelve a correr el parser", r.stderr)


if __name__ == "__main__":
    unittest.main()
