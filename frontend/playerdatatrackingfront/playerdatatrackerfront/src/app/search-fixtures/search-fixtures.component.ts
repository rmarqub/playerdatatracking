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
  selectedSearchLeagueIds: number[] = [];
  searchLeaguesDropdownOpen = false;
  studiedLeagues: Torneo[] = [];
  fixtures: Fixture[] = [];
  isLoadingSearch: boolean = false;
  isLoadingAnalysisSearch: boolean = false;
  searchPerformed: boolean = false;

  // ── Live API ───────────────────────────────────────────────────────────────
  liveFixturesApi: ApiFixtureItem[] = [];
  isLoadingLive: boolean = false;
  liveLoaded: boolean = false;
  liveError: string = '';

  // ── Live filters ───────────────────────────────────────────────────────────
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
    const saved = sessionStorage.getItem('searchFixturesState');
    if (saved) {
      try {
        const state = JSON.parse(saved);
        this.teamName = state.teamName ?? '';
        this.selectedSearchLeagueIds = state.selectedSearchLeagueIds ?? [];
        this.fixtures = state.fixtures ?? [];
        this.searchPerformed = state.searchPerformed ?? false;
      } catch {}
    }
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    const target = event.target as HTMLElement;
    if (!target.closest?.('.filter-dropdown-wrap')) {
      this.countriesDropdownOpen = false;
      this.leaguesDropdownOpen = false;
      this.searchLeaguesDropdownOpen = false;
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

  // ── Live filter toggles ────────────────────────────────────────────────────
  toggleCountriesDropdown(): void {
    this.leaguesDropdownOpen = false;
    this.searchLeaguesDropdownOpen = false;
    this.countriesDropdownOpen = !this.countriesDropdownOpen;
  }

  toggleLeaguesDropdown(): void {
    this.countriesDropdownOpen = false;
    this.searchLeaguesDropdownOpen = false;
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

  // ── DB search ──────────────────────────────────────────────────────────────
  toggleSearchLeaguesDropdown(): void {
    this.countriesDropdownOpen = false;
    this.leaguesDropdownOpen = false;
    this.searchLeaguesDropdownOpen = !this.searchLeaguesDropdownOpen;
  }

  toggleSearchLeague(leagueId: number): void {
    const idx = this.selectedSearchLeagueIds.indexOf(leagueId);
    if (idx >= 0) {
      this.selectedSearchLeagueIds = this.selectedSearchLeagueIds.filter(id => id !== leagueId);
    } else {
      this.selectedSearchLeagueIds = [...this.selectedSearchLeagueIds, leagueId];
    }
  }

  get searchLeaguesLabel(): string {
    if (this.selectedSearchLeagueIds.length === 0) return 'Todas las ligas';
    if (this.selectedSearchLeagueIds.length === 1)
      return this.studiedLeagues.find(l => l.id === this.selectedSearchLeagueIds[0])?.name ?? '1 liga';
    return `${this.selectedSearchLeagueIds.length} ligas`;
  }

  get canSearch(): boolean {
    return this.teamName.trim().length > 0 || this.selectedSearchLeagueIds.length > 0;
  }

  searchFixtures(): void {
    if (!this.canSearch) return;
    this.isLoadingSearch = true;
    this.searchPerformed = false;
    this.fixtureService.searchFixturesCombined(this.teamName, this.selectedSearchLeagueIds).subscribe({
      next: fixtures => {
        this.fixtures = fixtures;
        this.isLoadingSearch = false;
        this.searchPerformed = true;
      },
      error: () => {
        this.fixtures = [];
        this.isLoadingSearch = false;
        this.searchPerformed = true;
      }
    });
  }

  searchWithAnalysis(): void {
    this.isLoadingAnalysisSearch = true;
    this.searchPerformed = false;
    this.fixtureService.getFixturesWithAnalysis().subscribe({
      next: fixtures => {
        this.fixtures = fixtures;
        this.isLoadingAnalysisSearch = false;
        this.searchPerformed = true;
      },
      error: () => {
        this.fixtures = [];
        this.isLoadingAnalysisSearch = false;
        this.searchPerformed = true;
      }
    });
  }

  // ── Navigation ─────────────────────────────────────────────────────────────
  navigateToFixture(fixtureId: number, source?: string): void {
    sessionStorage.setItem('searchFixturesState', JSON.stringify({
      teamName: this.teamName,
      selectedSearchLeagueIds: this.selectedSearchLeagueIds,
      fixtures: this.fixtures,
      searchPerformed: this.searchPerformed
    }));
    const queryParams = source ? { source } : {};
    this.router.navigate(['/fixture', fixtureId], { queryParams });
  }

  // ── Display helpers ────────────────────────────────────────────────────────
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
