"""Un solo proceso a la vez escribe en una carpeta de salida (22/10/2026).

Dos `procesar.py` sobre la misma `_estudio/salida` se pisaron el reporte: la parte 12 de la tanda
corrió mientras la 11 seguía, cada uno guardó su copia, y las 18 fotos de la parte 11 desaparecieron
del reporte aunque sus archivos seguían en disco. Solo biblioteca estándar: corre sin OpenCV."""

import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "scripts"))

import candado  # noqa: E402


def muerto() -> int:
    p = subprocess.Popen([sys.executable, "-c", "pass"])
    p.wait()
    return p.pid


class Candado(unittest.TestCase):
    def test_toma_la_carpeta_y_la_suelta_al_terminar(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            with candado.Candado(raiz, "procesar.py"):
                datos = json.loads(candado.ruta(raiz).read_text(encoding="utf-8"))
                self.assertEqual(os.getpid(), datos["pid"])
                self.assertEqual("procesar.py", datos["quien"])
            self.assertFalse(candado.ruta(raiz).exists())

    def test_la_suelta_aunque_haya_un_error(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            with self.assertRaises(ZeroDivisionError):
                with candado.Candado(raiz, "procesar.py"):
                    1 / 0
            self.assertFalse(candado.ruta(raiz).exists())

    def test_otro_proceso_vivo_la_niega_y_dice_quien(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            candado.ruta(raiz).parent.mkdir(parents=True, exist_ok=True)
            # el proceso padre de las pruebas está vivo y no somos nosotros
            candado.ruta(raiz).write_text(json.dumps({"pid": os.getppid(), "quien": "procesar.py",
                                                      "desde": "2026-10-22T17:00:00"}), encoding="utf-8")
            with self.assertRaises(candado.CarpetaOcupada) as e:
                with candado.Candado(raiz, "marcar.py"):
                    pass
            self.assertIn(str(os.getppid()), str(e.exception))
            self.assertIn("procesar.py", str(e.exception))
            self.assertTrue(candado.ruta(raiz).exists(), "no se le quita el candado a un proceso vivo")

    def test_el_candado_de_un_proceso_muerto_no_bloquea(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            candado.ruta(raiz).parent.mkdir(parents=True, exist_ok=True)
            candado.ruta(raiz).write_text(json.dumps({"pid": muerto(), "quien": "procesar.py"}), encoding="utf-8")
            with candado.Candado(raiz, "procesar.py"):
                datos = json.loads(candado.ruta(raiz).read_text(encoding="utf-8"))
                self.assertEqual(os.getpid(), datos["pid"])

    def test_un_candado_ilegible_no_bloquea_para_siempre(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            candado.ruta(raiz).parent.mkdir(parents=True, exist_ok=True)
            candado.ruta(raiz).write_text("{roto", encoding="utf-8")
            with candado.Candado(raiz, "procesar.py"):
                pass

    def test_no_crea_una_carpeta_que_no_existe(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp) / "no-existe"
            with candado.Candado(raiz, "marcar.py"):
                pass
            self.assertFalse(raiz.exists())

    def test_ocupante_dice_si_hay_alguien(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            self.assertIsNone(candado.ocupante(raiz))
            with candado.Candado(raiz, "procesar.py"):
                self.assertIsNone(candado.ocupante(raiz), "el propio proceso no se cuenta como otro")


if __name__ == "__main__":
    unittest.main()
