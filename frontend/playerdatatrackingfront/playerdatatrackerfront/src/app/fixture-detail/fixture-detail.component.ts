import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin } from 'rxjs';
import { FixtureService, Fixture, FixtureEvent, ApiFixtureItem } from '../services/fixture.service';

@Component({
  selector: 'app-fixture-detail',
  templateUrl: './fixture-detail.component.html',
  styleUrls: ['./fixture-detail.component.css']
})
export class FixtureDetailComponent implements OnInit {
  fixture: Fixture | null = null;
  events: FixtureEvent[] = [];
  isLoading: boolean = true;
  error: string = '';
  source: string = '';

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
        events: this.fixtureService.getFixtureEventsFromDb(id)
      }).subscribe({
        next: ({ fixture, events }) => {
          this.fixture = fixture;
          this.events = events.sort((a, b) => (a.timeElapsed ?? 0) - (b.timeElapsed ?? 0));
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
