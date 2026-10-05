import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { correoValido } from '../../../../../shared/formularios/validadores';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { usarTraductor } from '../../../../../core/i18n/traductor';
import { fechaConHora } from '../../../../../core/i18n/fecha-colombia';
import { TsMigas } from '../../../../../shared/ts-migas/ts-migas';
import { usarMigasAdmin } from '../../../migas-admin';
import { TsBoton } from '../../../../../shared/ui/boton/ts-boton';
import { TsCampo } from '../../../../../shared/ui/campo/ts-campo';
import { OpcionSelect, TsSelect } from '../../../../../shared/ui/select/ts-select';
import { TsSelectControl } from '../../../../../shared/ui/select/ts-select-control';
import { usarAccionesAtencion } from '../../application/acciones-atencion.mutaciones';
import { usarBandejaAtencion } from '../../application/bandeja-atencion.consulta';
import {
  EstadoSolicitudAtencion,
  SolicitudAtencion,
  TIPOS_CON_PRORROGA,
  TIPOS_DE_SOLICITUD,
  TipoSolicitud,
  VerdictoPlazo,
} from '../../domain/atencion.model';
import { mensajeDeError } from '../../../../../core/errores/mensaje-de-error';
import { TsCargando } from '../../../../../shared/ui/cargando/ts-cargando';

const CLAVE_TIPO: Record<TipoSolicitud, string> = {
  PETICION: 'admin.atencion.tipos.peticion',
  QUEJA: 'admin.atencion.tipos.queja',
  RECLAMO: 'admin.atencion.tipos.reclamo',
  CONSULTA_DATOS: 'admin.atencion.tipos.consulta_datos',
  RECLAMO_DATOS: 'admin.atencion.tipos.reclamo_datos',
  GARANTIA: 'admin.atencion.tipos.garantia',
  REVERSION: 'admin.atencion.tipos.reversion',
};

const CLAVE_ESTADO: Record<EstadoSolicitudAtencion, string> = {
  RADICADA: 'admin.atencion.estados.radicada',
  PRORROGADA: 'admin.atencion.estados.prorrogada',
  RESPONDIDA: 'admin.atencion.estados.respondida',
};

const CLAVE_VERDICTO: Record<VerdictoPlazo, string> = {
  EN_PLAZO: 'admin.atencion.verdicto.en_plazo',
  VENCIDO: 'admin.atencion.verdicto.vencido',
  INDETERMINADO: 'admin.atencion.verdicto.indeterminado',
};

/**
 * La bandeja de peticiones, quejas y reclamos: que hay abierto y cuanto falta para que venza.
 *
 * Existe porque un plazo que solo vive en una columna no lo cumple nadie. Los documentos publicados
 * prometen responder dentro de unos dias habiles, y demostrar que se cumplio exige antes que nada
 * que alguien pueda ver lo que esta por vencerse.
 *
 * Pantalla propia y no una seccion del pedido, al reves que el retracto: una consulta de datos
 * personales no viene de una compra, y colgarla del pedido dejaria fuera justo las que no tienen
 * pedido detras.
 */
