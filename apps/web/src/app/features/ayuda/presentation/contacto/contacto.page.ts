import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoDirective, TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { usarTraductorDeObjetos } from '../../../../core/i18n/traductor';
import { TsIcono } from '../../../../shared/ui/icono/ts-icono';
import { TsIconoMarca } from '../../../../shared/ui/icono/ts-icono-marca';
import {
  iconoCorreo,
  iconoHorario,
  iconoTelefono,
  iconoUbicacion,
} from '../../../../shared/ui/icono/iconos';
import { marcaWhatsapp } from '../../../../shared/ui/icono/marcas.generado';

/**
 * Contáctanos: todos los canales en una página, con qué esperar de cada uno.
 *
 * <p>Existía repartido entre el bloque de contacto del pie —que da los datos pero no dice para qué
 * sirve cada canal ni en cuánto se responde— y el numeral 12 de los términos, que es donde nadie
 * los busca. Aquí están juntos.
 *
 * <p><b>Ningún dato del negocio se escribe en este componente ni en su scope de i18n.</b> El
 * correo, el WhatsApp, el teléfono, el NIT, la dirección y el horario se leen de las claves `pie.*`
 * del paquete raíz, que son las que la Ley 1480 obliga a publicar y las que `npm run datos-negocio`
 * cruza entre todas sus copias. Lo que sí vive en `ayuda` son las etiquetas y las notas: "la vía
 * más rápida", "para garantías y retractos", los plazos. Una segunda copia del celular sería
 * exactamente lo que esa herramienta existe para impedir — ya pasó una vez, con el celular mal en
 * el pie y en tres párrafos legales durante una fase entera.
 *
 * <p>El teléfono sí va como enlace `tel:` aquí, y en el pie no. No es una incoherencia: en el pie
 * es un dato de identificación dentro de una frase, y lo que se pidió fue enseñar el número; esta
 * es la página a la que se llega **para llamar**, y un número que no se puede tocar desde un
 * teléfono no sirve de nada.
 */
@Component({
  selector: 'app-contacto',
  // `TranslocoDirective` para las claves del scope `ayuda` y `TranslocoPipe` para las de `pie`,
  // que viven en el paquete raíz. Las dos formas conviven a propósito: el bloque `*transloco`
  // declara el scope una vez, y el pipe deja a la vista que esos datos no son de esta pantalla.
  imports: [RouterLink, TranslocoDirective, TranslocoPipe, TsIcono, TsIconoMarca],
  templateUrl: './contacto.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ContactoPage {
  private readonly transloco = inject(TranslocoService);
  private readonly traducirObjeto = usarTraductorDeObjetos();

  protected readonly idioma = this.transloco.activeLang;

  protected readonly iconoCorreo = iconoCorreo;
  protected readonly iconoHorario = iconoHorario;
  protected readonly iconoTelefono = iconoTelefono;
  protected readonly iconoUbicacion = iconoUbicacion;
  protected readonly marcaWhatsapp = marcaWhatsapp;

  protected readonly plazos = computed(
    () => this.traducirObjeto()<string[]>('ayuda.contacto.plazos') ?? [],
  );
}
