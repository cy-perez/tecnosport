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
python generador/kit_ui.py tokens.json --out . --fuentes
```

Los estados (hover, presionado, foco, texto sobre cada fondo), el modo oscuro y
el informe de contraste se recalculan solos.

**No se editan `tokens.css` ni `fuentes.css` a mano.** El siguiente regenerado
borra el cambio. Si falta un valor, se agrega a `tokens.json` con nombre.

## Consumo desde el frontend

`apps/web` copia `tokens.css`, `fuentes.css`, `fuentes/` y `logo/` a
`src/assets/marca/` en el paso de preparación del build. La carpeta `fuentes/`
va completa, con sus licencias: distribuirlas es condición de la SIL OFL 1.1.

## Reglas que no cambian

- Radio 0 en todo. La firma de la marca es el chaflán a 45 grados en la esquina
  superior izquierda y la inferior derecha, aplicado con la clase `.chaflan`.
- El ámbar `#F5B301` es una sola cosa por pantalla y solo como relleno con texto
  grafito encima. Sobre blanco da 1.85:1 y no es legible.
- El logo es monocromo y tiene versión positiva y negativa; el tema decide cuál
  se muestra.
