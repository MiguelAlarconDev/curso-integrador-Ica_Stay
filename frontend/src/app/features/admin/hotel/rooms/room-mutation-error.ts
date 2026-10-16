import { HttpErrorResponse } from '@angular/common/http';

export function roomMutationError(error: unknown, statusChange = false): string {
  switch (error instanceof HttpErrorResponse ? error.status : -1) {
    case 0: return 'No pudimos conectar con el servidor. Tus datos se conservan; inténtalo de nuevo.';
    case 400: return statusChange ? 'El servidor rechazó el cambio de estado. Consulta de nuevo la habitación.'
      : 'Revisa los datos: número, capacidad, precio y código de moneda ISO 4217 válido.';
    case 401: return 'Tu sesión ya no es válida. Inicia sesión de nuevo.';
    case 403: return 'No tienes permiso para gestionar esta habitación.';
    case 404: return 'El hotel o la habitación ya no están disponibles para tu usuario.';
    case 409: return statusChange ? 'No se pudo cambiar el estado por un conflicto. Consulta de nuevo la habitación.'
      : 'Ya existe una habitación con ese número en tu hotel.';
    default: return 'No pudimos guardar el cambio. Inténtalo de nuevo más tarde.';
  }
}
