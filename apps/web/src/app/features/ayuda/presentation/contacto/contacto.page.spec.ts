import { provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { render, screen } from '@testing-library/angular';
import en from '../../../../../assets/i18n/en.json';
import es from '../../../../../assets/i18n/es.json';
import ayudaEn from '../../../../../assets/i18n/scopes/ayuda/en.json';
import ayudaEs from '../../../../../assets/i18n/scopes/ayuda/es.json';
import { ContactoPage } from './contacto.page';

async function renderContacto() {
  const resultado = await render(ContactoPage, {
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { es, en, 'ayuda/es': ayudaEs, 'ayuda/en': ayudaEn } as never,
        translocoConfig: { availableLangs: ['es', 'en'], defaultLang: 'es' },
        preloadLangs: true,
      }),
    ],
    providers: [provideRouter([])],
  });
  await resultado.fixture.whenStable();
  return resultado;
}

describe('ContactoPage', () => {
  /**
   * Los tres canales, cada uno con su protocolo. Lo que esta prueba protege no es que existan los
   * enlaces sino que apunten a donde dicen: un `mailto:` con el número o un `wa.me` con el correo
   * se ven perfectamente bien en la página.
   */
  it('ofrece WhatsApp, correo y teléfono, cada uno a su protocolo', async () => {
    await renderContacto();

    // Los valores salen de `es.pie.*` y **no escritos aquí**, que es como estaban y fue un hallazgo
    // de la revisión: `npm run datos-negocio` solo recorre los JSON de i18n, así que tres cadenas
    // con el celular y el correo en un `.spec.ts` son tres copias que la herramienta no vigila —
    // exactamente lo que existe para impedir. Y el javadoc de abajo ya decía que se comparaba
    // contra el JSON raíz, cosa que era cierta de la otra prueba y no de esta.
    expect(screen.getByRole('link', { name: es.pie.whatsapp }).getAttribute('href')).toBe(
      `https://wa.me/${es.pie.whatsapp_numero}`,
    );
    expect(screen.getByRole('link', { name: es.pie.correo }).getAttribute('href')).toBe(
      `mailto:${es.pie.correo}`,
    );
    expect(screen.getByRole('link', { name: es.pie.telefono }).getAttribute('href')).toBe(
      `tel:${es.pie.telefono_e164}`,
    );
  });

  /**
   * <b>Los datos del negocio salen de `pie.*`, no de una copia en el scope `ayuda`.</b> Es la regla
   * que `npm run datos-negocio` vigila y que ya se rompió una vez —el celular estuvo mal en el pie
   * y en tres párrafos legales durante una fase entera—. Se comprueba contra el JSON raíz: si
   * alguien escribe el correo a mano en `ayuda/es.json`, esta prueba sigue pasando pero la
   * herramienta falla, y si alguien cambia el del pie, esta prueba lo sigue solo.
   */
  it('publica la identificación del vendedor que exige la Ley 1480', async () => {
    await renderContacto();

    expect(screen.getByText(es.pie.nombre_comercial)).toBeTruthy();
    expect(screen.getByText(es.pie.nit)).toBeTruthy();
    expect(screen.getByText(es.pie.direccion)).toBeTruthy();
    expect(screen.getByText(es.pie.horario)).toBeTruthy();
  });

  it('dice qué esperar de cada canal y en cuánto se responde', async () => {
    await renderContacto();

    for (const plazo of ayudaEs.contacto.plazos) {
      expect(screen.getByText(plazo)).toBeTruthy();
    }
    expect(screen.getByText(ayudaEs.contacto.punto_nota)).toBeTruthy();
  });

  it('lleva a las preguntas frecuentes, al estado del pedido y a la política de datos', async () => {
    await renderContacto();

    expect(
      screen.getByRole('link', { name: ayudaEs.contacto.enlace_preguntas }).getAttribute('href'),
    ).toBe('/es/ayuda/preguntas-frecuentes');
    expect(
      screen.getByRole('link', { name: ayudaEs.contacto.enlace_estado }).getAttribute('href'),
    ).toBe('/es/checkout/estado');
    expect(
      screen.getByRole('link', { name: ayudaEs.contacto.enlace_privacidad }).getAttribute('href'),
    ).toBe('/es/legales/privacidad');
  });
});
