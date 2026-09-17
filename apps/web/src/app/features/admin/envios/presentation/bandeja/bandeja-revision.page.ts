import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
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
import {
  EmisionEnRevision,
  EstadoEmisionEnRevision,
  EstadoEnvioEnRevision,
  GuiaEnRevision,
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
  imports: [DatePipe, ReactiveFormsModule, TranslocoPipe, TsBoton, TsCampo, TsMigas],
  templateUrl: './bandeja-revision.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BandejaRevisionPage {
  private readonly transloco = inject(TranslocoService);
  private readonly traducir = usarTraductor();

  protected readonly migas = usarMigasAdmin([{ clave: 'admin.revision_envios.titulo' }]);

  protected readonly consulta = usarBandejaRevision();
  protected readonly acciones = usarAccionesRevision();

  protected readonly error = signal<string | null>(null);
  protected readonly abierta = signal<string | null>(null);

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

  private notaEscrita(): string | null {
    const nota = this.formularioNota.controls.nota.value.trim();
    return nota === '' ? null : nota;
  }

  private async ejecutar(accion: () => Promise<unknown>): Promise<void> {
    this.error.set(null);
    try {
      await accion();
      this.abierta.set(null);
      this.formularioNota.reset({ nota: '' });
    } catch (causa) {
      // El codigo que manda el backend decide el mensaje; sin codigo, el generico de siempre.
      this.error.set(mensajeDeError(causa, this.transloco, 'admin.revision_envios.error'));
    }
  }
}
