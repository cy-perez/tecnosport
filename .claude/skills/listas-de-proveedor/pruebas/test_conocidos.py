"""Pruebas de la base de productos conocidos, dividida en modelos y configuraciones.

La base es lo que hace que una lista no se procese desde cero: guarda lo que
costó investigar —la ficha por modelo, el precio por configuración— y lo que la
lista mueve cada vez —el costo, los colores que marcó, la última fecha en que
apareció—.
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

DESCRIPCION = (
    "El Galaxy A57 es un celular 5G.\n\n## Ficha técnica\n\n| Atributo | Detalle |\n|---|---|\n"
    "| Pantalla | 6,7\" |\n| Memoria | 8GB de RAM · 256GB de almacenamiento |\n\n"
    "## Colores\n\nDisponible en Azul, Gris.\n\n## Garantía y notas\n\n"
    "- Este equipo funciona con eSIM: no tiene bandeja para SIM física.\n"
    "- Producto nuevo, sellado y sin activar.\n")
DESCRIPCION_DE_MODELO = (
    "El Galaxy A57 es un celular 5G.\n\n## Ficha técnica\n\n| Atributo | Detalle |\n|---|---|\n"
    "| Pantalla | 6,7\" |\n\n## Garantía y notas\n\n- Producto nuevo, sellado y sin activar.\n")


def producto(**cambios):
    base = {
        "id": "samsung-galaxy-a57-5g-8gb-ram-256gb", "titulo": "Samsung Galaxy A57 5G 8GB RAM 256GB",
        "id_modelo": "samsung-galaxy-a57-5g", "titulo_modelo": "Samsung Galaxy A57 5G",
        "categoria": "celulares", "marca": "Samsung",
        "precio_proveedor_cop": 1_290_000, "precio_mercado_cop": 1_599_900,
        "nivel_precio": "inventario propio",
        "fuentes_precio": [{"tienda": "Alkosto", "precio_cop": 1_599_900}],
        "notas_precio": [], "fecha_precio": "2026-10-08",
        "descripcion": DESCRIPCION, "meta_titulo": "Samsung Galaxy A57 5G 8GB RAM 256GB",
        "meta_descripcion": "Celular 5G.",
        "colores_oficiales": ["Azul Marino Asombroso", "Gris Asombroso"],
        "colores_familia": [], "bloques": ["ANDROID"],
        "supuestos": ["título confirmado el 02/10/2026", "colores: samsung.com/co, preguntas frecuentes"],
        "supuestos_lista": ["título confirmado el 02/10/2026"],
        "fuentes_ficha": [{"fuente": "samsung.com/co", "url": "https://www.samsung.com/co/", "fecha": "2026-10-08"}],
    }
    base.update(cambios)
    return base


def otra_memoria(**cambios):
    return producto(id="samsung-galaxy-a57-5g-12gb-ram-512gb", titulo="Samsung Galaxy A57 5G 12GB RAM 512GB",
                    precio_proveedor_cop=1_700_000, precio_mercado_cop=2_099_900, **cambios)


def lista(*productos, fecha="2026-10-08"):
    return {"fecha_lista": fecha, "productos": list(productos),
            "comparacion": {"fecha": fecha, "resumen": {}}}


HOY = date(2026, 10, 8)


def consolidar(base, datos, hoy=HOY):
    return conocidos.consolidar(base, datos, hoy)


class Modelos(unittest.TestCase):
    def test_dos_memorias_del_mismo_equipo_son_un_modelo_y_dos_configuraciones(self):
        base, resumen = consolidar(conocidos.base_vacia(), lista(producto(), otra_memoria()))
        self.assertEqual(["samsung-galaxy-a57-5g"], list(base["modelos"]))
        self.assertEqual(["samsung-galaxy-a57-5g-8gb-ram-256gb", "samsung-galaxy-a57-5g-12gb-ram-512gb"],
                         list(base["configuraciones"]))
        self.assertEqual((1, 2), (resumen["modelos_altas"], resumen["configuraciones_altas"]))

    def test_la_descripcion_del_modelo_no_lleva_colores_ni_memoria_ni_notas_de_sim(self):
        # Decisión del 08/10/2026: color y memoria se eligen al comprar.
        base, _ = consolidar(conocidos.base_vacia(), lista(producto()))
        self.assertEqual(DESCRIPCION_DE_MODELO, base["modelos"]["samsung-galaxy-a57-5g"]["descripcion"])

    def test_el_modelo_guarda_la_paleta_y_de_donde_salio_la_ficha(self):
        base, _ = consolidar(conocidos.base_vacia(), lista(producto()))
        m = base["modelos"]["samsung-galaxy-a57-5g"]
        self.assertEqual(["Azul Marino Asombroso", "Gris Asombroso"], m["paleta"])
        self.assertEqual(["colores: samsung.com/co, preguntas frecuentes"], m["supuestos_paleta"])
        self.assertEqual("samsung.com/co", m["fuentes_ficha"][0]["fuente"])
        self.assertEqual("Samsung Galaxy A57 5G", m["titulo"])

    def test_la_paleta_se_suma_y_no_se_pierde(self):
        base, _ = consolidar(conocidos.base_vacia(), lista(producto()))
        base, _ = consolidar(base, lista(producto(colores_oficiales=["Lila Asombroso"])))
        self.assertEqual(["Azul Marino Asombroso", "Gris Asombroso", "Lila Asombroso"],
                         base["modelos"]["samsung-galaxy-a57-5g"]["paleta"])

    def test_un_supuesto_sobre_el_precio_o_una_linea_es_de_esa_lista_y_no_se_guarda(self):
        p = producto(supuestos=[
            "precio tomado del bloque «PRECIOS DE VENTA» (línea 530)",
            "la línea trae «760» sin el signo $",
            "la lista dice 7\"; la Galaxy Tab A11 es de 8,7\"",
        ], supuestos_lista=[])
        base, _ = consolidar(conocidos.base_vacia(), lista(p))
        self.assertEqual(["la lista dice 7\"; la Galaxy Tab A11 es de 8,7\""],
                         base["modelos"]["samsung-galaxy-a57-5g"]["supuestos_investigacion"])

    def test_un_modelo_conserva_su_fecha_de_alta_y_sus_fotos(self):
        base, _ = consolidar(conocidos.base_vacia(), lista(producto(), fecha="2026-10-02"), date(2026, 10, 2))
        base["modelos"]["samsung-galaxy-a57-5g"]["fotos"] = [{"nombre": "samsung-galaxy-a57-5g_1.jpg"}]
        base, _ = consolidar(base, lista(producto()))
        m = base["modelos"]["samsung-galaxy-a57-5g"]
        self.assertEqual("2026-10-02", m["fecha_alta"])
        self.assertEqual([{"nombre": "samsung-galaxy-a57-5g_1.jpg"}], m["fotos"])


class Configuraciones(unittest.TestCase):
    def conf(self, base, cid="samsung-galaxy-a57-5g-8gb-ram-256gb"):
        return base["configuraciones"][cid]

    def test_cada_memoria_guarda_su_precio(self):
        base, _ = consolidar(conocidos.base_vacia(), lista(producto(), otra_memoria()))
        self.assertEqual(1_599_900, self.conf(base)["precio_mercado_cop"])
        self.assertEqual(2_099_900, self.conf(base, "samsung-galaxy-a57-5g-12gb-ram-512gb")["precio_mercado_cop"])

    def test_la_fecha_del_precio_es_la_de_la_consulta_y_no_la_de_consolidar(self):
        base, _ = consolidar(conocidos.base_vacia(), lista(producto()), date(2026, 10, 20))
        self.assertEqual("2026-10-08", self.conf(base)["fecha_precio"])

    def test_un_precio_sin_fecha_toma_la_de_hoy(self):
        base, _ = consolidar(conocidos.base_vacia(), lista(producto(fecha_precio=None)))
        self.assertEqual("2026-10-08", self.conf(base)["fecha_precio"])

    def test_lo_que_mueve_la_lista(self):
        p = producto(precio_proveedor_cop=1_250_000, colores_familia=["Azul"], bloques=["ANDROID"])
        base, _ = consolidar(conocidos.base_vacia(), lista(p))
        c = self.conf(base)
        self.assertEqual((1_250_000, ["Azul"], ["ANDROID"], "2026-10-08"),
                         (c["ultimo_costo_cop"], c["colores_de_la_lista"], c["bloques"], c["visto_por_ultima_vez"]))

    def test_un_aviso_de_llegada_no_le_borra_el_bloque(self):
        base, _ = consolidar(conocidos.base_vacia(), lista(producto()))
        base, _ = consolidar(base, lista(producto(bloques=[])))
        self.assertEqual(["ANDROID"], self.conf(base)["bloques"])

    def test_consolidar_una_lista_vieja_no_retrocede_el_costo_ni_el_visto(self):
        base, _ = consolidar(conocidos.base_vacia(), lista(producto(precio_proveedor_cop=990_000)))
        base, _ = consolidar(base, lista(producto(precio_proveedor_cop=1_100_000, bloques=[]), fecha="2026-10-02"))
        c = self.conf(base)
        self.assertEqual((990_000, "2026-10-08", ["ANDROID"]),
                         (c["ultimo_costo_cop"], c["visto_por_ultima_vez"], c["bloques"]))

    def test_si_el_paso_4_no_encuentra_precio_se_conserva_el_anterior(self):
        base, _ = consolidar(conocidos.base_vacia(), lista(producto(fecha_precio="2026-10-02")))
        sin = producto(precio_mercado_cop=None, nivel_precio=None, fuentes_precio=[], fecha_precio=None)
        base, _ = consolidar(base, lista(sin))
        self.assertEqual((1_599_900, "2026-10-02"),
                         (self.conf(base)["precio_mercado_cop"], self.conf(base)["fecha_precio"]))


class Consolidar(unittest.TestCase):
    def test_un_producto_sin_descripcion_no_entra(self):
        base, resumen = consolidar(conocidos.base_vacia(), lista(producto(descripcion=None)))
        self.assertEqual(conocidos.base_vacia(), base)
        self.assertEqual(["samsung-galaxy-a57-5g-8gb-ram-256gb"], resumen["sin_terminar"])

    def test_sin_la_comparacion_no_se_consolida(self):
        datos = lista(producto())
        del datos["comparacion"]
        with self.assertRaisesRegex(ValueError, "comparar_lista"):
            consolidar(conocidos.base_vacia(), datos)

    def test_no_toca_la_base_que_recibe(self):
        original = conocidos.base_vacia()
        consolidar(original, lista(producto()))
        self.assertEqual(conocidos.base_vacia(), original)


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
    def base(self):
        base, _ = consolidar(conocidos.base_vacia(), lista(producto()))
        return base

    def test_el_archivo_guardado_es_valido(self):
        self.assertGreater(len(conocidos.cargar()["modelos"]), 0)

    def test_la_clave_del_modelo_sale_de_su_titulo(self):
        b = self.base()
        b["modelos"]["samsung-galaxy-a57-5g"]["titulo"] = "Samsung Galaxy A56 5G"
        with self.assertRaisesRegex(ValueError, "no corresponde al título"):
            conocidos.validar(b)

    def test_una_configuracion_sin_su_modelo_no_se_acepta(self):
        b = self.base()
        del b["modelos"]["samsung-galaxy-a57-5g"]
        with self.assertRaisesRegex(ValueError, "su modelo .* no está en la base"):
            conocidos.validar(b)

    def test_un_precio_sin_fecha_o_sin_fuentes_no_se_acepta(self):
        b = self.base()
        b["configuraciones"]["samsung-galaxy-a57-5g-8gb-ram-256gb"]["fecha_precio"] = None
        with self.assertRaisesRegex(ValueError, "sin fecha_precio"):
            conocidos.validar(b)
        b = self.base()
        b["configuraciones"]["samsung-galaxy-a57-5g-8gb-ram-256gb"]["fuentes_precio"] = []
        with self.assertRaisesRegex(ValueError, "sin fuentes"):
            conocidos.validar(b)

    def test_el_dinero_es_entero(self):
        # Regla dura 6: el dinero nunca es float, tampoco en un JSON de trabajo.
        b = self.base()
        b["configuraciones"]["samsung-galaxy-a57-5g-8gb-ram-256gb"]["precio_mercado_cop"] = 1_599_900.5
        with self.assertRaisesRegex(ValueError, "pesos enteros"):
            conocidos.validar(b)


class Guardar(unittest.TestCase):
    def test_guardar_y_cargar_es_estable(self):
        base, _ = consolidar(conocidos.base_vacia(), lista(producto(), otra_memoria()))
        with tempfile.TemporaryDirectory() as tmp:
            ruta = Path(tmp) / "conocidos.json"
            conocidos.guardar(base, ruta)
            primero = ruta.read_bytes()
            conocidos.guardar(conocidos.cargar(ruta), ruta)
            self.assertEqual(primero, ruta.read_bytes())
            self.assertNotIn(b"\r\n", primero)
            documento = json.loads(primero)
            self.assertEqual(base, {"modelos": documento["modelos"], "configuraciones": documento["configuraciones"]})


if __name__ == "__main__":
    unittest.main()
