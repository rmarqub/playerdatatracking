
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

get statsBySeason(): { season: string; rows: PlayerMatchRow[]; summary: any }[] {
  if (!this.basicStats) return [];
  const map = new Map<string, PlayerMatchRow[]>();
  for (const r of this.basicStats) {
    const key = r.season ?? '-';
    if (!map.has(key)) map.set(key, []);
    map.get(key)!.push(r);
  }
  return Array.from(map.entries())
    .map(([season, rows]) => ({ season, rows, summary: this.computeSummary(rows) }))
    .sort((a, b) => b.season.localeCompare(a.season));
}

private computeSummary(rows: PlayerMatchRow[]): any {
  const sumOf = (key: keyof PlayerMatchRow): number =>
    rows.reduce((acc, r) => acc + ((r[key] as number) ?? 0), 0);

  const avgOf = (key: keyof PlayerMatchRow): string => {
    const valid = rows.filter(r => r[key] !== null && r[key] !== undefined);
    if (!valid.length) return '-';
    const avg = valid.reduce((acc, r) => acc + (r[key] as number), 0) / valid.length;
    return avg.toFixed(2);
  };

  return {
    leagueName: 'TOTAL',
    teamName: '-',
    minutes:      sumOf('minutes'),
    position:     '-',
    rating:       avgOf('rating'),
    goals:        sumOf('goals'),
    assists:      sumOf('assists'),
    shotsOn:      sumOf('shotsOn'),
    shotsTotal:   sumOf('shotsTotal'),
    passesKey:    sumOf('passesKey'),
    passesAcc:    avgOf('passesAcc'),
    passesTotal:  sumOf('passesTotal'),
    dribblesSuc:  sumOf('dribblesSuc'),
    dribblesAtt:  sumOf('dribblesAtt'),
    interceptions:sumOf('interceptions'),
    duelsWon:     sumOf('duelsWon'),
    duelsTotal:   sumOf('duelsTotal'),
    foulsDrawn:   sumOf('foulsDrawn'),
    foulsComm:    sumOf('foulsComm'),
    tacklesTotal: sumOf('tacklesTotal'),
    yc:           sumOf('yc'),
    rc:           sumOf('rc'),
  };
}

sortedBasicStats(rows: PlayerMatchRow[]) {
  if (!rows || !this.basicSort.active) return rows || [];
  const key = this.basicSort.active;
  const dir = this.basicSort.dir === 'asc' ? 1 : -1;

  const coerce = (val: any) => {
    if (val === null || val === undefined || val === '-') return null;
    if (this.numericBasicKeys.has(key)) {
      const n = Number(val);
      return Number.isNaN(n) ? null : n;
    }
    return String(val).toLowerCase();
  };

  const arr = [...rows];
  arr.sort((a: any, b: any) => {
    const va = coerce(a[key]);
    const vb = coerce(b[key]);
    if (va === null && vb === null) return 0;
    if (va === null) return 1;
    if (vb === null) return -1;
    if (va < vb) return -1 * dir;
    if (va > vb) return 1 * dir;
    const t = (Number(b.minutes) || 0) - (Number(a.minutes) || 0);
    if (t !== 0) return t;
    return String(a.teamName || '').localeCompare(String(b.teamName || ''));
  });

  return arr;
}

trackByBasicRow(index: number, r: any): string {
  return `${r.season || ''}|${r.leagueName || ''}|${r.teamName || ''}|${r.minutes ?? ''}`;
}

formatSeason(season: string): string {
  const year = parseInt(season, 10);
  if (isNaN(year)) return season;
  const next = (year + 1) % 100;
  return `${year}/${next.toString().padStart(2, '0')}`;
}

}
