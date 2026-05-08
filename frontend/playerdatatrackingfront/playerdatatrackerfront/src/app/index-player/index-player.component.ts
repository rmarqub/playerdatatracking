
import { Component, OnInit, OnDestroy, AfterViewChecked, ChangeDetectorRef, ElementRef, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { PlayerService } from '../services/player-service.service';
import { PlayerStatsService, PlayerPercentile } from '../services/player-stats.service';
import { PlayerMatchRow } from '../entitites/player-stats';
import Chart from 'chart.js/auto';

const fmtDate = new Intl.DateTimeFormat('es-ES', {
  timeZone: 'Europe/Madrid', year: 'numeric', month: '2-digit', day: '2-digit'
});

const fmtDateTime = new Intl.DateTimeFormat('es-ES', {
  timeZone: 'Europe/Madrid', year: 'numeric', month: '2-digit', day: '2-digit',
  hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false
});

const RADAR_LABELS = [
  'Minutos', 'Valoración', 'Goles P90', 'Asist. P90',
  'Tiros P90', 'Tiros Puerta P90', 'Pases P90', 'Pases Clave P90',
  'Precisión Pases', 'Tackles P90', 'Intercepciones P90',
  'Duelos Ganados', 'Regates', 'Faltas Rec. P90',
];

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
export class IndexPlayerComponent implements OnInit, OnDestroy, AfterViewChecked {
  player: any = null;
  isFavorite = false;
  basicStats: PlayerMatchRow[] | null = null;
  private sub?: Subscription;
  photoSrc = 'assets/images/standard-pic.jpg';
  showBasicStats = false;
  loadingBasic = false;
  errorBasic: string | null = null;

  basicSort: { active: BasicKey | null; dir: 'asc' | 'desc' } = { active: null, dir: 'asc' };
  private numericBasicKeys = new Set<BasicKey>([
    'minutes', 'rating', 'goals', 'assists', 'shotsOn', 'shotsTotal',
    'passesKey', 'passesAcc', 'passesTotal', 'dribblesSuc', 'dribblesAtt',
    'interceptions', 'duelsWon', 'duelsTotal', 'foulsDrawn', 'foulsComm',
    'tacklesTotal', 'yc', 'rc'
  ]);

  // ── Percentile panel ────────────────────────────────────────────
  @ViewChild('radarCanvas') radarCanvasRef?: ElementRef<HTMLCanvasElement>;

  showPercentilePanel = false;
  percentileLoading = false;
  percentileError: string | null = null;
  allPercentiles: PlayerPercentile[] = [];
  availableSeasons: string[] = [];
  selectedPercentileSeason = '';
  percentileScope: 'global' | 'league' = 'global';
  private radarChart: Chart | null = null;
  private needsChartInit = false;

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
          const v = (this.player as any)?.photoUpdatedAt || (this.player as any)?.lastUpdated || '';
          this.photoSrc = this.buildPhotoUrl(playerId, v);
          this.cdr.markForCheck();
        },
        error: err => console.error('Error obteniendo jugador:', err)
      });
    });
  }

  ngAfterViewChecked(): void {
    if (this.needsChartInit && this.radarCanvasRef?.nativeElement) {
      this.needsChartInit = false;
      this.renderChart();
    }
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
    this.radarChart?.destroy();
  }

  // ── Basic stats ──────────────────────────────────────────────────

  onLoadBasicStats(): void {
    if (!this.player?.indexId) { this.errorBasic = 'No se encontró el index_id del jugador.'; return; }
    this.loadingBasic = true;
    this.errorBasic = null;
    this.statsService.getBasicStats(this.player.indexId).subscribe({
      next: rows => { this.basicStats = rows; this.showBasicStats = true; this.loadingBasic = false; },
      error: () => { this.errorBasic = 'No se pudo cargar la data básica.'; this.loadingBasic = false; }
    });
  }

  // ── Percentile panel ─────────────────────────────────────────────

  onLoadPercentiles(): void {
    if (!this.player?.indexId) { this.percentileError = 'No se encontró el index_id del jugador.'; return; }
    if (this.showPercentilePanel) { this.showPercentilePanel = false; return; }

    this.percentileLoading = true;
    this.percentileError = null;
    this.statsService.getPlayerPercentiles(this.player.indexId).subscribe({
      next: rows => {
        this.allPercentiles = rows;
        const seasons = [...new Set(rows.map(r => r.season))].sort((a, b) => b.localeCompare(a));
        this.availableSeasons = seasons;
        this.selectedPercentileSeason = seasons[0] ?? '';
        this.percentileScope = 'global';
        this.percentileLoading = false;
        this.showPercentilePanel = true;
        this.needsChartInit = true;
      },
      error: () => {
        this.percentileError = 'No se pudieron cargar los percentiles.';
        this.percentileLoading = false;
      }
    });
  }

  onPercentileSeasonChange(): void { this.renderChart(); }

  onPercentileScopeChange(scope: 'global' | 'league'): void {
    this.percentileScope = scope;
    this.renderChart();
  }

  goToCompare(): void {
    this.router.navigate(['/compare-players'], {
      queryParams: { indexId: this.player?.indexId, name: this.player?.fullname }
    });
  }

  get currentPercentileRow(): PlayerPercentile | null {
    return this.allPercentiles.find(
      r => r.season === this.selectedPercentileSeason &&
           (this.percentileScope === 'global' ? r.leagueId === 0 : r.leagueId !== 0)
    ) ?? null;
  }

  get noPercentileData(): boolean {
    return this.showPercentilePanel && !this.percentileLoading && !this.currentPercentileRow;
  }

  private renderChart(): void {
    const canvas = this.radarCanvasRef?.nativeElement;
    if (!canvas) { this.needsChartInit = true; return; }

    const row = this.currentPercentileRow;
    if (!row) {
      this.radarChart?.destroy();
      this.radarChart = null;
      return;
    }

    const values = this.extractValues(row);
    const label = this.player?.fullname ?? 'Jugador';
    const isDark = document.body.classList.contains('dark-mode');
    const textColor = isDark ? '#e6edf3' : '#333';
    const gridColor = isDark ? '#30363d' : '#ddd';

    if (this.radarChart) {
      this.radarChart.data.datasets[0].data = values;
      (this.radarChart.data.datasets[0] as any).label = label;
      this.radarChart.update();
      return;
    }

    this.radarChart = new Chart(canvas, {
      type: 'radar',
      data: {
        labels: RADAR_LABELS,
        datasets: [{
          label,
          data: values,
          backgroundColor: 'rgba(205, 133, 63, 0.18)',
          borderColor: 'rgba(205, 133, 63, 0.9)',
          pointBackgroundColor: 'rgba(205, 133, 63, 0.9)',
          borderWidth: 2,
          pointRadius: 3,
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: true,
        scales: {
          r: {
            min: 0, max: 100,
            ticks: { stepSize: 20, color: textColor, backdropColor: 'transparent' },
            grid: { color: gridColor },
            pointLabels: { color: textColor, font: { size: 11 } },
            angleLines: { color: gridColor },
          }
        },
        plugins: { legend: { labels: { color: textColor } } }
      }
    });
  }

  private extractValues(p: PlayerPercentile): number[] {
    return [
      p.pctMinutes ?? 0, p.pctRating ?? 0, p.pctGoalsP90 ?? 0,
      p.pctAssistsP90 ?? 0, p.pctShotsTotalP90 ?? 0, p.pctShotsOnP90 ?? 0,
      p.pctPassesTotalP90 ?? 0, p.pctPassesKeyP90 ?? 0, p.pctPassAccuracy ?? 0,
      p.pctTacklesP90 ?? 0, p.pctInterceptionsP90 ?? 0, p.pctDuelsWon ?? 0,
      p.pctDribblesSuccess ?? 0, p.pctFoulsDrawnP90 ?? 0,
    ];
  }

  // ── Misc ─────────────────────────────────────────────────────────

  toggleFavorite(): void {
    this.isFavorite = !this.isFavorite;
    if (this.player && this.isFavorite) {
      this.router.navigate(['/addplayer'], {
        state: {
          prefill: {
            nombre: this.player.firstname + ' ' + this.player.lastname,
            club: this.player.team, birth: this.player.birth ?? '',
            age: this.player.age ?? null, fbrefId: this.player.fbrefId ?? null,
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

  get lastUpdatedPretty(): string {
    const d = this.toDate(this.player?.lastUpdated);
    return d ? fmtDateTime.format(d) : '';
  }

  sortBasic(key: BasicKey): void {
    if (this.basicSort.active === key) {
      this.basicSort.dir = this.basicSort.dir === 'asc' ? 'desc' : 'asc';
    } else {
      this.basicSort.active = key;
      this.basicSort.dir = 'asc';
    }
  }

  isBasicAsc(key: BasicKey): boolean { return this.basicSort.active === key && this.basicSort.dir === 'asc'; }
  isBasicDesc(key: BasicKey): boolean { return this.basicSort.active === key && this.basicSort.dir === 'desc'; }

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
    const sumOf = (key: keyof PlayerMatchRow) => rows.reduce((acc, r) => acc + ((r[key] as number) ?? 0), 0);
    const avgOf = (key: keyof PlayerMatchRow): string => {
      const valid = rows.filter(r => r[key] !== null && r[key] !== undefined);
      if (!valid.length) return '-';
      return (valid.reduce((acc, r) => acc + (r[key] as number), 0) / valid.length).toFixed(2);
    };
    return {
      leagueName: 'TOTAL', teamName: '-',
      minutes: sumOf('minutes'), position: '-', rating: avgOf('rating'),
      goals: sumOf('goals'), assists: sumOf('assists'), shotsOn: sumOf('shotsOn'),
      shotsTotal: sumOf('shotsTotal'), passesKey: sumOf('passesKey'), passesAcc: avgOf('passesAcc'),
      passesTotal: sumOf('passesTotal'), dribblesSuc: sumOf('dribblesSuc'), dribblesAtt: sumOf('dribblesAtt'),
      interceptions: sumOf('interceptions'), duelsWon: sumOf('duelsWon'), duelsTotal: sumOf('duelsTotal'),
      foulsDrawn: sumOf('foulsDrawn'), foulsComm: sumOf('foulsComm'), tacklesTotal: sumOf('tacklesTotal'),
      yc: sumOf('yc'), rc: sumOf('rc'),
    };
  }

  sortedBasicStats(rows: PlayerMatchRow[]) {
    if (!rows || !this.basicSort.active) return rows || [];
    const key = this.basicSort.active;
    const dir = this.basicSort.dir === 'asc' ? 1 : -1;
    const coerce = (val: any) => {
      if (val === null || val === undefined || val === '-') return null;
      if (this.numericBasicKeys.has(key)) { const n = Number(val); return Number.isNaN(n) ? null : n; }
      return String(val).toLowerCase();
    };
    return [...rows].sort((a: any, b: any) => {
      const va = coerce(a[key]), vb = coerce(b[key]);
      if (va === null && vb === null) return 0;
      if (va === null) return 1;
      if (vb === null) return -1;
      if (va < vb) return -1 * dir;
      if (va > vb) return 1 * dir;
      return 0;
    });
  }

  trackByBasicRow(_: number, r: any): string {
    return `${r.season || ''}|${r.leagueName || ''}|${r.teamName || ''}|${r.minutes ?? ''}`;
  }

  formatSeason(season: string): string {
    const year = parseInt(season, 10);
    if (isNaN(year)) return season;
    return `${year}/${((year + 1) % 100).toString().padStart(2, '0')}`;
  }
}
