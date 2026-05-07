import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin } from 'rxjs';
import {
  FixtureService, Fixture, FixtureEvent, ApiFixtureItem,
  FixtureTeamStats, FixturePlayerStats, FixtureLineupEntry,
  H2HFixtureSummary, H2HComparisonData, MatchPrediction, ContextualAnalysisData
} from '../services/fixture.service';

@Component({
  selector: 'app-fixture-detail',
  templateUrl: './fixture-detail.component.html',
  styleUrls: ['./fixture-detail.component.css']
})
export class FixtureDetailComponent implements OnInit {
  fixture: Fixture | null = null;
  events: FixtureEvent[] = [];
  rawApiItem: ApiFixtureItem | null = null;
  isLoading: boolean = true;
  error: string = '';
  source: string = '';

  dbTeamStats: FixtureTeamStats[] = [];
  dbPlayerStats: FixturePlayerStats[] = [];
  dbLineupEntries: FixtureLineupEntry[] = [];

  showH2H: boolean = false;
  h2hLoading: boolean = false;
  h2hError: string = '';
  h2hFixtures: H2HFixtureSummary[] = [];

  showComparison: boolean = false;
  comparisonLoading: boolean = false;
  h2hComparison: H2HComparisonData | null = null;

  showPrediction: boolean = false;
  predictionLoading: boolean = false;
  predictionError: string = '';
  prediction: MatchPrediction | null = null;

  showAnalysis: boolean = false;
  analysisLoading: boolean = false;
  analysisSaveError: string = '';
  analysisSaved: boolean = false;
  contextualForm: ContextualAnalysisForm = defaultContextualForm();

