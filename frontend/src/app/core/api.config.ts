import { InjectionToken } from '@angular/core';
import { environment } from '../../environments/environment';

// Resolved per build configuration via fileReplacements in angular.json.
export const API_BASE_URL = new InjectionToken<string>('API_BASE_URL', {
  providedIn: 'root',
  factory: () => environment.apiBaseUrl,
});
