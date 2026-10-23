"""Pruebas del modo de carpetas de modelo: preparar el árbol de trabajo, devolver
cada resultado a su carpeta con el nombre de su original, y la tanda del bloque 2.
No corren procesar.py (pide OpenCV): el reporte y las salidas se fabrican aquí,
**con la forma que de verdad escribe procesar.py** —motivos finales como texto,
`archivo` y `huella_origen` en cada registro—. Una prueba que fabricaba los motivos
como diccionarios pasaba mientras el código se caía con el primer reporte real."""

import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SKILL = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(SKILL / "scripts"))

import procesar_fichas as pf  # noqa: E402

MODELO = "samsung-galaxy-s25-ultra"
MOTIVO_AMPLIACION = "El producto se amplió 1.80× (mide 900 px; lo ideal es ≥ 1620 px): revisa la nitidez"


def armar(raiz: Path, nombres=("samsung-galaxy-s25-ultra_1.jpg", "samsung-galaxy-s25-ultra_2.png"),
          titulo="Samsung Galaxy S25 Ultra", mid=MODELO):
    carpeta = raiz / titulo
    (carpeta / pf.ORIGINALES).mkdir(parents=True)
    (carpeta / f"{mid}-ficha.txt").write_text("MODELO", encoding="utf-8")
    for n in nombres:
        (carpeta / pf.ORIGINALES / n).write_bytes(b"original " + n.encode())
    return carpeta


