# packages/marca

Kit de marca de TecnoSport. Es la fuente única del sistema visual: tokens,
tipografías y logos.

## Contenido

```
tokens.json    Las decisiones. Este es el único archivo que se edita.
tokens.css     Variables CSS. GENERADO.
fuentes.css    Declaraciones @font-face. GENERADO.
fuentes/       Archivos woff2 y sus licencias OFL.
logo/          Variantes del logo en SVG.
generador/     Script que produce los archivos generados.
contraste.md   Informe WCAG de los dos modos.
tipografia.md  Familias, pesos, licencia e instalación.
```

## Regenerar

```
python generador/kit_ui.py tokens.json --out .
```

Los estados (hover, presionado, foco, texto sobre cada fondo), el modo oscuro y
el informe de contraste se recalculan solos. **Las tipografías y los logos que
ya están dentro del kit se conservan**, así que no hay que volver a pasarlos.

**Sin `--fuentes`, y no es un olvido.** Esa bandera vuelve a descargar las
tipografías de `google/fonts`, y solo hace falta al montar el kit por primera vez
o al cambiar de familia. Pedirla con las fuentes ya puestas rehace trabajo hecho,
y sin `fonttools` y `brotli` instalados dejaba el kit **peor** que antes: `.ttf`
en vez de `.woff2`, declarados como woff2 y sin el rango de pesos de las
variables. Desde el 21 de septiembre de 2026 se niega en vez de hacerlo.

**No se editan `tokens.css` ni `fuentes.css` a mano.** El siguiente regenerado
borra el cambio. Si falta un valor, se agrega a `tokens.json` con nombre.

## Consumo desde el frontend

`apps/web` copia `tokens.css`, `fuentes.css`, `fuentes/` y `logo/` a
`src/assets/marca/` en el paso de preparación del build. La carpeta `fuentes/`
va completa, con sus licencias: distribuirlas es condición de la SIL OFL 1.1.

## Reglas que no cambian

- El chaflán a 45 grados en la esquina superior izquierda y la inferior derecha
  es la firma de la marca, aplicado con la clase `.chaflan`. Va en el logo y en
  las piezas gráficas. **No va en la interfaz del sitio**, que redondea con la
  escala `--radio-*`: 6, 8, 12 px y la píldora. Ver `docs/adr/0060`.
- El ámbar `#F5B301` es una sola cosa por pantalla y solo como relleno con texto
  grafito encima. Sobre blanco da 1.85:1 y no es legible.
- El logo es monocromo y tiene versión positiva y negativa; el tema decide cuál
  se muestra.
