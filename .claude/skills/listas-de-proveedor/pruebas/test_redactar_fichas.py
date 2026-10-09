"""Pruebas del redactor de fichas: una descripción por modelo, con la ficha de la
marca primero y la de Icecat como segunda opción, siempre con su aviso."""

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SKILL = Path(__file__).resolve().parent.parent
SCRIPTS = SKILL / "scripts"
sys.path.insert(0, str(SCRIPTS))

import redactar_fichas  # noqa: E402

PROSA = {"samsung-galaxy-a57-5g": {"apertura": "El Galaxy A57 es un celular 5G.", "caja": ["Cable USB-C"]}}
MARCA = {"samsung-galaxy-a57-5g": {
    "fuente": "samsung.com/co", "url": "https://www.samsung.com/co/", "fecha": "2026-10-08",
    "f": {"Pantalla": "6,7\" Super AMOLED Plus", "Memoria": "8GB / 256GB", "Batería": "5000 mAh"}}}
ICECAT = {"especificaciones": [
    {"atributo": "Diagonal de la pantalla", "valor": "17 cm (6.7\")"},
    {"atributo": "Capacidad de RAM", "valor": "8 GB"},
    {"atributo": "Capacidad de almacenamiento interno", "valor": "256 GB"},
    {"atributo": "Capacidad de batería", "valor": "5000 mAh"}]}


def producto(id_, titulo, **cambios):
    p = {"id": id_, "titulo": titulo, "id_modelo": "samsung-galaxy-a57-5g", "titulo_modelo": "Samsung Galaxy A57 5G",
         "categoria": "celulares", "marca": "Samsung", "ram": "8GB", "ram_virtual": "8GB",
         "almacenamiento": "256GB", "atributos": ["eSIM"], "colores_oficiales": ["Azul", "Gris"],
         "revisar": [], "pendiente": ["precio", "ficha", "descripcion"]}
    p.update(cambios)
    return p


def redactar(productos, marca=None, icecat=None, prosa=PROSA):
    with tempfile.TemporaryDirectory() as tmp:
        tmp = Path(tmp)
        (tmp / "p.json").write_text(json.dumps({"productos": productos}), encoding="utf-8")
        (tmp / "prosa.json").write_text(json.dumps(prosa), encoding="utf-8")
        args = [sys.executable, "-B", str(SCRIPTS / "redactar_fichas.py"), str(tmp / "p.json"),
                "--prosa", str(tmp / "prosa.json"), "--fecha", "2026-10-09"]
        if marca is not None:
            (tmp / "marca.json").write_text(json.dumps(marca), encoding="utf-8")
            args += ["--marca", str(tmp / "marca.json")]
        if icecat is not None:
            (tmp / "icecat").mkdir()
            (tmp / "icecat" / "samsung-galaxy-a57-5g.json").write_text(json.dumps(icecat), encoding="utf-8")
            args += ["--icecat", str(tmp / "icecat")]
        subprocess.run(args, check=True, capture_output=True)
        return json.loads((tmp / "p.json").read_text(encoding="utf-8"))["productos"]


DOS = [producto("samsung-galaxy-a57-5g-8gb-ram-256gb", "Samsung Galaxy A57 5G 8GB RAM 256GB"),
       producto("samsung-galaxy-a57-5g-12gb-ram-512gb", "Samsung Galaxy A57 5G 12GB RAM 512GB",
                ram="12GB", almacenamiento="512GB")]


class UnaPorModelo(unittest.TestCase):
    def test_las_dos_memorias_comparten_la_misma_descripcion(self):
        a, b = redactar(DOS, marca=MARCA)
        self.assertEqual(a["descripcion"], b["descripcion"])
        self.assertEqual("Samsung Galaxy A57 5G", a["meta_titulo"])

    def test_no_lleva_memoria_ni_colores_ni_notas_de_configuracion(self):
        [a, _] = redactar(DOS, marca=MARCA)
        d = a["descripcion"]
        for prohibido in ("| Memoria |", "8GB / 256GB", "## Colores", "RAM física", "eSIM"):
            self.assertNotIn(prohibido, d)
        self.assertIn("| Batería | 5000 mAh |", d)


class OrdenDeFuentes(unittest.TestCase):
    def test_la_marca_va_primero(self):
        [a, _] = redactar(DOS, marca=MARCA, icecat=ICECAT)
        self.assertIn("| Pantalla | 6,7\" Super AMOLED Plus |", a["descripcion"])
        self.assertIn("Ficha técnica: samsung.com/co, consultado el 2026-10-08.", a["descripcion"])
        self.assertNotIn("Icecat", a["descripcion"])
        self.assertEqual([{"fuente": "samsung.com/co", "url": "https://www.samsung.com/co/", "fecha": "2026-10-08"}],
                         a["fuentes_ficha"])

    def test_icecat_entra_si_no_hay_ficha_de_marca_y_sin_la_memoria(self):
        [a, _] = redactar(DOS, icecat=ICECAT)
        self.assertIn("| Batería | 5000 mAh |", a["descripcion"])
        self.assertNotIn("256 GB", a["descripcion"])
        self.assertEqual("Open Icecat", a["fuentes_ficha"][0]["fuente"])


class AvisoDeIcecat(unittest.TestCase):
    """Open Content License v1.4 (11/02/2026), cláusulas 1 y 2. Auditoría del 08/10/2026:
    el aviso anterior («Specs Icecat…») no era el que pide la licencia."""

    def test_lleva_el_aviso_literal_la_licencia_y_la_nota_de_modificacion(self):
        [a, _] = redactar(DOS, icecat=ICECAT)
        d = a["descripcion"]
        self.assertIn("Database Right data-sheet 2026 Icecat. All rights reserved.", d)
        self.assertIn("https://iceclog.com/open-content-license-opl/", d)
        self.assertIn("sin garantía de ningún tipo", d)
        self.assertIn("Modificado por TecnoSport el 2026-10-09", d)


class SinProsa(unittest.TestCase):
    def test_sin_prosa_no_se_inventa_y_se_marca(self):
        a, b = redactar(DOS, marca=MARCA, prosa={})
        self.assertNotIn("descripcion", {k for k, v in a.items() if v})
        self.assertTrue(any(r.startswith("sin descripcion") for r in a["revisar"]))
        self.assertTrue(any(r.startswith("sin descripcion") for r in b["revisar"]))


class Filas(unittest.TestCase):
    def test_la_ficha_de_marca_suelta_las_filas_de_memoria(self):
        filas = redactar_fichas.tabla_mi({"f": {"Memoria": "8GB", "RAM": "8GB", "Almacenamiento": "256GB",
                                                "Pantalla": "6,7\""}})
        self.assertEqual([("Pantalla", "6,7\"")], filas)


if __name__ == "__main__":
    unittest.main()
