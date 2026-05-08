import {
  Component, OnInit, OnDestroy, AfterViewChecked,
  ChangeDetectorRef, ElementRef, ViewChild
} from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { FormControl } from '@angular/forms';
import { Subscription, EMPTY, of } from 'rxjs';
import { debounceTime, distinctUntilChanged, switchMap, catchError } from 'rxjs/operators';
import Chart from 'chart.js/auto';
import { PlayerService } from '../services/player-service.service';
import { PlayerStatsService, PlayerPercentile } from '../services/player-stats.service';
import { Player } from '../entitites/player';

const RADAR_LABELS = [
  'Minutos', 'Valoración', 'Goles P90', 'Asist. P90',
  'Tiros P90', 'Tiros Puerta P90', 'Pases P90', 'Pases Clave P90',
  'Precisión Pases', 'Tackles P90', 'Intercepciones P90',
  'Duelos Ganados', 'Regates', 'Faltas Rec. P90',
];

const SLOT_COLORS = [
  { bg: 'rgba(54,162,235,0.18)',  border: 'rgba(54,162,235,0.9)'  },
  { bg: 'rgba(255,99,132,0.18)',  border: 'rgba(255,99,132,0.9)'  },
  { bg: 'rgba(75,192,150,0.18)',  border: 'rgba(75,192,150,0.9)'  },
  { bg: 'rgba(255,159,64,0.18)',  border: 'rgba(255,159,64,0.9)'  },
];

interface Slot {
  control: FormControl;
  results: Player[];
  showDropdown: boolean;
  selected: Player | null;
  percentiles: PlayerPercentile[] | null;
  loading: boolean;
}

@Component({
  selector: 'app-compare-players',
  templateUrl: './compare-players.component.html',
  styleUrls: ['./compare-players.component.css']
})
export class ComparePlayersComponent implements OnInit, OnDestroy, AfterViewChecked {

  readonly SLOT_COLORS = SLOT_COLORS;
  slots: Slot[] = [];

  selectedSeason = '';
  availableSeasons: string[] = [];
  scope: 'global' | 'league' = 'global';

  @ViewChild('compareCanvas') compareCanvasRef?: ElementRef<HTMLCanvasElement>;
  private chart: Chart | null = null;
  private needsChartInit = false;
  private subs: Subscription[] = [];

  constructor(
    private route: ActivatedRoute,
    private playerService: PlayerService,
    private statsService: PlayerStatsService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    for (let i = 0; i < 4; i++) {
      const slot: Slot = {
        control: new FormControl(''),
        results: [],
        showDropdown: false,
        selected: null,
        percentiles: null,
        loading: false,
      };
      this.slots.push(slot);

      const sub = slot.control.valueChanges.pipe(
        debounceTime(300),
        distinctUntilChanged(),
        switchMap(q => {
          const query = (q as string | null) ?? '';
          if (query.length < 2) {
            slot.results = [];
            slot.showDropdown = false;
            return EMPTY;
          }
          return this.playerService.searchPlayers(query).pipe(catchError(() => of([])));
        })
      ).subscribe(players => {
        slot.results = players;
        slot.showDropdown = players.length > 0;
        this.cdr.detectChanges();
      });
      this.subs.push(sub);
    }

    const indexId = this.route.snapshot.queryParamMap.get('indexId');
    const name    = this.route.snapshot.queryParamMap.get('name');
    if (indexId) {
      this.slots[0].control.setValue(name ?? '', { emitEvent: false });
      this.slots[0].selected = { fullname: name ?? '', indexId: +indexId } as Player;
      this.loadPercentilesForSlot(0, +indexId);
    }
  }

  ngAfterViewChecked(): void {
    if (this.needsChartInit && this.compareCanvasRef?.nativeElement) {
      this.needsChartInit = false;
      this.renderChart();
    }
  }

  ngOnDestroy(): void {
    this.subs.forEach(s => s.unsubscribe());
    this.chart?.destroy();
  }

  // ── Player selection ─────────────────────────────────────────────