  readonly scale5 = [1, 2, 3, 4, 5];
  readonly scale3 = [1, 2, 3];

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private fixtureService: FixtureService
  ) {}

  ngOnInit(): void {
    this.route.paramMap.subscribe(params => {
      const id = Number(params.get('id'));
      this.source = this.route.snapshot.queryParamMap.get('source') ?? '';
      this.resetState();
      if (!id) {
        this.error = 'ID de partido no válido.';
        this.isLoading = false;
        return;
      }
      this.loadFixtureData(id);
    });
  }

  private resetState(): void {
    this.fixture = null;
    this.events = [];
    this.rawApiItem = null;
    this.isLoading = true;
    this.error = '';
    this.dbTeamStats = [];
    this.dbPlayerStats = [];
    this.dbLineupEntries = [];
    this.showH2H = false;
    this.h2hLoading = false;
    this.h2hError = '';
    this.h2hFixtures = [];
    this.showComparison = false;
    this.comparisonLoading = false;
    this.h2hComparison = null;
    this.showPrediction = false;
    this.predictionLoading = false;
    this.predictionError = '';
    this.prediction = null;
    this.showAnalysis = false;
    this.analysisLoading = false;
    this.analysisSaveError = '';
    this.analysisSaved = false;
    this.contextualForm = defaultContextualForm();
  }

  private loadFixtureData(id: number): void {
    if (this.source === 'api') {
      this.fixtureService.getFixtureDetailFromApi(id).subscribe({
        next: (item) => {
          if (item) {
            this.rawApiItem = item;
            this.fixture = this.normalizeApiFixture(item);
            this.events = this.normalizeApiEvents(item);
          } else {
            this.error = 'No se encontraron datos para este partido.';
          }
          this.isLoading = false;
        },
        error: () => {
          this.error = 'Error al cargar los datos del partido.';
          this.isLoading = false;
        }
      });
    } else {
      forkJoin({
        fixture: this.fixtureService.getFixtureById(id),
        events: this.fixtureService.getFixtureEventsFromDb(id),
        teamStats: this.fixtureService.getFixtureTeamStats(id),
        playerStats: this.fixtureService.getFixturePlayerStats(id),
        lineup: this.fixtureService.getFixtureLineup(id),
        analysis: this.fixtureService.getContextualAnalysis(id)
      }).subscribe({
        next: ({ fixture, events, teamStats, playerStats, lineup, analysis }) => {
          this.fixture = fixture;
          this.events = events.sort((a, b) => (a.timeElapsed ?? 0) - (b.timeElapsed ?? 0));
          this.dbTeamStats = teamStats;
          this.dbPlayerStats = playerStats;
          this.dbLineupEntries = lineup;
          this.isLoading = false;
          if (!fixture) this.error = 'No se encontraron datos para este partido.';
          if (analysis) this.applyAnalysisToForm(analysis);
        },
        error: () => {
          this.error = 'Error al cargar los datos del partido.';
          this.isLoading = false;
        }
      });
    }
  }

  private normalizeApiFixture(item: ApiFixtureItem): Fixture {
    return {
      id: item.fixture.id,
      leagueId: item.league.id,
      leagueName: item.league.name,
      season: item.league.season,
      round: item.league.round,
      matchDate: item.fixture.date,
      matchTimestamp: item.fixture.timestamp,
      statusShort: item.fixture.status.short,
      statusLong: item.fixture.status.long,
      statusElapsed: item.fixture.status.elapsed,
      homeTeamId: item.teams.home.id,
      homeTeamName: item.teams.home.name,
      awayTeamId: item.teams.away.id,
      awayTeamName: item.teams.away.name,
      goalsHome: item.goals.home,
      goalsAway: item.goals.away,
      scoreHtHome: item.score.halftime.home,
      scoreHtAway: item.score.halftime.away,
      scoreEtHome: item.score.extratime.home,
      scoreEtAway: item.score.extratime.away,
      scorePenHome: item.score.penalty.home,
      scorePenAway: item.score.penalty.away,
      referee: item.fixture.referee,
      venueName: item.fixture.venue.name,
      venueCity: item.fixture.venue.city
    };
  }

  private normalizeApiEvents(item: ApiFixtureItem): FixtureEvent[] {
    if (!item.events?.length) return [];
    return (item.events as any[]).map((ev, idx) => ({
      id: idx,
      teamId: ev.team?.id ?? null,
      timeElapsed: ev.time?.elapsed ?? null,
      timeExtra: ev.time?.extra ?? null,
      playerId: ev.player?.id ?? null,
      playerName: ev.player?.name ?? null,
      assistId: ev.assist?.id ?? null,
      assistName: ev.assist?.name ?? null,
      eventType: ev.type ?? '',
      eventDetail: ev.detail ?? null,
      comments: ev.comments ?? null
    }));
  }

  navigateToPlayer(indexId: number | null, playerName: string | null): void {
    if (indexId == null) return;
    this.fixtureService.getPlayerIdByIndexId(indexId).subscribe(playerId => {
      if (playerId != null) {
        this.router.navigate(['/player', playerId]);
      } else {
        this.router.navigate(['/player-not-found'], { queryParams: { name: playerName ?? '' } });
      }
    });
  }

  // ── API mode getters ──────────────────────────────────────────────────────

  get homeStats(): any[] { return this.rawApiItem?.statistics?.[0]?.statistics ?? []; }
  get awayStats(): any[] { return this.rawApiItem?.statistics?.[1]?.statistics ?? []; }
  get statTypes(): string[] {
    const all = [...this.homeStats, ...this.awayStats].map((s: any) => s.type);
    return [...new Set(all)] as string[];
  }

  get homeLineup(): any | null {
    if (!this.rawApiItem?.lineups?.length) return null;
    const homeId = this.fixture?.homeTeamId;
    return (this.rawApiItem.lineups as any[]).find(l => l.team?.id === homeId) ?? this.rawApiItem.lineups[0] ?? null;
  }
  get awayLineup(): any | null {
    if (!this.rawApiItem?.lineups?.length) return null;
    const awayId = this.fixture?.awayTeamId;
    return (this.rawApiItem.lineups as any[]).find(l => l.team?.id === awayId) ?? this.rawApiItem.lineups[1] ?? null;
  }

  get homePlayers(): any[] {
    if (!this.rawApiItem?.players?.length) return [];
    const homeId = this.fixture?.homeTeamId;
    const team = (this.rawApiItem.players as any[]).find(t => t.team?.id === homeId) ?? this.rawApiItem.players[0];
    return team?.players ?? [];
  }
  get awayPlayers(): any[] {
    if (!this.rawApiItem?.players?.length) return [];
    const awayId = this.fixture?.awayTeamId;
    const team = (this.rawApiItem.players as any[]).find(t => t.team?.id === awayId) ?? this.rawApiItem.players[1];
    return team?.players ?? [];
  }

  getStatValue(stats: any[], type: string): string {
    return stats.find((s: any) => s.type === type)?.value ?? '-';
  }

  // ── DB mode: team stats ───────────────────────────────────────────────────

  get dbHomeTeamStats(): FixtureTeamStats | null {
    return this.dbTeamStats.find(s => s.teamId === this.fixture?.homeTeamId) ?? null;
  }
  get dbAwayTeamStats(): FixtureTeamStats | null {
    return this.dbTeamStats.find(s => s.teamId === this.fixture?.awayTeamId) ?? null;
  }

  get teamStatRows(): Array<{ label: string; homeVal: number | null; awayVal: number | null }> {
    const h = this.dbHomeTeamStats;
    const a = this.dbAwayTeamStats;
    if (!h && !a) return [];
    return [
      { label: 'Posesión (%)',             homeVal: h?.ballPossession   ?? null, awayVal: a?.ballPossession   ?? null },
      { label: 'Tiros a puerta',            homeVal: h?.shotsOnGoal      ?? null, awayVal: a?.shotsOnGoal      ?? null },
      { label: 'Tiros fuera',               homeVal: h?.shotsOffGoal     ?? null, awayVal: a?.shotsOffGoal     ?? null },
      { label: 'Tiros totales',             homeVal: h?.shotsTotal       ?? null, awayVal: a?.shotsTotal       ?? null },
      { label: 'Tiros bloqueados',          homeVal: h?.shotsBlocked     ?? null, awayVal: a?.shotsBlocked     ?? null },
      { label: 'Tiros dentro del área',     homeVal: h?.shotsInsideBox   ?? null, awayVal: a?.shotsInsideBox   ?? null },
      { label: 'Tiros fuera del área',      homeVal: h?.shotsOutsideBox  ?? null, awayVal: a?.shotsOutsideBox  ?? null },
      { label: 'Córners',                   homeVal: h?.cornerKicks      ?? null, awayVal: a?.cornerKicks      ?? null },
      { label: 'Fueras de juego',           homeVal: h?.offsides         ?? null, awayVal: a?.offsides         ?? null },
      { label: 'Faltas',                    homeVal: h?.fouls            ?? null, awayVal: a?.fouls            ?? null },
      { label: 'Tarjetas amarillas',        homeVal: h?.yellowCards      ?? null, awayVal: a?.yellowCards      ?? null },
      { label: 'Tarjetas rojas',            homeVal: h?.redCards         ?? null, awayVal: a?.redCards         ?? null },
      { label: 'Paradas portero',           homeVal: h?.goalkeeperSaves  ?? null, awayVal: a?.goalkeeperSaves  ?? null },
      { label: 'Pases totales',             homeVal: h?.totalPasses      ?? null, awayVal: a?.totalPasses      ?? null },
      { label: 'Pases precisos',            homeVal: h?.passesAccurate   ?? null, awayVal: a?.passesAccurate   ?? null },
      { label: 'Precisión pases (%)',       homeVal: h?.passesPct        ?? null, awayVal: a?.passesPct        ?? null },
      { label: 'Goles esperados (xG)',      homeVal: h?.expectedGoals    ?? null, awayVal: a?.expectedGoals    ?? null },
      { label: 'Goles evitados',            homeVal: h?.goalsPrevented   ?? null, awayVal: a?.goalsPrevented   ?? null },
    ];
  }

  getBarPct(homeVal: number | null, awayVal: number | null, side: 'home' | 'away'): string {
    const h = homeVal != null ? Number(homeVal) : 0;
    const a = awayVal != null ? Number(awayVal) : 0;
    const total = h + a;
    if (total === 0) return '50%';
    return side === 'home'
      ? `${(h / total * 100).toFixed(0)}%`
      : `${(a / total * 100).toFixed(0)}%`;
  }

  // ── DB mode: lineup ───────────────────────────────────────────────────────

  get dbHomeLineupStarters(): FixtureLineupEntry[] {
    return this.dbLineupEntries.filter(l => l.teamId === this.fixture?.homeTeamId && !l.substitute && l.playerId != null);
  }
  get dbHomeLineupSubs(): FixtureLineupEntry[] {
    return this.dbLineupEntries.filter(l => l.teamId === this.fixture?.homeTeamId && l.substitute && l.playerId != null);
  }
  get dbAwayLineupStarters(): FixtureLineupEntry[] {
    return this.dbLineupEntries.filter(l => l.teamId === this.fixture?.awayTeamId && !l.substitute && l.playerId != null);
  }
  get dbAwayLineupSubs(): FixtureLineupEntry[] {
    return this.dbLineupEntries.filter(l => l.teamId === this.fixture?.awayTeamId && l.substitute && l.playerId != null);
  }
  get dbHomeFormation(): string | null {
    return this.dbLineupEntries.find(l => l.teamId === this.fixture?.homeTeamId && l.formation)?.formation ?? null;
  }
  get dbAwayFormation(): string | null {
    return this.dbLineupEntries.find(l => l.teamId === this.fixture?.awayTeamId && l.formation)?.formation ?? null;
  }
  get dbHomeCoach(): string | null {
    return this.dbLineupEntries.find(l => l.teamId === this.fixture?.homeTeamId && l.coachName)?.coachName ?? null;
  }
  get dbAwayCoach(): string | null {
    return this.dbLineupEntries.find(l => l.teamId === this.fixture?.awayTeamId && l.coachName)?.coachName ?? null;
  }

  getRatingForPlayer(playerId: number | null): string {
    if (playerId == null) return '-';
    const ps = this.dbPlayerStats.find(s => s.playerId === playerId);
    return ps?.rating != null ? String(ps.rating) : '-';
  }

  isHighRating(playerId: number | null): boolean {
    if (playerId == null) return false;
    const ps = this.dbPlayerStats.find(s => s.playerId === playerId);
    return ps?.rating != null && Number(ps.rating) >= 7;
  }

  // ── DB mode: player stats table ───────────────────────────────────────────

  get dbHomePlayerStats(): FixturePlayerStats[] {
    return this.dbPlayerStats
      .filter(s => s.teamId === this.fixture?.homeTeamId)
      .sort((a, b) => {
        if (!a.substitute && b.substitute) return -1;
        if (a.substitute && !b.substitute) return 1;
        return (b.minutesPlayed ?? 0) - (a.minutesPlayed ?? 0);
      });
  }
  get dbAwayPlayerStats(): FixturePlayerStats[] {
    return this.dbPlayerStats
      .filter(s => s.teamId === this.fixture?.awayTeamId)
      .sort((a, b) => {
        if (!a.substitute && b.substitute) return -1;
        if (a.substitute && !b.substitute) return 1;
        return (b.minutesPlayed ?? 0) - (a.minutesPlayed ?? 0);
      });
  }

  val(v: any): string {
    if (v == null) return '-';
    return String(v);
  }

  // ── H2H ──────────────────────────────────────────────────────────────────

  toggleH2H(): void {
    if (this.showH2H) {
      this.showH2H = false;
      return;
    }
    this.showH2H = true;
    if (this.h2hFixtures.length > 0 || this.h2hError) return;
    this.h2hLoading = true;
    this.h2hError = '';
    const id = this.fixture?.id;
    if (!id) { this.h2hLoading = false; return; }
    this.fixtureService.getH2HFixtures(id).subscribe({
      next: (data) => {
        this.h2hFixtures = data;
        this.h2hLoading = false;
      },
      error: () => {
        this.h2hError = 'Error al cargar los datos H2H.';
        this.h2hLoading = false;
      }
    });
  }

  loadComparison(): void {
    if (this.showComparison) {
      this.showComparison = false;
      return;
    }
    this.showComparison = true;
    if (this.h2hComparison) return;
    this.comparisonLoading = true;
    const id = this.fixture?.id;
    if (!id) { this.comparisonLoading = false; return; }
    this.fixtureService.getH2HComparison(id).subscribe({
      next: (data) => {
        this.h2hComparison = data;
        this.comparisonLoading = false;
      },
      error: () => {
        this.comparisonLoading = false;
      }
    });
  }

  navigateToFixture(fixtureId: number): void {
    this.router.navigate(['/fixture', fixtureId]);
  }

  h2hResultLabel(m: H2HFixtureSummary): string {
    if (m.goalsHome == null || m.goalsAway == null) return '';
    if (m.goalsHome > m.goalsAway) return 'home-win';
    if (m.goalsAway > m.goalsHome) return 'away-win';
    return 'draw';
  }

  formatH2HDate(dateStr: string | null): string {
    if (!dateStr) return '';
    const d = new Date(dateStr);
    const day   = String(d.getDate()).padStart(2, '0');
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const year  = d.getFullYear();
    return `${day}/${month}/${year}`;
  }

  comparisonRows(): Array<{ label: string; v1: number | null; v2: number | null }> {
    const c = this.h2hComparison;
    if (!c) return [];
    return [
      { label: 'Posesión media (%)',       v1: c.team1AvgPossession,   v2: c.team2AvgPossession   },
      { label: 'Tiros por partido',         v1: c.team1AvgShots,        v2: c.team2AvgShots        },
      { label: 'Tiros a puerta por partido',v1: c.team1AvgShotsOnTarget,v2: c.team2AvgShotsOnTarget},
      { label: 'Córners por partido',       v1: c.team1AvgCorners,      v2: c.team2AvgCorners      },
      { label: 'Faltas por partido',        v1: c.team1AvgFouls,        v2: c.team2AvgFouls        },
      { label: 'Amarillas por partido',     v1: c.team1AvgYellowCards,  v2: c.team2AvgYellowCards  },
      { label: 'xG por partido',            v1: c.team1AvgxG,           v2: c.team2AvgxG           },
    ].filter(r => r.v1 != null || r.v2 != null);
  }

  getCompBarPct(v1: number | null, v2: number | null, side: 1 | 2): string {
    const a = v1 != null ? Number(v1) : 0;
    const b = v2 != null ? Number(v2) : 0;
    const total = a + b;
    if (total === 0) return '50%';
    return side === 1 ? `${(a / total * 100).toFixed(0)}%` : `${(b / total * 100).toFixed(0)}%`;
  }

  // ── Shared helpers ────────────────────────────────────────────────────────

  goBack(): void {
    this.router.navigate(['/searchFixtures']);
  }

  get isLive(): boolean {
    return ['1H', 'HT', '2H', 'ET', 'BT', 'P', 'LIVE'].includes(this.fixture?.statusShort ?? '');
  }

  get isFinished(): boolean {
    return this.fixture?.statusShort === 'FT';
  }

  get statusLabel(): string {
    const s = this.fixture?.statusShort ?? '';
    const elapsed = this.fixture?.statusElapsed ?? null;
    const long = this.fixture?.statusLong ?? '';
    if (['1H', '2H', 'ET'].includes(s)) return `${elapsed ?? 0}'`;
    if (s === 'HT') return 'MT';
    if (s === 'FT') return 'FT';
    if (s === 'NS') return 'No iniciado';
    if (s === 'PST') return 'Aplazado';
    if (s === 'CANC') return 'Cancelado';
    return long || s;
  }

  formatDate(dateString: string): string {
    if (!dateString) return '';
    const d = new Date(dateString);
    const day   = String(d.getDate()).padStart(2, '0');
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const year  = d.getFullYear();
    const hours = String(d.getHours()).padStart(2, '0');
    const mins  = String(d.getMinutes()).padStart(2, '0');
    return `${day}/${month}/${year}  ${hours}:${mins}`;
  }

  getEventIcon(type: string, detail: string | null): string {
    if (type === 'Goal') return detail?.includes('Own Goal') ? 'own-goal' : detail?.includes('Penalty') ? 'penalty-goal' : 'goal';
    if (type === 'Card') return detail?.includes('Red') || detail?.includes('Yellow Red') ? 'red-card' : 'yellow-card';
    if (type === 'subst') return 'substitution';
    if (type === 'Var') return 'var';
    return 'event';
  }

  isHomeTeamEvent(event: FixtureEvent): boolean {
    return event.teamId === this.fixture?.homeTeamId;
  }

  // ── Contextual Analysis ───────────────────────────────────────────────────

  toggleAnalysis(): void {
    this.showAnalysis = !this.showAnalysis;
  }

  private applyAnalysisToForm(data: ContextualAnalysisData): void {
    this.analysisSaved = true;
    this.contextualForm = {
      home: {
        currentForm:         data.homeCurrentForm,
        stadiumAtmosphere:   data.homeStadiumAtmosphere,
        defensiveBlock:      data.homeDefensiveBlock,
        offensiveRhythm:     data.homeOffensiveRhythm,
        teamNeeds:           data.homeTeamNeeds,
        setPieces:           data.homeSetPieces,
        fatigue:             data.homeFatigue,
        unavailablePlayers:  data.homeUnavailablePlayers ?? [],
        unavailableInput:    '',
      },
      away: {
        currentForm:         data.awayCurrentForm,
        stadiumAtmosphere:   data.awayStadiumAtmosphere,
        defensiveBlock:      data.awayDefensiveBlock,
        offensiveRhythm:     data.awayOffensiveRhythm,
        teamNeeds:           data.awayTeamNeeds,
        setPieces:           data.awaySetPieces,
        fatigue:             data.awayFatigue,
        unavailablePlayers:  data.awayUnavailablePlayers ?? [],
        unavailableInput:    '',
      },
      notes: data.notes ?? '',
    };
  }

  saveAnalysis(): void {
    const id = this.fixture?.id;
    if (!id) return;
    this.analysisLoading = true;
    this.analysisSaveError = '';
    const f = this.contextualForm;
    this.fixtureService.saveContextualAnalysis(id, {
      homeCurrentForm:        f.home.currentForm,
      homeStadiumAtmosphere:  f.home.stadiumAtmosphere,
      homeDefensiveBlock:     f.home.defensiveBlock,
      homeOffensiveRhythm:    f.home.offensiveRhythm,
      homeTeamNeeds:          f.home.teamNeeds,
      homeSetPieces:          f.home.setPieces,
      homeFatigue:            f.home.fatigue,
      homeUnavailablePlayers: f.home.unavailablePlayers,
      awayCurrentForm:        f.away.currentForm,
      awayStadiumAtmosphere:  f.away.stadiumAtmosphere,
      awayDefensiveBlock:     f.away.defensiveBlock,
      awayOffensiveRhythm:    f.away.offensiveRhythm,
      awayTeamNeeds:          f.away.teamNeeds,
      awaySetPieces:          f.away.setPieces,
      awayFatigue:            f.away.fatigue,
      awayUnavailablePlayers: f.away.unavailablePlayers,
      notes:                  f.notes,
    }).subscribe({
      next: (data) => {
        this.analysisLoading = false;
        if (data) {
          this.analysisSaved = true;
        } else {
          this.analysisSaveError = 'Error al guardar el análisis.';
        }
      },
      error: () => {
        this.analysisLoading = false;
        this.analysisSaveError = 'Error de conexión al guardar el análisis.';
      }
    });
  }

  addUnavailablePlayer(team: 'home' | 'away'): void {
    const ctx = this.contextualForm[team];
    const name = ctx.unavailableInput.trim();
    if (!name || ctx.unavailablePlayers.includes(name)) return;
    ctx.unavailablePlayers = [...ctx.unavailablePlayers, name];
    ctx.unavailableInput = '';
  }

  removeUnavailablePlayer(team: 'home' | 'away', index: number): void {
    const ctx = this.contextualForm[team];
    ctx.unavailablePlayers = ctx.unavailablePlayers.filter((_, i) => i !== index);
  }

  onUnavailableKeydown(event: KeyboardEvent, team: 'home' | 'away'): void {
    if (event.key === 'Enter') {
      event.preventDefault();
      this.addUnavailablePlayer(team);
    }
  }

  resetAnalysisForm(): void {
    this.contextualForm = defaultContextualForm();
  }

  // ── Prediction ────────────────────────────────────────────────────────────

  loadPrediction(): void {
    if (this.showPrediction) {
      this.showPrediction = false;
      return;
    }
    this.showPrediction = true;
    if (this.prediction || this.predictionError) return;
    this.predictionLoading = true;
    const id = this.fixture?.id;
    if (!id) { this.predictionLoading = false; return; }
    this.fixtureService.getMatchPrediction(id).subscribe({
      next: (data) => {
        this.prediction = data;
        this.predictionLoading = false;
        if (!data) this.predictionError = 'No se pudo obtener la predicción.';
      },
      error: () => {
        this.predictionError = 'Error al calcular la predicción. Asegúrate de que el servicio de predicción está activo (puerto 8001).';
        this.predictionLoading = false;
      }
    });
  }

  pct(val: number | null | undefined): string {
    if (val == null) return '-';
    return `${(val * 100).toFixed(1)}%`;
  }

  confidenceLabel(conf: number | null | undefined): string {
    if (conf == null) return '';
    if (conf >= 0.18) return 'Alta';
    if (conf >= 0.08) return 'Media';
    return 'Baja';
  }

  confidenceClass(conf: number | null | undefined): string {
    if (conf == null) return '';
    if (conf >= 0.18) return 'conf-high';
    if (conf >= 0.08) return 'conf-mid';
    return 'conf-low';
  }
}

