"""Pruebas del redactor de fichas: una descripción por modelo, con la ficha de
Icecat primero y la de la marca para lo que Icecat no cubre, siempre con su aviso."""

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
        subprocess.run(env=ENTORNO_UTF8, args=args, check=True, capture_output=True)
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
    """Desde el 22/10/2026 Icecat va primero y la marca cubre lo que Icecat no tiene."""

    def test_icecat_va_primero_y_sin_la_memoria(self):
        [a, _] = redactar(DOS, marca=MARCA, icecat=ICECAT)
        self.assertIn("| Batería | 5000 mAh |", a["descripcion"])
        self.assertNotIn("256 GB", a["descripcion"])
        self.assertNotIn("samsung.com/co", a["descripcion"])
        self.assertEqual("Open Icecat", a["fuentes_ficha"][0]["fuente"])

    def test_la_marca_entra_si_icecat_no_tiene_la_ficha(self):
        [a, _] = redactar(DOS, marca=MARCA)
        self.assertIn("| Pantalla | 6,7\" Super AMOLED Plus |", a["descripcion"])
        self.assertIn("Ficha técnica: samsung.com/co, consultado el 2026-10-08.", a["descripcion"])
        self.assertNotIn("Icecat", a["descripcion"])
        self.assertEqual([{"fuente": "samsung.com/co", "url": "https://www.samsung.com/co/", "fecha": "2026-10-08"}],
                         a["fuentes_ficha"])


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


class CompletarHeredada(unittest.TestCase):
    """La descripción heredada de la base se completa con la ficha y la caja, sin tocar la prosa."""

    HEREDADA = ("El Galaxy A57 es un celular 5G.\n\n## Características principales\n\n- Pantalla de 6,7\".\n\n"
                "## Garantía y notas\n\n- Producto nuevo, sellado y sin activar.\n")
    MARCA_CON_CAJA = {"samsung-galaxy-a57-5g": dict(MARCA["samsung-galaxy-a57-5g"],
                                                     caja=["Galaxy A57", "Cable USB-C"])}

    def heredados(self):
        return [producto(p["id"], p["titulo"], descripcion=self.HEREDADA, pendiente=["ficha", "completar"],
                         fuentes_ficha=[], revisar=["descripción de la base: no tiene «Contenido de la caja»"])
                for p in DOS]

    def test_inserta_la_ficha_y_la_caja_de_la_marca_sin_tocar_la_prosa(self):
        a, b = redactar(self.heredados(), marca=self.MARCA_CON_CAJA, prosa={})
        d = a["descripcion"]
        self.assertTrue(d.startswith("El Galaxy A57 es un celular 5G.\n\n## Características principales\n\n"
                                     "- Pantalla de 6,7\"."), d)
        self.assertIn("| Pantalla | 6,7\" Super AMOLED Plus |", d)
        self.assertIn("## Contenido de la caja\n\n- Galaxy A57\n- Cable USB-C", d)
        self.assertLess(d.index("## Ficha técnica"), d.index("## Contenido de la caja"))
        self.assertLess(d.index("## Contenido de la caja"), d.index("## Garantía y notas"))
        self.assertIn("Ficha técnica: samsung.com/co, consultado el 2026-10-08.", d)
        self.assertIn("Contenido de la caja: samsung.com/co, consultado el 2026-10-08.", d)
        self.assertEqual(d, b["descripcion"])
        self.assertEqual("samsung.com/co", a["fuentes_ficha"][0]["fuente"])
        # Lo que faltaba ya no se pide; lo que la ficha de la marca no trae —el peso— se sigue diciendo.
        self.assertEqual(["descripción de la base: la ficha técnica no dice peso ni dimensiones"],
                         [r for r in a["revisar"] if r.startswith("descripción de la base")])

    def test_icecat_va_primero_tambien_al_completar(self):
        [a, _] = redactar(self.heredados(), marca=MARCA, icecat=ICECAT, prosa={})
        self.assertIn("Database Right data-sheet 2026 Icecat", a["descripcion"])
        self.assertIn("| Batería | 5000 mAh |", a["descripcion"])

    def test_sin_fuente_no_inventa_y_la_deja_como_estaba(self):
        [a, _] = redactar(self.heredados(), prosa={})
        self.assertEqual(self.HEREDADA, a["descripcion"])

    def test_completada_ya_no_queda_pendiente(self):
        # Revisión del PR #268: `completar` y `ficha` seguían pendientes y una segunda corrida la
        # reportaba «sin fuente para completar», e icecat_local la volvía a buscar.
        [a, _] = redactar(self.heredados(), marca=self.MARCA_CON_CAJA, prosa={})
        self.assertNotIn("completar", a["pendiente"])
        self.assertNotIn("ficha", a["pendiente"])

    def test_una_segunda_corrida_no_la_da_por_sin_fuente(self):
        primera = redactar(self.heredados(), marca=self.MARCA_CON_CAJA, prosa={})
        segunda = redactar([dict(p, pendiente=["ficha", "completar"]) for p in primera],
                           marca=self.MARCA_CON_CAJA, prosa={})
        self.assertEqual(primera[0]["descripcion"], segunda[0]["descripcion"])
        self.assertNotIn("completar", segunda[0]["pendiente"])

    def test_no_duplica_la_atribucion_de_la_ficha_en_el_pie(self):
        # Revisión del PR #268: una heredada sin «## Ficha técnica» pero con «Ficha técnica: …» en el
        # pie quedaba, al completarse, con dos atribuciones.
        heredada = self.HEREDADA + "\n---\n\nFicha técnica: una fuente vieja, consultado el 2026-09-01.\n"
        [a, _] = redactar([dict(p, descripcion=heredada) for p in self.heredados()],
                          marca=self.MARCA_CON_CAJA, prosa={})
        pie = a["descripcion"].split("\n---\n")[-1]
        self.assertEqual(1, pie.count("Ficha técnica:"), pie)
        self.assertIn("Ficha técnica: samsung.com/co", pie)

    def test_una_redaccion_nueva_no_se_completa(self):
        # La que tiene `descripcion` pendiente se redacta entera; `completar` es solo para heredadas.
        [a, _] = redactar([dict(p, pendiente=["descripcion", "completar"]) for p in DOS], marca=MARCA)
        self.assertTrue(a["descripcion"].startswith("El Galaxy A57 es un celular 5G.\n\n## Ficha técnica"))


