import { Injectable } from '@angular/core';
import { CanActivate, Router, UrlTree } from '@angular/router';
import { AuthService, User } from '../auth.service';
import { Observable, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';

@Injectable({ providedIn: 'root' })
export class AuthGuard implements CanActivate {
  constructor(private auth: AuthService, private router: Router) {}

  canActivate(): Observable<boolean | UrlTree> {
    const cached = this.auth.getCachedUser();

    if (!cached) {
      return this.auth.ensureFreshUser().pipe(
        map((u: User) => true),
        catchError(() => of(this.router.createUrlTree(['/login'])))
      );
    }
    this.auth.userWithBackgroundRevalidate().subscribe(); // fire-and-forget
    return of(true);
  }
}
