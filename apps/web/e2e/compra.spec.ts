import { expect, test } from '@playwright/test';

/**
 * El recorrido de compra completo, de la portada al pedido creado, contra el backend real.
 *
 * Es lo que ninguna prueba de Vitest puede dar: allí el repositorio es un doble, aquí el pedido se
 * crea de verdad, con su reserva de inventario, su número consecutivo y su constancia de
 * autorización de datos en la base.
 *
 * Se paga contraentrega o por transferencia a propósito: el pago por Wompi sale del sitio hacia una
 * pasarela que no es nuestra, y automatizar el checkout de un tercero es frágil y no prueba nuestro
 * código.
 */
test('de la portada al pedido creado, pagando por transferencia', async ({ page }) => {
  await page.goto('/es');

  // Portada -> ficha de un producto. La tarjeta entera es el enlace y su nombre accesible es el
  // del producto, así que se hace clic por nombre y no por una posición que cambia con la siembra.
  await page.getByRole('link', { name: /Tenis Trail Runner/i }).first().click();

  const agregar = page.getByRole('button', { name: 'Agregar al carrito' });
  await expect(agregar).toBeVisible();
  await agregar.click();

  // Carrito, alcanzado desde el encabezado y no tecleando la URL.
  await page.getByRole('link', { name: /carrito/i }).first().click();
  await expect(page.getByRole('heading', { name: 'Carrito' })).toBeVisible();
  await page.getByRole('link', { name: 'Ir a pagar' }).click();

  // Resumen: datos de entrega y la casilla de autorización.
  await expect(page.getByLabel('Correo electrónico')).toBeVisible();
  await page.getByLabel('Correo electrónico').fill('e2e@tecnosport.co');
  await page.getByLabel('Departamento').selectOption('05');
  await page.getByLabel('Ciudad').selectOption('05001');
  await page.getByLabel('Dirección').fill('Cra. 26C #38B-31');

  // Sin la autorización no se avanza, y además se dice por qué. Esta pantalla no deshabilita el
  // botón —muestra los errores al enviar, como el resto de sus campos—, así que lo que se
  // comprueba es que el intento no pase de aquí y que el comprador vea el motivo.
  const continuar = page.getByRole('button', { name: 'Continuar' });
  await continuar.click();
  await expect(
    page.getByText('Tienes que autorizar el tratamiento de datos para continuar.'),
  ).toBeVisible();

  await page.getByLabel(/Autorizo el tratamiento de mis datos/).check();
  await continuar.click();

  // Método de pago.
  const transferencia = page.getByRole('button', { name: 'Transferencia bancaria' });
  await expect(transferencia).toBeVisible();
  await transferencia.click();
  await page.getByRole('button', { name: 'Continuar' }).click();

  // Confirmación: aquí se crea el pedido de verdad, con su reserva de inventario y su constancia
  // de autorización en la base.
  await page.getByRole('button', { name: 'Confirmar pedido' }).click();

  // El número consecutivo lo genera el backend, así que verlo en pantalla prueba que el pedido
  // existe en la base y no solo en el navegador.
  await expect(page.getByText(/TS-\d{4}-\d{6}/)).toBeVisible({ timeout: 15000 });
});
