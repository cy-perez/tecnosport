"""Pruebas del modo de carpetas de modelo: preparar el árbol de trabajo y devolver
cada resultado a su carpeta con el nombre de su original. No corren procesar.py
(pide OpenCV): el reporte y las salidas se fabrican aquí."""

import json
import sys
import tempfile
import unittest
from pathlib import Path

SKILL = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(SKILL / "scripts"))

import procesar_fichas as pf  # noqa: E402

MODELO = "samsung-galaxy-s25-ultra"


def armar(raiz: Path, nombres=("samsung-galaxy-s25-ultra_1.jpg", "samsung-galaxy-s25-ultra_2.png")):
    carpeta = raiz / "Samsung Galaxy S25 Ultra"
    (carpeta / pf.ORIGINALES).mkdir(parents=True)
    (carpeta / f"{MODELO}-ficha.txt").write_text("MODELO", encoding="utf-8")
    for n in nombres:
        (carpeta / pf.ORIGINALES / n).write_bytes(b"original " + n.encode())
    return carpeta


def salida_de_procesar(raiz: Path, fotos):
    """Lo que procesar.py deja en _estudio/salida para cada (nombre, estado)."""
    salida = raiz / pf.TRABAJO / "salida"
    registro = []
    for nombre, estado in fotos:
        f = {"nombre": nombre, "grupo": MODELO, "estado": estado, "salidas": {}}
        if estado != "REPETIR":
            maestra = Path(MODELO) / "maestra" / f"{nombre}.jpg"
            web = Path(MODELO) / "1200" / f"{nombre}.avif"
            for r, contenido in ((maestra, b"maestra"), (web, b"avif")):
                (salida / r).parent.mkdir(parents=True, exist_ok=True)
                (salida / r).write_bytes(contenido + nombre.encode())
            f["salidas"] = {"maestra": {"ruta": maestra.as_posix()}, "web": [{"ruta": web.as_posix()}]}
        registro.append(f)
    (salida / "reporte.json").write_text(json.dumps({"fotos": registro}), encoding="utf-8")


class Preparar(unittest.TestCase):
    def test_copia_las_originales_al_arbol_de_trabajo_por_id_de_modelo(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            armar(raiz)
            copiadas, errores = pf.preparar(raiz)
            self.assertEqual((2, []), (copiadas, errores))
            self.assertTrue((raiz / pf.TRABAJO / "crudas" / MODELO / "samsung-galaxy-s25-ultra_2.png").is_file())

    def test_la_segunda_vez_no_vuelve_a_copiar(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            armar(raiz)
            pf.preparar(raiz)
            self.assertEqual((0, []), pf.preparar(raiz))

    def test_dos_nombres_que_darian_la_misma_salida_se_informan(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            armar(raiz, ("x_1.jpg", "x-1.png"))
            copiadas, errores = pf.preparar(raiz)
            self.assertEqual(1, copiadas)
            self.assertEqual(1, len(errores))


class Devolver(unittest.TestCase):
    def test_cada_procesada_queda_con_el_nombre_de_su_original(self):
        # Decisión del 08/10/2026: mismo nombre, la extensión puede cambiar.
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            carpeta = armar(raiz)
            salida_de_procesar(raiz, [("samsung-galaxy-s25-ultra-1", "LISTA"), ("samsung-galaxy-s25-ultra-2", "LISTA")])
            r = pf.devolver(raiz)
            procesadas = carpeta / pf.PROCESADAS
            self.assertEqual(2, r["devueltas"])
            self.assertEqual(b"maestrasamsung-galaxy-s25-ultra-1",
                             (procesadas / "samsung-galaxy-s25-ultra_1.jpg").read_bytes())
            self.assertTrue((procesadas / "samsung-galaxy-s25-ultra_2.jpg").is_file())
            self.assertTrue((procesadas / "web" / "1200" / "samsung-galaxy-s25-ultra_2.avif").is_file())

    def test_una_repetir_no_se_devuelve_y_se_informa(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            carpeta = armar(raiz)
            salida_de_procesar(raiz, [("samsung-galaxy-s25-ultra-1", "REPETIR"), ("samsung-galaxy-s25-ultra-2", "REVISAR")])
            r = pf.devolver(raiz)
            self.assertFalse((carpeta / pf.PROCESADAS / "samsung-galaxy-s25-ultra_1.jpg").exists())
            self.assertEqual(["Samsung Galaxy S25 Ultra/samsung-galaxy-s25-ultra_1.jpg"], r["repetir"])
            self.assertEqual(["Samsung Galaxy S25 Ultra/samsung-galaxy-s25-ultra_2.png"], r["revisar"])

    def test_no_toca_las_originales(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            carpeta = armar(raiz)
            salida_de_procesar(raiz, [("samsung-galaxy-s25-ultra-1", "LISTA")])
            pf.devolver(raiz)
            self.assertEqual(b"original samsung-galaxy-s25-ultra_1.jpg",
                             (carpeta / pf.ORIGINALES / "samsung-galaxy-s25-ultra_1.jpg").read_bytes())

    def test_el_arbol_de_trabajo_no_cuenta_como_modelo(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            armar(raiz)
            pf.preparar(raiz)
            self.assertEqual([MODELO], list(pf.modelos(raiz)))


if __name__ == "__main__":
    unittest.main()
