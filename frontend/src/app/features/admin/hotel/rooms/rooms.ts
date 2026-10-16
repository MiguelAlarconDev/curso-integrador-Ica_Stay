import { ChangeDetectionStrategy, Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize, Subscription } from 'rxjs';
import { AdminRoomService } from '../../../../core/services/admin-room.service';
import { AdminRoomView } from '../../../../core/models/admin-room';
import { RoomForm } from './room-form';
import { roomMutationError } from './room-mutation-error';

@Component({
  selector: 'app-hotel-rooms', imports: [DecimalPipe, RoomForm],
  templateUrl: './rooms.html', styleUrl: './rooms.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HotelRooms implements OnInit {
  private readonly service = inject(AdminRoomService);
  private readonly destroyRef = inject(DestroyRef);
  private detailRequest?: Subscription;
  protected readonly rooms = signal<AdminRoomView[]>([]);
  protected readonly loading = signal(false);
  protected readonly error = signal('');
  protected readonly selectedId = signal<string | null>(null);
  protected readonly detail = signal<AdminRoomView | null>(null);
  protected readonly detailLoading = signal(false);
  protected readonly detailError = signal('');
  protected readonly editing = signal(false);
  protected readonly editRoom = signal<AdminRoomView | null>(null);
  protected readonly saving = signal(false);
  protected readonly statusRoom = signal<AdminRoomView | null>(null);
  protected readonly statusError = signal('');
  protected readonly success = signal('');

  protected openForm(room: AdminRoomView | null): void {
    if (this.saving() || this.editing() || this.statusRoom()) return;
    if (this.detailLoading()) this.selectedId.set(null);
    this.detailRequest?.unsubscribe();
    this.editRoom.set(room); this.editing.set(true); this.success.set('');
  }

  protected closeForm(): void {
    if (this.saving()) return;
    this.editing.set(false); this.editRoom.set(null);
  }

  protected saved(room: AdminRoomView): void {
    this.applyRoom(room); this.saving.set(false); this.editing.set(false); this.editRoom.set(null);
    this.success.set('Habitación guardada correctamente.');
  }

  protected confirmStatus(room: AdminRoomView): void {
    if (this.saving() || this.editing() || this.statusRoom()) return;
    if (this.detailLoading()) this.selectedId.set(null);
    this.detailRequest?.unsubscribe();
    this.statusRoom.set(room); this.statusError.set(''); this.success.set('');
  }

  protected cancelStatus(): void {
    if (this.saving()) return;
    this.statusRoom.set(null); this.statusError.set('');
  }

  protected changeStatus(): void {
    const room = this.statusRoom();
    if (!room || this.saving()) return;
    this.saving.set(true); this.statusError.set('');
    this.service.changeStatus(room.id, room.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE')
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.saving.set(false)))
      .subscribe({ next: updated => {
        this.applyRoom(updated); this.statusRoom.set(null); this.success.set('Estado actualizado correctamente.');
      }, error: error => this.statusError.set(roomMutationError(error, true)) });
  }

  private applyRoom(room: AdminRoomView): void {
    this.rooms.update(rooms => [...rooms.filter(item => item.id !== room.id), room]
      .sort((a, b) => a.number.localeCompare(b.number) || a.id.localeCompare(b.id)));
    if (this.selectedId() === room.id) { this.detail.set(room); this.detailError.set(''); }
  }

  ngOnInit(): void { this.load(); }

  protected load(): void {
    if (this.loading()) return;
    this.loading.set(true); this.error.set('');
    this.service.list().pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.loading.set(false)))
      .subscribe({ next: rooms => this.rooms.set(rooms), error: error => this.error.set(this.message(error, false)) });
  }

  protected showDetail(id: string): void {
    if (this.editing() || this.statusRoom() || this.saving()) return;
    if (this.selectedId() === id && this.detailLoading()) return;
    this.detailRequest?.unsubscribe();
    this.selectedId.set(id); this.detail.set(null); this.detailError.set(''); this.detailLoading.set(true);
    this.detailRequest = this.service.get(id)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.detailLoading.set(false)))
      .subscribe({ next: room => this.detail.set(room), error: error => this.detailError.set(this.message(error, true)) });
  }

  private message(error: unknown, detail: boolean): string {
    const status = error instanceof HttpErrorResponse ? error.status : -1;
    switch (status) {
      case 0: return 'No pudimos conectar con el servidor. Revisa tu conexión e inténtalo de nuevo.';
      case 401: return 'Tu sesión ya no es válida. Inicia sesión de nuevo.';
      case 403: return 'No tienes permiso para consultar estas habitaciones.';
      case 404: return detail ? 'La habitación no está disponible o no pertenece a tu hotel.'
        : 'No tienes un hotel asociado. Contacta con la administración de Ica Stay.';
      default: return 'No pudimos cargar ' + (detail ? 'la habitación.' : 'las habitaciones.') + ' Inténtalo de nuevo más tarde.';
    }
  }
}
