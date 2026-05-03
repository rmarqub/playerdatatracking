import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { FixtureService, ApiFixtureItem } from '../services/fixture.service';

@Component({
  selector: 'app-fixture-detail',
  templateUrl: './fixture-detail.component.html',
  styleUrls: ['./fixture-detail.component.css']
})
export class FixtureDetailComponent implements OnInit {
  fixture: ApiFixtureItem | null = null;
  isLoading: boolean = true;
  error: string = '';

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private fixtureService: FixtureService
  ) {}

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!id) {
      this.error = 'ID de partido no válido.';
      this.isLoading = false;
      return;
    }
    this.fixtureService.getFixtureDetailFromApi(id).subscribe({
      next: data => {
        this.fixture = data;
        this.isLoading = false;
        if (!data) this.error = 'No se encontraron datos para este partido.';
      },
      error: () => {
        this.error = 'Error al cargar los datos del partido.';
        this.isLoading = false;
      }
    });
  }

  goBack(): void {
    this.router.navigate(['/searchFixtures']);
  }

  get isLive(): boolean {
    const s = this.fixture?.fixture.status.short ?? '';
    return ['1H', 'HT', '2H', 'ET', 'BT', 'P', 'LIVE'].includes(s);
  }

  get statusLabel(): string {
    const s = this.fixture?.fixture.status.short ?? '';
    const elapsed = this.fixture?.fixture.status.elapsed ?? null;
    const long = this.fixture?.fixture.status.long ?? '';
    if (s === '1H' || s === '2H' || s === 'ET') return `${elapsed ?? 0}'`;
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
    const day = String(d.getDate()).padStart(2, '0');
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const year = d.getFullYear();
    const hours = String(d.getHours()).padStart(2, '0');
    const mins = String(d.getMinutes()).padStart(2, '0');
    return `${day}/${month}/${year}  ${hours}:${mins}`;
  }

  getEventIcon(type: string, detail: string): string {
    if (type === 'Goal') return detail?.includes('Own Goal') ? 'own-goal' : detail?.includes('Penalty') ? 'penalty-goal' : 'goal';
    if (type === 'Card') return detail?.includes('Red') ? 'red-card' : detail?.includes('Yellow Red') ? 'yr-card' : 'yellow-card';
    if (type === 'subst') return 'substitution';
    if (type === 'Var') return 'var';
    return 'event';
  }

  isHomeTeamEvent(event: any): boolean {
    return event?.team?.id === this.fixture?.teams.home.id;
  }

  getStatValue(stats: any[], type: string): any {
    return stats?.find((s: any) => s.type === type)?.value ?? '-';
  }

  get homeStats(): any[] {
    return this.fixture?.statistics?.[0]?.statistics ?? [];
  }

  get awayStats(): any[] {
    return this.fixture?.statistics?.[1]?.statistics ?? [];
  }

  get statTypes(): string[] {
    return this.homeStats.map((s: any) => s.type);
  }

  get homeLineup(): any {
    return this.fixture?.lineups?.[0] ?? null;
  }

  get awayLineup(): any {
    return this.fixture?.lineups?.[1] ?? null;
  }

  get sortedEvents(): any[] {
    return [...(this.fixture?.events ?? [])].sort((a, b) => (a.time?.elapsed ?? 0) - (b.time?.elapsed ?? 0));
  }

  get homePlayers(): any[] {
    return this.fixture?.players?.[0]?.players ?? [];
  }

  get awayPlayers(): any[] {
    return this.fixture?.players?.[1]?.players ?? [];
  }
}
