"""Pruebas de la base de productos conocidos.

La base es lo que hace que una lista no se procese desde cero: guarda lo que
costó investigar —precio de mercado con sus fuentes, descripción, colores— y lo
que la lista mueve cada vez —el costo y la última fecha en que apareció—.
"""

import json
import sys
import tempfile
import unittest
from datetime import date
from pathlib import Path

SKILL = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(SKILL / "scripts"))

import conocidos  # noqa: E402


DESCRIPCION = "El JBL Xtreme 4 es un parlante portátil.\n\n## Colores\n\nDisponible en Negro, Azul.\n"


def producto(**cambios):
    base = {
        "id": "jbl-xtreme-4", "titulo": "JBL Xtreme 4", "categoria": "parlantes", "marca": "JBL",
        "precio_proveedor_cop": 1_100_000, "precio_mercado_cop": 1_260_400,
        "nivel_precio": "marketplace",
        "fuentes_precio": [{"tienda": "Éxito", "precio_cop": 1_289_900}],
        "notas_precio": ["sin vitrina en Alkosto"],
        "descripcion": DESCRIPCION,
        "meta_titulo": "JBL Xtreme 4", "meta_descripcion": "Parlante portátil.",
        "colores_oficiales": ["Negro", "Azul"],
        "supuestos": ["título confirmado el 02/10/2026", "colores: jbl.com"],
        "supuestos_lista": ["título confirmado el 02/10/2026"],
    }
    base.update(cambios)
    return base


def lista(*productos, fecha="2026-10-08"):
    return {"fecha_lista": fecha, "productos": list(productos),
            "comparacion": {"fecha": fecha, "resumen": {}}}