// ── Contextual Analysis types ─────────────────────────────────────────────────

export interface TeamContext {
  currentForm: number;         // 1-5: racha reciente más allá de los datos estadísticos
  stadiumAtmosphere: number;   // 1-5: apoyo del estadio (local) / impacto del ambiente (visitante)
  defensiveBlock: number;      // 1-5: bloque bajo → presión alta
  offensiveRhythm: number;     // 1-5: posesión/control → transición/vertical
  teamNeeds: number;           // 1-5: sin urgencia → urgencia máxima (descenso, título)
  setPieces: number;           // 1-3: peligrosidad a balón parado
  fatigue: number;             // 1-5: descansado → muy cargado (acumulación de partidos)
  unavailablePlayers: string[];
  unavailableInput: string;
}

export interface ContextualAnalysisForm {
  home: TeamContext;
  away: TeamContext;
  notes: string;
}

function defaultTeamContext(): TeamContext {
  return {
    currentForm: 3,
    stadiumAtmosphere: 3,
    defensiveBlock: 3,
    offensiveRhythm: 3,
    teamNeeds: 3,
    setPieces: 2,
    fatigue: 3,
    unavailablePlayers: [],
    unavailableInput: '',
  };
}

function defaultContextualForm(): ContextualAnalysisForm {
  return { home: defaultTeamContext(), away: defaultTeamContext(), notes: '' };
}
