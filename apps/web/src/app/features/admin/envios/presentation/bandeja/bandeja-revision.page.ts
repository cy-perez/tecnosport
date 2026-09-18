import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  inject,
  signal,
} from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { mensajeDeError } from '../../../../../core/errores/mensaje-de-error';
import { usarTraductor } from '../../../../../core/i18n/traductor';
import { TsMigas } from '../../../../../shared/ts-migas/ts-migas';
import { TsBoton } from '../../../../../shared/ui/boton/ts-boton';
import { TsCampo } from '../../../../../shared/ui/campo/ts-campo';
import { usarMigasAdmin } from '../../../migas-admin';
import { usarAccionesRevision } from '../../application/acciones-revision.mutaciones';
import { usarBandejaRevision } from '../../application/bandeja-revision.consulta';
import { fechaConHora } from '../../../../../core/i18n/fecha-colombia';
import {
  EmisionEnRevision,
  EstadoEmisionEnRevision,
  EstadoEnvioEnRevision,
  GuiaEnRevision,
  VeredictoDeEmision,
} from '../../domain/revision-envio.model';

const CLAVE_ESTADO_ENVIO: Record<EstadoEnvioEnRevision, string> = {
  EXCEPCION: 'admin.revision_envios.estados_envio.excepcion',
  RETENIDO: 'admin.revision_envios.estados_envio.retenido',
  CANCELADO: 'admin.revision_envios.estados_envio.cancelado',
  DESTRUIDO: 'admin.revision_envios.estados_envio.destruido',
  FALLIDO: 'admin.revision_envios.estados_envio.fallido',
};

const CLAVE_ESTADO_EMISION: Record<EstadoEmisionEnRevision, string> = {
  INDETERMINADA: 'admin.revision_envios.estados_emision.indeterminada',
  PARCIAL: 'admin.revision_envios.estados_emision.parcial',
  SIN_ANULAR: 'admin.revision_envios.estados_emision.sin_anular',
};

/**
 * La bandeja de revision de envios: que paquete necesita que alguien haga algo.
 *
 * Existe porque los cinco estados de envio que dejan el paquete quieto y los dos de emision con
 * plata comprometida solo aparecian en un `warn` del registro, que es tanto como no aparecer.
 *
 * Pantalla propia y no una seccion de la lista de pedidos, al reves que el retracto: una emision
 * indeterminada puede no tener envio, y una guia retenida hay que verla sin saber de antemano de
 * que pedido es. Colgarla del pedido obligaria a saber la respuesta antes de preguntar.
 *
 * **Acusar no resuelve nada**, y la pantalla lo dice con esas palabras: deja escrito quien miro.
 * Una emision indeterminada sigue bloqueando su pedido despues del acuse.
 */
