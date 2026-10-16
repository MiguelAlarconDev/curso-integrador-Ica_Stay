# Interfaz de pagos simulados (6.4)

**Estado:** en revisión

**Actualizado:** 2026-10-16

## Objetivo y permisos

Desde el detalle de una reserva propia, USER puede elegir «Pagar (simulación)» y simular APPROVED o REJECTED. Se conservan los guards y el interceptor JWT. No hay cobros ni campos para tarjetas, identidad o importes.

## Contrato y flujo

El servicio envía únicamente reservationId y result a POST /api/v1/payments/simulate. Respuesta: id, result, reference. El detalle existente muestra hotel, habitación, importe y moneda históricos y vencimiento.

APPROVED consulta nuevamente GET /api/v1/reservations/{id}; solo ese detalle actualiza el estado visible. REJECTED informa el rechazo sin modificar reserva ni vencimiento. Los pagos se deshabilitan durante el envío, para estados distintos de PENDING_PAYMENT y al vencer la retención. La estimación visual usa evaluatedAt del servidor y tiempo transcurrido monotónico; el backend valida la vigencia definitiva.

## Errores y reintentos

400/403/404 muestran mensajes seguros y requieren actualizar el detalle. 401 utiliza cierre de sesión del interceptor. 409, errores de conexión, 5xx, respuestas inválidas o cancelación del envío mantienen bloqueado otro pago y ofrecen consultar el estado, sin reintento automático. Una consulta que aún devuelve PENDING_PAYMENT no demuestra que un envío incierto haya fallado; no desbloquea el pago. La protección se conserva al recrear el componente dentro de la misma instancia de la aplicación, por usuario y reserva; no constituye idempotencia del backend.

## Criterios de aceptación

- Aprobación refresca desde backend; rechazo conserva datos históricos y retención.
- Estados no pagables, expiración y envíos duplicados bloqueados.
- Errores y autorización cubiertos por pruebas focalizadas.
- Controles accesibles por teclado, mensajes de estado/alerta y disposición adaptable a móvil.

## Fuera de alcance

Pasarelas reales, tarjetas, backend, HOTEL_ADMIN, dependencias nuevas y reenvío automático de pagos.

## Validación

48 pruebas Angular focalizadas de reservas y pagos aprobadas (24 nuevas de pagos), sin ejecutar la suite completa. Build de producción aprobado. Pendiente validación manual en navegador con backend real: aprobación/rechazo, sesión expirada, pérdida de conexión, navegación por teclado y presentación móvil.
