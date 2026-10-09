import {
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { QueryClient } from '@tanstack/angular-query-experimental';
import { mensajeDeError } from '../../../../core/errores/mensaje-de-error';
import { ErrorHttp } from '../../../../core/http/respuesta-http';
import { TsBoton } from '../../../../shared/ui/boton/ts-boton';
import { usarFoco } from '../../../../shared/foco/foco';
import { TsCampo } from '../../../../shared/ui/campo/ts-campo';
import { TsEsqueleto } from '../../../../shared/ts-esqueleto/ts-esqueleto';
import { TsMigas } from '../../../../shared/ts-migas/ts-migas';
import { Marca } from '../../../catalogo/domain/producto.model';
import { usarMigasAdmin } from '../../migas-admin';
import { usarCrearMarca } from '../application/crear-marca.mutacion';
import { usarEliminarMarca, usarRenombrarMarca } from '../application/editar-marca.mutaciones';
import { CLAVE_MARCAS_ADMIN, usarMarcasAdmin } from '../application/listar-marcas-admin.consulta';
import { usarFocoEnPrimerInvalido } from '../../../../shared/foco/foco';

/**
 * Las marcas del catálogo, el formulario para dar de alta una y, en cada fila, renombrarla o
 * eliminarla.
 *
 * Pantalla propia y no un formulario dentro del alta de producto, que es donde de verdad se
 * descubre que la marca falta: en línea habría que decidir qué pasa con lo ya tecleado del
 * producto, y eso es más pantalla de la que esta tarea necesita. El costo queda escrito —salir del
 * formulario de producto pierde lo escrito— y si molesta, se resuelve después.
 *
 * Renombrar se hace en la misma fila, sin pregunta: es reversible y el nombre nuevo está a la vista
 * antes de guardar. Eliminar sí pregunta, en una fila aparte como la lista de ingestas, porque no
 * tiene vuelta; y con productos el servidor lo rechaza diciendo qué hacer (`ADR-0076`).
 */
@Component({
  selector: 'app-marcas-admin',
  imports: [ReactiveFormsModule, TranslocoPipe, TsBoton, TsCampo, TsEsqueleto, TsMigas],
  templateUrl: './marcas-admin.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MarcasAdminPage {
  /** Al fallar el envío, el foco va al primer campo con error y no se queda en el botón. */
  private readonly enfocarPrimerInvalido = usarFocoEnPrimerInvalido();
  protected readonly migas = usarMigasAdmin([{ clave: 'admin.marcas.titulo' }]);

  private readonly transloco = inject(TranslocoService);

  protected readonly consulta = usarMarcasAdmin();
  private readonly mutacion = usarCrearMarca();

  protected readonly marcas = computed<readonly Marca[]>(() => this.consulta.data() ?? []);
  protected readonly enviando = computed(() => this.mutacion.isPending());

  private readonly enfocarDespuesDePintar = usarFoco();
  private readonly avisoMarca = viewChild<ElementRef<HTMLElement>>('avisoMarca');

  protected readonly error = signal<string | null>(null);
  /** El nombre recién creado, para confirmarlo por su nombre y no con un "listo" genérico. */
  protected readonly creada = signal<string | null>(null);

  protected readonly form = new FormGroup({
    nombre: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  protected enviar(): void {
    // Guarda de reentrada en vez de deshabilitar el botón mientras va la petición: un botón que se
    // apaga bajo el dedo manda el foco a `<body>`, y aquí el formulario además se reinicia.
    if (this.enviando()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.enfocarPrimerInvalido();
      // Se dice qué falta en vez de deshabilitar el botón: un `<button disabled>` sale del orden de
      // tabulación y quien navega con teclado no llega a enterarse de por qué no pasa nada. Mismo
      // criterio que la pantalla de variantes sin medir.
      this.error.set(this.transloco.translate('admin.marcas.faltaNombre'));
      return;
    }
    this.error.set(null);
    this.creada.set(null);

    this.mutacion.mutate(this.form.getRawValue().nombre, {
      onSuccess: (resultado) => {
        if (resultado.tipo === 'YA_EXISTE') {
          this.error.set(this.transloco.translate('admin.marcas.yaExiste'));
          return;
        }
        this.creada.set(resultado.marca.nombre);
        this.form.reset();
        // El formulario queda en blanco: sin esto, quien usa teclado no tiene forma de distinguir
        // "se creó" de "no se envió".
        this.enfocarDespuesDePintar(() => this.avisoMarca()?.nativeElement);
      },
      onError: () => this.error.set(this.transloco.translate('admin.marcas.error')),
    });
  }

  // --- Lo que cambia una fila: renombrar en el sitio y eliminar con pregunta. ---

  private readonly raiz = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly queryClient = inject(QueryClient);
  private readonly renombrado = usarRenombrarMarca();
  private readonly eliminacion = usarEliminarMarca();
  private escrituraEnVuelo = false;
  protected readonly guardandoEdicion = computed(() => this.renombrado.isPending());
  protected readonly eliminando = computed(() => this.eliminacion.isPending());

  /** La marca cuya fila está en edición, y la que pregunta si eliminar: una sola de cada a la vez. */
  protected readonly editando = signal<string | null>(null);
  protected readonly confirmandoEliminar = signal<string | null>(null);
  protected readonly errorEdicion = signal<string | null>(null);
  protected readonly errorEliminar = signal<string | null>(null);
  /** Lo último que pasó en la lista, para decirlo cuando la fila ya cambió o no existe. */
  protected readonly avisoLista = signal<string | null>(null);
  private readonly avisoListaRef = viewChild<ElementRef<HTMLElement>>('avisoListaRef');
  private readonly cajaEliminar = viewChild<ElementRef<HTMLElement>>('cajaEliminar');

  protected readonly formEditar = new FormGroup({
    nombre: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  protected empezarEdicion(marca: Marca): void {
    this.confirmandoEliminar.set(null);
    this.errorEdicion.set(null);
    this.avisoLista.set(null);
    this.formEditar.reset({ nombre: marca.nombre });
    this.editando.set(marca.id);
    this.enfocarDespuesDePintar(() =>
      this.raiz.nativeElement.querySelector<HTMLElement>('#editar-marca-nombre'),
    );
  }

  protected cancelarEdicion(): void {
    const id = this.editando();
    this.editando.set(null);
    this.errorEdicion.set(null);
    this.enfocarDespuesDePintar(() => this.botonDeLaFila('editar', id));
  }

  protected guardarEdicion(marca: Marca): void {
    if (this.escrituraEnVuelo) {
      return;
    }
    if (this.formEditar.invalid) {
      this.formEditar.markAllAsTouched();
      this.errorEdicion.set(this.transloco.translate('admin.marcas.editar.faltaNombre'));
      return;
    }
    const nombre = this.formEditar.getRawValue().nombre;
    // Sin cambios no hay nada que pedirle al servidor: se cierra como si se hubiera guardado.
    if (nombre.trim() === marca.nombre) {
      this.cancelarEdicion();
      return;
    }
    this.escrituraEnVuelo = true;
    this.errorEdicion.set(null);
    this.renombrado.mutate(
      { id: marca.id, nombre },
      {
        onSettled: () => (this.escrituraEnVuelo = false),
        onSuccess: (resultado) => {
          if (resultado.tipo === 'YA_EXISTE') {
            this.errorEdicion.set(this.transloco.translate('admin.marcas.yaExiste'));
            return;
          }
          this.editando.set(null);
          this.avisoLista.set(
            this.transloco.translate('admin.marcas.editar.hecho', {
              nombre: resultado.marca.nombre,
            }),
          );
          // El campo desaparece con el formulario: el foco va al aviso, que dice el nombre nuevo.
          this.enfocarDespuesDePintar(() => this.avisoListaRef()?.nativeElement);
        },
        onError: (error: unknown) => {
          this.errorEdicion.set(mensajeDeError(error, this.transloco, 'admin.marcas.editar.error'));
          this.refrescarSiYaNoExiste(error);
        },
      },
    );
  }

  protected preguntarSiEliminar(marca: Marca): void {
    this.editando.set(null);
    this.errorEliminar.set(null);
    this.avisoLista.set(null);
    this.confirmandoEliminar.set(marca.id);
    this.enfocarDespuesDePintar(() => this.cajaEliminar()?.nativeElement);
  }

  protected cancelarEliminar(): void {
    const id = this.confirmandoEliminar();
    this.confirmandoEliminar.set(null);
    this.errorEliminar.set(null);
    this.enfocarDespuesDePintar(() => this.botonDeLaFila('eliminar', id));
  }

  protected eliminar(marca: Marca): void {
    if (this.escrituraEnVuelo) {
      return;
    }
    this.escrituraEnVuelo = true;
    this.errorEliminar.set(null);
    this.eliminacion.mutate(marca.id, {
      onSettled: () => (this.escrituraEnVuelo = false),
      onSuccess: () => {
        this.confirmandoEliminar.set(null);
        this.avisoLista.set(
          this.transloco.translate('admin.marcas.eliminar.hecho', { nombre: marca.nombre }),
        );
        this.enfocarDespuesDePintar(() => this.avisoListaRef()?.nativeElement);
      },
      onError: (error: unknown) => {
        this.errorEliminar.set(
          mensajeDeError(error, this.transloco, 'admin.marcas.eliminar.error'),
        );
        this.refrescarSiYaNoExiste(error);
      },
    });
  }

  /** Otra pestaña la borró: reintentar daría 404 para siempre, así que la lista se vuelve a pedir. */
  private refrescarSiYaNoExiste(error: unknown): void {
    if (error instanceof ErrorHttp && error.estado === 404) {
      void this.queryClient.invalidateQueries({ queryKey: CLAVE_MARCAS_ADMIN });
    }
  }

  /** El `<button>` real vive dentro de `ts-boton`. */
  private botonDeLaFila(accion: 'editar' | 'eliminar', id: string | null): HTMLElement | null {
    return this.raiz.nativeElement.querySelector<HTMLElement>(`[data-${accion}="${id}"] button`);
  }
}
