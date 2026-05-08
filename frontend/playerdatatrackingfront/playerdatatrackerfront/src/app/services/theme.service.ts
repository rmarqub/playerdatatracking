import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  isDark = false;

  constructor() {
    this.isDark = localStorage.getItem('theme') === 'dark';
    this.apply();
  }

  toggle(): void {
    this.isDark = !this.isDark;
    localStorage.setItem('theme', this.isDark ? 'dark' : 'light');
    this.apply();
  }

  private apply(): void {
    document.body.classList.toggle('dark-mode', this.isDark);
  }
}
