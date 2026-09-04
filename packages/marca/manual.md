# Manual de marca — Tecno Sport

## La idea

Tecno Sport vende celulares, tecnología y ropa y calzado deportivo en Medellín,
al mayor y al detal. La marca no habla como una tienda de barrio: habla como un
**distribuidor confiable con diez años de oficio**, que sabe surtir tanto al que
compra un equipo como al que compra cien.

De ahí sale todo el sistema: geometría rígida, un solo ángulo, ninguna curva
decorativa, y una sola cosa que llama la atención en cada pieza.

---

## El isotipo

Una **T** calada dentro de un bloque cortado a 45° en dos esquinas opuestas.

- La **T** es la inicial, y está hecha en contraforma: es el vacío, no la masa.
  Por eso el símbolo sobrevive a 16 px, donde un logo de líneas finas se
  convierte en mancha.
- Los **dos cortes a 45°** son la firma de la marca. Leen a la vez como un
  bisel mecanizado (tecnología, hardware, precisión) y como el corte de una caja
  en perspectiva (distribución, bodega, despacho).
- No hay una sola curva ni una sola esquina redondeada en todo el sistema.
  Esa disciplina es lo que lo hace reconocible.

**El chaflán a 45° se repite en todo:** botones, tarjetas de producto, etiquetas
de precio, recortes de fotografía, banners. Siempre las mismas dos esquinas —
superior izquierda e inferior derecha. Está resuelto en `tokens.css` con la
clase `.chaflan`.

---

## Logo: variantes y cuándo usar cada una

| Variante | Archivo | Cuándo usarla |
|---|---|---|
| Principal / horizontal | `logo-horizontal.svg` | Uso preferente. Barra de navegación, cabeceras, firma de correo, factura |
| Horizontal con descriptor | `logo-horizontal-descriptor.svg` | Primera presentación de la marca: portada, tarjeta de presentación, papelería |
| Vertical | `logo-vertical.svg` | Espacios cuadrados o estrechos: sellos, pendones, empaque |
| Isotipo | `isotipo.svg` | Favicon, ícono de app, avatar de redes, marca de agua |
| Mono positivo | `logo-mono-positivo.svg` | Un solo color sobre fondo claro: sello, factura, grabado, bordado |
| Mono negativo | `logo-mono-negativo.svg` | Un solo color sobre fondo oscuro o fotografía |
| Ícono de app | `icono-app.svg` | Fuente de los íconos de iOS y Android (ámbar sobre grafito) |
| Favicon | `favicon-fuente.svg` | Versión simplificada: bloque a sangre y T más gruesa, para 16–48 px |

**El logo es monocromo.** El ámbar no entra dentro del logotipo: vive en el ícono
de app y en la interfaz. Un logo que necesita dos colores falla en el sello, en
la factura en blanco y negro y en el bordado.

### Área de respeto

Un margen libre igual a **1/4 del alto del isotipo** por los cuatro lados.
Sobre un isotipo de 120 unidades son 30 unidades. Ningún texto, foto, borde ni
otro logo puede entrar en ese margen.

### Tamaño mínimo (probado, no inventado)

| | Pantalla | Impreso | Bordado / vinilo |
|---|---|---|---|
| Logo horizontal | 160 px de ancho | 35 mm de ancho | 45 mm |
| Logo vertical | 110 px de ancho | 24 mm de ancho | 32 mm |
| Isotipo | 20 px (16 px con `favicon-fuente.svg`) | 6 mm | 20 mm |

Por debajo del mínimo del logo completo, se usa el isotipo solo. Nunca se
encoge el lockup hasta que "SPORT" deje de leerse.

---

## Usos prohibidos

- No deformar ni cambiar las proporciones (nada de estirar para llenar un espacio).
- No rotar. El logo va siempre horizontal.
- No recolorear fuera de la paleta. Nada de logo ámbar sobre blanco: da 1.85:1 de contraste.
- No añadir sombras, contornos, degradados ni brillos.
- No reordenar el lockup ni cambiar la separación entre isotipo y palabra.
- No poner el logo sobre fotos con poco contraste. Sobre foto va el **mono negativo**, y si la foto es clara, se oscurece la zona.
- No redondear las esquinas del bloque. El chaflán a 45° es la marca.
- No encerrar el logo en un círculo, escudo u otra forma ajena al sistema.

---

## Color

