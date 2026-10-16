# 5.2 — Disponibilidad por hotel

Estado: en revisión. GET `/api/v1/hotels/{hotelId}/rooms` conserva el listado
sin filtros. Si se envía checkIn, checkOut o guests, exige los tres. Fechas ISO
YYYY-MM-DD, entrada desde hoy UTC, salida posterior, huéspedes enteros positivos.
Inválidos/incompletos: 400. Hotel inexistente/inactivo: 404. Sin resultados: 200 [].

Solo habitaciones ACTIVE con capacidad suficiente y sin reservas solapadas
CONFIRMED o PENDING_PAYMENT con expiresAt > now. Salida exclusiva; reservas
consecutivas permitidas. Un solo instante por consulta, reloj controlable.
RoomView sin cambios. Retención existente de 15 minutos sin modificaciones.

Detalle público incorpora formulario y listado, carga/vacío/error/reintento.
Editar cancela consultas obsoletas y retira resultados anteriores. No se reserva
ni se garantiza disponibilidad futura: la creación existente revalida bajo bloqueo.
UTC se explicita en la interfaz. Se mantiene retorno al catálogo y sus filtros.

Aceptación: capacidad límite, estados, expiración exacta, solapamientos y extremos,
inactivos, validación HTTP, compatibilidad sin filtros y cancelación frontend.
Sin migraciones, pagos, historial ni cambios administrativos.
