"""
Un solo proceso a la vez escribe en una carpeta de salida.

`procesar.py` toma una copia del reporte al empezar y la guarda al terminar, con sus fotos
encima; `marcar.py` y `hojas.py` hacen lo mismo en pequeño. Dos a la vez sobre la misma carpeta se
pisan: gana el último que guarda, y lo que el otro agregó desaparece del reporte aunque sus
archivos sigan en disco. Pasó el 22/10/2026: la parte 12 de la tanda corrió mientras la 11 seguía
en segundo plano, y las 18 fotos de la 11 se perdieron del reporte; la tanda las dio por «sin
procesar» y hubo que rehacerlas. La escritura atómica del reporte (reporte.escribir_json) evita un
JSON a medias, no esto.

El candado es un archivo en `.trabajo/` con el pid de quien escribe. Uno de un proceso que ya no
existe —lo mató la memoria, se cerró la sesión— no bloquea: se toma. Tampoco uno de más de
`VIGENCIA_HORAS` (variable de entorno `FOTOS_CANDADO_HORAS`, 12 por omisión): Windows reasigna el
PID de un proceso muerto, y sin un límite un candado huérfano se tomaba por vivo para siempre
(revisión del PR #268). Leer la hora de arranque de otro proceso no se puede sin dependencias en
todos los sistemas; una tanda de 300 fotos tarda cerca de una hora, así que 12 sobra. Solo
biblioteca estándar.
"""

from __future__ import annotations

import json
import os
from datetime import datetime, timezone
from pathlib import Path

import salidas

VIGENCIA_HORAS = float(os.environ.get("FOTOS_CANDADO_HORAS") or 12)
TRABAJO = ".trabajo"               # = reporte.TRABAJO; se repite para no cargar reporte aquí
ARCHIVO = "candado.json"


class CarpetaOcupada(RuntimeError):
    """Otro proceso vivo está escribiendo en la carpeta."""


def ruta(raiz: Path) -> Path:
    return Path(raiz) / TRABAJO / ARCHIVO


def _leer(raiz: Path):
    try:
        return json.loads(ruta(raiz).read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return None


def ocupante(raiz: Path):
    """Los datos del candado si lo tiene otro proceso vivo, o None."""
    datos = _leer(raiz)
    if not datos:
        return None
    pid = int(datos.get("pid") or 0)
    if pid == os.getpid() or not salidas.vivo(pid):
        return None
    if _horas_desde(datos.get("desde")) > VIGENCIA_HORAS:
        print(f"[aviso] el candado de {raiz} ({datos.get('quien', '?')}, PID {pid}) es de {datos.get('desde')}, "
              f"de hace más de {VIGENCIA_HORAS:g} h: se toma como huérfano —ese PID puede ser ya de otro proceso—.")
        return None
    return datos


def _horas_desde(desde) -> float:
    """Horas desde la marca ISO del candado; 0 si no se puede leer, para no tomar uno que sí vive."""
    try:
        cuando = datetime.fromisoformat(str(desde))
    except ValueError:
        return 0.0
    if cuando.tzinfo is None:
        cuando = cuando.replace(tzinfo=timezone.utc)
    return (datetime.now(timezone.utc) - cuando).total_seconds() / 3600


def mensaje(raiz: Path, datos: dict) -> str:
    return (f"Otra corrida está escribiendo en {raiz} ({datos.get('quien', '?')}, PID {datos.get('pid')}, "
            f"desde {datos.get('desde', '?')}): espera a que termine. Dos a la vez se pisan el reporte —el "
            "22/10/2026 una parte de la tanda borró del reporte las 18 fotos de la otra—. Si ese proceso ya "
            f"no existe, borra {ruta(raiz)}.")


class Candado:
    """`with Candado(raiz, "procesar.py"):` — se niega con CarpetaOcupada si otro proceso vivo la tiene."""

    def __init__(self, raiz: Path, quien: str):
        self.raiz = Path(raiz)
        self.quien = quien
        self.tomado = False

    def __enter__(self):
        if not self.raiz.is_dir():
            return self             # una carpeta que no existe no tiene reporte que proteger
        destino = ruta(self.raiz)
        destino.parent.mkdir(parents=True, exist_ok=True)
        for _ in range(3):
            try:
                fd = os.open(destino, os.O_CREAT | os.O_EXCL | os.O_WRONLY)
            except FileExistsError:
                datos = ocupante(self.raiz)
                if datos:
                    raise CarpetaOcupada(mensaje(self.raiz, datos))
                try:
                    destino.unlink()        # de un proceso muerto, ilegible o nuestro: se toma
                except FileNotFoundError:
                    pass
                continue
            with os.fdopen(fd, "w", encoding="utf-8") as fh:
                json.dump({"pid": os.getpid(), "quien": self.quien,
                           "desde": datetime.now(timezone.utc).isoformat(timespec="seconds")}, fh)
            self.tomado = True
            return self
        raise CarpetaOcupada(f"No se pudo tomar el candado de {self.raiz}: inténtalo de nuevo.")

    def __exit__(self, *exc):
        if self.tomado:
            datos = _leer(self.raiz)
            if datos is None or int(datos.get("pid") or 0) == os.getpid():
                try:
                    ruta(self.raiz).unlink()
                except FileNotFoundError:
                    pass
            self.tomado = False
        return False
