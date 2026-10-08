"""Pruebas del paso de diferencias: qué trae la lista de hoy frente a lo conocido."""

import json
import subprocess
import sys
import tempfile
import unittest
from copy import deepcopy
from datetime import date
from pathlib import Path

SKILL = Path(__file__).resolve().parent.parent
SCRIPTS = SKILL / "scripts"
sys.path.insert(0, str(SCRIPTS))

import comparar_lista  # noqa: E402
from pendientes import necesita  # noqa: E402

HOY = date(2026, 10, 8)


def conocido(**cambios):
    e = {
        "titulo": "JBL Xtreme 4", "categoria": "parlantes", "marca": "JBL",
        "precio_mercado_cop": 1_260_400, "nivel_precio": "inventario propio",
        "fuentes_precio": [{"tienda": "Alkosto", "precio_cop": 1_260_400}],
        "notas_precio": [], "fecha_precio": "2026-10-02",
        "descripcion": "El JBL Xtreme 4 es un parlante portátil.",
        "meta_titulo": "JBL Xtreme 4", "meta_descripcion": "Parlante portátil.",
        "colores_oficiales": ["Negro", "Azul"],
        "supuestos_investigacion": ["colores: jbl.com"],
        "bloques": ["VARIEDAD"], "ultimo_costo_cop": 1_100_000,
        "visto_por_ultima_vez": "2026-10-02", "fecha_alta": "2026-10-02",
    }
    e.update(cambios)
    return e


def producto(id_="jbl-xtreme-4", titulo="JBL Xtreme 4", costo=1_100_000, **cambios):
    p = {
        "id": id_, "titulo": titulo, "categoria": "parlantes", "marca": "JBL",
        "precio_proveedor_cop": costo, "precio_mercado_cop": None, "fuentes_precio": [],
        "descripcion": None, "colores_oficiales": [], "revisar": [],
        "supuestos": ["título confirmado el 02/10/2026"], "bloques": ["VARIEDAD"],
    }
    p.update(cambios)
    return p


def lista(*productos, bloques=("VARIEDAD",)):
    return {"fecha_lista": "2026-10-08", "bloques": list(bloques), "productos": list(productos)}


def comparar(datos, base, hoy=HOY):
    datos = deepcopy(datos)
    comparar_lista.comparar(datos, base, hoy)
    return datos


class Estados(unittest.TestCase):
    BASE = {"jbl-xtreme-4": conocido()}

    def test_un_producto_que_no_esta_en_la_base_es_nuevo_y_se_investiga_entero(self):
        [p] = comparar(lista(producto("jbl-flip-7", "JBL Flip 7")), self.BASE)["productos"]
        self.assertEqual("nuevo", p["estado_lista"])
        self.assertEqual(["precio", "ficha", "descripcion"], p["pendiente"])

    def test_un_conocido_al_mismo_costo_no_tiene_nada_pendiente(self):
        [p] = comparar(lista(producto()), self.BASE)["productos"]
        self.assertEqual("sin_cambios", p["estado_lista"])
        self.assertEqual([], p["pendiente"])

    def test_un_conocido_con_otro_costo_lo_dice_con_el_costo_anterior(self):
        [p] = comparar(lista(producto(costo=1_000_000)), self.BASE)["productos"]
        self.assertEqual("costo_cambio", p["estado_lista"])
        self.assertEqual(1_100_000, p["costo_anterior_cop"])
        self.assertEqual([], p["pendiente"])

    def test_un_precio_vencido_se_vuelve_a_investigar_y_lo_demas_se_hereda(self):
        base = {"jbl-xtreme-4": conocido(fecha_precio="2026-09-30")}
        [p] = comparar(lista(producto()), base)["productos"]
        self.assertEqual("sin_precio_vigente", p["estado_lista"])
        self.assertEqual(["precio"], p["pendiente"])
        self.assertIsNone(p["precio_mercado_cop"])
        self.assertEqual("El JBL Xtreme 4 es un parlante portátil.", p["descripcion"])

    def test_un_conocido_que_nunca_tuvo_precio_tambien_lo_pide(self):
        base = {"jbl-xtreme-4": conocido(precio_mercado_cop=None, fecha_precio=None,
                                         fuentes_precio=[], nivel_precio=None)}
        [p] = comparar(lista(producto()), base)["productos"]
        self.assertEqual(["precio"], p["pendiente"])


