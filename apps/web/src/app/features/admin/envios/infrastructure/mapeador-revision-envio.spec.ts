import { aAcuseDeRevision, aBandejaDeRevision } from './mapeador-revision-envio';

describe('mapeador de la bandeja de revision', () => {
  /**
   * Springdoc declara todo opcional, asi que el DTO llega con cada campo posiblemente ausente. Lo
   * que no puede pasar es que un campo que falta se convierta en `undefined` dentro del modelo: el
   * resto de la pantalla asume que las cadenas son cadenas.
   */
  it('una respuesta vacia da dos listas vacias y no revienta', () => {
    expect(aBandejaDeRevision({})).toEqual({ guias: [], emisiones: [] });
  });

  it('mapea la guia con sus dos fechas y su descripcion', () => {
    const bandeja = aBandejaDeRevision({
      guias: [
        {
          guiaId: 'g1',
          numeroGuia: '0340545',
          transportadora: 'Servientrega',
          pedidoId: 'p1',
          numeroPedido: 'TS-2026-000042',
          estado: 'RETENIDO',
          descripcion: 'retenido en bodega',
          ocurrioEn: '2026-09-15T14:00:00Z',
          recibidoEn: '2026-09-15T14:30:00Z',
        },
      ],
    });

    expect(bandeja.guias[0]).toEqual({
      guiaId: 'g1',
      numeroGuia: '0340545',
      transportadora: 'Servientrega',
      pedidoId: 'p1',
      numeroPedido: 'TS-2026-000042',
      estado: 'RETENIDO',
      descripcion: 'retenido en bodega',
      ocurrioEn: '2026-09-15T14:00:00Z',
      recibidoEn: '2026-09-15T14:30:00Z',
      revisadaEn: null,
    });
  });

  /**
   * `revisadaEn` ausente es `null` y no `undefined`: la pantalla pregunta por el valor para decidir
   * si avisa "ya se habia revisado", y `undefined` en una plantilla de Angular se pinta vacio en
   * vez de ocultar la linea.
   */
  it('la fecha de revision ausente queda en nulo', () => {
    const bandeja = aBandejaDeRevision({ guias: [{ guiaId: 'g1' }] });

    expect(bandeja.guias[0].revisadaEn).toBeNull();
    expect(bandeja.guias[0].descripcion).toBeNull();
  });

  it('mapea la emision con su tarifa y sus envios de plataforma', () => {
    const bandeja = aBandejaDeRevision({
      emisiones: [
        {
          emisionId: 'e1',
          pedidoId: 'p2',
          numeroPedido: 'TS-2026-000043',
          transportadora: 'Coordinadora',
          idTarifa: 'tarifa-1',
          estado: 'PARCIAL',
          enviosEnPlataforma: ['env-1', 'env-2'],
          solicitadaEn: '2026-09-16T10:00:00Z',
          actor: 'admin:7',
        },
      ],
    });

    expect(bandeja.emisiones[0].idTarifa).toBe('tarifa-1');
    expect(bandeja.emisiones[0].estado).toBe('PARCIAL');
    expect(bandeja.emisiones[0].enviosEnPlataforma).toEqual(['env-1', 'env-2']);
    expect(bandeja.emisiones[0].detalle).toBeNull();
  });

  it('mapea el acuse con su instante y su actor', () => {
    expect(
      aAcuseDeRevision({
        id: 'a1',
        tipo: 'GUIA',
        referencia: 'g1',
        revisadoEn: '2026-09-17T15:00:00Z',
        actor: 'admin:7',
        nota: 'Reclamé a la transportadora.',
      }),
    ).toEqual({
      id: 'a1',
      tipo: 'GUIA',
      referencia: 'g1',
      revisadoEn: '2026-09-17T15:00:00Z',
      actor: 'admin:7',
      nota: 'Reclamé a la transportadora.',
    });
  });
});
