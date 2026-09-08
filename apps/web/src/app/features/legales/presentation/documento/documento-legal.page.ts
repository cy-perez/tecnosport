import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslocoDirective, TranslocoService } from '@jsverse/transloco';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs';
import { usarTraductorDeObjetos } from '../../../../core/i18n/traductor';

/** Una sección numerada del documento. Todo es opcional menos el título. */
interface SeccionLegal {
  readonly titulo: string;
  readonly parrafos?: readonly string[];
  readonly lista?: readonly string[];
  readonly cierre?: readonly string[];
}

type ClaveDocumento = 'privacidad' | 'terminos' | 'cookies';

const DOCUMENTOS: readonly ClaveDocumento[] = ['privacidad', 'terminos', 'cookies'];

/**
 * Sirve los tres documentos legales. El texto vive entero en el scope `legales` de Transloco
 * (regla dura #4), como una lista de secciones que esta plantilla recorre — así el documento se
 * edita sin tocar el componente, y la versión en inglés no se puede quedar corta sin que se note.
 *
 * Se lee con `usarTraductorDeObjetos` porque lo que hace falta no es una cadena sino la estructura
 * entera, y porque la clave depende de qué documento se esté viendo.
 */
@Component({
  selector: 'ts-documento-legal',
  standalone: true,
  imports: [RouterLink, TranslocoDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './documento-legal.page.html',
})
export class DocumentoLegalPage {
  private readonly ruta = inject(ActivatedRoute);
  private readonly transloco = inject(TranslocoService);
  private readonly traducirObjeto = usarTraductorDeObjetos();

  /**
   * De `data` y no de un parámetro de URL: los tres documentos son rutas distintas y fijas, no un
   * mismo recurso parametrizado. Así una ruta inventada da el 404 del router en vez de una página
   * legal en blanco.
   */
  protected readonly documento = toSignal(
    this.ruta.data.pipe(map((d) => d['documento'] as ClaveDocumento)),
    { initialValue: 'terminos' as ClaveDocumento },
  );

  protected readonly idioma = toSignal(this.transloco.langChanges$, {
    initialValue: this.transloco.getActiveLang(),
  });

  protected readonly titulo = computed(
    () => this.traducirObjeto()<string>(`legales.${this.documento()}.titulo`) ?? '',
  );

  protected readonly entradilla = computed(
    () => this.traducirObjeto()<string>(`legales.${this.documento()}.entradilla`) ?? '',
  );

  protected readonly secciones = computed(
    () => this.traducirObjeto()<SeccionLegal[]>(`legales.${this.documento()}.secciones`) ?? [],
  );

  /** Los otros dos documentos, para saltar entre ellos sin volver al pie. */
  protected readonly otros = computed(() => {
    const traducir = this.traducirObjeto();
    return DOCUMENTOS.filter((d) => d !== this.documento()).map((d) => ({
      clave: d,
      etiqueta: traducir<string>(`legales.nav.${d}`) ?? d,
    }));
  });
}
