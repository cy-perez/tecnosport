import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
  viewChild,
  ElementRef,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { TsBoton } from '../../../shared/ui/boton/ts-boton';
import { TsMigas } from '../../../shared/ts-migas/ts-migas';
import { TsVisor360 } from '../../../shared/ts-visor-360/ts-visor-360';
import { usarMigasAdmin } from '../../admin/migas-admin';
import { TsIndicadorNivel } from './indicador-nivel/ts-indicador-nivel';
import { TsSuperposicionGuia } from './superposicion-guia/ts-superposicion-guia';
import { CapturaStore } from '../application/captura.store';
import { claveDeToma, FOTOGRAMAS_POSIBLES, gradosDeToma } from '../domain/sesion-captura.model';

/**
 * Los pasos 1 a 4 del asistente (`docs/10-captura-360.md`): preparación, permisos, captura
 * secuencial y revisión por toma. El procesamiento y la subida son el paso siguiente de la fase;
 * aquí el obturador entrega el fotograma en bruto.
 *
 * Todo lo que toca el dispositivo entra por un puerto (`CAMARA`, `SENSOR_ORIENTACION`,
 * `PANTALLA_DESPIERTA`), así que esta pantalla se prueba entera sin cámara — y el camino
 * degradado sin sensor se prueba de verdad, no de palabra.
 */
@Component({
  selector: 'app-captura-360',
  imports: [TranslocoPipe, TsBoton, TsIndicadorNivel, TsMigas, TsSuperposicionGuia, TsVisor360],
  templateUrl: './captura-360.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Captura360Page {
  protected readonly migas = usarMigasAdmin([
    { clave: 'admin.productos.titulo', ruta: ['productos'] },
    { clave: 'captura360.titulo' },
  ]);

  protected readonly store = inject(CapturaStore);
  protected readonly opciones = FOTOGRAMAS_POSIBLES;

  private readonly video = viewChild<ElementRef<HTMLVideoElement>>('video');
  private readonly route = inject(ActivatedRoute);
  private readonly paramMap = toSignal(this.route.paramMap, {
    initialValue: this.route.snapshot.paramMap,
  });

  protected readonly errorCaptura = signal<string | null>(null);
  protected readonly mostrarFantasma = signal(true);

  protected readonly claveDeTomaActual = computed(() =>
    claveDeToma(this.store.siguienteOrden(), this.store.fotogramasPrometidos()),
  );

  protected readonly gradosDeTomaActual = computed(() =>
    gradosDeToma(this.store.siguienteOrden(), this.store.fotogramasPrometidos()),
  );

  constructor() {
    // Qué producto se captura, y si quedó una captura suya a medias en disco. No toca la red.
    effect(() => {
      const productoId = this.paramMap().get('productoId') ?? '';
      if (productoId !== '' && this.store.productoId() !== productoId) {
        void this.store.configurar(productoId);
      }
    });

    // El elemento de video no existe hasta que hay permiso, así que la asignación del stream se
    // cuelga de la señal del viewChild en vez de hacerse dentro de `pedirCamara`.
    effect(() => {
      const elemento = this.video()?.nativeElement;
      const stream = this.store.streamActual();
      if (elemento !== undefined && stream !== null && elemento.srcObject !== stream) {
        elemento.srcObject = stream;
      }
    });
  }

  protected elegirFotogramas(cantidad: number): void {
    this.store.fotogramasPrometidos.set(cantidad);
  }

  protected async capturar(): Promise<void> {
    const elemento = this.video()?.nativeElement;
    if (elemento === undefined) {
      return;
    }

    this.errorCaptura.set(null);
    try {
      await this.store.capturar(elemento);
    } catch {
      this.errorCaptura.set('captura360.error_captura');
    }
  }
}