def salida_de_procesar(raiz: Path, fotos, grupo=MODELO, lienzo=2000, revisadas=(), sumar=False):
    """Lo que procesar.py deja en _estudio/salida para cada (nombre, estado).

    Cada registro lleva el `archivo` y la `huella_origen` de la original con ese nombre, como
    los escribe procesar.py, y los motivos como los deja reporte.estado_final."""
    salida = raiz / pf.TRABAJO / "salida"
    salida.mkdir(parents=True, exist_ok=True)
    carpeta = pf.modelos(raiz).get(grupo)
    por_slug = {pf.slug(f.stem): f for f in pf.originales(carpeta)} if carpeta else {}
    previos = []
    if sumar and (salida / "reporte.json").is_file():
        previos = json.loads((salida / "reporte.json").read_text(encoding="utf-8"))["fotos"]
    registro = []
    for nombre, estado in fotos:
        original = por_slug.get(nombre)
        revisada = nombre in revisadas
        auto = [{"estado": "REVISAR", "codigo": "ampliacion", "texto": MOTIVO_AMPLIACION}] \
            if estado == "REVISAR" or revisada else []
        if estado == "REPETIR":
            auto = [{"estado": "REPETIR", "codigo": "ampliacion", "texto": "El producto se amplió 3.40×"}]
        f = {"nombre": nombre, "grupo": grupo, "estado": estado, "salidas": {}, "lienzo": [lienzo, lienzo],
             "archivo": original.name if original else f"{nombre}.jpg",
             "huella_origen": pf.huella_archivo(original) if original else None,
             "revisada": {"nota": "a ojo"} if revisada else None,
             "motivos_auto": auto,
             "motivos": [] if estado == "LISTA" else [m["texto"] for m in auto]}
        if estado != "REPETIR":
            maestra = Path(grupo) / "maestra" / f"{nombre}.jpg"
            web = Path(grupo) / "1200" / f"{nombre}.avif"
            for r, contenido in ((maestra, b"maestra"), (web, b"avif")):
                (salida / r).parent.mkdir(parents=True, exist_ok=True)
                (salida / r).write_bytes(contenido + nombre.encode())
            f["salidas"] = {"maestra": {"ruta": maestra.as_posix()}, "web": [{"ruta": web.as_posix()}]}
        registro.append(f)
    nuevos = {(r["grupo"], r["nombre"]) for r in registro}
    todos = [p for p in previos if (p["grupo"], p["nombre"]) not in nuevos] + registro
    (salida / "reporte.json").write_text(json.dumps({"fotos": todos}), encoding="utf-8")


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

    def test_avif_y_jfif_son_originales_y_otro_formato_no(self):
        # Los sitios de marca entregan AVIF y JFIF; antes se ignoraban sin aviso.
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            carpeta = armar(raiz, ("x_1.avif", "x_2.jfif", "x_3.JPG", "x_4.gif"))
            self.assertEqual(["x_1.avif", "x_2.jfif", "x_3.JPG"],
                             [f.name for f in pf.originales(carpeta)])
            self.assertEqual((3, []), pf.preparar(raiz))

    def test_una_original_que_ya_no_esta_sale_del_arbol_de_trabajo(self):
        # Si no, procesar.py la volvería a procesar y la nueva saldría como «x-1-2».
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            carpeta = armar(raiz, ("x_1.jpg",))
            pf.preparar(raiz)
            (carpeta / pf.ORIGINALES / "x_1.jpg").unlink()
            (carpeta / pf.ORIGINALES / "x_1.png").write_bytes(b"otra")
            pf.preparar(raiz)
            crudas = raiz / pf.TRABAJO / "crudas" / MODELO
            self.assertEqual(["x_1.png"], sorted(f.name for f in crudas.iterdir()))


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

    def test_el_registro_de_una_foto_reemplazada_no_se_devuelve(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            carpeta = armar(raiz)
            salida_de_procesar(raiz, [("samsung-galaxy-s25-ultra-1", "LISTA")])
            (carpeta / pf.ORIGINALES / "samsung-galaxy-s25-ultra_1.jpg").write_bytes(b"otra foto")
            self.assertEqual(0, pf.devolver(raiz)["devueltas"])

    def test_los_anchos_de_la_version_anterior_se_van(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            carpeta = armar(raiz)
            vieja = carpeta / pf.PROCESADAS / pf.WEB / "600" / "samsung-galaxy-s25-ultra_1.avif"
            vieja.parent.mkdir(parents=True)
            vieja.write_bytes(b"de un lienzo de 600")
            salida_de_procesar(raiz, [("samsung-galaxy-s25-ultra-1", "LISTA")])
            pf.devolver(raiz)
            self.assertFalse(vieja.exists(), "se subiría como un ancho de la foto nueva")
            self.assertTrue((carpeta / pf.PROCESADAS / pf.WEB / "1200" / "samsung-galaxy-s25-ultra_1.avif").exists())


# --------------------------------------------------------------------------
# La tanda del bloque 2 (ADR-0079): cuatro LISTA por modelo, o espera
# --------------------------------------------------------------------------

CFG = {"lienzo_minimo": 1200, "ocupacion": 0.85, "ampliacion_revisar": 1.6}
CUATRO = tuple(f"{MODELO}_{i}.jpg" for i in range(1, 5))
NOMBRES = [f"{MODELO}-{i}" for i in range(1, 5)]
GRANDE = lambda ruta: (2400, 2400)  # noqa: E731


def tanda(raiz, ids=(MODELO,), procesar=None, medidor=GRANDE, solo_devolver=False, fecha=None):
    llamadas = []

    def procesar_fn(nombres):
        llamadas.append(list(nombres))
        if procesar:
            procesar()
        return True

    estados = pf.correr_tanda(raiz, list(ids), CFG, procesar_fn, solo_devolver, medidor, fecha)
    tanda.nombres = llamadas
    return {e["modelo"]: e for e in estados}, len(llamadas)


def listas(raiz, nombres=NOMBRES, **kw):
    return lambda: salida_de_procesar(raiz, [(n, "LISTA") for n in nombres], **kw)


PIEZAS = {"estado": "REVISAR", "codigo": "varias_piezas",
          "texto": "El recorte tiene 3 piezas separadas: confirma que son un solo producto (par o kit)."}


def con_motivos(raiz, motivos, revisadas=(), marcas=None):
    """Cuatro fotos LISTA salvo las de `motivos`, que salen con esos motivos automáticos y el
    estado que les daría reporte.estado_final —y `marcas`, a ojo—."""
    marcas = marcas or {}

    def procesar():
        salida_de_procesar(raiz, [(n, "LISTA") for n in NOMBRES])
        ruta = raiz / pf.TRABAJO / "salida" / "reporte.json"
        datos = json.loads(ruta.read_text(encoding="utf-8"))
        for f in datos["fotos"]:
            if f["nombre"] not in motivos:
                continue
            f["motivos_auto"] = motivos[f["nombre"]]
            f["revisada"] = {"nota": "a ojo"} if f["nombre"] in revisadas else None
            if f["nombre"] in marcas:
                f["marcas"] = [{"estado": "REVISAR", "motivo": marcas[f["nombre"]]}]
            f["estado"] = "LISTA" if f["revisada"] and not f.get("marcas") else "REVISAR"
            f["motivos"] = [] if f["estado"] == "LISTA" else [m["texto"] for m in f["motivos_auto"]]
        ruta.write_text(json.dumps(datos), encoding="utf-8")
    return procesar


class Tanda(unittest.TestCase):
    def test_con_cuatro_lista_el_modelo_queda_apto_y_lo_escribe(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            carpeta = armar(raiz, CUATRO)
            estados, procesos = tanda(raiz, procesar=listas(raiz))
            self.assertEqual(1, procesos)
            self.assertEqual(sorted(NOMBRES), sorted(tanda.nombres[0]), "el modelo se procesa entero")
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
        # 1200 × 0,85 / 1,6 = 638 px: una foto de 600 nunca da un producto LISTA en 1200.
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            armar(raiz, CUATRO)
            pequena = lambda ruta: (600, 450) if ruta.name.endswith("_4.jpg") else (2400, 2400)  # noqa: E731
            estados, procesos = tanda(raiz, medidor=pequena)
            self.assertEqual(0, procesos)
            fallida = estados[MODELO]["fallidas"][0]
            self.assertEqual("PEQUEÑA", fallida["estado"])
            self.assertIn("638 px", fallida["motivo"])

    def test_una_foto_que_no_se_puede_medir_la_decide_el_retoque(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            armar(raiz, CUATRO)
            _, procesos = tanda(raiz, medidor=lambda ruta: None, procesar=listas(raiz))
            self.assertEqual(1, procesos)

    def test_una_revisar_con_el_reporte_real_dice_por_que(self):
        # El reporte guarda los motivos finales como texto: con diccionarios esto se caía.
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            armar(raiz, CUATRO)
            estados, _ = tanda(raiz, procesar=lambda: salida_de_procesar(
                raiz, [(n, "LISTA") for n in NOMBRES[:3]] + [(NOMBRES[3], "REVISAR")]))
            self.assertEqual(pf.EN_ESPERA, estados[MODELO]["estado"])
            self.assertIn("1.80×", estados[MODELO]["fallidas"][0]["motivo"])

    def test_una_repetir_con_el_reporte_real_dice_por_que(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            armar(raiz, CUATRO)
            estados, _ = tanda(raiz, procesar=lambda: salida_de_procesar(
                raiz, [(n, "LISTA") for n in NOMBRES[:3]] + [(NOMBRES[3], "REPETIR")]))
            self.assertEqual("REPETIR", estados[MODELO]["fallidas"][0]["estado"])
            self.assertIn("3.40×", estados[MODELO]["fallidas"][0]["motivo"])

    def test_una_revisar_aprobada_a_ojo_cuenta_y_queda_anotada(self):
        # Decisión del 22/10/2026: hasta entonces no contaba.
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            armar(raiz, CUATRO)
            estados, _ = tanda(raiz, procesar=listas(raiz, revisadas=(NOMBRES[3],)))
            self.assertEqual(pf.APTO, estados[MODELO]["estado"])
            self.assertEqual(4, len(estados[MODELO]["listas"]))
            self.assertEqual([CUATRO[3]], estados[MODELO]["aprobadas_a_ojo"])
            self.assertEqual([], estados[MODELO]["fallidas"])

    def test_una_revisar_sin_aprobar_sigue_sin_contar(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            armar(raiz, CUATRO)
            estados, _ = tanda(raiz, procesar=lambda: salida_de_procesar(
                raiz, [(n, "LISTA") for n in NOMBRES[:3]] + [(NOMBRES[3], "REVISAR")]))
            self.assertEqual(pf.EN_ESPERA, estados[MODELO]["estado"])
            self.assertEqual(3, len(estados[MODELO]["listas"]))
            self.assertEqual([], estados[MODELO]["aprobadas_a_ojo"])

    def test_aprobar_a_ojo_una_lista_que_no_tenia_nada_que_aprobar_no_la_descarta(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            armar(raiz, CUATRO)

            def con_aprobacion_vacia():
                salida_de_procesar(raiz, [(n, "LISTA") for n in NOMBRES])
                ruta = raiz / pf.TRABAJO / "salida" / "reporte.json"
                datos = json.loads(ruta.read_text(encoding="utf-8"))
                datos["fotos"][0]["revisada"] = {"nota": "vista"}
                ruta.write_text(json.dumps(datos), encoding="utf-8")

            estados, _ = tanda(raiz, procesar=con_aprobacion_vacia)
            self.assertEqual(pf.APTO, estados[MODELO]["estado"])

    def test_una_lista_en_un_lienzo_menor_al_del_sitio_no_cuenta(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            armar(raiz, CUATRO)
            estados, _ = tanda(raiz, procesar=listas(raiz, lienzo=800))
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

    def test_una_foto_nueva_reprocesa_el_modelo_entero(self):
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
            self.assertEqual(5, len(tanda.nombres[0]), "el lienzo depende de todas las fotos del modelo")
            self.assertEqual(pf.APTO, estados[MODELO]["estado"])

    def test_un_modelo_apto_no_se_vuelve_a_procesar(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            armar(raiz, CUATRO)
            tanda(raiz, procesar=listas(raiz))
            estados, procesos = tanda(raiz)
            self.assertEqual(0, procesos)
            self.assertEqual(pf.APTO, estados[MODELO]["estado"])

    def test_una_foto_marcada_a_ojo_despues_de_apto_deja_de_contar(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            armar(raiz, CUATRO)
            tanda(raiz, procesar=listas(raiz))
            # marcar.py --repetir "marca de agua" sobre la primera
            salida_de_procesar(raiz, [(NOMBRES[0], "REPETIR")], sumar=True)
            estados, procesos = tanda(raiz)
            self.assertEqual(0, procesos)
            self.assertEqual(pf.EN_ESPERA, estados[MODELO]["estado"])
            self.assertNotIn(f"{MODELO}_1.jpg", estados[MODELO]["listas"])

    def test_sin_procesar_no_es_un_callejon_sin_salida(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            armar(raiz, CUATRO)
            estados, _ = tanda(raiz, procesar=lambda: None)  # el retoque no dejó nada
            self.assertEqual(pf.SIN_PROCESAR, estados[MODELO]["fallidas"][0]["estado"])
            estados, procesos = tanda(raiz, procesar=listas(raiz))
            self.assertEqual(1, procesos, "se vuelve a intentar aunque la carpeta no cambió")
            self.assertEqual(pf.APTO, estados[MODELO]["estado"])

    def test_un_apto_al_que_otra_corrida_le_borro_el_reporte_se_rehace_en_la_misma_corrida(self):
        # 22/10/2026: la parte 12 de la tanda pisó el reporte de la 11. Sus modelos quedaron aptos en
        # estado-fotos.json, sin registros en el reporte, y la tanda los daba por «sin fotos nuevas»
        # con 0 LISTA en vez de rehacerlos.
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            armar(raiz, CUATRO)
            tanda(raiz, procesar=listas(raiz))
            (raiz / pf.TRABAJO / "salida" / "reporte.json").write_text(json.dumps({"fotos": []}), encoding="utf-8")
            estados, procesos = tanda(raiz, procesar=listas(raiz))
            self.assertEqual(1, procesos, "se rehace en esta corrida, no en la siguiente")
            self.assertEqual(pf.APTO, estados[MODELO]["estado"])
            self.assertFalse(estados[MODELO].get("sin_cambios"))

    def test_una_foto_sin_registro_dice_que_falta_en_el_reporte_y_no_habla_de_heic(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            armar(raiz, CUATRO)
            estados, _ = tanda(raiz, procesar=lambda: None)
            motivo = estados[MODELO]["fallidas"][0]["motivo"]
            self.assertIn("no tiene registro en el reporte", motivo)
            self.assertNotIn("pillow-heif", motivo)

    def test_dos_originales_con_el_mismo_nombre_no_cuentan_doble(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            armar(raiz, ("m_1.jpg", "m-1.png", "m_2.jpg", "m-2.png"))
            estados, procesos = tanda(raiz)
            self.assertEqual(0, procesos, "son dos fotos, no cuatro")
            self.assertEqual({"DUPLICADA"}, {f["estado"] for f in estados[MODELO]["fallidas"]})

    def test_las_listas_van_en_orden_natural(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            nombres = [f"{MODELO}_{i}.jpg" for i in (1, 2, 10, 11)]
            armar(raiz, nombres)
            estados, _ = tanda(raiz, procesar=listas(raiz, [f"{MODELO}-{i}" for i in (1, 2, 10, 11)]))
            self.assertEqual([f"{MODELO}_{i}.jpg" for i in (1, 2, 10, 11)], estados[MODELO]["listas"])

    def test_la_foto_marcada_principal_encabeza_las_listas(self):
        # Decisión del 22/10/2026: sin marca, la principal era la _1 que se descargó primero.
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            armar(raiz, CUATRO)

            def con_principal():
                salida_de_procesar(raiz, [(n, "LISTA") for n in NOMBRES])
                ruta = raiz / pf.TRABAJO / "salida" / "reporte.json"
                datos = json.loads(ruta.read_text(encoding="utf-8"))
                for f in datos["fotos"]:
                    if f["nombre"] == NOMBRES[2]:  # marcar.py --principal
                        f["principal"] = {"fecha": "2026-10-22"}
                ruta.write_text(json.dumps(datos), encoding="utf-8")

            estados, _ = tanda(raiz, procesar=con_principal)
            self.assertEqual([CUATRO[2], CUATRO[0], CUATRO[1], CUATRO[3]], estados[MODELO]["listas"])

    def test_una_principal_que_no_salio_lista_no_cambia_el_orden(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            armar(raiz, CUATRO)

            def con_principal_revisar():
                salida_de_procesar(raiz, [(n, "LISTA") for n in NOMBRES[:3]] + [(NOMBRES[3], "REVISAR")])
                ruta = raiz / pf.TRABAJO / "salida" / "reporte.json"
                datos = json.loads(ruta.read_text(encoding="utf-8"))
                for f in datos["fotos"]:
                    if f["nombre"] == NOMBRES[3]:
                        f["principal"] = {"fecha": "2026-10-22"}
                ruta.write_text(json.dumps(datos), encoding="utf-8")

            estados, _ = tanda(raiz, procesar=con_principal_revisar)
            self.assertEqual([CUATRO[0], CUATRO[1], CUATRO[2]], estados[MODELO]["listas"])

    def test_sacarlo_es_de_la_persona_y_vuelve_con_fotos_nuevas_o_con_otra_lista(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            carpeta = armar(raiz, CUATRO[:2])
            with self.assertRaises(ValueError):
                pf.sacar(raiz, carpeta, MODELO, " ", "2026-10-10")
            pf.sacar(raiz, carpeta, MODELO, "sin material oficial", "2026-10-10")
            estados, _ = tanda(raiz, fecha="2026-10-10")
            self.assertEqual(pf.FUERA, estados[MODELO]["estado"])
            self.assertIn("TANDA COMPLETA", pf.informe(list(estados.values())))
            estados, _ = tanda(raiz, fecha="2026-10-15")
            self.assertEqual(pf.EN_ESPERA, estados[MODELO]["estado"], "otra lista: se vuelve a intentar")
            pf.sacar(raiz, carpeta, MODELO, "sin material oficial", "2026-10-15")
            (carpeta / pf.ORIGINALES / f"{MODELO}_3.jpg").write_bytes(b"otra")
            estados, _ = tanda(raiz, fecha="2026-10-15")
            self.assertEqual(pf.EN_ESPERA, estados[MODELO]["estado"], "fotos nuevas: se vuelve a intentar")

    def test_un_modelo_sin_carpeta_espera_y_se_puede_sacar(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            armar(raiz, CUATRO)
            otro = "xiaomi-poco-f9-ultra-5g"
            estados, _ = tanda(raiz, ids=(otro,), fecha="2026-10-10")
            self.assertEqual(pf.EN_ESPERA, estados[otro]["estado"])
            self.assertIn("paso 5", estados[otro]["motivo"])
            pf.sacar(raiz, None, otro, "no lo vamos a vender", "2026-10-10")
            estados, _ = tanda(raiz, ids=(otro,), fecha="2026-10-10")
            self.assertEqual(pf.FUERA, estados[otro]["estado"])

    def test_en_un_kit_las_piezas_sueltas_cuentan_y_en_los_demas_no(self):
        # Decisión del 18/10/2026: la consola con sus Joy-Con sueltos es el producto. En un modelo
        # que no es kit, tres piezas pueden ser dos productos distintos: sigue sin contar.
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            armar(raiz, CUATRO)
            procesar = con_motivos(raiz, {NOMBRES[3]: [PIEZAS]})
            estados, _ = tanda(raiz, procesar=procesar)
            self.assertEqual(pf.EN_ESPERA, estados[MODELO]["estado"])
            with self.assertRaises(ValueError):
                pf.declarar_kit(raiz, MODELO, " ")
            pf.declarar_kit(raiz, MODELO, "consola con los Joy-Con sueltos")
            estados, procesos = tanda(raiz, procesar=procesar)
            self.assertEqual(0, procesos, "declarar el kit no pide otro retoque: recalcula el veredicto")
            self.assertEqual(pf.APTO, estados[MODELO]["estado"])
            pf.declarar_kit(raiz, MODELO, "", quitar=True)
            estados, _ = tanda(raiz, procesar=procesar)
            self.assertEqual(pf.EN_ESPERA, estados[MODELO]["estado"])

    def test_en_un_kit_otro_reparo_o_una_marca_a_ojo_siguen_sin_contar(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            armar(raiz, CUATRO)
            pf.declarar_kit(raiz, MODELO, "tablet con teclado y mouse")
            ampliada = {"estado": "REVISAR", "codigo": "ampliacion", "texto": MOTIVO_AMPLIACION}
            estados, _ = tanda(raiz, procesar=con_motivos(raiz, {NOMBRES[2]: [PIEZAS, ampliada],
                                                                 NOMBRES[3]: [PIEZAS]},
                                                          marcas={NOMBRES[3]: "se ve una mano"}))
            self.assertEqual(2, len(estados[MODELO]["listas"]))

    def test_en_un_kit_aprobar_a_ojo_las_piezas_no_la_descarta(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            armar(raiz, CUATRO)
            pf.declarar_kit(raiz, MODELO, "consola con los Joy-Con sueltos")
            estados, _ = tanda(raiz, procesar=con_motivos(raiz, {NOMBRES[3]: [PIEZAS]},
                                                          revisadas=(NOMBRES[3],)))
            self.assertEqual(pf.APTO, estados[MODELO]["estado"])

    def test_solo_procesa_los_modelos_de_la_tanda(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            armar(raiz, CUATRO)
            otro = armar(raiz, ("jbl-flip-7_1.jpg",), titulo="JBL Flip 7", mid="jbl-flip-7")
            tanda(raiz, procesar=listas(raiz))
            self.assertFalse((raiz / pf.TRABAJO / "crudas" / "jbl-flip-7").exists())
            self.assertFalse((otro / pf.ESTADO).exists())

    def test_la_tanda_se_lee_de_por_proponer(self):
        with tempfile.TemporaryDirectory() as tmp:
            ruta = Path(tmp) / "por-proponer.json"
            ruta.write_text(json.dumps({"fechaLista": "2026-10-10", "modelos": ["a", "b", "a"],
                                        "configuraciones": ["c"]}), encoding="utf-8")
            self.assertEqual(("2026-10-10", ["a", "b"], set()), pf.leer_tanda(ruta))

    def test_la_tanda_lee_los_modelos_sin_ficha(self):
        with tempfile.TemporaryDirectory() as tmp:
            ruta = Path(tmp) / "por-proponer.json"
            ruta.write_text(json.dumps({"fechaLista": "2026-10-15", "modelos": ["a", "b"],
                                        "sinFicha": ["b"], "configuraciones": []}), encoding="utf-8")
            self.assertEqual(("2026-10-15", ["a", "b"], {"b"}), pf.leer_tanda(ruta))


class PorPartes(unittest.TestCase):
    """22/10/2026: 297 fotos en una sola llamada a procesar.py no caben en primer plano, y en segundo
    plano ya murió una por memoria. `--lote N` las corre por partes de modelos enteros."""

    def armar_tres(self, raiz):
        ids = []
        for k in range(1, 4):
            mid = f"modelo-{k}"
            armar(raiz, tuple(f"{mid}_{i}.jpg" for i in range(1, 5)), titulo=f"Modelo {k}", mid=mid)
            ids.append(mid)
        return ids

    def test_las_partes_son_de_modelos_enteros_y_no_pasan_del_lote(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            ids = self.armar_tres(raiz)
            self.assertEqual([["modelo-1", "modelo-2"], ["modelo-3"]], pf.partes(raiz, ids, 8))

    def test_un_modelo_mas_grande_que_el_lote_va_solo_y_entero(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            ids = self.armar_tres(raiz)
            self.assertEqual([["modelo-1"], ["modelo-2"], ["modelo-3"]], pf.partes(raiz, ids, 3))

    def test_sin_lote_es_una_sola_parte(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            ids = self.armar_tres(raiz)
            self.assertEqual([ids], pf.partes(raiz, ids, None))

    def test_la_tanda_por_partes_llama_al_retoque_una_vez_por_parte_y_decide_al_final(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            ids = self.armar_tres(raiz)
            llamadas = []

            def procesar_fn(nombres):
                llamadas.append(list(nombres))
                grupos = sorted({n.rsplit("-", 1)[0] for n in nombres})
                for g in grupos:
                    salida_de_procesar(raiz, [(n, "LISTA") for n in nombres if n.startswith(g + "-")],
                                       grupo=g, sumar=True)
                return True

            estados = pf.correr_tanda(raiz, ids, CFG, procesar_fn, medidor=GRANDE, lote=8)
            self.assertEqual(2, len(llamadas))
            self.assertEqual(8, len(llamadas[0]))
            self.assertEqual({pf.APTO}, {e["estado"] for e in estados})


class ValidarAntesDeProcesar(unittest.TestCase):
    """22/10/2026: antes de gastar horas de retoque se validaron 297 fotos a mano (nombres, formatos,
    duplicados, LISTA estimadas). `--validar` lo hace sin retocar nada."""

    LADOS = {"chica": 500}

    def estimar(self, ruta):
        return 300 if "chica" in ruta.name else 1600

    def test_informa_nombres_formatos_pequenas_y_estimadas_por_modelo(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            carpeta = armar(raiz, (f"{MODELO}_1.jpg", f"{MODELO}_2.jpg", "Samsung Galaxy S25 Ultra_3.webp",
                                   f"{MODELO}_4.jpg", "chica_5.png"))
            (carpeta / pf.ORIGINALES / "nota.bmp").write_bytes(b"bmp")
            v = pf.validar(raiz, [MODELO], pf.cargar_config(), medidor=lambda r: (400, 400) if "chica" in r.name
                           else (2000, 2000), estimar=self.estimar)
            m = v["modelos"][MODELO]
            self.assertEqual({"Samsung Galaxy S25 Ultra_3.webp", "chica_5.png"}, set(m["nombres_fuera"]))
            self.assertEqual(["nota.bmp"], m["no_admitidos"])
            self.assertEqual(["chica_5.png"], m["pequenas"])
            self.assertEqual(4, m["listas_estimadas"])
            self.assertTrue(v["completa"])

    def test_detecta_fotos_identicas_en_dos_modelos(self):
        # La Redmi Pad 2 bundle y la 4G compartían sus seis fotos byte a byte.
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            a = armar(raiz, ("uno_1.jpg",), titulo="Uno", mid="uno")
            b = armar(raiz, ("dos_1.jpg",), titulo="Dos", mid="dos")
            (b / pf.ORIGINALES / "dos_1.jpg").write_bytes((a / pf.ORIGINALES / "uno_1.jpg").read_bytes())
            v = pf.validar(raiz, ["uno", "dos"], pf.cargar_config(), medidor=lambda r: None, estimar=lambda r: None)
            self.assertEqual([["Dos/dos_1.jpg", "Uno/uno_1.jpg"]], v["duplicados"])
            self.assertIn("idénticas", pf.informe_validacion(v))

    def test_un_modelo_que_no_reune_cuatro_estimadas_deja_la_tanda_incompleta(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            armar(raiz, CUATRO)
            v = pf.validar(raiz, [MODELO], pf.cargar_config(), medidor=lambda r: None, estimar=lambda r: 300)
            self.assertEqual(0, v["modelos"][MODELO]["listas_estimadas"])
            self.assertFalse(v["completa"])

    def test_una_foto_sobre_una_mesa_se_estima_como_en_la_tanda(self):
        # Revisión del PR #268: validar usaba el estimador «fiable», que no da número sin un fondo de
        # estudio, y contaba 0 LISTA donde la tanda —con el estimador del producto— sí procesa.
        try:
            import numpy as np
            from PIL import Image
        except ImportError:
            self.skipTest("sin NumPy o Pillow")
        with tempfile.TemporaryDirectory() as tmp:
            ruta = Path(tmp) / "mesa.png"
            ruido = np.random.default_rng(7).integers(0, 255, (900, 1200, 3), dtype=np.uint8)
            Image.fromarray(ruido).save(ruta)
            import estimador
            self.assertIsNone(estimador.estimar_lado_fiable(ruta))
            self.assertEqual(estimador.estimar_lado_producto(ruta), pf.estimar_producto(ruta))

    def test_sin_estimador_no_se_inventa_el_numero(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            armar(raiz, CUATRO)
            v = pf.validar(raiz, [MODELO], pf.cargar_config(), medidor=lambda r: None, estimar=None)
            self.assertIsNone(v["modelos"][MODELO]["listas_estimadas"])

    def test_renombrar_deja_el_id_en_orden_natural_y_un_registro(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            carpeta = armar(raiz, ("Foto_10.jpg", "Foto_2.png", "Foto_1.webp"))
            registro = pf.renombrar(raiz, [MODELO])
            self.assertEqual([f"{MODELO}_1.webp", f"{MODELO}_2.png", f"{MODELO}_3.jpg"],
                             [f.name for f in pf.originales(carpeta)])
            self.assertEqual("Foto_10.jpg", registro["renombradas"][-1]["antes"])
            self.assertTrue(Path(registro["archivo"]).is_file())

    def test_renombrar_se_niega_si_el_modelo_ya_tiene_procesadas(self):
        # Renombrar cambia la huella: fuerza el reproceso y caduca aprobaciones.
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            carpeta = armar(raiz, ("Foto_1.jpg",))
            (carpeta / pf.PROCESADAS).mkdir()
            (carpeta / pf.PROCESADAS / "Foto_1.jpg").write_bytes(b"maestra")
            registro = pf.renombrar(raiz, [MODELO])
            self.assertEqual([], registro["renombradas"])
            self.assertIn(MODELO, registro["negados"])
            self.assertEqual(["Foto_1.jpg"], [f.name for f in pf.originales(carpeta)])


class Portadas(unittest.TestCase):
    """22/10/2026: ninguna de las 56 fotos de portada de la tanda se había elegido; el borrador subía
    la _1 que se descargó primero. Al quedar la tanda completa se arma la hoja para elegirlas."""

    def test_las_filas_son_los_aptos_con_sus_lista_y_la_principal_primero(self):
        estados = [{"modelo": "a", "estado": pf.APTO, "listas": ["a_3.jpg", "a_1.jpg", "a_2.jpg"]},
                   {"modelo": "b", "estado": pf.EN_ESPERA, "listas": ["b_1.jpg"]},
                   {"modelo": "c", "estado": pf.APTO, "listas": ["c_1.jpg", "c_2.jpg"]}]
        filas = pf.filas_de_portadas(estados, sin_ficha={"c"})
        self.assertEqual([("a", ["a-3", "a-1", "a-2"])], filas)

    def test_la_orden_para_cambiar_principales_va_en_una_sola_llamada(self):
        texto = pf.orden_de_portadas([("a", ["a-3", "a-1"]), ("b", ["b-1"])], Path("SAL"))
        self.assertIn("a: a-3", texto)
        self.assertEqual(1, texto.count("--principal"))
        self.assertIn("--sin-hojas", texto)


class PistaDeKit(unittest.TestCase):
    """Antes de procesar: un modelo que por su ficha parece un kit recibe la sugerencia, sin declararlo.
    El 22/10/2026 el Switch 2, el PartyBox Encore 2 y la Fly Go Pad «con teclado y mouse» esperaron
    una corrida entera por piezas sueltas que la ficha ya anunciaba."""

    def ficha(self, raiz, modelo, categoria):
        carpeta = armar(raiz, CUATRO)
        (carpeta / f"{MODELO}-ficha.txt").write_text(f"MODELO: {modelo}\nCATEGORÍA: {categoria}\n", encoding="utf-8")
        return carpeta

    def test_con_teclado_o_una_consola_dan_pista(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.assertIsNotNone(pf.pista_de_kit(self.ficha(Path(tmp), 'Fly Go Pad 10″ 4G con teclado y mouse', "tablets")))
        with tempfile.TemporaryDirectory() as tmp:
            self.assertIsNotNone(pf.pista_de_kit(self.ficha(Path(tmp), "Nintendo Switch 2", "consolas")))

    def test_un_celular_comun_no_da_pista(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.assertIsNone(pf.pista_de_kit(self.ficha(Path(tmp), "Samsung Galaxy A37 5G", "celulares")))

    def test_la_tanda_avisa_la_pista_y_no_declara_el_kit(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            self.ficha(raiz, "Nintendo Switch 2", "consolas")
            avisos = pf.pistas_de_kit(raiz, [MODELO], kits={})
            self.assertEqual([MODELO], [mid for mid, _ in avisos])
            self.assertEqual({}, pf.leer_kits(raiz))
            self.assertEqual([], pf.pistas_de_kit(raiz, [MODELO], kits={MODELO: {"motivo": "x"}}))


class SinFichaEnLaLista(unittest.TestCase):
    """Decisión del 22/10/2026: un modelo que la lista no exporta —sin descripción— no puede tener
    borrador, así que no bloquea la tanda; sus fotos se procesan para no perder el trabajo."""

    def test_un_modelo_sin_ficha_en_espera_no_detiene_la_tanda(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            armar(raiz, CUATRO)
            armar(raiz, ("note_1.jpg", "note_2.jpg"), "Xiaomi Redmi Note 17 Pro Max",
                  "xiaomi-redmi-note-17-pro-max")
            estados, _ = tanda(raiz, ids=(MODELO, "xiaomi-redmi-note-17-pro-max"), procesar=listas(raiz))
            self.assertEqual(pf.APTO, estados[MODELO]["estado"])
            self.assertEqual(pf.EN_ESPERA, estados["xiaomi-redmi-note-17-pro-max"]["estado"])
            texto = pf.informe(list(estados.values()), sin_ficha={"xiaomi-redmi-note-17-pro-max"})
            self.assertIn("TANDA COMPLETA", texto, "el que espera no tiene borrador posible: no bloquea")
            self.assertIn("SIN FICHA", texto)
            self.assertIn("1 sin ficha en la lista", texto)
            self.assertNotIn("LA TANDA ESPERA FOTOS", texto)

    def test_sin_el_campo_nada_cambia(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            armar(raiz, CUATRO[:3])
            estados, _ = tanda(raiz)
            texto = pf.informe(list(estados.values()))
            self.assertIn("LA TANDA ESPERA FOTOS", texto)
            self.assertNotIn("SIN FICHA", texto)

    def test_un_sin_ficha_apto_no_se_cuenta_como_apto_de_la_tanda(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            armar(raiz, CUATRO)
            estados, _ = tanda(raiz, procesar=listas(raiz))
            texto = pf.informe(list(estados.values()), sin_ficha={MODELO})
            self.assertIn("Tanda: 0 aptos", texto)
            self.assertIn("4 LISTA; no bloquea la tanda", texto)



class CategoriaYTanda(unittest.TestCase):
    """Decisión del 20/10/2026: los celulares y las tablets van con --sin-huecos, y una carpeta
    con fotos que no está en la tanda se avisa en lugar de quedarse sin procesar en silencio."""

    def ficha(self, carpeta: Path, mid: str, categoria: str):
        (carpeta / f"{mid}-ficha.txt").write_text(
            f"MODELO: X\nID DEL MODELO: {mid}\nCATEGORÍA: {categoria}\nMARCA: X\n", encoding="utf-8")

    def test_la_categoria_sale_de_la_ficha(self):
        with tempfile.TemporaryDirectory() as tmp:
            carpeta = armar(Path(tmp))
            self.ficha(carpeta, MODELO, "celulares")
            self.assertEqual("celulares", pf.categoria_de(carpeta))

    def test_solo_celulares_y_tablets_van_sin_huecos(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            for titulo, mid, cat in (("Galaxy", "galaxy", "celulares"), ("Pad", "pad", "tablets"),
                                     ("Watch", "watch", "relojes"), ("Buds", "buds", "audifonos")):
                self.ficha(armar(raiz, ("a_1.jpg",), titulo, mid), mid, cat)
            self.assertEqual(["galaxy", "pad"], sorted(pf.sin_huecos(raiz)))

    def test_una_ficha_sin_categoria_no_va_sin_huecos(self):
        with tempfile.TemporaryDirectory() as tmp:
            armar(Path(tmp))
            self.assertEqual([], pf.sin_huecos(Path(tmp)))

    def test_avisa_las_carpetas_con_fotos_que_no_estan_en_la_tanda(self):
        # El Redmi Note 17 Pro y el Pro Max tenían sus fotos y no estaban en por-proponer.json.
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            armar(raiz)
            armar(raiz, ("note_1.webp",), "Xiaomi Redmi Note 17 Pro 5G", "xiaomi-redmi-note-17-pro-5g")
            armar(raiz, (), "Sin fotos", "sin-fotos")
            vieja = armar(raiz, ("v_1.jpg",), "De otra tanda", "de-otra-tanda")
            pf.escribir_estado(vieja, {"modelo": "de-otra-tanda", "estado": pf.APTO})
            self.assertEqual(["Xiaomi Redmi Note 17 Pro 5G"], pf.fuera_de_la_tanda(raiz, [MODELO]))



class SinHuecosPorModelo(unittest.TestCase):
    """Decisión del 20/10/2026: la persona declara un modelo sin huecos pasantes aunque su categoría
    los admita —el estuche transparente de los Redmi Buds 6 Active—."""

    def test_un_modelo_declarado_va_sin_huecos_aunque_su_categoria_no(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            carpeta = armar(raiz, ("b_1.webp",), "Buds", "buds")
            (carpeta / "buds-ficha.txt").write_text("CATEGORÍA: audifonos\n", encoding="utf-8")
            self.assertEqual([], pf.sin_huecos(raiz))
            pf.declarar_sin_huecos(raiz, "buds", "estuche cerrado")
            self.assertEqual(["buds"], pf.sin_huecos(raiz))
            self.assertEqual("estuche cerrado", pf.leer_sin_huecos(raiz)["buds"]["motivo"])
            pf.declarar_sin_huecos(raiz, "buds", "", quitar=True)
            self.assertEqual([], pf.sin_huecos(raiz))

    def test_declararlo_pide_motivo_y_una_carpeta(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            armar(raiz)
            with self.assertRaises(ValueError):
                pf.declarar_sin_huecos(raiz, MODELO, "  ")
            with self.assertRaises(ValueError):
                pf.declarar_sin_huecos(raiz, "no-existe", "motivo")

    def test_declararlo_deja_el_modelo_por_procesar(self):
        # Cambia cómo se retoca: un modelo ya decidido tiene que volver a procesarse entero.
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            nombres = tuple(f"samsung-galaxy-s25-ultra_{i}.jpg" for i in range(1, 5))
            carpeta = armar(raiz, nombres)
            salida_de_procesar(raiz, [(pf.slug(Path(n).stem), "LISTA") for n in nombres])
            cfg = dict(pf.cargar_config())
            estado, _ = pf.decidir(MODELO, carpeta, cfg, pf.leer_reporte(raiz), medidor=lambda r: None)
            pf.escribir_estado(carpeta, estado)
            _, procesar = pf.decidir(MODELO, carpeta, cfg, None, medidor=lambda r: None)
            self.assertFalse(procesar)
            pf.declarar_sin_huecos(raiz, MODELO, "sin huecos")
            _, procesar = pf.decidir(MODELO, carpeta, cfg, None, medidor=lambda r: None)
            self.assertTrue(procesar)



class RetirarLoQueYaNoSePublica(unittest.TestCase):
    """Una foto que pasa a REPETIR no deja su versión anterior donde material-catalogo.mjs la sube."""

    def test_la_version_anterior_de_una_foto_en_repetir_se_retira(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            carpeta = armar(raiz)
            procesadas = carpeta / pf.PROCESADAS
            (procesadas / pf.WEB / "1200").mkdir(parents=True)
            (procesadas / "samsung-galaxy-s25-ultra_1.jpg").write_bytes(b"malformada")
            (procesadas / pf.WEB / "1200" / "samsung-galaxy-s25-ultra_1.avif").write_bytes(b"avif")
            salida_de_procesar(raiz, [("samsung-galaxy-s25-ultra-1", "REPETIR"), ("samsung-galaxy-s25-ultra-2", "LISTA")])
            r = pf.devolver(raiz)
            self.assertFalse((procesadas / "samsung-galaxy-s25-ultra_1.jpg").exists())
            self.assertFalse((procesadas / pf.WEB / "1200" / "samsung-galaxy-s25-ultra_1.avif").exists())
            retiradas = procesadas / pf.RETIRADAS
            self.assertEqual(b"malformada", (retiradas / "samsung-galaxy-s25-ultra_1.jpg").read_bytes())
            self.assertTrue((retiradas / pf.WEB / "1200" / "samsung-galaxy-s25-ultra_1.avif").is_file())
            self.assertTrue((procesadas / "samsung-galaxy-s25-ultra_2.jpg").is_file())
            self.assertEqual(1, len(r["retiradas"]))

    def test_una_repetir_sin_version_anterior_no_retira_nada(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            armar(raiz)
            salida_de_procesar(raiz, [("samsung-galaxy-s25-ultra-1", "REPETIR")])
            self.assertEqual([], pf.devolver(raiz)["retiradas"])



class ParConEstuche(unittest.TestCase):
    """Decisión del 22/10/2026: en audífonos, hasta 4 piezas son el par con su estuche y no
    detienen la tanda; con 5 o más —o con otro reparo— sigue decidiendo la persona."""

    def armar_audifonos(self, raiz, piezas, motivos_extra=()):
        carpeta = armar(raiz, CUATRO)
        (carpeta / f"{MODELO}-ficha.txt").write_text(
            f"MODELO: X\nID DEL MODELO: {MODELO}\nCATEGORÍA: audifonos\n", encoding="utf-8")
        salida_de_procesar(raiz, [(n, "LISTA") for n in NOMBRES])
        ruta = raiz / pf.TRABAJO / "salida" / "reporte.json"
        datos = json.loads(ruta.read_text(encoding="utf-8"))
        for f in datos["fotos"]:
            if f["nombre"] == NOMBRES[3]:
                f["motivos_auto"] = [PIEZAS] + list(motivos_extra)
                f["estado"] = "REVISAR"
                f["motivos"] = [m["texto"] for m in f["motivos_auto"]]
                f["recorte"] = {"piezas": piezas}
        ruta.write_text(json.dumps(datos), encoding="utf-8")
        return carpeta

    def decidir(self, raiz, carpeta):
        estado, _ = pf.decidir(MODELO, carpeta, pf.cargar_config(), pf.leer_reporte(raiz),
                               medidor=lambda r: None)
        return estado

    def test_el_par_con_su_estuche_cuenta_como_lista(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            estado = self.decidir(raiz, self.armar_audifonos(raiz, piezas=3))
            self.assertEqual(pf.APTO, estado["estado"])
            self.assertEqual([], estado.get("aprobadas_a_ojo"), "no lo aprobó una persona: es la categoría")

    def test_cinco_piezas_siguen_esperando_y_sugieren_el_kit(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            estado = self.decidir(raiz, self.armar_audifonos(raiz, piezas=5))
            self.assertEqual(pf.EN_ESPERA, estado["estado"])
            self.assertTrue(estado["fallidas"][0].get("solo_piezas"))

    def test_otro_reparo_ademas_de_las_piezas_sigue_esperando(self):
        ampliada = {"estado": "REVISAR", "codigo": "ampliacion", "texto": MOTIVO_AMPLIACION}
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            estado = self.decidir(raiz, self.armar_audifonos(raiz, piezas=3, motivos_extra=[ampliada]))
            self.assertEqual(pf.EN_ESPERA, estado["estado"])

    def test_en_otra_categoria_nada_cambia(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            carpeta = self.armar_audifonos(raiz, piezas=3)
            (carpeta / f"{MODELO}-ficha.txt").write_text("CATEGORÍA: parlantes\n", encoding="utf-8")
            estado = self.decidir(raiz, carpeta)
            self.assertEqual(pf.EN_ESPERA, estado["estado"])

    def test_un_reporte_sin_el_conteo_de_piezas_no_perdona(self):
        # Un reporte viejo no trae `recorte`: ante la duda, decide la persona.
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            carpeta = self.armar_audifonos(raiz, piezas=3)
            ruta = raiz / pf.TRABAJO / "salida" / "reporte.json"
            datos = json.loads(ruta.read_text(encoding="utf-8"))
            for f in datos["fotos"]:
                f.pop("recorte", None)
            ruta.write_text(json.dumps(datos), encoding="utf-8")
            self.assertEqual(pf.EN_ESPERA, self.decidir(raiz, carpeta)["estado"])


class PrepararLaAprobacionAOjo(unittest.TestCase):
    """Decisión del 22/10/2026: la espera deja la revisión a un paso del --aprobar. Tres de los
    cuatro modelos en espera del 15/10 lo estaban por una sola foto REVISAR que estaba bien."""

    def estados_con_revisar(self, raiz):
        armar(raiz, CUATRO)
        estados, _ = tanda(raiz, procesar=lambda: salida_de_procesar(
            raiz, [(n, "LISTA") for n in NOMBRES[:3]] + [(NOMBRES[3], "REVISAR")]))
        return list(estados.values())

    def test_el_informe_deja_los_comandos_de_revision_listos(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            estados = self.estados_con_revisar(raiz)
            texto = pf.informe(estados, salida=raiz / pf.TRABAJO / "salida")
            self.assertIn("se aprueba y cuenta como LISTA", texto)
            self.assertIn(f"ampliar.py\" \"{raiz / pf.TRABAJO / 'salida'}\" {NOMBRES[3]}", texto)
            self.assertIn(f"--aprobar", texto)

    def test_sin_salida_el_informe_no_inventa_comandos(self):
        with tempfile.TemporaryDirectory() as tmp:
            texto = pf.informe(self.estados_con_revisar(Path(tmp)))
            self.assertNotIn("ampliar.py", texto)

    def test_una_repetir_o_una_pequena_no_se_sugiere(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            armar(raiz, CUATRO)
            estados, _ = tanda(raiz, procesar=lambda: salida_de_procesar(
                raiz, [(n, "LISTA") for n in NOMBRES[:3]] + [(NOMBRES[3], "REPETIR")]))
            texto = pf.informe(list(estados.values()), salida=raiz / pf.TRABAJO / "salida")
            self.assertNotIn("--aprobar", texto)

    def test_los_mosaicos_se_generan_solo_para_lo_que_detiene_y_esta_cerca(self):
        pedidos = []

        def falso_ampliar(nombre):
            pedidos.append(nombre)
            return f"zoom/{nombre}-zonas.jpg"

        estados = [
            {"modelo": "cerca", "estado": pf.EN_ESPERA, "faltan": 1,
             "fallidas": [{"foto": "cerca_2.jpg", "estado": "REVISAR", "motivo": "ampliación"}]},
            {"modelo": "lejos", "estado": pf.EN_ESPERA, "faltan": 3,
             "fallidas": [{"foto": "lejos_1.jpg", "estado": "REVISAR", "motivo": "ampliación"}]},
            {"modelo": "kit-pendiente", "estado": pf.EN_ESPERA, "faltan": 1,
             "fallidas": [{"foto": "k_1.jpg", "estado": "REVISAR", "motivo": "piezas", "solo_piezas": True}]},
            {"modelo": "sin-ficha", "estado": pf.EN_ESPERA, "faltan": 1,
             "fallidas": [{"foto": "s_1.jpg", "estado": "REVISAR", "motivo": "ampliación"}]},
            {"modelo": "listo", "estado": pf.APTO, "faltan": 0, "fallidas": []},
        ]
        mosaicos = pf.preparar_revision(Path("."), estados, sin_ficha={"sin-ficha"}, ampliar_fn=falso_ampliar)
        self.assertEqual(["cerca-2"], pedidos)
        self.assertEqual([("cerca/cerca_2.jpg", "zoom/cerca-2-zonas.jpg")], mosaicos)

    def test_un_ampliar_que_falla_no_rompe_nada(self):
        estados = [{"modelo": "m", "estado": pf.EN_ESPERA, "faltan": 1,
                    "fallidas": [{"foto": "m_1.jpg", "estado": "REVISAR", "motivo": "x"}]}]
        self.assertEqual([], pf.preparar_revision(Path("."), estados, ampliar_fn=lambda n: None))


class SugerirElKit(unittest.TestCase):
    """Un modelo que espera solo por piezas sueltas recibe la sugerencia del kit; nunca se declara solo."""

    def armar_con_piezas(self, raiz):
        nombres = tuple(f"samsung-galaxy-s25-ultra_{i}.jpg" for i in range(1, 5))
        carpeta = armar(raiz, nombres)
        salida_de_procesar(raiz, [(pf.slug(Path(n).stem), "LISTA") for n in nombres[:3]]
                           + [(pf.slug(Path(nombres[3]).stem), "REVISAR")])
        ruta = raiz / pf.TRABAJO / "salida" / "reporte.json"
        rep = json.loads(ruta.read_text(encoding="utf-8"))
        for f in rep["fotos"]:
            if f["estado"] == "REVISAR":
                f["motivos_auto"] = [{"estado": "REVISAR", "codigo": "varias_piezas", "texto": "3 piezas"}]
                f["motivos"] = ["3 piezas"]
        ruta.write_text(json.dumps(rep), encoding="utf-8")
        return carpeta

    def test_el_informe_sugiere_el_kit(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            carpeta = self.armar_con_piezas(raiz)
            estado, _ = pf.decidir(MODELO, carpeta, pf.cargar_config(), pf.leer_reporte(raiz), medidor=lambda r: None)
            self.assertEqual(pf.EN_ESPERA, estado["estado"])
            texto = pf.informe([estado])
            self.assertIn("¿Es un kit?", texto)
            self.assertIn(f"--kit {MODELO}", texto)

    def test_un_kit_declarado_no_recibe_la_sugerencia(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            carpeta = self.armar_con_piezas(raiz)
            estado, _ = pf.decidir(MODELO, carpeta, pf.cargar_config(), pf.leer_reporte(raiz),
                                   medidor=lambda r: None, kits={MODELO: {"motivo": "x"}})
            self.assertNotIn("¿Es un kit?", pf.informe([estado]))



class AvisoDeRetoqueAnterior(unittest.TestCase):
    """El aviso cuenta originales por rehacer, no registros del reporte (21/10/2026)."""

    def test_cuenta_las_originales_procesadas_con_un_retoque_anterior(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            armar(raiz)
            salida_de_procesar(raiz, [("samsung-galaxy-s25-ultra-1", "LISTA"), ("samsung-galaxy-s25-ultra-2", "LISTA")])
            ruta = raiz / pf.TRABAJO / "salida" / "reporte.json"
            rep = json.loads(ruta.read_text(encoding="utf-8"))
            rep["fotos"][1]["version_retoque"] = pf.salidas.VERSION_RETOQUE
            ruta.write_text(json.dumps(rep), encoding="utf-8")
            viejas = pf.con_retoque_anterior(raiz, [MODELO], pf.leer_reporte(raiz)["fotos"])
            self.assertEqual(["Samsung Galaxy S25 Ultra/samsung-galaxy-s25-ultra_1.jpg"], viejas)

    def test_no_cuenta_el_registro_de_una_foto_renombrada(self):
        # «XXiaomi Redmi Pad 2 11″ 4G_6.webp» se renombró; su registro viejo quedó en el reporte.
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            armar(raiz, ("samsung-galaxy-s25-ultra_1.jpg",))
            salida_de_procesar(raiz, [("samsung-galaxy-s25-ultra-1", "LISTA")])
            ruta = raiz / pf.TRABAJO / "salida" / "reporte.json"
            rep = json.loads(ruta.read_text(encoding="utf-8"))
            rep["fotos"][0]["version_retoque"] = pf.salidas.VERSION_RETOQUE
            rep["fotos"].append({"nombre": "xsamsung-galaxy-s25-ultra-1", "grupo": MODELO, "estado": "LISTA",
                                 "archivo": "Xsamsung-galaxy-s25-ultra_1.jpg", "salidas": {"maestra": {"ruta": "x"}}})
            ruta.write_text(json.dumps(rep), encoding="utf-8")
            self.assertEqual([], pf.con_retoque_anterior(raiz, [MODELO], pf.leer_reporte(raiz)["fotos"]))


class RetoqueEnCurso(unittest.TestCase):
    """Un avance en «procesando» de un proceso que ya murió no bloquea el --devolver (21/10/2026)."""

    def avance(self, raiz: Path, **datos):
        ruta = raiz / pf.TRABAJO / "salida" / ".trabajo" / "avance.json"
        ruta.parent.mkdir(parents=True)
        ruta.write_text(json.dumps({"total": 1, "hechas": 1, **datos}), encoding="utf-8")

    def test_un_proceso_vivo_sigue_en_curso(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.avance(Path(tmp), estado="procesando", pid=os.getpid())
            self.assertIsNotNone(pf.retoque_en_curso(Path(tmp)))

    def test_un_proceso_que_murio_sin_terminar_no_esta_en_curso(self):
        muerto = subprocess.Popen([sys.executable, "-c", "pass"])
        muerto.wait()
        with tempfile.TemporaryDirectory() as tmp:
            self.avance(Path(tmp), estado="procesando", pid=muerto.pid)
            self.assertIsNone(pf.retoque_en_curso(Path(tmp)))

    def test_un_avance_terminado_no_esta_en_curso(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.avance(Path(tmp), estado="terminado", pid=os.getpid())
            self.assertIsNone(pf.retoque_en_curso(Path(tmp)))


if __name__ == "__main__":
    unittest.main()
