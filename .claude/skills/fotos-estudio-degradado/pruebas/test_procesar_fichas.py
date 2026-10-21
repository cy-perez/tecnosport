"""Pruebas del modo de carpetas de modelo: preparar el árbol de trabajo, devolver
cada resultado a su carpeta con el nombre de su original, y la tanda del bloque 2.
No corren procesar.py (pide OpenCV): el reporte y las salidas se fabrican aquí,
**con la forma que de verdad escribe procesar.py** —motivos finales como texto,
`archivo` y `huella_origen` en cada registro—. Una prueba que fabricaba los motivos
como diccionarios pasaba mientras el código se caía con el primer reporte real."""

import json
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

    def test_una_revisar_aprobada_a_ojo_no_cuenta(self):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            armar(raiz, CUATRO)
            estados, _ = tanda(raiz, procesar=listas(raiz, revisadas=(NOMBRES[3],)))
            self.assertEqual(pf.EN_ESPERA, estados[MODELO]["estado"])
            self.assertEqual(3, len(estados[MODELO]["listas"]))
            self.assertEqual("REVISAR", estados[MODELO]["fallidas"][0]["estado"])

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
            self.assertEqual(("2026-10-10", ["a", "b"]), pf.leer_tanda(ruta))



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


if __name__ == "__main__":
    unittest.main()
