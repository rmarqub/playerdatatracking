import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { PlayerMatchRow } from '../entitites/player-stats';
import { GenericResponse } from '../entitites/GenericResponse';
import { catchError, map } from 'rxjs/operators';

export interface PlayerPercentile {
  id: number;
  playerId: number;
  indexId: number;
  /** 0 = global (todos los jugadores de la temporada), N = liga específica */
  leagueId: number;
  season: string;
  pctMinutes: number | null;
  pctRating: number | null;
  pctGoalsP90: number | null;
  pctAssistsP90: number | null;
  pctShotsTotalP90: number | null;
  pctShotsOnP90: number | null;
  pctPassesTotalP90: number | null;
  pctPassesKeyP90: number | null;
  pctPassAccuracy: number | null;
  pctTacklesP90: number | null;
  pctInterceptionsP90: number | null;
  pctDuelsWon: number | null;
  pctDribblesSuccess: number | null;
  pctFoulsDrawnP90: number | null;
  computedAt: string | null;
}

@Injectable({ providedIn: 'root' })
export class PlayerStatsService {
  private baseUrl = 'http://localhost:8080';

  constructor(private http: HttpClient) {}

  getBasicStats(indexId: number): Observable<PlayerMatchRow[]> {
    return this.http
      .get<GenericResponse<PlayerMatchRow>>(`${this.baseUrl}/players/${indexId}/basic-stats`)
      .pipe(map(res => res.entityList ?? []));
  }

  getPlayerPercentiles(indexId: number, season?: string): Observable<PlayerPercentile[]> {
    return this.http
      .post<GenericResponse<PlayerPercentile>>(`${this.baseUrl}/getPlayerPercentiles`, {
        indexId,
        season: season ?? null,
      })
      .pipe(
        map(res => res.entityList ?? []),
        catchError(() => of([]))
      );
  }
}