class CotejoIcecatMarca(unittest.TestCase):
    """22/10/2026: la ficha de Icecat del Moto G15 era la de otro mercado —IP54, sin adaptador,
    Bluetooth 5.0— frente a la de Motorola Colombia: IP52, cargador en la caja."""

    ICECAT_OTRO_MERCADO = {"especificaciones": [
        {"atributo": "Diagonal de la pantalla", "valor": "17,1 cm (6.72\")"},
        {"atributo": "Código IP (International Protection)", "valor": "IP54"},
        {"atributo": "Adaptador AC incluido", "valor": "No"},
        {"atributo": "Versión de Bluetooth", "valor": "5.0"}]}
    MARCA_CO = {"samsung-galaxy-a57-5g": {
        "fuente": "motorola.com/co", "fecha": "2026-10-22",
        "f": {"Pantalla": "LTPS de 6,72 pulgadas", "Resistencia": "IP52 (salpicaduras)",
              "Conectividad": "Wi-Fi 5 · Bluetooth 5.4 · NFC"},
        "caja": ["Teléfono", "Cargador de viaje", "Cable"]}}

    def test_si_se_contradicen_manda_la_marca_y_queda_el_aviso_con_los_valores(self):
        [a, _] = redactar(DOS, marca=self.MARCA_CO, icecat=self.ICECAT_OTRO_MERCADO)
        self.assertIn("| Resistencia | IP52 (salpicaduras) |", a["descripcion"])
        self.assertNotIn("Icecat", a["descripcion"])
        [aviso] = [r for r in a["revisar"] if r.startswith("la ficha de Icecat no coincide")]
        for dato in ("resistencia IP54 contra IP52", "Bluetooth 5.0 contra 5.4", "cargador incluido no contra sí"):
            self.assertIn(dato, aviso)

    def test_si_coinciden_va_icecat(self):
        icecat = {"especificaciones": [{"atributo": "Diagonal de la pantalla", "valor": "17,1 cm (6.72\")"},
                                       {"atributo": "Código IP (International Protection)", "valor": "IP52"}]}
        [a, _] = redactar(DOS, marca=self.MARCA_CO, icecat=icecat)
        self.assertIn("Database Right data-sheet 2026 Icecat", a["descripcion"])
        self.assertFalse(any(r.startswith("la ficha de Icecat no coincide") for r in a["revisar"]))

    def test_un_adaptador_que_no_es_cargador_no_cuenta(self):
        # Revisión del PR #268: «Adaptador USB-C a 3,5 mm», «Adaptador de SIM» o «Cargador no
        # incluido» se leían como cargador en la caja.
        icecat = {"especificaciones": [{"atributo": "Adaptador AC incluido", "valor": "No"}]}
        for caja in (["Teléfono", "Adaptador USB-C a 3,5 mm"], ["Teléfono", "Adaptador de SIM"],
                     ["Teléfono", "Cargador no incluido"]):
            self.assertEqual([], redactar_fichas.cotejar(icecat, {"f": {"Pantalla": "OLED"}, "caja": caja}), caja)
        self.assertEqual(["cargador incluido no contra sí"], redactar_fichas.cotejar(
            icecat, {"f": {"Pantalla": "OLED"}, "caja": ["Teléfono", "Adaptador de corriente de 33 W"]}))

    def test_bluetooth_5_es_5_0_y_se_lee_con_la_marca_registrada(self):
        icecat = {"especificaciones": [{"atributo": "Versión de Bluetooth", "valor": "5.0"}]}
        self.assertEqual([], redactar_fichas.cotejar(icecat, {"f": {"Conectividad": "Bluetooth 5"}}))
        self.assertEqual(["Bluetooth 5.0 contra 5.4"],
                         redactar_fichas.cotejar(icecat, {"f": {"Conectividad": "Bluetooth® 5.4"}}))

    def test_solo_compara_lo_que_las_dos_dicen(self):
        self.assertEqual([], redactar_fichas.cotejar({"especificaciones": [
            {"atributo": "Versión de Bluetooth", "valor": "5.4"}]}, {"f": {"Pantalla": "OLED"}}))


