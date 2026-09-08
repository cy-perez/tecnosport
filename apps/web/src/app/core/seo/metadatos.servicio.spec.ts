import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Routes } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { TranslocoService, TranslocoTestingModule } from '@jsverse/transloco';
import en from '../../../assets/i18n/en.json';
import es from '../../../assets/i18n/es.json';
import { MetadatosSeo } from './metadatos.servicio';

@Component({ template: '' })
class PaginaFalsa {}

const RUTAS: Routes = [
  {
    path: 'es',
    children: [
      {
        path: '',
        data: { seo: { clave: 'seo.portada', indexable: true } },
        component: PaginaFalsa,
      },
      { path: 'carrito', data: { seo: { clave: 'seo.carrito' } }, component: PaginaFalsa },
      {
        path: 'inventada',
        data: { seo: { clave: 'seo.no_existe', indexable: true } },
        component: PaginaFalsa,
      },
      { path: 'sin-declarar', component: PaginaFalsa },
    ],
  },
];

const cabeza = () => document.head;
const canonicas = () => Array.from(cabeza().querySelectorAll('link[rel="canonical"]'));
const alternativas = () =>
  Array.from(cabeza().querySelectorAll('link[rel="alternate"]')).map((enlace) => [
    enlace.getAttribute('hreflang'),
    enlace.getAttribute('href'),
  ]);
const contenidoMeta = (selector: string) =>
  cabeza().querySelector(`meta[${selector}]`)?.getAttribute('content') ?? null;

async function navegarA(url: string): Promise<void> {
  TestBed.inject(MetadatosSeo).escuchar();
  const banco = await RouterTestingHarness.create();
  await banco.navigateByUrl(url);
}

describe('MetadatosSeo', () => {
  beforeEach(() => {
    // El origen sale de APP_URL_PUBLICA (regla dura #5). Fijarlo aquí hace que
    // la prueba no dependa del host que jsdom le invente al documento.
    process.env['APP_URL_PUBLICA'] = 'https://tecnosport.co';
    for (const previo of Array.from(cabeza().querySelectorAll('link[data-seo]'))) {
      previo.remove();
    }

    TestBed.configureTestingModule({
      imports: [
        TranslocoTestingModule.forRoot({
          langs: { es, en },
          translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
          preloadLangs: true,
        }),
      ],
      providers: [provideRouter(RUTAS)],
    });
  });

  it('escribe título, descripción y canónico de una pantalla indexable', async () => {
    await navegarA('/es');

    expect(document.title).toBe(es.seo.portada.titulo);
    expect(contenidoMeta("name='description'")).toBe(es.seo.portada.descripcion);
    expect(contenidoMeta("name='robots'")).toBe('index,follow');
    expect(canonicas().map((e) => e.getAttribute('href'))).toEqual(['https://tecnosport.co/es']);
  });

  it('declara las tres alternativas de idioma en una pantalla indexable', async () => {
    await navegarA('/es');

    expect(alternativas()).toEqual([
      ['es-CO', 'https://tecnosport.co/es'],
      ['en', 'https://tecnosport.co/en'],
      ['x-default', 'https://tecnosport.co/es'],
    ]);
  });

  it('marca noindex lo que no se indexa, y no le declara alternativas', async () => {
    await navegarA('/es/carrito');

    expect(document.title).toBe(es.seo.carrito.titulo);
    expect(contenidoMeta("name='robots'")).toBe('noindex,nofollow');
    expect(alternativas()).toEqual([]);
  });

  // El descuido que esta prueba impide: una pantalla nueva que se olvide de
  // declarar `seo` no puede terminar indexada por omisión.
  it('trata como no indexable la ruta que no declara nada', async () => {
    await navegarA('/es/sin-declarar');

    expect(contenidoMeta("name='robots'")).toBe('noindex,nofollow');
    expect(document.title).toBe(es.app.titulo);
  });

  it('no deja la clave cruda en la pestaña cuando falta la traducción', async () => {
    await navegarA('/es/inventada');

    expect(document.title).toBe(es.app.titulo);
    expect(contenidoMeta("name='description'")).toBe(null);
  });

  it('poda los parámetros de consulta del canónico', async () => {
    await navegarA('/es/carrito?paso=2');

    expect(canonicas().map((e) => e.getAttribute('href'))).toEqual([
      'https://tecnosport.co/es/carrito',
    ]);
  });

  // Sin borrar los `<link>` anteriores, tres navegaciones dejarían tres
  // canónicos, que para un rastreador equivale a no declarar ninguno.
  it('reemplaza los enlaces anteriores en vez de acumularlos', async () => {
    TestBed.inject(MetadatosSeo).escuchar();
    const banco = await RouterTestingHarness.create();

    await banco.navigateByUrl('/es');
    await banco.navigateByUrl('/es/carrito');

    expect(canonicas().length).toBe(1);
    expect(alternativas()).toEqual([]);
  });

  it('deja que la pantalla anule los metadatos de su ruta', async () => {
    await navegarA('/es');

    TestBed.inject(MetadatosSeo).aplicar({
      titulo: 'Camiseta running · Tecno Sport',
      descripcion: 'Tejido técnico.',
      indexable: true,
      imagen: 'https://imagenes.tecnosport.co/camiseta.jpg',
    });

    expect(document.title).toBe('Camiseta running · Tecno Sport');
    expect(contenidoMeta("property='og:image'")).toBe(
      'https://imagenes.tecnosport.co/camiseta.jpg',
    );
    expect(contenidoMeta("name='twitter:card'")).toBe('summary_large_image');
  });

  it('descarta la anulación en la siguiente navegación', async () => {
    TestBed.inject(MetadatosSeo).escuchar();
    const banco = await RouterTestingHarness.create();
    await banco.navigateByUrl('/es');
    TestBed.inject(MetadatosSeo).aplicar({
      titulo: 'Camiseta running · Tecno Sport',
      descripcion: 'Tejido técnico.',
      indexable: true,
    });

    await banco.navigateByUrl('/es/carrito');

    expect(document.title).toBe(es.seo.carrito.titulo);
    expect(contenidoMeta("property='og:image'")).toBe(null);
  });

  it('hace absoluta una imagen relativa, porque quien la lee es un servidor ajeno', async () => {
    await navegarA('/es');
    TestBed.inject(MetadatosSeo).aplicar({
      titulo: 'x',
      descripcion: 'y',
      indexable: true,
      imagen: '/assets/marca/og.png',
    });

    expect(contenidoMeta("property='og:image'")).toBe('https://tecnosport.co/assets/marca/og.png');
  });

  // Cambiar de idioma no navega: sin escuchar a Transloco, la pestaña se
  // quedaría en el idioma anterior hasta la siguiente navegación.
  it('reescribe los metadatos al cambiar de idioma sin navegar', async () => {
    await navegarA('/es');

    TestBed.inject(TranslocoService).setActiveLang('en');

    expect(document.title).toBe(en.seo.portada.titulo);
    expect(contenidoMeta("property='og:locale'")).toBe('en_US');
  });
});
