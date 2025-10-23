
import { Component, OnInit, ChangeDetectorRef  } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { PlayerService } from '../services/player-service.service';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { PlayerStatsService } from '../services/player-stats.service';
import { PlayerMatchRow } from '../entitites/player-stats';


    const fmtDate = new Intl.DateTimeFormat('es-ES', {
    timeZone: 'Europe/Madrid',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  });

  const fmtDateTime = new Intl.DateTimeFormat('es-ES', {
    timeZone: 'Europe/Madrid',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false
  });

  type BasicKey =
    | 'season' | 'leagueName' | 'teamName' | 'minutes' | 'position' | 'rating'
    | 'goals' | 'assists' | 'shotsOn' | 'shotsTotal' | 'passesKey' | 'passesAcc'
    | 'passesTotal' | 'dribblesSuc' | 'dribblesAtt' | 'interceptions' | 'duelsWon'
    | 'duelsTotal' | 'foulsDrawn' | 'foulsComm' | 'tacklesTotal' | 'yc' | 'rc';
@Component({
  selector: 'app-index-player',
  templateUrl: './index-player.component.html',
  styleUrls: ['./index-player.component.css']
})


export class IndexPlayerComponent implements OnInit {
  player: any = null;
  isFavorite: boolean = false;
  basicStats: PlayerMatchRow[] | null = null;
  private sub?: Subscription;
  photoSrc = 'assets/images/standard-pic.jpg';
  showBasicStats = false;
  loadingBasic = false;
  errorBasic: string | null = null;

  basicSort: { active: BasicKey | null; dir: 'asc' | 'desc' } = {
      active: null,
      dir: 'asc'
  };

  private numericBasicKeys = new Set<BasicKey>([
  'minutes','rating','goals','assists','shotsOn','shotsTotal',
  'passesKey','passesAcc','passesTotal','dribblesSuc','dribblesAtt',
  'interceptions','duelsWon','duelsTotal','foulsDrawn','foulsComm',
  'tacklesTotal','yc','rc'
   ]);

  constructor(
    private route: ActivatedRoute,
    private playerService: PlayerService,
    private cdr: ChangeDetectorRef,
    private statsService: PlayerStatsService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.sub = this.route.paramMap.subscribe(pm => {
      const playerId = Number(pm.get('id'));
      if (!playerId) { this.player = null; this.photoSrc = 'assets/images/standard-pic.jpg'; return; }

      this.playerService.getIndxPlayer(playerId).subscribe({
        next: data => {
          this.player = data ?? null;

          // cache-busting opcional si tu API lo expone
          const v = (this.player as any)?.photoUpdatedAt || (this.player as any)?.lastUpdated || '';
          this.photoSrc = this.buildPhotoUrl(playerId, v);

          console.log('Detalle → URL foto:', this.photoSrc); // Debes verla en consola
          this.cdr.markForCheck();
        },
        error: err => console.error('Error obteniendo jugador:', err)
      });
    });
  }

  toggleFavorite(): void {

    this.isFavorite = !this.isFavorite;


    if (this.player && this.isFavorite) {
      this.router.navigate(['/addplayer'], {
        state: {
          prefill: {
            nombre: this.player.firstname + " " + this.player.lastname,
            club: this.player.team,
            birth: this.player.birth ?? '',
            age: this.player.age ?? null,
            fbrefId: this.player.fbrefId ?? null,
            indexId: this.player.id ?? null
          }
        }
      });
    }
  }

  buildPhotoUrl(id: number, v?: string): string {
    return `http://localhost:8080/players/${id}/photo`;
  }
  onImgError(ev: Event): void {
    (ev.target as HTMLImageElement).src = 'assets/images/standard-pic.jpg';
  }

    private toDate(value: any): Date | null {
    if (!value) return null;
    return value instanceof Date ? value : new Date(value);
  }

  ngOnDestroy(): void { this.sub?.unsubscribe(); }

    get lastUpdatedPretty(): string {
    const d = this.toDate(this.player?.lastUpdated);
    return d ? fmtDateTime.format(d) : '';
  }
  onLoadBasicStats(): void {
    if (!this.player?.indexId) {
      this.errorBasic = 'No se encontró el index_id del jugador.';
      return;
    }
    this.loadingBasic = true;
    this.errorBasic = null;
    this.statsService.getBasicStats(this.player.indexId).subscribe({
      next: rows => {
        this.basicStats = rows;
        this.showBasicStats = true;
        this.loadingBasic = false;
      },
      error: err => {
        this.errorBasic = 'No se pudo cargar la data básica.';
        this.loadingBasic = false;
      }
    });
  }

  // Tipado opcional para las claves ordenables


sortBasic(key: BasicKey): void {
  if (this.basicSort.active === key) {
    this.basicSort.dir = this.basicSort.dir === 'asc' ? 'desc' : 'asc';
  } else {
    this.basicSort.active = key;
    this.basicSort.dir = 'asc';
  }
}

isBasicAsc(key: BasicKey): boolean {
  return this.basicSort.active === key && this.basicSort.dir === 'asc';
}

isBasicDesc(key: BasicKey): boolean {
  return this.basicSort.active === key && this.basicSort.dir === 'desc';
}

sortedBasicStats() {
  if (!this.basicStats || !this.basicSort.active) {
    return this.basicStats || [];
  }
  const key = this.basicSort.active;
  const dir = this.basicSort.dir === 'asc' ? 1 : -1;

  const coerce = (val: any) => {
    if (val === null || val === undefined || val === '-') return null;
    if (this.numericBasicKeys.has(key)) {
      const n = Number(val);
      return Number.isNaN(n) ? null : n;
    }
    // temporada tipo "2023/2024": comparamos como string minúscula
    return String(val).toLowerCase();
  };

  const arr = [...this.basicStats];
  arr.sort((a: any, b: any) => {
    const va = coerce(a[key]);
    const vb = coerce(b[key]);
    if (va === null && vb === null) return 0;
    if (va === null) return 1;  // nulos al final
    if (vb === null) return -1;
    if (va < vb) return -1 * dir;
    if (va > vb) return 1 * dir;
    // desempate estable: por minutos desc y luego por teamName
    const t = (Number(b.minutes) || 0) - (Number(a.minutes) || 0);
    if (t !== 0) return t;
    return String(a.teamName || '').localeCompare(String(b.teamName || ''));
  });

  return arr;
}

// trackBy para filas de stats (reduce re-render)
trackByBasicRow(index: number, r: any): string {
  return `${r.season || ''}|${r.leagueName || ''}|${r.teamName || ''}|${r.minutes ?? ''}`;
}

}