class Herencia(unittest.TestCase):
    BASE = {"jbl-xtreme-4": conocido()}

    def test_copia_lo_investigado_con_la_fecha_del_precio(self):
        [p] = comparar(lista(producto()), self.BASE)["productos"]
        self.assertEqual(1_260_400, p["precio_mercado_cop"])
        self.assertEqual("2026-10-02", p["fecha_precio"])
        self.assertEqual([{"tienda": "Alkosto", "precio_cop": 1_260_400}], p["fuentes_precio"])
        self.assertEqual(["Negro", "Azul"], p["colores_oficiales"])
        self.assertEqual("Parlante portátil.", p["meta_descripcion"])

    def test_el_margen_se_calcula_con_el_costo_de_hoy(self):
        [p] = comparar(lista(producto(costo=1_000_000)), self.BASE)["productos"]
        self.assertEqual(260_400, p["ganancia_cop"])
        self.assertEqual(0.2604, p["margen"])

    def test_si_el_costo_sube_por_encima_del_mercado_lo_marca(self):
        [p] = comparar(lista(producto(costo=1_300_000)), self.BASE)["productos"]
        self.assertTrue(any(r.startswith("POR DEBAJO DEL COSTO") for r in p["revisar"]), p["revisar"])

    def test_los_supuestos_de_la_investigacion_vuelven_y_los_de_la_lista_quedan_aparte(self):
        [p] = comparar(lista(producto()), self.BASE)["productos"]
        self.assertEqual(["título confirmado el 02/10/2026", "colores: jbl.com"], p["supuestos"])
        self.assertEqual(["título confirmado el 02/10/2026"], p["supuestos_lista"])

    def test_correrlo_dos_veces_da_lo_mismo(self):
        una = comparar(lista(producto(costo=1_000_000)), self.BASE)
        dos = comparar(una, self.BASE)
        self.assertEqual(una, dos)


class Desaparecidos(unittest.TestCase):
    def test_falta_un_conocido_de_un_bloque_que_llego(self):
        base = {"jbl-xtreme-4": conocido(), "jbl-flip-7": conocido(titulo="JBL Flip 7")}
        datos = comparar(lista(producto()), base)
        self.assertEqual(["jbl-flip-7"], [d["id"] for d in datos["desaparecidos"]])

    def test_si_su_bloque_no_llego_no_se_sabe_nada(self):
        base = {"jbl-xtreme-4": conocido(),
                "apple-iphone-17-pro-256gb-esim": conocido(titulo="Apple iPhone 17 Pro 256GB eSIM",
                                                           bloques=["GAMA ALTA"])}
        self.assertEqual([], comparar(lista(producto()), base)["desaparecidos"])

    def test_uno_que_solo_llego_en_avisos_no_se_da_por_desaparecido(self):
        base = {"jbl-xtreme-4": conocido(), "apple-iphone-16-128gb": conocido(
            titulo="Apple iPhone 16 128GB", bloques=[])}
        self.assertEqual([], comparar(lista(producto()), base)["desaparecidos"])

    def test_uno_que_vino_pero_quedo_descartado_no_desaparecio(self):
        # 08/10/2026: el S25 Ultra vino en gama alta sin precio. No desapareció;
        # no entra, y por qué es lo que hay que ver.
        base = {"jbl-flip-7": conocido(titulo="JBL Flip 7")}
        datos = lista()
        datos["descartados"] = [{"id": "jbl-flip-7", "motivo": "sin precio de proveedor"}]
        datos = comparar(datos, base)
        self.assertEqual([], datos["desaparecidos"])
        self.assertEqual([{"id": "jbl-flip-7", "titulo": "JBL Flip 7", "motivo": "sin precio de proveedor"}],
                         datos["conocidos_descartados"])

    def test_dice_cuando_se_vio_por_ultima_vez_y_a_que_costo(self):
        base = {"jbl-flip-7": conocido(titulo="JBL Flip 7")}
        [d] = comparar(lista(), base)["desaparecidos"]
        self.assertEqual({"id": "jbl-flip-7", "titulo": "JBL Flip 7", "bloques": ["VARIEDAD"],
                          "visto_por_ultima_vez": "2026-10-02", "ultimo_costo_cop": 1_100_000}, d)


