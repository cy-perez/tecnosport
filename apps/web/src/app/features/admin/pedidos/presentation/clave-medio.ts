import { MedioReintegro } from '../../retractos/domain/retracto.model';

/** Las etiquetas de los medios de reintegro, compartidas con el panel de retracto. */
export const CLAVE_MEDIO: Record<MedioReintegro, string> = {
  WOMPI: 'admin.retractos.medios.wompi',
  SISTECREDITO: 'admin.retractos.medios.sistecredito',
  TRANSFERENCIA_BANCARIA: 'admin.retractos.medios.transferencia_bancaria',
  EFECTIVO: 'admin.retractos.medios.efectivo',
  OTRO: 'admin.retractos.medios.otro',
};
