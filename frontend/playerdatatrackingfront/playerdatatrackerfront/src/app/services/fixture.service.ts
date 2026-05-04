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
  statusElapsed: number | null;
  homeTeamId: number;
  homeTeamName: string;
  awayTeamId: number;
  awayTeamName: string;
  goalsHome: number | null;
  goalsAway: number | null;
  scoreHtHome: number | null;
  scoreHtAway: number | null;
  scoreEtHome: number | null;
  scoreEtAway: number | null;
  scorePenHome: number | null;
  scorePenAway: number | null;
  referee: string | null;
  venueName: string | null;
  venueCity: string | null;
}

export interface FixtureEvent {
  id: number;
  teamId: number | null;
  timeElapsed: number | null;
  timeExtra: number | null;
  playerId: number | null;
  playerName: string | null;
  assistId: number | null;
  assistName: string | null;
  eventType: string;
  eventDetail: string | null;
  comments: string | null;
}

export interface Torneo {
  id: number;
  name: string;
  studied: boolean;
}

export interface ApiFixtureStatus {
  long: string;
  short: string;
  elapsed: number | null;
  extra: number | null;
}

export interface ApiFixtureTeam {
  id: number;
  name: string;
  logo: string;
  winner: boolean | null;
}

export interface ApiFixtureItem {
  fixture: {
    id: number;
    referee: string | null;
    timezone: string;
    date: string;
    timestamp: number;
    periods: { first: number | null; second: number | null };
    venue: { id: number | null; name: string | null; city: string | null };
    status: ApiFixtureStatus;
  };
  league: {
    id: number;
    name: string;
    country: string;
    logo: string;
    flag: string | null;
    season: number;
    round: string;
  };
  teams: { home: ApiFixtureTeam; away: ApiFixtureTeam };
  goals: { home: number | null; away: number | null };
  score: {
    halftime: { home: number | null; away: number | null };
    fulltime: { home: number | null; away: number | null };
    extratime: { home: number | null; away: number | null };
    penalty: { home: number | null; away: number | null };
  };
  events: any[];
  lineups: any[];
  statistics: any[];
  players: any[];
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

  searchFixturesCombined(teamName: string, leagueIds: number[]): Observable<Fixture[]> {
    const body: any = {};
    if (teamName?.trim()) body.nombre = teamName.trim();
    if (leagueIds?.length > 0) body.leagueIds = leagueIds;
    return this.http.post<GenericResponse<Fixture>>(`${this.base}/searchFixtures`, body).pipe(
      map(r => r.code === 0 ? (r.entityList || []) : []),
      catchError(() => of([]))
    );
  }

  getFixtureById(fixtureId: number): Observable<Fixture | null> {
    return this.http.post<GenericResponse<Fixture>>(`${this.base}/fixtureById`, { id: fixtureId }).pipe(
      map(r => r.code === 0 ? (r.entity || null) : null),
      catchError(() => of(null))
    );
  }

  getFixtureEventsFromDb(fixtureId: number): Observable<FixtureEvent[]> {
    return this.http.post<GenericResponse<FixtureEvent>>(`${this.base}/fixtureEvents`, { id: fixtureId }).pipe(
      map(r => r.code === 0 ? (r.entityList || []) : []),
      catchError(() => of([]))
    );
  }

  getLiveFixturesFromApi(): Observable<ApiFixtureItem[]> {
    return this.http.post<GenericResponse<ApiFixtureItem>>(`${this.base}/liveFixturesApi`, {}).pipe(
      map(r => r.code === 0 ? (r.entityList || []) : []),
      catchError(() => of([]))
    );
  }

  getFixtureDetailFromApi(fixtureId: number): Observable<ApiFixtureItem | null> {
    return this.http.post<GenericResponse<ApiFixtureItem>>(`${this.base}/fixtureDetailApi`, { id: fixtureId }).pipe(
      map(r => r.code === 0 ? (r.entity || null) : null),
      catchError(() => of(null))
    );
  }

  getPlayerIdByIndexId(indexId: number): Observable<number | null> {
    return this.http.post<GenericResponse<number>>(`${this.base}/playerIdByIndexId`, { indexId }).pipe(
      map(r => r.code === 0 && r.entity != null ? r.entity : null),
      catchError(() => of(null))
    );
  }
}