class Consolidar(unittest.TestCase):
    HOY = date(2026, 10, 8)

    def test_un_producto_terminado_entra_con_todo_lo_investigado(self):
        base, resumen = conocidos.consolidar({}, lista(producto()), self.HOY)
        e = base["jbl-xtreme-4"]
        self.assertEqual("JBL Xtreme 4", e["titulo"])
        self.assertEqual(1_260_400, e["precio_mercado_cop"])
        self.assertEqual([{"tienda": "Éxito", "precio_cop": 1_289_900}], e["fuentes_precio"])
        self.assertEqual(DESCRIPCION, e["descripcion"])
        self.assertEqual(["Negro", "Azul"], e["colores_oficiales"])
        self.assertEqual({"altas": 1, "actualizados": 0, "sin_terminar": []}, resumen)

    def test_la_fecha_del_precio_es_la_de_la_investigacion(self):
        base, _ = conocidos.consolidar({}, lista(producto()), self.HOY)
        self.assertEqual("2026-10-08", base["jbl-xtreme-4"]["fecha_precio"])

    def test_la_fecha_del_precio_es_la_de_la_consulta_y_no_la_de_consolidar(self):
        # Revisión del 08/10/2026: consolidar días después rejuvenecía el precio.
        p = producto(fecha_precio="2026-10-08")
        base, _ = conocidos.consolidar({}, lista(p), date(2026, 10, 20))
        self.assertEqual("2026-10-08", base["jbl-xtreme-4"]["fecha_precio"])

    def test_consolidar_una_lista_vieja_no_retrocede_el_costo_ni_el_visto(self):
        base, _ = conocidos.consolidar({}, lista(producto(precio_proveedor_cop=990_000,
                                                          bloques=["VARIEDAD"])), self.HOY)
        vieja = lista(producto(precio_proveedor_cop=1_100_000, bloques=[]), fecha="2026-10-02")
        base, _ = conocidos.consolidar(base, vieja, self.HOY)
        e = base["jbl-xtreme-4"]
        self.assertEqual((990_000, "2026-10-08", ["VARIEDAD"]),
                         (e["ultimo_costo_cop"], e["visto_por_ultima_vez"], e["bloques"]))

    def test_si_el_paso_4_no_encuentra_precio_se_conserva_el_anterior(self):
        base, _ = conocidos.consolidar({}, lista(producto(fecha_precio="2026-10-02")), self.HOY)
        sin = producto(precio_mercado_cop=None, nivel_precio=None, fuentes_precio=[], fecha_precio=None)
        base, _ = conocidos.consolidar(base, lista(sin), self.HOY)
        e = base["jbl-xtreme-4"]
        self.assertEqual((1_260_400, "2026-10-02"), (e["precio_mercado_cop"], e["fecha_precio"]))

    def test_un_precio_heredado_de_la_base_conserva_su_fecha(self):
        # comparar_lista copia el precio con su fecha; consolidar no puede
        # rejuvenecerlo, o un precio no vencería nunca.
        p = producto(fecha_precio="2026-10-02")
        base, _ = conocidos.consolidar({}, lista(p), self.HOY)
        self.assertEqual("2026-10-02", base["jbl-xtreme-4"]["fecha_precio"])

    def test_lo_que_mueve_la_lista_es_el_costo_y_la_fecha_en_que_aparecio(self):
        base, _ = conocidos.consolidar({}, lista(producto(precio_proveedor_cop=990_000)), self.HOY)
        self.assertEqual(990_000, base["jbl-xtreme-4"]["ultimo_costo_cop"])
        self.assertEqual("2026-10-08", base["jbl-xtreme-4"]["visto_por_ultima_vez"])

    def test_solo_se_guardan_los_supuestos_de_la_investigacion(self):
        # Los del parser se repiten solos en cada lista; guardarlos los duplicaría.
        # Los de colores van aparte: solo valen mientras la lista marque lo mismo.
        p = producto(supuestos=["título confirmado el 02/10/2026", "colores: jbl.com",
                                "la marca escribe «Xtreme», no «Extreme»"])
        base, _ = conocidos.consolidar({}, lista(p), self.HOY)
        self.assertEqual(["la marca escribe «Xtreme», no «Extreme»"],
                         base["jbl-xtreme-4"]["supuestos_investigacion"])
        self.assertEqual(["colores: jbl.com"], base["jbl-xtreme-4"]["supuestos_colores"])

    def test_un_supuesto_sobre_el_precio_o_una_linea_es_de_esa_lista_y_no_se_guarda(self):
        # Revisión del 08/10/2026: la base sembrada traía «precio tomado del bloque
        # PRECIOS DE VENTA (línea 530 …)» y se reinyectaba en la lista siguiente.
        p = producto(supuestos=[
            "precio tomado del bloque «PRECIOS DE VENTA» (línea 530: «17 PRO 256 ESIM *$3.750*»)",
            "dos precios para el mismo equipo: 2.700.000 (línea 2) y 2.550.000 (línea 519)",
            "la línea trae «760» sin el signo $: se leyó como 760.000 COP",
            "la lista dice 7\"; la Galaxy Tab A11 es de 8,7\"",
        ])
        base, _ = conocidos.consolidar({}, lista(p), self.HOY)
        self.assertEqual(["la lista dice 7\"; la Galaxy Tab A11 es de 8,7\""],
                         base["jbl-xtreme-4"]["supuestos_investigacion"])

    def test_sin_la_comparacion_no_se_consolida(self):
        # Sin `supuestos_lista` no hay cómo separar los supuestos del parser, y
        # quedarían en la base para siempre.
        datos = lista(producto())
        del datos["comparacion"]
        with self.assertRaisesRegex(ValueError, "comparar_lista"):
            conocidos.consolidar({}, datos, self.HOY)

    def test_guarda_los_colores_que_marcaba_la_lista(self):
        base, _ = conocidos.consolidar({}, lista(producto(colores_familia=["Negro"])), self.HOY)
        self.assertEqual(["Negro"], base["jbl-xtreme-4"]["colores_de_la_lista"])

    def test_la_seccion_de_colores_de_la_descripcion_sigue_a_los_colores(self):
        p = producto(colores_oficiales=["Negro"],
                     descripcion="Intro.\n\n## Colores\n\nDisponible en Negro, Azul.\n\n## Contenido de la caja\n\n- Parlante\n")
        base, _ = conocidos.consolidar({}, lista(p), self.HOY)
        self.assertEqual("Intro.\n\n## Colores\n\nDisponible en Negro.\n\n## Contenido de la caja\n\n- Parlante\n",
                         base["jbl-xtreme-4"]["descripcion"])

    def test_un_producto_sin_descripcion_no_entra(self):
        base, resumen = conocidos.consolidar({}, lista(producto(descripcion=None)), self.HOY)
        self.assertEqual({}, base)
        self.assertEqual(["jbl-xtreme-4"], resumen["sin_terminar"])

    def test_un_conocido_conserva_su_fecha_de_alta(self):
        base, _ = conocidos.consolidar({}, lista(producto(), fecha="2026-10-02"), date(2026, 10, 2))
        base, resumen = conocidos.consolidar(base, lista(producto()), self.HOY)
        self.assertEqual("2026-10-02", base["jbl-xtreme-4"]["fecha_alta"])
        self.assertEqual("2026-10-08", base["jbl-xtreme-4"]["visto_por_ultima_vez"])
        self.assertEqual(1, resumen["actualizados"])

    def test_guarda_de_que_bloque_viene(self):
        base, _ = conocidos.consolidar({}, lista(producto(bloques=["VARIEDAD"])), self.HOY)
        self.assertEqual(["VARIEDAD"], base["jbl-xtreme-4"]["bloques"])

    def test_un_aviso_de_llegada_no_le_borra_el_bloque(self):
        base, _ = conocidos.consolidar({}, lista(producto(bloques=["VARIEDAD"])), self.HOY)
        base, _ = conocidos.consolidar(base, lista(producto(bloques=[])), self.HOY)
        self.assertEqual(["VARIEDAD"], base["jbl-xtreme-4"]["bloques"])

    def test_no_toca_la_base_que_recibe(self):
        original = {}
        conocidos.consolidar(original, lista(producto()), self.HOY)
        self.assertEqual({}, original)


