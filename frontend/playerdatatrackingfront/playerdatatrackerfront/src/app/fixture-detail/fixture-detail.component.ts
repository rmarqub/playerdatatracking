import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin } from 'rxjs';
import {
  FixtureService, Fixture, FixtureEvent, ApiFixtureItem,
  FixtureTeamStats, FixturePlayerStats, FixtureLineupEntry
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

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private fixtureService: FixtureService
  ) {}

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.source = this.route.snapshot.queryParamMap.get('source') ?? '';
    if (!id) {
      this.error = 'ID de partido no válido.';
      this.isLoading = false;
      return;
    }
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
        lineup: this.fixtureService.getFixtureLineup(id)
      }).subscribe({
        next: ({ fixture, events, teamStats, playerStats, lineup }) => {
          this.fixture = fixture;
          this.events = events.sort((a, b) => (a.timeElapsed ?? 0) - (b.timeElapsed ?? 0));
          this.dbTeamStats = teamStats;
          this.dbPlayerStats = playerStats;
          this.dbLineupEntries = lineup;
          this.isLoading = false;
          if (!fixture) this.error = 'No se encontraron datos para este partido.';
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

  // ── Shared helpers ────────────────────────────────────────────────────────

  goBack(): void {
    this.router.navigate(['/searchFixtures']);
  }

  get isLive(): boolean {
    return ['1H', 'HT', '2H', 'ET', 'BT', 'P', 'LIVE'].includes(this.fixture?.statusShort ?? '');
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
}