class PaletaDeLaMarca(unittest.TestCase):
    """22/10/2026: la paleta de mi.com y de JBL se escribía a mano en el paso 4 con un script suelto."""

    MARCA_CON_PALETA = {"samsung-galaxy-a57-5g": dict(MARCA["samsung-galaxy-a57-5g"], paleta=["Negro", "Lila"])}

    def test_lleva_la_paleta_a_los_que_no_tienen_y_dice_de_donde_salio(self):
        [a, _] = redactar([dict(p, colores_oficiales=[]) for p in DOS], marca=self.MARCA_CON_PALETA)
        self.assertEqual(["Negro", "Lila"], a["colores_oficiales"])
        self.assertIn("colores: la paleta que publica samsung.com/co (2026-10-08)", a["supuestos"])

    def test_no_pisa_una_paleta_que_ya_existe(self):
        [a, _] = redactar(DOS, marca=self.MARCA_CON_PALETA)
        self.assertEqual(["Azul", "Gris"], a["colores_oficiales"])

    def test_sin_emojis_deja_dicho_que_se_sugiere_la_paleta_completa(self):
        [a, _] = redactar([dict(p, colores_familia=[], supuestos=[], supuestos_lista=[]) for p in DOS],
                          marca=self.MARCA_CON_PALETA)
        self.assertTrue(any(s.startswith("colores sugeridos: la lista no marca colores") for s in a["supuestos"]))
        self.assertTrue(any(s.startswith("colores sugeridos:") for s in a["supuestos_lista"]),
                        "es de esta lista: consolidar no lo lleva a la base")


class Filas(unittest.TestCase):
    def test_la_ficha_de_marca_suelta_las_filas_de_memoria(self):
        filas = redactar_fichas.tabla_mi({"f": {"Memoria": "8GB", "RAM": "8GB", "Almacenamiento": "256GB",
                                                "Pantalla": "6,7\""}})
        self.assertEqual([("Pantalla", "6,7\"")], filas)


if __name__ == "__main__":
    unittest.main()
