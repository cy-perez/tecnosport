"""La forma de catalogo/ por subproceso: cada script deja lo suyo donde `rutas.py` dice.

Se corren los comandos tal como los escribe SKILL.md, desde una raíz temporal: si alguno
volviera a escribir en la raíz de catalogo/, el paso siguiente no encontraría su archivo.
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

import rutas  # noqa: E402

LISTA = "*15/OCTUBRE/2026*\n*LISTADO DE ANDROID*\n *SAMSUNG*\n🎃A17 4G (8+256)$650\n"


def correr(raiz, script, *argumentos):
    subprocess.run(env=ENTORNO_UTF8, args=[sys.executable, "-B", str(SCRIPTS / script), *argumentos],
                   cwd=raiz, check=True, capture_output=True, timeout=60)


class FormaDelCatalogo(unittest.TestCase):
    def test_cada_paso_deja_lo_suyo_en_su_subcarpeta(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            (raiz / rutas.LISTA).mkdir(parents=True)
            (raiz / rutas.LISTA / "lista.txt").write_text(LISTA, encoding="utf-8")

            correr(raiz, "parsear_lista.py", "catalogo/1-lista/lista.txt")
            correr(raiz, "comparar_lista.py", "catalogo/productos.json")
            correr(raiz, "exportar_lista.py", "catalogo/productos.json")

            for archivo in ("catalogo/productos.json", "catalogo/1-lista/revision.md",
                            "catalogo/1-lista/cambios.md", "catalogo/5-api/lista-api.json"):
                self.assertTrue((raiz / archivo).is_file(), archivo)
            self.assertEqual({"1-lista", "5-api", "productos.json"},
                             {p.name for p in (raiz / "catalogo").iterdir()})
            self.assertEqual("2026-10-15", json.loads(
                (raiz / "catalogo/5-api/lista-api.json").read_text(encoding="utf-8"))["fechaLista"])

    def test_las_rutas_van_por_subproceso(self):
        self.assertEqual(["1-lista", "2-precios", "3-redaccion", "4-entregables", "5-api"],
                         [r.name for r in (rutas.LISTA, rutas.PRECIOS, rutas.REDACCION,
                                           rutas.ENTREGABLES, rutas.API)])
        self.assertEqual(Path("catalogo/productos.json"), rutas.PRODUCTOS)


if __name__ == "__main__":
    unittest.main()