@Component({
  selector: 'app-bandeja-revision-envios',
  imports: [ReactiveFormsModule, TranslocoPipe, TsBoton, TsCampo, TsMigas],
  templateUrl: './bandeja-revision.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BandejaRevisionPage {
  private readonly transloco = inject(TranslocoService);

  /**
   * Fecha y hora en el idioma de quien lee y en hora de Colombia. No con `DatePipe`: sin `LOCALE_ID`
   * registrado cae a `en-US` y sin zona usa la del entorno. Ver `core/i18n/fecha-colombia.ts`.
   */
  protected fechaConHora(iso: string): string {
    return fechaConHora(iso, this.transloco.activeLang());
  }
  private readonly traducir = usarTraductor();

  protected readonly migas = usarMigasAdmin([{ clave: 'admin.revision_envios.titulo' }]);

  protected readonly consulta = usarBandejaRevision();
  protected readonly acciones = usarAccionesRevision();

  protected readonly error = signal<string | null>(null);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly inyector = inject(Injector);

  protected readonly abierta = signal<string | null>(null);
  /** Se enciende al intentar resolver con envio y sin identificadores; se apaga al cambiar de fila. */
  private readonly faltanIdentificadores = signal(false);

  protected readonly guias = computed<readonly GuiaEnRevision[]>(
    () => this.consulta.data()?.guias ?? [],
  );
  protected readonly emisiones = computed<readonly EmisionEnRevision[]>(
    () => this.consulta.data()?.emisiones ?? [],
  );
  protected readonly vacia = computed(
    () => this.guias().length === 0 && this.emisiones().length === 0,
  );

  protected readonly formularioNota = new FormGroup({
    nota: new FormControl('', { nonNullable: true }),
  });

  /**
   * Los identificadores que la persona copio del panel de la plataforma, separados por coma o
   * por espacio. Se parsea aqui y no se exige un formato: quien los copia esta leyendo otra
   * pantalla, y rechazarle el pegado por un espacio de mas seria pedirle que teclee a mano un
   * UUID.
   */
  protected readonly formularioResolucion = new FormGroup({
    envios: new FormControl('', { nonNullable: true }),
  });

  protected etiquetaEstadoEnvio(estado: EstadoEnvioEnRevision): string {
    return this.traducir()(CLAVE_ESTADO_ENVIO[estado]);
  }

  protected etiquetaEstadoEmision(estado: EstadoEmisionEnRevision): string {
    return this.traducir()(CLAVE_ESTADO_EMISION[estado]);
  }

  /**
   * Abre el formulario de una fila y cierra el que hubiera. La nota se limpia al cambiar de fila:
   * arrastrar lo escrito para una guia hasta otra es la forma de dejar un rastro que miente.
   */
  protected alternar(referencia: string): void {
    this.abierta.update((actual) => (actual === referencia ? null : referencia));
    this.formularioNota.reset({ nota: '' });
    this.formularioResolucion.reset({ envios: '' });
    this.faltanIdentificadores.set(false);
  }

  protected async acusarGuia(guia: GuiaEnRevision): Promise<void> {
    await this.ejecutar(() =>
      this.acciones.acusarGuia.mutateAsync({
        numeroGuia: guia.numeroGuia,
        nota: this.notaEscrita(),
      }),
    );
  }

  protected async acusarEmision(emision: EmisionEnRevision): Promise<void> {
    await this.ejecutar(() =>
      this.acciones.acusarEmision.mutateAsync({
        emisionId: emision.emisionId,
        nota: this.notaEscrita(),
      }),
    );
  }

  /**
   * Resolver es distinto de acusar: acusar deja constancia, esto desbloquea el pedido. Solo aparece
   * en las indeterminadas, que son las unicas de las que se puede decir que se cerraron sin saber.
   */
  protected esIndeterminada(emision: EmisionEnRevision): boolean {
    return emision.estado === 'INDETERMINADA';
  }

  /**
   * Lo que le falta al campo de identificadores, atado al campo.
   *
   * <p>Antes, "falta identificador" se escribia en `error()`, que se pinta en la cabecera de la
   * pagina: podia quedar muchas pantallas por encima del campo que lo causo, sin `aria-invalid` y
   * sin mover el foco. Es la WCAG 3.3.1 y lo levanto la auditoria de accesibilidad.
   */
  protected errorIdentificadores(): string | null {
    return this.faltanIdentificadores()
      ? this.traducir()('admin.revision_envios.resolucion.falta_identificador')
      : null;
  }

  protected async resolver(
    emision: EmisionEnRevision,
    veredicto: VeredictoDeEmision,
  ): Promise<void> {
    const envios = this.enviosEscritos();
    if (veredicto === 'CON_ENVIO' && envios.length === 0) {
      this.faltanIdentificadores.set(true);
      this.formularioResolucion.controls.envios.markAsTouched();
      return;
    }
    this.faltanIdentificadores.set(false);
    await this.ejecutar(() =>
      this.acciones.resolverEmision.mutateAsync({
        emisionId: emision.emisionId,
        veredicto,
        enviosEnPlataforma: envios,
        nota: this.notaEscrita(),
      }),
    );
  }

  private enviosEscritos(): readonly string[] {
    return this.formularioResolucion.controls.envios.value
      .split(/[\s,]+/)
      .map((valor) => valor.trim())
      .filter((valor) => valor !== '');
  }

  /**
   * Devuelve el foco al boton de la fila que se acaba de cerrar.
   *
   * <p>Cerrar el formulario destruye el boton que estaba enfocado, asi que el foco caia a `<body>` y
   * quien navega con teclado volvia al principio del documento. Es la WCAG 2.4.3 y lo levanto la
   * auditoria de accesibilidad.
   *
   * <p>Se busca dentro del host y por `aria-controls`, que ya identifica cada boton con la region
   * que abre: asi no hace falta ni `document` ni un `id` nuevo en `ts-boton`. El `afterNextRender`
   * es lo que espera a que Angular haya repintado la lista sin la fila abierta.
   */
  private devolverElFoco(region: string | null): void {
    if (region === null) {
      return;
    }
    const prefijo = region.startsWith('resolver:') ? 'resolver-' : null;
    const referencia = prefijo === null ? region : region.slice('resolver:'.length);
    afterNextRender(
      () => {
        const boton = this.host.nativeElement.querySelector<HTMLElement>(
          prefijo === null
            ? `[aria-controls$="${referencia}"]`
            : `[aria-controls="${prefijo}${referencia}"]`,
        );
        boton?.focus();
      },
      { injector: this.inyector },
    );
  }

  private notaEscrita(): string | null {
    const nota = this.formularioNota.controls.nota.value.trim();
    return nota === '' ? null : nota;
  }

  private async ejecutar(accion: () => Promise<unknown>): Promise<void> {
    this.error.set(null);
    const region = this.abierta();
    try {
      await accion();
      this.abierta.set(null);
      this.formularioNota.reset({ nota: '' });
      this.formularioResolucion.reset({ envios: '' });
      this.faltanIdentificadores.set(false);
      this.devolverElFoco(region);
    } catch (causa) {
      // El codigo que manda el backend decide el mensaje; sin codigo, el generico de siempre.
      this.error.set(mensajeDeError(causa, this.transloco, 'admin.revision_envios.error'));
    }
  }
}
