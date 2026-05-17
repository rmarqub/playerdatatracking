import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from 'src/enviroment/environment';
import { CryptoService } from '../services/crypto.service';
import { BehaviorSubject, Observable, of, from } from 'rxjs';
import { catchError, map, switchMap, tap } from 'rxjs/operators';

export interface User {
  id?: number;
  username: string;
  roles: string[];
}

export function isAdmin(user: User | null): boolean {
  return user?.roles?.includes('admin') ?? false;
}

export interface UserInfo {
  id: number;
  username: string;
  role: string;
  password: string | null;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private base = environment.apiUrl;
    // Caché en memoria del usuario + timestamp
  private user$ = new BehaviorSubject<User | null>(null);
  private lastFetch = 0; // epoch ms de última validación exitosa

  private STALE_MS = 2 * 60 * 1000;


  constructor(private http: HttpClient, private crypto: CryptoService) {}

  me(): Observable<User> {
    return this.http.get<User>(`${this.base}/auth/me`);
  }

  private ensurePubKey(): Observable<void> {
  return this.http.get<{kid:string, pem:string, alg:string}>(`${this.base}/auth/pubkey`)
    .pipe(
      switchMap(({ kid, pem }) => from(this.crypto.loadPublicKeyFromPem(pem, kid)))
    );
  }

  login(username: string, password: string): Observable<boolean> {
    return this.ensurePubKey().pipe(
      switchMap(() => from(this.crypto.encryptPassword(password))),
      switchMap((pwdB64) => this.http.post(`${this.base}/auth/login`, {
        username,
        pwd: pwdB64,
        kid: this.crypto.getKid()
      }, { withCredentials: true })),
      switchMap(() => this.ensureFreshUser()),
      map(() => true)
    );
  }

  logout(): Observable<boolean> {
  return this.http.post(`${this.base}/auth/logout`, {})
    .pipe(
      tap(() => {
        this.user$.next(null);
        this.lastFetch = 0;
      }),
      map(() => true)
    );
  }

  register(username: string, password: string, role: string = 'user') {
    return this.ensurePubKey().pipe(
      switchMap(() => from(this.crypto.encryptPassword(password))),
      switchMap((pwdB64) =>
        this.http.post(`${this.base}/auth/register`, {
          username,
          pwd: pwdB64,
          kid: this.crypto.getKid(),
          role
        }, { withCredentials: true })
      )
    );
  }

  getUsers(): Observable<UserInfo[]> {
    return this.http.get<UserInfo[]>(`${this.base}/auth/users`, { withCredentials: true });
  }

  createUser(username: string, password: string, role: string): Observable<any> {
    return this.ensurePubKey().pipe(
      switchMap(() => from(this.crypto.encryptPassword(password))),
      switchMap((pwdB64) =>
        this.http.post(`${this.base}/auth/register`, {
          username,
          pwd: pwdB64,
          kid: this.crypto.getKid(),
          role
        }, { withCredentials: true })
      )
    );
  }


    /** Devuelve el usuario en caché (puede ser null) */
  getCachedUser(): User | null {
    return this.user$.value;
  }

    userWithBackgroundRevalidate(): Observable<User | null> {
    const cached = this.user$.value;
    const age = Date.now() - this.lastFetch;

    // 1) Emitimos inmediatamente el cache (aunque sea null)
    const immediate$ = of(cached);

    // 2) Si está “viejo” o no hay cache, intentamos revalidar en segundo plano
    const needsRefresh = !cached || age > this.STALE_MS;

    const refresh$ = needsRefresh
      ? this.me().pipe(
          tap(u => {
            this.user$.next(u);
            this.lastFetch = Date.now();
          }),
          catchError(() => {
            // Si falla, invalidamos cache
            this.user$.next(null);
            this.lastFetch = 0;
            return of(null);
          })
        )
      : of(null);

    // Emitimos el cache y luego (si corresponde) el resultado de la revalidación
    return immediate$.pipe(
      switchMap(first => refresh$.pipe(
        map(ref => ref ?? first) // si la revalidación no corrió, devolvemos el primero
      ))
    );
  }

  /** Forzar revalidación síncrona (para primera carga de una ruta protegida) */
  ensureFreshUser(): Observable<User> {
    return this.me().pipe(
      tap(u => {
        this.user$.next(u);
        this.lastFetch = Date.now();
      })
    );
  }



}