@Component({
  selector: 'app-bandeja-atencion',
  imports: [
    ReactiveFormsModule,
    TranslocoPipe,
    TsBoton,
    TsCampo,
    TsSelect,
    TsSelectControl,
    TsMigas,
    TsCargando,
  ],
  templateUrl: './bandeja-atencion.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BandejaAtencionPage {
  private readonly transloco = inject(TranslocoService);
  private readonly traducir = usarTraductor();

  protected readonly migas = usarMigasAdmin([{ clave: 'admin.atencion.titulo' }]);

  protected readonly filtro = signal<EstadoSolicitudAtencion | null>(null);
  protected readonly consulta = usarBandejaAtencion(() => this.filtro());
  protected readonly acciones = usarAccionesAtencion();

  protected readonly error = signal<string | null>(null);
  protected readonly abierta = signal<string | null>(null);

  protected readonly solicitudes = computed<readonly SolicitudAtencion[]>(
    () => this.consulta.data() ?? [],
  );

  protected readonly formularioRadicar = new FormGroup({
    tipo: new FormControl<string>('PETICION', { nonNullable: true }),
    correo: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, correoValido],
    }),
    asunto: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    recibidaEn: new FormControl('', { nonNullable: true }),
  });

  protected readonly formularioResponder = new FormGroup({
    resumen: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  protected readonly formularioProrrogar = new FormGroup({
    motivo: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  protected readonly opcionesTipo = computed<OpcionSelect[]>(() =>
    TIPOS_DE_SOLICITUD.map((tipo) => ({
      valor: tipo,
      etiqueta: this.traducir()(CLAVE_TIPO[tipo]),
    })),
  );

  protected etiquetaTipo(tipo: TipoSolicitud): string {
    return this.traducir()(CLAVE_TIPO[tipo]);
  }

  protected etiquetaEstado(estado: EstadoSolicitudAtencion): string {
    return this.traducir()(CLAVE_ESTADO[estado]);
  }

  protected etiquetaVerdicto(verdicto: VerdictoPlazo): string {
    return this.traducir()(CLAVE_VERDICTO[verdicto]);
  }

  protected admiteProrroga(solicitud: SolicitudAtencion): boolean {
    return solicitud.estado === 'RADICADA' && TIPOS_CON_PRORROGA.includes(solicitud.tipo);
  }

  /**
   * Si el plazo legal venció lo decide el servidor (`verdicto`), con su calendario y su reloj. Lo
   * decidía esta pantalla con `Math.ceil(...) < 0`, y en las primeras 24 horas de incumplimiento
   * `Math.ceil` daba `-0`, que no es menor que cero: la bandeja decía "quedan 0 días" en gris sobre
   * una PQR ya vencida. El mismo defecto que `panel-retracto` ya había corregido.
   */
  protected plazoVencido(solicitud: SolicitudAtencion): boolean {
    return solicitud.verdicto === 'VENCIDO';
  }

  /** Solo el número que se muestra mientras corre el plazo; nunca negativo. */
  protected diasRestantes(solicitud: SolicitudAtencion): number {
    const milisegundosPorDia = 86_400_000;
    return Math.max(
      0,
      Math.ceil(
        (new Date(solicitud.limiteDeRespuesta).getTime() - Date.now()) / milisegundosPorDia,
      ),
    );
  }

  /**
   * Con la zona de Colombia y el idioma activo. El `DatePipe` sin `LOCALE_ID` caía a `en-US` y a la
   * zona del navegador: el plazo legal de una PQR salía como "Oct 4, 2026, 3:15:00 PM".
   */
  protected formatearFecha(iso: string): string {
    return fechaConHora(iso, this.transloco.activeLang());
  }

  protected filtrar(estado: EstadoSolicitudAtencion | null): void {
    this.filtro.set(estado);
  }

  protected alternar(solicitudId: string): void {
    this.abierta.update((actual) => (actual === solicitudId ? null : solicitudId));
  }

  protected async radicar(): Promise<void> {
    if (this.formularioRadicar.invalid) {
      this.formularioRadicar.markAllAsTouched();
      return;
    }
    const valores = this.formularioRadicar.getRawValue();
    const recibidaEn = valores.recibidaEn.trim();
    await this.ejecutar(() =>
      this.acciones.radicar.mutateAsync({
        tipo: valores.tipo as TipoSolicitud,
        correo: valores.correo.trim(),
        pedidoId: null,
        recibidaEn: recibidaEn === '' ? null : new Date(recibidaEn).toISOString(),
        asunto: valores.asunto.trim(),
      }),
    );
    this.formularioRadicar.reset({ tipo: 'PETICION', correo: '', asunto: '', recibidaEn: '' });
  }

  protected async responder(solicitud: SolicitudAtencion): Promise<void> {
    if (this.formularioResponder.invalid) {
      this.formularioResponder.markAllAsTouched();
      return;
    }
    await this.ejecutar(() =>
      this.acciones.responder.mutateAsync({
        solicitudId: solicitud.id,
        resumen: this.formularioResponder.controls.resumen.value.trim(),
      }),
    );
    this.formularioResponder.reset({ resumen: '' });
  }

  protected async prorrogar(solicitud: SolicitudAtencion): Promise<void> {
    if (this.formularioProrrogar.invalid) {
      this.formularioProrrogar.markAllAsTouched();
      return;
    }
    await this.ejecutar(() =>
      this.acciones.prorrogar.mutateAsync({
        solicitudId: solicitud.id,
        motivo: this.formularioProrrogar.controls.motivo.value.trim(),
      }),
    );
    this.formularioProrrogar.reset({ motivo: '' });
  }

  private async ejecutar(accion: () => Promise<unknown>): Promise<void> {
    this.error.set(null);
    try {
      await accion();
    } catch (error) {
      // El codigo que manda el backend decide el mensaje; sin codigo, el generico de siempre.
      this.error.set(mensajeDeError(error, this.transloco, 'admin.atencion.error'));
    }
  }
}
