"""Pruebas de lo que el recorte deja fuera del producto (decisión del 20/10/2026).

En la tanda del 15/10/2026, cuarenta fotos salieron con la pantalla manchada: el modelo de recorte
toma por fondo lo claro de un fondo de pantalla y deja huecos encerrados por el bisel, por los que
se veía la sombra de contacto. Las máscaras y las fotos se fabrican aquí con la forma de esos casos:
el teléfono con la pantalla clara, la tapa blanca mordida por el contorno, la correa cerrada que sí
es un hueco, la sombra del piso entre dos teléfonos y los dos equipos a pocos píxeles.

Piden NumPy y OpenCV, que no están en la integración continua: ahí se omiten y en local corren."""

import sys
import unittest
from pathlib import Path

SKILL = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(SKILL / "scripts"))

try:
    import cv2  # noqa: F401
    import numpy as np

    import imagen
except ImportError:  # pragma: no cover - sin NumPy u OpenCV
    imagen = None

BLANCO = (1.0, 1.0, 1.0)
GRIS_APPLE = (245 / 255, 245 / 255, 247 / 255)


def lienzo(alto=400, ancho=400, fondo=BLANCO):
    rgb = np.empty((alto, ancho, 3), np.float32)
    rgb[:] = fondo
    return rgb


def telefono_con_pantalla_clara():
    """Un teléfono de marco negro cuya pantalla es casi blanca, y la máscara que devuelve el
    modelo: solo el marco. La pantalla queda como un hueco encerrado."""
    rgb = lienzo(fondo=GRIS_APPLE)
    rgb[60:340, 120:280] = (0.1, 0.1, 0.1)
    rgb[72:328, 132:268] = (0.99, 0.99, 1.0)            # el blanco del fondo de pantalla
    rgb[72:200, 132:268] = (0.55, 0.75, 0.80)           # y algo de color arriba
    alfa = np.zeros((400, 400), np.float32)
    alfa[60:340, 120:280] = 1.0
    alfa[72:328, 132:268] = 0.0
    alfa[72:200, 132:268] = 1.0                         # lo de color sí lo vio
    return rgb, alfa


