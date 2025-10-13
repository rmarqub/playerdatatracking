import { Injectable } from '@angular/core';
import {
  HttpInterceptor, HttpRequest, HttpHandler, HttpEvent, HttpErrorResponse
} from '@angular/common/http';
import { Observable, catchError, throwError } from 'rxjs';
import { Router } from '@angular/router';

@Injectable()
export class CredentialsInterceptor implements HttpInterceptor {
  constructor(private router: Router) {}

  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    const withCreds = req.clone({ withCredentials: true });

    return next.handle(withCreds).pipe(
      catchError((err: HttpErrorResponse) => {
        if (err.status === 401 || err.status === 403) {
          // Si el backend está devolviendo 403 al no autenticado → mandamos a /login
          this.router.navigate(['/login']);
        }
        return throwError(() => err);
      })
    );
  }
}