class SeccionDeColores(unittest.TestCase):
    DESCRIPCION = ("Intro.\n\n## Colores\n\nDisponible en Negro, Azul.\n\n"
                   "## Contenido de la caja\n\n- Parlante\n")

    def test_con_los_mismos_colores_no_cambia_nada(self):
        self.assertEqual(self.DESCRIPCION, conocidos.con_colores(self.DESCRIPCION, ["Negro", "Azul"]))

    def test_sin_colores_la_seccion_se_va(self):
        self.assertEqual("Intro.\n\n## Contenido de la caja\n\n- Parlante\n",
                         conocidos.con_colores(self.DESCRIPCION, []))

    def test_si_no_estaba_entra_antes_del_contenido_de_la_caja(self):
        sin = conocidos.con_colores(self.DESCRIPCION, [])
        self.assertEqual(self.DESCRIPCION, conocidos.con_colores(sin, ["Negro", "Azul"]))

    def test_las_descripciones_de_la_base_ya_cuadran_con_sus_colores(self):
        for pid, e in conocidos.cargar().items():
            with self.subTest(pid=pid):
                self.assertEqual(e["descripcion"], conocidos.con_colores(e["descripcion"], e["colores_oficiales"]))


class VigenciaDelPrecio(unittest.TestCase):
    """Decisión del negocio, 08/10/2026: un precio de mercado vale 7 días."""

    def test_vale_siete_dias(self):
        self.assertEqual(7, conocidos.VIGENCIA_PRECIO_DIAS)

    def test_el_septimo_dia_sigue_vigente_y_el_octavo_no(self):
        e = {"precio_mercado_cop": 1_000_000, "fecha_precio": "2026-10-02"}
        self.assertTrue(conocidos.precio_vigente(e, date(2026, 10, 9)))
        self.assertFalse(conocidos.precio_vigente(e, date(2026, 10, 10)))

    def test_sin_precio_no_hay_nada_vigente(self):
        self.assertFalse(conocidos.precio_vigente({"precio_mercado_cop": None}, date(2026, 10, 2)))


class Validacion(unittest.TestCase):
    def entrada(self, **cambios):
        base, _ = conocidos.consolidar({}, lista(producto()), date(2026, 10, 8))
        e = base["jbl-xtreme-4"]
        e.update(cambios)
        return {"jbl-xtreme-4": e}

    def test_el_archivo_guardado_es_valido(self):
        self.assertGreater(len(conocidos.cargar()), 0)

    def test_la_clave_tiene_que_salir_del_titulo(self):
        with self.assertRaisesRegex(ValueError, "no corresponde al título"):
            conocidos.validar(self.entrada(titulo="JBL Extreme 4"))

    def test_un_precio_sin_fecha_no_se_acepta(self):
        # Sin fecha no se puede saber si venció.
        with self.assertRaisesRegex(ValueError, "sin fecha_precio"):
            conocidos.validar(self.entrada(fecha_precio=None))

    def test_un_precio_sin_fuentes_no_se_acepta(self):
        with self.assertRaisesRegex(ValueError, "sin fuentes"):
            conocidos.validar(self.entrada(fuentes_precio=[]))

    def test_el_dinero_es_entero(self):
        # Regla dura 6: el dinero nunca es float, tampoco en un JSON de trabajo.
        with self.assertRaisesRegex(ValueError, "pesos enteros"):
            conocidos.validar(self.entrada(precio_mercado_cop=1_260_400.5))


class Guardar(unittest.TestCase):
    def test_guardar_y_cargar_es_estable(self):
        base, _ = conocidos.consolidar({}, lista(producto()), date(2026, 10, 8))
        with tempfile.TemporaryDirectory() as tmp:
            ruta = Path(tmp) / "conocidos.json"
            conocidos.guardar(base, ruta)
            primero = ruta.read_bytes()
            conocidos.guardar(conocidos.cargar(ruta), ruta)
            self.assertEqual(primero, ruta.read_bytes())
            self.assertNotIn(b"\r\n", primero)
            self.assertEqual(base, json.loads(primero)["productos"])


if __name__ == "__main__":
    unittest.main()