@unittest.skipIf(imagen is None, "pide NumPy y OpenCV")
class CompletarProducto(unittest.TestCase):
    def test_la_pantalla_clara_de_un_celular_se_devuelve_al_producto(self):
        rgb, alfa = telefono_con_pantalla_clara()
        nuevo, datos = imagen.completar_producto(alfa, rgb, sin_huecos=True)
        self.assertTrue((nuevo[200:328, 132:268] == 1.0).all())
        self.assertEqual(1, datos["huecos_rellenos"])
        self.assertEqual([], imagen.huecos_encerrados(nuevo))

    def test_sin_la_regla_el_hueco_seguia_ahi(self):
        # Es el defecto del iPhone 15: un hueco del 30 % del equipo que solidificar_interior no
        # cierra, porque pasa del 0,5 % que se reserva para no tapar un asa.
        rgb, alfa = telefono_con_pantalla_clara()
        solido, _ = imagen.solidificar_interior(alfa, 3.0, 0.005, 0.5, 0.0)
        self.assertTrue(imagen.huecos_encerrados(solido))

    def test_la_correa_cerrada_de_un_reloj_sigue_abierta(self):
        # Un aro cuyo hueco, en la foto, es del color del fondo: es un hueco de verdad.
        rgb = lienzo()
        alfa = np.zeros((400, 400), np.float32)
        cv2.circle(alfa, (200, 200), 150, 1.0, -1)
        cv2.circle(alfa, (200, 200), 90, 0.0, -1)
        rgb[alfa > 0] = (0.6, 0.6, 0.62)
        nuevo, datos = imagen.completar_producto(alfa, rgb, sin_huecos=False)
        self.assertEqual(0.0, float(nuevo[200, 200]))
        self.assertEqual(1, datos["huecos_abiertos"])

    def test_fuera_de_celulares_un_hueco_que_no_es_fondo_tambien_se_devuelve(self):
        rgb = lienzo()
        alfa = np.zeros((400, 400), np.float32)
        alfa[100:300, 100:300] = 1.0
        rgb[100:300, 100:300] = (0.2, 0.3, 0.6)
        alfa[150:250, 150:250] = 0.0                    # el modelo lo dejó fuera, pero es azul
        nuevo, datos = imagen.completar_producto(alfa, rgb, sin_huecos=False)
        self.assertEqual(1.0, float(nuevo[200, 200]))
        self.assertEqual(1, datos["huecos_rellenos"])

    def test_la_tapa_blanca_mordida_por_el_contorno_se_completa(self):
        # Moto G17 _3, iPhone 16e _4: la tapa casi blanca sobre fondo blanco; el modelo le muerde el
        # borde de abajo. (Una esquina entera no vuelve completa: la envolvente convexa solo
        # recupera el triángulo; de eso avisa el control del interior.)
        rgb = lienzo()
        rgb[60:340, 120:280] = (0.90, 0.93, 0.95)
        alfa = np.zeros((400, 400), np.float32)
        alfa[60:340, 120:280] = 1.0
        alfa[300:340, 160:240] = 0.0                    # el mordisco, abierto hacia afuera
        nuevo, datos = imagen.completar_producto(alfa, rgb, sin_huecos=True)
        self.assertEqual(1.0, float(nuevo[320, 200]))
        self.assertEqual(1, datos["muescas_rellenas"])

    def test_la_sombra_del_piso_entre_dos_telefonos_no_es_producto(self):
        # Galaxy S26 Ultra _1 y A17 _1: los teléfonos escalonados forman una sola pieza, y bajo el
        # más corto la sombra del render cae dentro de la envolvente. Rellenarla la pinta como equipo.
        rgb = lienzo()
        alfa = np.zeros((400, 400), np.float32)
        rgb[60:340, 100:200] = (0.15, 0.15, 0.15)
        rgb[60:300, 200:300] = (0.55, 0.7, 0.85)
        alfa[60:340, 100:200] = 1.0
        alfa[60:300, 200:300] = 1.0
        for i, y in enumerate(range(300, 340)):          # la sombra se desvanece hacia el fondo
            rgb[y, 200:300] = 0.6 + 0.4 * i / 40
        nuevo, datos = imagen.completar_producto(alfa, rgb, sin_huecos=True)
        self.assertEqual(0.0, float(nuevo[310, 250]))
        self.assertEqual(0, datos["muescas_rellenas"])

    def test_la_cunna_de_fondo_entre_dos_telefonos_se_queda_fuera(self):
        rgb = lienzo()
        alfa = np.zeros((400, 400), np.float32)
        rgb[60:340, 100:200] = (0.15, 0.15, 0.15)
        rgb[100:340, 200:300] = (0.15, 0.15, 0.15)
        alfa[60:340, 100:200] = 1.0
        alfa[100:340, 200:300] = 1.0
        nuevo, _ = imagen.completar_producto(alfa, rgb, sin_huecos=True)
        self.assertEqual(0.0, float(nuevo[70, 280]))

    def test_sin_un_fondo_de_estudio_solo_cuenta_la_categoria(self):
        # Sobre una mesa el color no dice qué es fondo: fuera de celulares no se rellena nada.
        rng = np.random.default_rng(1)
        rgb = rng.random((400, 400, 3)).astype(np.float32) * 0.5
        alfa = np.zeros((400, 400), np.float32)
        alfa[100:300, 100:300] = 1.0
        alfa[150:250, 150:250] = 0.0
        nuevo, datos = imagen.completar_producto(alfa, rgb, sin_huecos=False)
        self.assertFalse(datos["fondo_leido"])
        self.assertEqual(0.0, float(nuevo[200, 200]))


    def test_lo_excluido_a_mano_no_vuelve_como_hueco(self):
        rgb, alfa = telefono_con_pantalla_clara()
        prohibido = np.zeros(alfa.shape, bool)
        prohibido[250:300, 150:250] = True                # marcar.py --excluir dentro de la pantalla
        nuevo, _ = imagen.completar_producto(alfa, rgb, sin_huecos=True, prohibido=prohibido)
        self.assertEqual(0.0, float(nuevo[275, 200]))
        self.assertEqual(1.0, float(nuevo[220, 200]))

    def test_una_foto_grande_decide_a_escala_y_rellena_a_resolucion_completa(self):
        # Hay fotos de 8000 px: a resolución completa la decisión pedía más de 3 GB, y un hueco a la
        # vez tardaba un minuto con miles de huecos de un píxel (una malla).
        lado = 4000
        rgb = np.ones((lado, lado, 3), np.float32)
        alfa = np.zeros((lado, lado), np.float32)
        alfa[400:3600, 1000:3000] = 1.0
        rgb[400:3600, 1000:3000] = (0.1, 0.1, 0.1)
        alfa[600:3400, 1200:2800] = 0.0                   # la pantalla, encerrada
        rgb[600:3400, 1200:2800] = (0.99, 0.99, 1.0)
        alfa[100:300:3, 100:300:3] = 0.0                   # no estorba: está fuera del producto
        malla = np.zeros((lado, lado), bool)
        malla[420:580:4, 1020:2980:4] = True               # miles de huecos de un píxel en el marco
        alfa[malla] = 0.0
        nuevo, datos = imagen.completar_producto(alfa, rgb, sin_huecos=True)
        self.assertEqual(1.0, float(nuevo[2000, 2000]))
        self.assertEqual(0.0, float(nuevo[200, 200]))      # el fondo exterior no se toca
        self.assertEqual(0.0, float(nuevo[399, 2000]))     # ni el borde de afuera del marco
        self.assertGreater(datos["huecos_rellenos"], 0)

    def test_rellenar_huecos_no_toca_lo_que_llega_al_borde(self):
        m = np.zeros((50, 50), bool)
        m[10:40, 10:40] = True
        m[20:30, 20:30] = False
        m[0:5, 0:5] = False
        lleno = imagen.rellenar_huecos(m)
        self.assertTrue(lleno[25, 25])
        self.assertFalse(lleno[2, 2])