  selectPlayer(slotIdx: number, player: Player): void {
    const slot = this.slots[slotIdx];
    slot.selected = player;
    slot.control.setValue(player.fullname, { emitEvent: false });
    slot.results = [];
    slot.showDropdown = false;
    this.loadPercentilesForSlot(slotIdx, player.indexId);
  }

  clearSlot(slotIdx: number): void {
    const slot = this.slots[slotIdx];
    slot.selected = null;
    slot.control.setValue('', { emitEvent: false });
    slot.percentiles = null;
    slot.results = [];
    slot.showDropdown = false;
    this.updateAvailableSeasons();
    if (!this.hasAnyData) {
      this.chart?.destroy();
      this.chart = null;
    } else {
      this.needsChartInit = true;
      this.cdr.detectChanges();
    }
  }

  onBlur(slotIdx: number): void {
    setTimeout(() => { this.slots[slotIdx].showDropdown = false; }, 150);
  }

  // ── Percentile loading ───────────────────────────────────────────

  private loadPercentilesForSlot(slotIdx: number, indexId: number): void {
    const slot = this.slots[slotIdx];
    slot.loading = true;
    this.statsService.getPlayerPercentiles(indexId).subscribe({
      next: rows => {
        slot.percentiles = rows;
        slot.loading = false;
        this.updateAvailableSeasons();
        this.needsChartInit = true;
        this.cdr.detectChanges();
      },
      error: () => {
        slot.loading = false;
        slot.percentiles = [];
      }
    });
  }

  private updateAvailableSeasons(): void {
    const seasons = new Set<string>();
    for (const slot of this.slots) {
      for (const p of slot.percentiles ?? []) seasons.add(p.season);
    }
    this.availableSeasons = [...seasons].sort((a, b) => b.localeCompare(a));
    if (!this.selectedSeason || !seasons.has(this.selectedSeason)) {
      this.selectedSeason = this.availableSeasons[0] ?? '';
    }
  }

  // ── Chart ────────────────────────────────────────────────────────

  renderChart(): void {
    const canvas = this.compareCanvasRef?.nativeElement;
    if (!canvas) { this.needsChartInit = true; return; }

    const isDark = document.body.classList.contains('dark-mode');
    const textColor  = isDark ? '#e6edf3' : '#333';
    const gridColor  = isDark ? '#30363d' : '#ddd';

    const datasets = this.slots
      .map((slot, i) => {
        if (!slot.selected || !slot.percentiles) return null;
        const row = slot.percentiles.find(
          p => p.season === this.selectedSeason &&
               (this.scope === 'global' ? p.leagueId === 0 : p.leagueId !== 0)
        );
        if (!row) return null;
        return {
          label: slot.selected.fullname,
          data: this.extractValues(row),
          backgroundColor: SLOT_COLORS[i].bg,
          borderColor: SLOT_COLORS[i].border,
          pointBackgroundColor: SLOT_COLORS[i].border,
          borderWidth: 2,
          pointRadius: 3,
        };
      })
      .filter(Boolean) as any[];

    if (this.chart) {
      this.chart.data.datasets = datasets;
      (this.chart.options as any).scales.r.ticks.color = textColor;
      (this.chart.options as any).scales.r.grid.color = gridColor;
      (this.chart.options as any).scales.r.pointLabels.color = textColor;
      (this.chart.options as any).scales.r.angleLines.color = gridColor;
      (this.chart.options as any).plugins.legend.labels.color = textColor;
      this.chart.update();
      return;
    }

    this.chart = new Chart(canvas, {
      type: 'radar',
      data: { labels: RADAR_LABELS, datasets },
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

  onSeasonChange(): void { this.needsChartInit = true; this.cdr.detectChanges(); }
  setScope(s: 'global' | 'league'): void { this.scope = s; this.needsChartInit = true; this.cdr.detectChanges(); }

  get hasAnyData(): boolean {
    return this.slots.some(s => s.selected !== null);
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

  formatSeason(season: string): string {
    const year = parseInt(season, 10);
    if (isNaN(year)) return season;
    return `${year}/${((year + 1) % 100).toString().padStart(2, '0')}`;
  }
}
