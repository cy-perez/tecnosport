# Tipografia del sitio — Tecno Sport

## Familias

| Rol | Familia | Pesos | Licencia | Uso |
|---|---|---|---|---|
| display | **Archivo** | 500, 700 | SIL OFL 1.1 | Titulares y logotipo. Mayusculas en titulares cortos |
| texto | **IBM Plex Sans** | 400, 500, 700 | SIL OFL 1.1 | Cuerpo, interfaz, fichas de producto y formularios |
| mono | **IBM Plex Mono** | 400, 500 | SIL OFL 1.1 | Precios, referencias, SKU y cantidades del mayorista |

## Como se instala

Las tipografias vienen **autoalojadas en el kit**, en `fuentes/`.
El sitio no depende de ningun servicio externo.

```html
<link rel="stylesheet" href="fuentes.css">
<link rel="stylesheet" href="tokens.css">
```

Copia la carpeta `fuentes/` completa, con las licencias que trae dentro:
distribuirlas es condicion de la licencia OFL.

## Escala y jerarquia

| Token | px | Donde se usa | Familia | Interlineado | Peso |
|---|---|---|---|---|---|
| `--texto-3xl` | 46 | H1 — titular de portada | Archivo | 1.08 | 700 |
| `--texto-2xl` | 34 | H2 — titulo de seccion | Archivo | 1.08 | 700 |
| `--texto-xl` | 26 | H3 — subtitulo | Archivo | 1.08 | 700 |
| `--texto-lg` | 20 | Entradilla | IBM Plex Sans | 1.55 | 400 |
| `--texto-base` | 16 | Cuerpo de texto | IBM Plex Sans | 1.55 | 400 |
| `--texto-sm` | 14 | Texto secundario, etiquetas | IBM Plex Sans | 1.55 | 400 |
| `--texto-xs` | 12 | Leyendas, avisos legales | IBM Plex Sans | 1.55 | 400 |

## Reglas

- El cuerpo **nunca baja de 16px**. Por debajo, los navegadores moviles
  hacen zoom automatico al enfocar un campo y descuadran la maqueta.
- Ancho de linea de 60 a 75 caracteres en texto corrido.
- Interlineado de 1.55 en texto y 1.08 en titulares.
- Maximo tres pesos en todo el sitio.
- Usa siempre `var(--fuente-texto)` y `var(--fuente-display)`,
  nunca el nombre de la familia escrito a mano en el CSS.