@unittest.skipIf(imagen is None, "pide NumPy y OpenCV")
class CierreYColor(unittest.TestCase):
    def test_el_cierre_no_une_dos_equipos_a_traves_del_fondo(self):
        # Galaxy A17 _1: el de frente y el de perfil a 10 px; el cierre de 31 px los pegaba.
        alfa = np.zeros((300, 300), np.float32)
        alfa[50:250, 50:140] = 1.0
        alfa[50:250, 150:240] = 1.0
        fondo = alfa == 0
        unido, _ = imagen.solidificar_interior(alfa, 3.0, 0.005, 0.5, 31.0)
        self.assertGreater(float(unido[150, 145]), 0.5)          # así estaba
        separado, _ = imagen.solidificar_interior(alfa, 3.0, 0.005, 0.5, 31.0, fondo)
        self.assertEqual(0.0, float(separado[150, 145]))

    def test_el_cierre_sigue_cerrando_una_grieta_que_no_es_fondo(self):
        alfa = np.zeros((300, 300), np.float32)
        alfa[50:250, 50:240] = 1.0
        alfa[50:250, 143:147] = 0.0                              # un reflejo partió el producto
        fondo = np.zeros_like(alfa, bool)
        fondo[:50] = fondo[250:] = True
        fondo[:, :50] = fondo[:, 240:] = True
        cerrado, _ = imagen.solidificar_interior(alfa, 3.0, 0.005, 0.5, 31.0, fondo)
        self.assertEqual(1.0, float(cerrado[150, 145]))

    def test_el_interior_publica_los_pixeles_del_original(self):
        # TCL Pad _3: pymatting estima un color suavizado donde el modelo dejó alfa parcial, y al
        # subir el alfa ese color se quedaba. Adentro, a más de la banda del borde, va el original.
        original = np.random.default_rng(2).random((200, 200, 3)).astype(np.float32)
        estimado = np.full_like(original, 0.5)
        alfa = np.zeros((200, 200), np.float32)
        alfa[20:180, 20:180] = 1.0
        color = imagen.interior_con_el_original(estimado, original, alfa, 14.0)
        np.testing.assert_allclose(color[60:140, 60:140], original[60:140, 60:140], atol=1e-6)
        np.testing.assert_allclose(color[20, 100], estimado[20, 100], atol=0.05)

    def test_una_mancha_gris_en_una_pantalla_blanca_se_detecta(self):
        # La crominancia no la ve: el gris de la sombra no tiene croma.
        original = np.ones((10000, 3), np.float32)
        salida = original.copy()
        salida[:500] = 0.62
        self.assertLessEqual(imagen.delta_croma(original, salida), 2.0)
        self.assertGreater(imagen.fraccion_alterada(original, salida), imagen.ALTERADO_MAXIMO)
        self.assertEqual(0.0, imagen.fraccion_alterada(original, original))


if __name__ == "__main__":
    unittest.main()
