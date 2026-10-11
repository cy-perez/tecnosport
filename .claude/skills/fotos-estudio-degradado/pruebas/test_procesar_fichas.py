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


def salida_de_procesar(raiz: Path, fotos, grupo=MODELO, lienzo=2000, revisadas=()):
    """Lo que procesar.py deja en _estudio/salida para cada (nombre, estado)."""
    salida = raiz / pf.TRABAJO / "salida"
    registro = []
    for nombre, estado in fotos:
        f = {"nombre": nombre, "grupo": grupo, "estado": estado, "salidas": {}, "lienzo": [lienzo, lienzo],
             "revisada": {"nota": "a ojo"} if nombre in revisadas else None,
             "motivos": [] if estado == "LISTA" and nombre not in revisadas
             else [{"estado": "REVISAR", "codigo": "ampliacion", "texto": "El producto se amplió 1.80×"}]}
        if estado != "REPETIR":
            maestra = Path(grupo) / "maestra" / f"{nombre}.jpg"
            web = Path(grupo) / "1200" / f"{nombre}.avif"
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


# --------------------------------------------------------------------------
# La tanda del bloque 2 (ADR-0079): cuatro LISTA por modelo, o espera
# --------------------------------------------------------------------------

CFG = {"lienzo_minimo": 1200, "ocupacion": 0.85, "ampliacion_revisar": 1.5}
CUATRO = tuple(f"{MODELO}_{i}.jpg" for i in range(1, 5))
NOMBRES = [f"{MODELO}-{i}" for i in range(1, 5)]
GRANDE = lambda ruta: (2400, 2400)  # noqa: E731


def tanda(raiz, ids=(MODELO,), procesar=None, medidor=GRANDE, solo_devolver=False):
    llamadas = []

    def procesar_fn():
        llamadas.append(True)
        if procesar:
            procesar()
        return True

    estados = pf.correr_tanda(raiz, list(ids), CFG, procesar_fn, solo_devolver, medidor)
    return {e["modelo"]: e for e in estados}, len(llamadas)


