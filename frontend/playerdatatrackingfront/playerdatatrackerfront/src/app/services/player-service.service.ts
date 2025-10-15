import { Player } from './../entitites/player';
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { ManualTrackedPlayer } from 'src/app/entitites/manual-tracker-player';
import { catchError, map } from 'rxjs/operators';

interface GenericResponse<T> {
  code: number;
  description: string;
  entity?: T;
  entityList?: T[];
}

@Injectable({
  providedIn: 'root'
})
export class PlayerService {
  private apiUrlManualP = 'http://localhost:8080/player';
  private apiUrlIndexalP = 'http://localhost:8080/search';
  private apiUrlIndxPlayer = 'http://localhost:8080/searchPlayer';

  constructor(private http: HttpClient) { }

  getPlayer(id: number): Observable<ManualTrackedPlayer | null> {
    const url = `${this.apiUrlManualP}/${id}`;

    return this.http.get<GenericResponse<ManualTrackedPlayer>>(url).pipe(
      map(response => {
        if (response.code === 0) {
          return response.entity as ManualTrackedPlayer;
        } else {
          console.error(response.description);
          return null;
        }
      }),
      catchError(error => {
        console.error('Error en la llamada GET:', error);
        return of(null);
      })
    );
  }

  getIndxPlayer(id: number): Observable<Player | null> {
    const url = `${this.apiUrlIndxPlayer}/${id}`;

    return this.http.get<GenericResponse<Player>>(url).pipe(
      map(response => {
        if (response.code === 0) {
          return response.entity as Player;
        } else {
          console.error(response.description);
          return null;
        }
      }),
      catchError(error => {
        console.error('Error en la llamada GET:', error);
        return of(null);
      })
    );
  }

  searchPlayers(playerName?: string, teamName?: string): Observable<Player[]> {
    let params: any = {};
    if (playerName) params.player = playerName;
    if (teamName) params.team = teamName;

    return this.http.get<GenericResponse<Player>>(this.apiUrlIndexalP, { params }).pipe(
      map(response => {
        if (response.code === 0) {
          console.log(response.entityList);
          return response.entityList || [];
        } else {
          console.error(response.description);
          return [];
        }
      }),
      catchError(error => {
        console.error('Error en la búsqueda de jugadores:', error);
        return of([]);
      })
    );
  }
}
