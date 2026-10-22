import { AfterViewChecked, OnInit, ChangeDetectionStrategy, Component, DestroyRef, ElementRef, inject, signal, viewChild } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { AiChatService } from '../../core/services/ai-chat.service';

interface ChatMessage { id: number; role: 'user' | 'assistant'; text: string; error?: string; }

@Component({
  selector: 'app-ai-chat', imports: [ReactiveFormsModule],
  templateUrl: './ai-chat.html', styleUrl: './ai-chat.css', changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AiChat implements AfterViewChecked, OnInit {
  private readonly service = inject(AiChatService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly field = viewChild<ElementRef<HTMLTextAreaElement>>('field');
  private readonly launcher = viewChild<ElementRef<HTMLButtonElement>>('launcher');
  private readonly history = viewChild<ElementRef<HTMLElement>>('history');
  private focusTarget: 'field' | 'launcher' | null = null;
  private scrollPending = false;
  private nextId = 1;
  protected readonly open = signal(false);
  protected readonly loading = signal(false);
  protected readonly historyLoading = signal(false);
  protected readonly historyError = signal('');
  protected readonly historyTruncated = signal(false);
  protected readonly started = signal(false);
  protected readonly announcement = signal('');
  protected readonly messages = signal<ChatMessage[]>([{
    id: 0, role: 'assistant', text: '¡Hola! Soy IcaStay AI. Puedo orientarte sobre consultas hoteleras en Ica. No tengo tarifas ni disponibilidad verificadas. ¿En qué puedo ayudarte?',
  }]);
  protected readonly message = new FormControl('', { nonNullable: true, validators: [
    Validators.maxLength(1000), control => typeof control.value === 'string' && control.value.trim() ? null : { blank: true },
  ] });

  ngOnInit(): void { this.loadHistory(); }

  protected loadHistory(): void {
    if (this.historyLoading() || this.started()) return;
    this.historyLoading.set(true);
    this.historyError.set('');
    if (this.open()) this.announcement.set('Recuperando conversación.');
    this.service.currentConversation().pipe(takeUntilDestroyed(this.destroyRef),
      finalize(() => this.historyLoading.set(false))).subscribe({
      next: page => {
        this.messages.update(messages => [messages[0], ...page.content.flatMap(item => [
          { id: this.nextId++, role: 'user' as const, text: item.question },
          { id: this.nextId++, role: 'assistant' as const, text: item.answer },
        ])]);
        this.historyTruncated.set(page.hasNext);
        if (this.open()) this.focusTarget = 'field';
        if (this.open()) this.announcement.set(page.content.length ? 'Conversación recuperada.' : 'Puedes iniciar una conversación.');
        this.scrollPending = true;
      },
      error: (error: Error) => {
        this.historyError.set(error.message);
        if (this.open()) this.announcement.set(error.message);
      },
    });
  }

  ngAfterViewChecked(): void {
    if (this.focusTarget) {
      (this.focusTarget === 'field' ? this.field() : this.launcher())?.nativeElement.focus();
      this.focusTarget = null;
    }
    if (this.scrollPending && this.open()) {
      const history = this.history()?.nativeElement;
      if (history) history.scrollTop = history.scrollHeight;
      this.scrollPending = false;
    }
  }

  protected toggle(): void {
    this.open.update(value => !value);
    this.announcement.set(this.open() && this.historyLoading() ? 'Recuperando conversación.' : '');
    this.focusTarget = this.open() ? 'field' : 'launcher';
    this.scrollPending = true;
  }

  protected close(event?: Event): void {
    event?.stopPropagation();
    if (this.open()) this.toggle();
  }

  protected onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
      event.preventDefault();
      this.send();
    }
  }

  protected send(): void {
    if (this.loading() || this.historyLoading()) return;
    this.message.markAsTouched();
    if (this.message.invalid) return;
    this.started.set(true);
    const message: ChatMessage = { id: this.nextId++, role: 'user', text: this.message.value.trim() };
    this.messages.update(messages => [...messages, message]);
    this.message.reset();
    this.request(message);
  }

  protected retry(message: ChatMessage): void {
    if (!this.loading() && !this.historyLoading() && message.error) this.request(message);
  }

  private request(message: ChatMessage): void {
    this.loading.set(true);
    this.focusTarget = 'field';
    this.messages.update(messages => messages.map(item => item.id === message.id ? { ...item, error: undefined } : item));
    this.announcement.set('IcaStay AI está preparando una respuesta.');
    this.scrollPending = true;
    this.service.chat(message.text).pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.loading.set(false)))
      .subscribe({
        next: answer => {
          this.messages.update(messages => messages.flatMap(item => item.id === message.id
            ? [item, { id: this.nextId++, role: 'assistant' as const, text: answer }] : [item]));
          this.announcement.set(`IcaStay AI: ${answer}`);
          this.scrollPending = true;
        },
        error: (error: Error) => {
          this.messages.update(messages => messages.map(item => item.id === message.id ? { ...item, error: error.message } : item));
          this.announcement.set(error.message);
          this.scrollPending = true;
        },
      });
  }
}