class Tanda(unittest.TestCase):
    def test_con_cuatro_lista_el_modelo_queda_apto_y_lo_escribe(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            carpeta = armar(raiz, CUATRO)
            estados, procesos = tanda(raiz, procesar=lambda: salida_de_procesar(raiz, [(n, "LISTA") for n in NOMBRES]))
            self.assertEqual(1, procesos)
            self.assertEqual(pf.APTO, estados[MODELO]["estado"])
            self.assertEqual(4, len(estados[MODELO]["listas"]))
            guardado = json.loads((carpeta / pf.ESTADO).read_text(encoding="utf-8"))
            self.assertEqual(pf.APTO, guardado["estado"])
            self.assertIn("TANDA COMPLETA", pf.informe(list(estados.values())))

    def test_con_menos_de_cuatro_originales_espera_sin_procesar(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            armar(raiz, CUATRO[:3])
            estados, procesos = tanda(raiz)
            self.assertEqual(0, procesos, "no se gasta un retoque que no puede completar el modelo")
            self.assertEqual(pf.EN_ESPERA, estados[MODELO]["estado"])
            self.assertEqual(1, estados[MODELO]["faltan"])
            self.assertIn("LA TANDA ESPERA FOTOS", pf.informe(list(estados.values())))

    def test_una_foto_demasiado_pequena_no_cuenta_antes_de_procesar(self):
        # 1200 × 0,85 / 1,5 = 680 px: una foto de 600 nunca da un producto LISTA en 1200.
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            armar(raiz, CUATRO)
            pequena = lambda ruta: (600, 450) if ruta.name.endswith("_4.jpg") else (2400, 2400)  # noqa: E731
            estados, procesos = tanda(raiz, medidor=pequena)
            self.assertEqual(0, procesos)
            fallida = estados[MODELO]["fallidas"][0]
            self.assertEqual("PEQUEÑA", fallida["estado"])
            self.assertIn("680 px", fallida["motivo"])

    def test_una_foto_que_no_se_puede_medir_la_decide_el_retoque(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            armar(raiz, CUATRO)
            _, procesos = tanda(raiz, medidor=lambda ruta: None,
                                procesar=lambda: salida_de_procesar(raiz, [(n, "LISTA") for n in NOMBRES]))
            self.assertEqual(1, procesos)

    def test_una_revisar_aprobada_a_ojo_no_cuenta(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            armar(raiz, CUATRO)
            estados, _ = tanda(raiz, procesar=lambda: salida_de_procesar(
                raiz, [(n, "LISTA") for n in NOMBRES], revisadas=(NOMBRES[3],)))
            self.assertEqual(pf.EN_ESPERA, estados[MODELO]["estado"])
            self.assertEqual(3, len(estados[MODELO]["listas"]))
            self.assertEqual("REVISAR", estados[MODELO]["fallidas"][0]["estado"])

    def test_una_revisar_dice_por_que(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            armar(raiz, CUATRO)
            estados, _ = tanda(raiz, procesar=lambda: salida_de_procesar(
                raiz, [(n, "LISTA") for n in NOMBRES[:3]] + [(NOMBRES[3], "REVISAR")]))
            self.assertIn("1.80×", estados[MODELO]["fallidas"][0]["motivo"])

    def test_una_lista_en_un_lienzo_menor_al_del_sitio_no_cuenta(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            armar(raiz, CUATRO)
            estados, _ = tanda(raiz, procesar=lambda: salida_de_procesar(
                raiz, [(n, "LISTA") for n in NOMBRES], lienzo=800))
            self.assertEqual(pf.EN_ESPERA, estados[MODELO]["estado"])
            self.assertEqual({"LIENZO"}, {f["estado"] for f in estados[MODELO]["fallidas"]})

    def test_sin_fotos_nuevas_no_se_reprocesa_y_se_dice(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            armar(raiz, CUATRO)
            tanda(raiz, procesar=lambda: salida_de_procesar(
                raiz, [(n, "LISTA") for n in NOMBRES[:3]] + [(NOMBRES[3], "REPETIR")]))
            estados, procesos = tanda(raiz)
            self.assertEqual(0, procesos, "la persona dijo que dejó fotos, pero la carpeta es la misma")
            self.assertTrue(estados[MODELO]["sin_cambios"])
            self.assertIn("sin fotos nuevas", pf.informe(list(estados.values())))

    def test_una_foto_nueva_vuelve_a_intentar_el_modelo(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            carpeta = armar(raiz, CUATRO)
            tanda(raiz, procesar=lambda: salida_de_procesar(
                raiz, [(n, "LISTA") for n in NOMBRES[:3]] + [(NOMBRES[3], "REPETIR")]))
            (carpeta / pf.ORIGINALES / f"{MODELO}_5.jpg").write_bytes(b"otra")
            nombres = NOMBRES[:3] + [f"{MODELO}-5"]
            estados, procesos = tanda(raiz, procesar=lambda: salida_de_procesar(
                raiz, [(n, "LISTA") for n in nombres] + [(NOMBRES[3], "REPETIR")]))
            self.assertEqual(1, procesos)
            self.assertEqual(pf.APTO, estados[MODELO]["estado"])

    def test_un_modelo_apto_no_se_vuelve_a_procesar(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            armar(raiz, CUATRO)
            tanda(raiz, procesar=lambda: salida_de_procesar(raiz, [(n, "LISTA") for n in NOMBRES]))
            estados, procesos = tanda(raiz)
            self.assertEqual(0, procesos)
            self.assertEqual(pf.APTO, estados[MODELO]["estado"])

    def test_sacarlo_es_de_la_persona_y_vuelve_con_fotos_nuevas(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            carpeta = armar(raiz, CUATRO[:2])
            with self.assertRaises(ValueError):
                pf.sacar(carpeta, MODELO, " ")
            pf.sacar(carpeta, MODELO, "sin material oficial")
            estados, _ = tanda(raiz)
            self.assertEqual(pf.FUERA, estados[MODELO]["estado"])
            self.assertIn("TANDA COMPLETA", pf.informe(list(estados.values())))
            (carpeta / pf.ORIGINALES / f"{MODELO}_3.jpg").write_bytes(b"otra")
            estados, _ = tanda(raiz)
            self.assertEqual(pf.EN_ESPERA, estados[MODELO]["estado"])

    def test_un_modelo_sin_carpeta_espera(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            armar(raiz, CUATRO)
            estados, _ = tanda(raiz, ids=("xiaomi-poco-f9-ultra-5g",))
            self.assertEqual(pf.EN_ESPERA, estados["xiaomi-poco-f9-ultra-5g"]["estado"])
            self.assertIn("paso 5", estados["xiaomi-poco-f9-ultra-5g"]["motivo"])

    def test_solo_procesa_los_modelos_de_la_tanda(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            armar(raiz, CUATRO)
            otro = raiz / "JBL Flip 7"
            (otro / pf.ORIGINALES).mkdir(parents=True)
            (otro / "jbl-flip-7-ficha.txt").write_text("MODELO", encoding="utf-8")
            (otro / pf.ORIGINALES / "jbl-flip-7_1.jpg").write_bytes(b"x")
            tanda(raiz, procesar=lambda: salida_de_procesar(raiz, [(n, "LISTA") for n in NOMBRES]))
            self.assertFalse((raiz / pf.TRABAJO / "crudas" / "jbl-flip-7").exists())
            self.assertFalse((otro / pf.ESTADO).exists())

    def test_la_tanda_se_lee_de_por_proponer(self):
        with tempfile.TemporaryDirectory() as tmp:
            ruta = Path(tmp) / "por-proponer.json"
            ruta.write_text(json.dumps({"fechaLista": "2026-10-10", "modelos": ["a", "b", "a"],
                                        "configuraciones": ["c"]}), encoding="utf-8")
            self.assertEqual(["a", "b"], pf.leer_tanda(ruta))


if __name__ == "__main__":
    unittest.main()
