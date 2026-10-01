import { InjectionToken } from '@angular/core';

// Override this token at bootstrap when deploying to another environment.
export const API_BASE_URL = new InjectionToken<string>('API_BASE_URL', {
  providedIn: 'root',
  factory: () => 'http://localhost:8080',
});
