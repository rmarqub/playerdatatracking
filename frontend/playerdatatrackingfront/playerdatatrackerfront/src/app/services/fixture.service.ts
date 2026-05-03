import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';

interface GenericResponse<T> {
  code: number;
  description: string;
  entity?: T;
  entityList?: T[];
}

export interface Fixture {
  id: number;
  leagueId: number;
  leagueName: string;
  season: number;
  round: string;
  matchDate: string;
  matchTimestamp: number;
  statusShort: string;
  statusLong: string;
  statusElapsed: number;
  homeTeamId: number;
  homeTeamName: string;
  awayTeamId: number;
  awayTeamName: string;
  goalsHome: number | null;
  goalsAway: number | null;
  venueName: string;
  venueCity: string;
}

export interface Torneo {
  id: number;
  name: string;
  studied: boolean;
}

@Injectable({
  providedIn: 'root'
})
export class FixtureService {
  private base = 'http://localhost:8080';

  constructor(private http: HttpClient) { }

  getLiveFixtures(): Observable<Fixture[]> {
    return this.http.post<GenericResponse<Fixture>>(`${this.base}/liveFixtures`, {}).pipe(
      map(r => r.code === 0 ? (r.entityList || []) : []),
      catchError(() => of([]))
    );
  }

  getStudiedLeagues(): Observable<Torneo[]> {
    return this.http.post<GenericResponse<Torneo>>(`${this.base}/studiedLeagues`, {}).pipe(
      map(r => r.code === 0 ? (r.entityList || []) : []),
      catchError(() => of([]))
    );
  }

  searchByTeam(teamName: string): Observable<Fixture[]> {
    return this.http.post<GenericResponse<Fixture>>(`${this.base}/searchFixtures`, { nombre: teamName }).pipe(
      map(r => r.code === 0 ? (r.entityList || []) : []),
      catchError(() => of([]))
    );
  }

  searchByLeague(leagueId: number): Observable<Fixture[]> {
    return this.http.post<GenericResponse<Fixture>>(`${this.base}/searchFixtures`, { id: leagueId }).pipe(
      map(r => r.code === 0 ? (r.entityList || []) : []),
      catchError(() => of([]))
    );
  }
}
