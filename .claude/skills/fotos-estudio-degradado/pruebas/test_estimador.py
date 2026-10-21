"""Pruebas de `estimador.py`: cuánto mide el producto antes de recortarlo, que es lo que elige el
lienzo. Las fotos se fabrican aquí con la forma de los casos reales: el gris de Apple con su
filete oscuro en el borde, el PNG recortado con negro guardado en lo transparente y la foto
apaisada con el producto de lado a lado.

Piden NumPy y Pillow, que no están en la integración continua: ahí se omiten y en local corren."""

import sys
import tempfile
import unittest
from pathlib import Path

SKILL = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(SKILL / "scripts"))

try:
    import numpy as np
    from PIL import Image

    import estimador
except ImportError:  # pragma: no cover - sin NumPy o Pillow
    estimador = None


def foto(tmp, nombre, alto, ancho, fondo, producto, valor=60, modo="L", filete=None):
    """Una foto con un rectángulo de `valor` en la caja `producto` (y0, x0, y1, x1)."""
    arr = np.full((alto, ancho), fondo, np.uint8)
    if filete is not None:
        arr[:2, :] = arr[-2:, :] = filete
        arr[:, :2] = arr[:, -2:] = filete
    y0, x0, y1, x1 = producto
    arr[y0:y1, x0:x1] = valor
    ruta = Path(tmp) / nombre
    Image.fromarray(arr, modo).save(ruta)
    return ruta


@unittest.skipIf(estimador is None, "pide NumPy y Pillow")
class EstimarLadoProducto(unittest.TestCase):
    def test_sobre_el_gris_de_apple_mide_el_telefono_y_no_la_foto(self):
        # El iPhone 18 Pro del 18/10/2026: 712 px en un cuadro de 1000 sobre #F5F5F7, con un
        # filete de 227. Medía 1000 y la tanda elegía 1600, donde salía a 1,91× y en REVISAR.
        with tempfile.TemporaryDirectory() as tmp:
            ruta = foto(tmp, "iphone.png", 1000, 1000, 245, (144, 330, 856, 670), filete=227)
            self.assertAlmostEqual(712, estimador.estimar_lado_producto(ruta), delta=4)

    def test_sobre_blanco_mide_lo_mismo_que_antes(self):
        with tempfile.TemporaryDirectory() as tmp:
            ruta = foto(tmp, "blanco.jpg", 1200, 1200, 255, (100, 300, 1100, 900))
            self.assertAlmostEqual(1000, estimador.estimar_lado_producto(ruta), delta=6)

    def test_sobre_una_mesa_no_hay_medida_fiable(self):
        # La foto entera «mide»: sirve para elegir un lienzo (sale mayor, no menor), pero no para
        # decidir cuánto reducir la foto al cargarla.
        with tempfile.TemporaryDirectory() as tmp:
            arr = (np.random.default_rng(3).random((1000, 1000)) * 120 + 60).astype(np.uint8)
            ruta = Path(tmp) / "mesa.png"
            Image.fromarray(arr, "L").save(ruta)
            self.assertEqual(1000, estimador.estimar_lado_producto(ruta))
            self.assertIsNone(estimador.estimar_lado_fiable(ruta))
            blanco = foto(tmp, "blanco.png", 1200, 1200, 255, (100, 300, 1100, 900))
            self.assertAlmostEqual(1000, estimador.estimar_lado_fiable(blanco), delta=6)

    def test_un_png_recortado_se_mide_por_su_alfa(self):
        # Lo transparente guardado en negro hacía que la foto entera «fuera producto».
        with tempfile.TemporaryDirectory() as tmp:
            rgba = np.zeros((1600, 1600, 4), np.uint8)
            rgba[300:1300, 400:1100] = (200, 200, 200, 255)
            ruta = Path(tmp) / "recorte.webp"
            Image.fromarray(rgba, "RGBA").save(ruta, lossless=True)
            self.assertEqual(1000, estimador.estimar_lado_producto(ruta))

    def test_el_producto_que_cruza_una_foto_apaisada_no_la_vuelve_un_fondo_raro(self):
        with tempfile.TemporaryDirectory() as tmp:
            ruta = foto(tmp, "apaisada.png", 600, 1200, 255, (0, 300, 600, 900))
            self.assertAlmostEqual(600, estimador.estimar_lado_producto(ruta), delta=30)

    def test_un_fondo_que_no_es_de_estudio_devuelve_la_foto_entera(self):
        # Una mesa de madera: no hay un fondo que restar, y la estimación conservadora es la foto.
        with tempfile.TemporaryDirectory() as tmp:
            arr = (np.indices((900, 900)).sum(axis=0) % 90 + 90).astype(np.uint8)
            arr[300:600, 300:600] = 20
            ruta = Path(tmp) / "mesa.png"
            Image.fromarray(arr, "L").save(ruta)
            self.assertEqual(900, estimador.estimar_lado_producto(ruta))

    def test_una_foto_que_no_abre_no_tiene_medida(self):
        with tempfile.TemporaryDirectory() as tmp:
            ruta = Path(tmp) / "rota.jpg"
            ruta.write_bytes(b"no es una imagen")
            self.assertIsNone(estimador.estimar_lado_producto(ruta))


if __name__ == "__main__":
    unittest.main()
