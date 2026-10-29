"""Pruebas de las decisiones de la persona sobre una lista (`catalogo/1-lista/decisiones.json`).

22/10/2026: el iPhone 18 Pro 256GB y el 17 Pro Max 256GB vinieron sin precio, la persona decidió
incluirlos con el costo de la lista anterior y la versión eSIM, y eso vivió en un script suelto que
había que correr a mano entre el parser y la comparación.
"""

import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SKILL = Path(__file__).resolve().parent.parent
SCRIPTS = SKILL / "scripts"
sys.path.insert(0, str(SCRIPTS))

# Los scripts escriben acentos y flechas: con la consola de Windows en cp1252 el hijo fallaba al
# imprimir y la prueba dependía de la codificación de quien la corre (revisión del PR #268).
ENTORNO_UTF8 = {**os.environ, "PYTHONIOENCODING": "utf-8"}

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

    def test_falta_informacion_saca_el_producto_a_descartados(self):
        datos = lista()
        datos["productos"] = [dict(IPHONE, precio_proveedor_cop=4_300_000, motivo=None)]
        datos["descartados"] = []
        decision = {"id_lista": IPHONE["id"], "falta_informacion": "la memoria: el aviso y la lista no coinciden",
                    "motivo": "la persona se queda con la de la lista", "fecha": "2026-10-28"}
        self.assertEqual([], decisiones.aplicar(datos, [decision]))
        self.assertEqual([], datos["productos"])
        [x] = datos["descartados"]
        self.assertEqual("falta información del proveedor: la memoria: el aviso y la lista no coinciden", x["motivo"])

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


class DescartadoDelParser(unittest.TestCase):
    """Revisión del PR #268: un descartado «sin precio» sale del parser sin `id_modelo` ni título
    definitivo —el filtro de precio corre antes que las equivalencias—, y devolverlo tal cual
    tumbaba la comparación con `KeyError: 'id_modelo'`."""

    def parsear(self, texto, equivalencias=None):
        import parsear_lista
        return parsear_lista.parsear(texto, equivalencias or {})

    def test_sin_titulo_queda_como_un_producto_parseado_y_la_comparacion_no_falla(self):
        import comparar_lista
        from datetime import date
        datos = self.parsear("*SAMSUNG*\n🚀S25 ULTRA 12/256🖤\n")
        [d] = datos["descartados"]
        self.assertEqual("sin precio de proveedor", d["motivo"])
        self.assertEqual([], decisiones.aplicar(datos, [{"id_lista": d["id_lista"], "costo_cop": 3_000_000,
                                                          "motivo": "precio dado por el proveedor por chat",
                                                          "fecha": "2026-10-22"}], equivalencias={}))
        [p] = datos["productos"]
        self.assertEqual(("Samsung Galaxy S25 Ultra", "samsung-galaxy-s25-ultra"), (p["titulo_modelo"], p["id_modelo"]))
        comparar_lista.comparar(datos, {"modelos": {}, "configuraciones": {}}, date(2026, 10, 22))
        self.assertEqual("nuevo", p["estado_lista"])

    def test_un_descartado_con_equivalencia_toma_el_titulo_definitivo(self):
        eq = {"samsung-galaxy-s25-ultra-12gb-ram-256gb": {
            "id": "samsung-galaxy-s25-ultra-5g-12gb-ram-256gb", "titulo": "Samsung Galaxy S25 Ultra 5G 12GB RAM 256GB",
            "fecha": "2026-10-02", "motivo": "prueba"}}
        datos = self.parsear("*SAMSUNG*\n🚀S25 ULTRA 12/256🖤\n", eq)
        [d] = datos["descartados"]
        decisiones.aplicar(datos, [{"id_lista": d["id_lista"], "costo_cop": 3_000_000, "motivo": "m",
                                    "fecha": "2026-10-22"}], equivalencias=eq)
        [p] = datos["productos"]
        self.assertEqual(("samsung-galaxy-s25-ultra-5g-12gb-ram-256gb", "Samsung Galaxy S25 Ultra 5G 12GB RAM 256GB"),
                         (p["id"], p["titulo"]))
        self.assertEqual("samsung-galaxy-s25-ultra-5g", p["id_modelo"])

    def test_los_dos_iphone_de_la_corrida_con_su_titulo_esim(self):
        datos = self.parsear("     *IPH NUEVOS* \n✔️18 PRO 256 🤍\n✔️17 PRO MAX 256 🧡\n")
        ids = {d["id_lista"] for d in datos["descartados"]}
        self.assertEqual({"apple-iphone-18-pro-256gb", "apple-iphone-17-pro-max-256gb"}, ids)
        decisiones.aplicar(datos, [
            dict(DECISION),
            {"id_lista": "apple-iphone-17-pro-max-256gb", "costo_cop": 4_080_000,
             "titulo": "Apple iPhone 17 Pro Max 256GB eSIM", "atributos": ["eSIM"], "motivo": "m", "fecha": "2026-10-22"},
        ], equivalencias={})
        self.assertEqual({("apple-iphone-18-pro-256gb-esim", "apple-iphone-18-pro"),
                          ("apple-iphone-17-pro-max-256gb-esim", "apple-iphone-17-pro-max")},
                         {(p["id"], p["id_modelo"]) for p in datos["productos"]})

    def test_si_la_lista_no_permite_identificarlo_vuelve_a_los_descartados(self):
        # El Watch SE sin generación (regla 10): darle costo no da el dato que falta.
        datos = self.parsear("*APPLE WATCH* 💥\n⌚WATCH  SE 44MM 🖤\n")
        [d] = datos["descartados"]
        decisiones.aplicar(datos, [{"id_lista": d["id_lista"], "costo_cop": 1_000_000, "motivo": "m",
                                    "fecha": "2026-10-22"}], equivalencias={})
        self.assertEqual([], datos["productos"])
        [d] = datos["descartados"]
        self.assertIn("falta información del proveedor", d["motivo"])


class ComparacionRepetible(unittest.TestCase):
    """Correr la comparación dos veces da lo mismo, y la decisión se aplica en las dos."""

    def correr(self, tmp):
        subprocess.run(env=ENTORNO_UTF8, args=[sys.executable, "-B", str(SCRIPTS / "comparar_lista.py"), str(tmp / "productos.json"),
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
            r = subprocess.run(env=ENTORNO_UTF8, args=[sys.executable, "-B", str(SCRIPTS / "comparar_lista.py"), str(tmp / "productos.json")],
                               capture_output=True, text=True, encoding="utf-8")
            self.assertNotEqual(0, r.returncode)
            self.assertIn("vuelve a correr el parser", r.stderr)


if __name__ == "__main__":
    unittest.main()