class Resumen(unittest.TestCase):
    def test_cuenta_cada_estado(self):
        base = {"jbl-xtreme-4": conocido(), "jbl-flip-7": conocido(titulo="JBL Flip 7")}
        datos = comparar(lista(producto(), producto("jbl-grip", "JBL Grip")), base)
        self.assertEqual({"nuevo": 1, "costo_cambio": 0, "sin_cambios": 1,
                          "sin_precio_vigente": 0, "desaparecidos": 1},
                         datos["comparacion"]["resumen"])
        self.assertEqual("2026-10-08", datos["comparacion"]["fecha"])

    def test_el_reporte_nombra_lo_que_hay_que_investigar(self):
        base = {"jbl-xtreme-4": conocido()}
        datos = comparar(lista(producto(costo=1_000_000), producto("jbl-grip", "JBL Grip")), base)
        texto = comparar_lista.reporte(datos)
        self.assertIn("JBL Grip", texto.split("## Nuevos")[1].split("##")[0])
        self.assertIn("1.100.000 → 1.000.000", texto)
        self.assertIn("ganancia 260.400", texto)

    def test_una_perdida_se_ve_en_pesos(self):
        # Con el margen redondeado, 700.000 contra 699.900 salía «margen -0,0%».
        base = {"jbl-xtreme-4": conocido(precio_mercado_cop=699_900)}
        texto = comparar_lista.reporte(comparar(lista(producto(costo=700_000)), base))
        self.assertIn("ganancia −100", texto)
        self.assertIn("POR DEBAJO DEL COSTO", texto)


class PasoCuatroRespetaLoPendiente(unittest.TestCase):
    """Los scripts del paso 4 reescribían todos los productos: un precio heredado
    se perdía al pasar por la mediana de un corpus que no lo traía."""

    def test_sin_comparacion_se_procesa_todo(self):
        self.assertTrue(necesita({}, "precio"))

    def test_asignar_precios_no_toca_un_precio_heredado(self):
        base = {"jbl-xtreme-4": conocido()}
        datos = comparar(lista(producto(), producto("jbl-grip", "JBL Grip")), base)
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            (tmp / "p.json").write_text(json.dumps(datos), encoding="utf-8")
            (tmp / "vtex.json").write_text("{}", encoding="utf-8")
            (tmp / "alk.json").write_text("{}", encoding="utf-8")
            subprocess.run([sys.executable, "-B", str(SCRIPTS / "asignar_precios.py"), str(tmp / "p.json"),
                            "--vtex", str(tmp / "vtex.json"), "--alkosto", str(tmp / "alk.json")],
                           check=True, capture_output=True)
            xtreme, grip = json.loads((tmp / "p.json").read_text(encoding="utf-8"))["productos"]
        self.assertEqual(1_260_400, xtreme["precio_mercado_cop"])
        self.assertEqual("2026-10-02", xtreme["fecha_precio"])
        self.assertIsNone(grip["precio_mercado_cop"])
        self.assertTrue(any("sin precio de mercado" in r for r in grip["revisar"]))

    def test_redactar_fichas_no_marca_sin_descripcion_a_un_heredado(self):
        base = {"jbl-xtreme-4": conocido()}
        datos = comparar(lista(producto()), base)
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            (tmp / "p.json").write_text(json.dumps(datos), encoding="utf-8")
            (tmp / "prosa.json").write_text("{}", encoding="utf-8")
            subprocess.run([sys.executable, "-B", str(SCRIPTS / "redactar_fichas.py"), str(tmp / "p.json"),
                            "--prosa", str(tmp / "prosa.json")], check=True, capture_output=True)
            [p] = json.loads((tmp / "p.json").read_text(encoding="utf-8"))["productos"]
        self.assertEqual("El JBL Xtreme 4 es un parlante portátil.", p["descripcion"])
        self.assertFalse(any("sin descripcion" in r for r in p["revisar"]), p["revisar"])

    def test_la_cosecha_no_consulta_las_tiendas_por_un_heredado(self):
        # Sin el filtro, esto sale a la red por cada tienda y cada consulta, y
        # con la pausa entre peticiones no termina en los 30 segundos.
        datos = comparar(lista(producto()), {"jbl-xtreme-4": conocido()})
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            (tmp / "p.json").write_text(json.dumps(datos), encoding="utf-8")
            subprocess.run([sys.executable, "-B", str(SCRIPTS / "precios.py"), str(tmp / "p.json"),
                            "--salida", str(tmp / "vtex.json")], check=True, capture_output=True, timeout=30)
            self.assertEqual({}, json.loads((tmp / "vtex.json").read_text(encoding="utf-8")))

    def test_icecat_solo_busca_ficha_a_lo_que_la_necesita(self):
        import icecat_local
        datos = comparar(lista(producto(), producto("jbl-grip", "JBL Grip")), {"jbl-xtreme-4": conocido()})
        with tempfile.TemporaryDirectory() as tmp:
            ruta = Path(tmp) / "p.json"
            ruta.write_text(json.dumps(datos), encoding="utf-8")
            self.assertEqual(["jbl-grip"], [p["id"] for p in icecat_local.cargar_productos(ruta)])


if __name__ == "__main__":
    unittest.main()
