import { ChangeDetectionStrategy, Component, Input, OnChanges, OnDestroy, SimpleChanges, inject, signal } from '@angular/core';
import { HttpErrorResponse, HttpEventType } from '@angular/common/http';
import { Observable, Subscription } from 'rxjs';
import { PhotoView } from '../../core/models/photo';
import { PhotoGalleryService } from '../../core/services/photo-gallery.service';

interface UploadItem {
  file: File;
  preview: string;
  state: string;
  progress: number | null;
  error: boolean;
}

@Component({
  selector: 'app-photo-gallery-manager',
  templateUrl: './photo-gallery-manager.html',
  styleUrl: './photo-gallery-manager.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PhotoGalleryManager implements OnChanges, OnDestroy {
  /** Kept for compatibility; the admin API resolves the own hotel from the JWT. */
  @Input() hotelId = '';
  @Input() roomId?: string;
  @Input() label = 'Hotel';
  protected readonly service = inject(PhotoGalleryService);
  private requests = new Subscription();
  private imageRequests = new Subscription();
  protected readonly opened = signal(false);
  protected readonly busy = signal(false);
  protected readonly ready = signal(false);
  protected readonly photos = signal<PhotoView[]>([]);
  protected readonly uploads = signal<UploadItem[]>([]);
  protected readonly error = signal('');
  protected readonly notice = signal('');
  protected readonly pendingDelete = signal<string | null>(null);
  /** Object URLs for JWT-protected admin content, keyed by photo id. */
  protected readonly images = signal<Record<string, string>>({});
  protected get limit() { return this.roomId ? 10 : 15; }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['hotelId'] || changes['roomId']) this.reset();
  }
  ngOnDestroy(): void { this.reset(); }
  private reset(): void {
    this.requests.unsubscribe(); this.requests = new Subscription();
    this.clearPreviews(); this.clearImages(); this.photos.set([]); this.opened.set(false);
    this.ready.set(false); this.busy.set(false); this.error.set(''); this.notice.set(''); this.pendingDelete.set(null);
  }
  private clearPreviews(): void {
    this.uploads().forEach(item => { if (item.preview) URL.revokeObjectURL(item.preview); });
    this.uploads.set([]);
  }
  private clearImages(): void {
    this.imageRequests.unsubscribe(); this.imageRequests = new Subscription();
    Object.values(this.images()).forEach(url => URL.revokeObjectURL(url));
    this.images.set({});
  }
  private loadImages(photos: PhotoView[]): void {
    this.clearImages();
    photos.forEach(photo => this.imageRequests.add(this.service.adminImage(photo).subscribe({
      next: blob => this.images.update(images => ({ ...images, [photo.id]: URL.createObjectURL(blob) })),
      error: () => undefined, // The tile keeps its placeholder; management actions stay available.
    })));
  }
  protected load(): void {
    if (this.busy()) return;
    this.opened.set(true); this.refresh();
  }
  private refresh(): void {
    this.busy.set(true); this.ready.set(false);
    // Owner-only admin read: works for inactive hotels and rooms, which the public API hides.
    this.requests.add(this.service.adminList(this.roomId).subscribe({
      next: photos => {
        const ordered = [...photos].sort((a, b) => a.position - b.position || a.id.localeCompare(b.id));
        this.photos.set(ordered); this.loadImages(ordered);
        this.ready.set(true); this.busy.set(false);
      },
      error: error => { this.error.set(this.message(error, true)); this.busy.set(false); },
    }));
  }
  protected reload(): void { if (!this.busy()) { this.error.set(''); this.notice.set(''); this.load(); } }

  protected choose(event: Event): void {
    const input = event.target as HTMLInputElement;
    const files = Array.from(input.files ?? []); input.value = '';
    if (this.busy() || !this.ready() || !files.length || this.pendingDelete()) return;
    this.clearPreviews(); this.error.set(''); this.notice.set('');
    let available = this.limit - this.photos().length;
    const seen = new Set<string>();
    const items = files.map(file => {
      const key = `${file.name}:${file.size}:${file.lastModified}`;
      let problem = '';
      if (seen.has(key)) problem = 'Archivo repetido en esta selección.';
      else if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) problem = 'Selecciona JPG, PNG o WebP.';
      else if (!file.size || file.size > 5 * 1024 * 1024) problem = 'El archivo debe pesar entre 1 byte y 5 MB.';
      else if (available <= 0) problem = `Límite de ${this.limit} fotografías alcanzado.`;
      seen.add(key);
      if (!problem) available--;
      return { file, preview: problem ? '' : URL.createObjectURL(file), state: problem || 'En espera', progress: null, error: !!problem };
    });
    this.uploads.set(items);
    const queue = items.filter(item => !item.error);
    if (queue.length) { this.busy.set(true); this.sendNext(queue); }
  }
  private update(item: UploadItem, state: string, error = false, progress: number | null = null): void {
    Object.assign(item, { state, error, progress }); this.uploads.update(items => [...items]);
  }
  private sendNext(queue: UploadItem[]): void {
    const item = queue.shift();
    if (!item) { this.notice.set('Subida finalizada.'); this.refresh(); return; }
    this.update(item, 'Subiendo…');
    this.requests.add(this.service.upload(item.file, this.roomId).subscribe({
      next: event => {
        if (event.type === HttpEventType.UploadProgress) {
          this.update(item, 'Subiendo…', false, event.total ? Math.min(100, Math.round(event.loaded * 100 / event.total)) : null);
        }
        if (event.type === HttpEventType.Response) this.update(item, 'Guardada', false, 100);
      },
      complete: () => this.sendNext(queue),
      error: error => {
        this.update(item, this.message(error), true);
        queue.forEach(waiting => this.update(waiting, 'No enviada. Revisa la galería antes de seleccionarla de nuevo.', true));
        this.error.set('La subida se detuvo. Consulta la galería antes de volver a enviar archivos: una respuesta perdida puede haber sido guardada.');
        this.refresh();
      },
    }));
  }
  protected askDelete(photo: PhotoView): void {
    if (!this.busy() && this.ready()) this.pendingDelete.set(photo.id);
  }
  protected cancelDelete(): void { if (!this.busy()) this.pendingDelete.set(null); }
  protected remove(): void {
    const id = this.pendingDelete();
    if (id) this.mutate(() => this.service.delete(id, this.roomId), 'Fotografía eliminada.');
  }
  protected cover(photo: PhotoView): void {
    if (!photo.isCover && !this.pendingDelete()) this.mutate(() => this.service.cover(photo.id, this.roomId), 'Portada actualizada.');
  }
  protected move(index: number, direction: number): void {
    const ordered = [...this.photos()]; const target = index + direction;
    if (target < 0 || target >= ordered.length || this.pendingDelete()) return;
    [ordered[index], ordered[target]] = [ordered[target], ordered[index]];
    this.mutate(() => this.service.reorder(ordered.map(photo => photo.id), this.roomId), 'Orden actualizado.');
  }
  private mutate(action: () => Observable<unknown>, message: string): void {
    if (this.busy() || !this.ready()) return;
    this.busy.set(true); this.error.set(''); this.notice.set('');
    this.requests.add(action().subscribe({
      next: () => { this.pendingDelete.set(null); this.notice.set(message); this.refresh(); },
      error: error => { this.pendingDelete.set(null); this.error.set(this.message(error)); this.refresh(); },
    }));
  }
  private message(error: unknown, reading = false): string {
    const status = error instanceof HttpErrorResponse ? error.status : -1;
    switch (status) {
      case 401: return 'Tu sesión expiró. Inicia sesión de nuevo.';
      case 403: return 'No tienes permiso para gestionar estas fotografías.';
      case 404: return reading ? 'Galería no disponible: el hotel o la habitación no existe o no te pertenece.' : 'La fotografía ya no está disponible. Se volverá a consultar la galería.';
      case 409: return 'La galería cambió o alcanzó su límite. Revisa la lista actualizada.';
      case 400: case 413: return 'Archivo o solicitud no válidos. Usa JPG, PNG o WebP de hasta 5 MB y dimensiones válidas.';
      default: return 'No se pudo completar la operación. Revisa la galería antes de reintentar.';
    }
  }
}
