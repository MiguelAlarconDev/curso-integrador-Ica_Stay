import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { API_BASE_URL } from '../../core/api.config';
import { App } from '../../app';
import { routes } from '../../app.routes';

@Component({ template: '' })
class Page {}

describe('Public chatbot routing', () => {
  it('explicitly enables only existing public pages', () => {
    expect(routes.filter(route => route.data?.['aiChat']).map(route => route.path).sort())
      .toEqual(['', 'hotels', 'hotels/:id', 'login', 'register']);
    expect(routes.filter(route => route.canActivate).every(route => !route.data?.['aiChat'])).toBe(true);
  });

  it('preserves the widget on public navigation and removes it on private navigation', async () => {
    TestBed.configureTestingModule({ imports: [App], providers: [provideHttpClient(), provideHttpClientTesting(),
      provideRouter([{ path: '', component: Page, data: { aiChat: true } },
        { path: 'hotels', component: Page, data: { aiChat: true } }, { path: 'private', component: Page }])] });
    const fixture = TestBed.createComponent(App); fixture.detectChanges();
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/'); fixture.detectChanges();
    const chat = fixture.nativeElement.querySelector('app-ai-chat'); expect(chat).not.toBeNull();
    const http = TestBed.inject(HttpTestingController);
    const historyUrl = `${TestBed.inject(API_BASE_URL)}/api/v1/ai/conversations/current?page=0&size=50`;
    http.expectOne(historyUrl).flush({ conversationId: null, content: [], page: 0, size: 50, hasNext: false });
    await router.navigateByUrl('/hotels'); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-ai-chat')).toBe(chat);
    http.expectNone(historyUrl);
    await router.navigateByUrl('/private'); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-ai-chat')).toBeNull();
    http.verify();
  });
});
