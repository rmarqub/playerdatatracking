import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PlayerMatchRow } from '../entitites/player-stats';
import { GenericResponse } from '../entitites/GenericResponse';
import { map } from 'rxjs';


@Injectable({ providedIn: 'root' })
export class PlayerStatsService {
  private baseUrl = 'http://localhost:8080';

  constructor(private http: HttpClient) {}

  getBasicStats(indexId: number): Observable<PlayerMatchRow[]> {
    return this.http
      .get<GenericResponse<PlayerMatchRow>>(`${this.baseUrl}/players/${indexId}/basic-stats`)
      .pipe(map(res => res.entityList ?? []));
  }
}



