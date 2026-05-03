import { Component, HostListener, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { FixtureService, Fixture, Torneo, ApiFixtureItem } from '../services/fixture.service';

@Component({
  selector: 'app-search-fixtures',
  templateUrl: './search-fixtures.component.html',
  styleUrls: ['./search-fixtures.component.css']
})
export class SearchFixturesComponent implements OnInit {
  // ── DB search ──────────────────────────────────────────────────────────────
  teamName: string = '';
  selectedLeagueId: number | null = null;
  studiedLeagues: Torneo[] = [];
  fixtures: Fixture[] = [];
  isLoadingSearch: boolean = false;
  searchPerformed: boolean = false;

  // ── Live API ───────────────────────────────────────────────────────────────
  liveFixturesApi: ApiFixtureItem[] = [];
  isLoadingLive: boolean = false;
  liveLoaded: boolean = false;
  liveError: string = '';

  // ── Filters ────────────────────────────────────────────────────────────────
  availableCountries: string[] = [];
  availableLeagues: { id: number; name: string; logo: string }[] = [];
  selectedCountries = new Set<string>();
  selectedLeagues = new Set<number>();
  countriesDropdownOpen = false;
  leaguesDropdownOpen = false;

  constructor(private fixtureService: FixtureService, private router: Router) {}

  ngOnInit(): void {
    this.fixtureService.getStudiedLeagues().subscribe(leagues => {
      this.studiedLeagues = leagues;
    });
  }

  // ── Close dropdowns on outside click ──────────────────────────────────────
  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    const target = event.target as HTMLElement;
    if (!target.closest?.('.filter-dropdown-wrap')) {
      this.countriesDropdownOpen = false;
      this.leaguesDropdownOpen = false;
    }
  }

  // ── Live fixtures ──────────────────────────────────────────────────────────
  loadLiveFixtures(): void {
    this.isLoadingLive = true;
    this.liveLoaded = false;
    this.liveError = '';
    this.fixtureService.getLiveFixturesFromApi().subscribe({
      next: fixtures => {
        this.liveFixturesApi = fixtures;
        this.selectedCountries = new Set();
        this.selectedLeagues = new Set();
        this.buildFilterOptions();
        this.isLoadingLive = false;
        this.liveLoaded = true;
      },
      error: () => {
        this.liveError = 'Error al conectar con la API externa.';
        this.isLoadingLive = false;
        this.liveLoaded = true;
      }
    });
  }

  private buildFilterOptions(): void {
    const countries = new Set<string>();
    const leagueMap = new Map<number, { id: number; name: string; logo: string }>();
    this.liveFixturesApi.forEach(f => {
      if (f.league.country) countries.add(f.league.country);
      if (!leagueMap.has(f.league.id))
        leagueMap.set(f.league.id, { id: f.league.id, name: f.league.name, logo: f.league.logo ?? '' });
    });
    this.availableCountries = [...countries].sort();
    this.availableLeagues = [...leagueMap.values()].sort((a, b) => a.name.localeCompare(b.name));
  }

  // ── Filter toggles ─────────────────────────────────────────────────────────
  toggleCountriesDropdown(): void {
    this.leaguesDropdownOpen = false;
    this.countriesDropdownOpen = !this.countriesDropdownOpen;
  }

  toggleLeaguesDropdown(): void {
    this.countriesDropdownOpen = false;
    this.leaguesDropdownOpen = !this.leaguesDropdownOpen;
  }

  toggleCountry(country: string): void {
    const next = new Set(this.selectedCountries);
    next.has(country) ? next.delete(country) : next.add(country);
    this.selectedCountries = next;
  }

  toggleLeague(leagueId: number): void {
    const next = new Set(this.selectedLeagues);
    next.has(leagueId) ? next.delete(leagueId) : next.add(leagueId);
    this.selectedLeagues = next;
  }

  clearFilters(): void {
    this.selectedCountries = new Set();
    this.selectedLeagues = new Set();
  }

  // ── Filtered list (applied to the live grid) ───────────────────────────────
  get filteredLiveFixtures(): ApiFixtureItem[] {
    return this.liveFixturesApi.filter(f => {
      const countryOk = this.selectedCountries.size === 0 || this.selectedCountries.has(f.league.country);
      const leagueOk  = this.selectedLeagues.size  === 0 || this.selectedLeagues.has(f.league.id);
      return countryOk && leagueOk;
    });
  }

  get hasActiveFilters(): boolean {
    return this.selectedCountries.size > 0 || this.selectedLeagues.size > 0;
  }

  get countriesLabel(): string {
    if (this.selectedCountries.size === 0) return 'Países';
    if (this.selectedCountries.size === 1) return [...this.selectedCountries][0];
    return `${this.selectedCountries.size} países`;
  }

  get leaguesLabel(): string {
    if (this.selectedLeagues.size === 0) return 'Competición';
    if (this.selectedLeagues.size === 1)
      return this.availableLeagues.find(l => this.selectedLeagues.has(l.id))?.name ?? '1 comp.';
    return `${this.selectedLeagues.size} competiciones`;
  }

  // ── Navigation ─────────────────────────────────────────────────────────────
  navigateToFixture(fixtureId: number): void {
    this.router.navigate(['/fixture', fixtureId]);
  }

  // ── DB search ──────────────────────────────────────────────────────────────
  searchByTeam(): void {
    if (!this.teamName.trim()) return;
    this.isLoadingSearch = true;
    this.searchPerformed = false;
    this.fixtureService.searchByTeam(this.teamName.trim()).subscribe(fixtures => {
      this.fixtures = fixtures;
      this.isLoadingSearch = false;
      this.searchPerformed = true;
    });
  }

  searchByLeague(): void {
    if (!this.selectedLeagueId) return;
    this.isLoadingSearch = true;
    this.searchPerformed = false;
    this.fixtureService.searchByLeague(this.selectedLeagueId).subscribe(fixtures => {
      this.fixtures = fixtures;
      this.isLoadingSearch = false;
      this.searchPerformed = true;
    });
  }

  // ── Shared display helpers ─────────────────────────────────────────────────
  statusLabel(statusShort: string, statusElapsed: number | null, statusLong: string): string {
    if (statusShort === '1H' || statusShort === '2H' || statusShort === 'ET')
      return `${statusElapsed ?? 0}'`;
    if (statusShort === 'HT') return 'HT';
    if (statusShort === 'FT') return 'FT';
    if (statusShort === 'NS') return 'No iniciado';
    if (statusShort === 'PST') return 'Aplazado';
    if (statusShort === 'CANC') return 'Cancelado';
    return statusLong || statusShort;
  }

  isLiveStatus(statusShort: string): boolean {
    return ['1H', 'HT', '2H', 'ET', 'BT', 'P', 'LIVE'].includes(statusShort);
  }

  getStatusLabel(fixture: Fixture): string {
    return this.statusLabel(fixture.statusShort, fixture.statusElapsed, fixture.statusLong);
  }

  isLive(fixture: Fixture): boolean {
    return this.isLiveStatus(fixture.statusShort);
  }

  getApiStatusLabel(f: ApiFixtureItem): string {
    return this.statusLabel(f.fixture.status.short, f.fixture.status.elapsed, f.fixture.status.long);
  }

  formatDate(dateString: string): string {
    if (!dateString) return '';
    const d = new Date(dateString);
    const day   = String(d.getDate()).padStart(2, '0');
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const year  = d.getFullYear();
    const hours = String(d.getHours()).padStart(2, '0');
    const mins  = String(d.getMinutes()).padStart(2, '0');
    return `${day}/${month}/${year} ${hours}:${mins}`;
  }
}