| Rol | HEX | Dónde va | Contraste verificado |
|---|---|---|---|
| Primario — grafito | `#1B1F26` | Logo, cabecera, titulares, botón principal | 16.5:1 sobre blanco · 15.3:1 sobre el fondo |
| Primario oscuro | `#0E1217` | Hover del botón principal, fondo en modo oscuro | 18.8:1 sobre blanco |
| Acento — ámbar señal | `#F5B301` | **Una sola cosa por pantalla**: precio destacado, botón de compra, etiqueta de oferta | 8.9:1 con grafito encima |
| Acento oscuro | `#C68F00` | Hover del botón de acento | — |
| Fondo | `#F5F6F8` | Fondo de página | — |
| Superficie | `#FFFFFF` | Tarjetas de producto | — |
| Texto | `#14171C` | Texto principal | 15.9:1 sobre blanco |
| Texto suave | `#5B6472` | Texto secundario, referencias, SKU | 5.98:1 sobre blanco |
| Borde | `#DFE3E9` | Divisores de 1 px | — |
| Éxito | `#14804A` | En stock, pedido confirmado | 4.98:1 sobre blanco |
| Alerta | `#B3261E` | Agotado, error de pago | 6.54:1 sobre blanco |

> **Regla del ámbar.** `#F5B301` sobre blanco da **1.85:1**: es invisible para
> mucha gente y es un fallo de accesibilidad. Nunca como texto ni como ícono
> sobre fondo claro. Solo como **relleno**, con texto grafito encima.

Los valores completos, con modo oscuro incluido, están en `tokens.json` y
`tokens.css`.

---

## Tipografía

| Uso | Familia | Licencia | Dónde bajarla |
|---|---|---|---|
| Display / logotipo | **Archivo** (700 y 500) | SIL OFL 1.1 — libre, sin costo | fonts.google.com/specimen/Archivo |
| Texto e interfaz | **IBM Plex Sans** | SIL OFL 1.1 — libre, sin costo | fonts.google.com/specimen/IBM+Plex+Sans |
| Precios y referencias | **IBM Plex Mono** | SIL OFL 1.1 — libre, sin costo | fonts.google.com/specimen/IBM+Plex+Mono |

Las tres son de licencia libre: se pueden usar en la web, en la app y en
impresos sin pagar nada y sin pedir permiso.

En el logotipo, **TECNO** va en peso 700 y **SPORT** en 500. Ese cambio de peso
—y no un espacio grande— es lo que separa las dos palabras y lo que representa
las dos líneas del negocio. El texto del logo ya está convertido a curvas: los
archivos no dependen de que la fuente esté instalada.

**Los precios y las referencias siempre en IBM Plex Mono**, con cifras
tabulares. En una tienda con listas de precios y códigos de producto, eso hace
que las columnas alineen solas. Está resuelto en `tokens.css` con `.precio` y `.sku`.

---

## Qué archivo va dónde

**Sitio web (tecnosport.co)**

1. Sube todo el contenido de `dist/web/` a la **raíz** del sitio (donde está el `index.html`).
2. Pega el contenido de `dist/web/head-snippet.html` dentro de la etiqueta `<head>`.
3. Para el logo de la cabecera usa `marca/logo-horizontal.svg` (o el PNG de `dist/raster/` si tu plantilla no acepta SVG).
4. Pega `marca/tokens.css` en el CSS global.
5. Sube `dist/social/og-image.png` y apúntale con `<meta property="og:image">`: es la imagen que aparece cuando alguien comparte el enlace en WhatsApp o Facebook.

**App iOS**

- Arrastra `dist/app/ios/` al Asset Catalog de Xcode (`AppIcon`).
- Ninguno de esos íconos tiene transparencia: App Store los acepta.

**App Android**

- Copia las carpetas `dist/app/android/mipmap-*/` dentro de `app/src/main/res/`.
- `ic_launcher_foreground.png` es la capa del ícono adaptativo; el color de fondo de esa capa es `#0E1217`.
- Sube `dist/app/android/play-store-512.png` a Play Console.

**Redes sociales**

- Foto de perfil (Instagram, Facebook, WhatsApp Business): `dist/social/avatar-redes.png`.
- Portada de Facebook: `facebook-cover.png` · LinkedIn: `linkedin-banner.png`.
- Plantillas de publicación e historia: `instagram-post.png` e `instagram-story.png`.

**Impresos, pendones y bordado**

- Entrega a la litografía los archivos de `dist/imprenta/` (PDF vectorial) o los `.svg` de `marca/`. No mandes PNG a imprenta.
- Para bordado y vinilo de vehículo: `logo-mono-positivo.svg` o `logo-mono-negativo.svg`, un solo color.

---

## Revisión visual

`revision.html` muestra los activos en contexto real: el favicon al tamaño en
que lo verá el navegador, el ícono en una pantalla de teléfono y el logo sobre
claro y sobre oscuro. Ábrelo en el navegador antes de aprobar cualquier cambio.
