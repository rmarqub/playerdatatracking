import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from 'src/enviroment/environment';
import { Observable, map } from 'rxjs';

export interface User {
  username: string;
  roles: string[];
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private base = environment.apiUrl;

  constructor(private http: HttpClient) {}

  me(): Observable<User> {
    return this.http.get<User>(`${this.base}/auth/me`);
  }

  login(username: string, password: string): Observable<boolean> {
    return this.http.post(`${this.base}/auth/login`, { username, password })
      .pipe(map(() => true));
  }

  logout(): Observable<boolean> {
    return this.http.post(`${this.base}/auth/logout`, {})
      .pipe(map(() => true));
  }
}
