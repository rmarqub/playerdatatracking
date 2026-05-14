import { Component, OnInit } from '@angular/core';
import { FixtureService, TorneoInfo } from '../services/fixture.service';

@Component({
  selector: 'app-league-management',
  templateUrl: './league-management.component.html',
  styleUrls: ['./league-management.component.css']
})
export class LeagueManagementComponent implements OnInit {

  leagues: TorneoInfo[] = [];
  loading = false;
  saving = false;
  errorMsg: string | null = null;
  saveMsg: string | null = null;
  saveError = false;

  countryFilter = '';
  availableCountries: string[] = [];

  constructor(private fixtureService: FixtureService) {}

  ngOnInit(): void {
    this.loadLeagues();
  }

  loadLeagues(): void {
    this.loading = true;
    this.errorMsg = null;
    this.fixtureService.getAllLeaguesForManagement().subscribe({
      next: (data) => {
        this.loading = false;
        this.leagues = data;
        this.availableCountries = [...new Set(
          data.map(l => l.paisName ?? 'Sin país').filter(Boolean)
        )].sort();
      },
      error: () => {
        this.loading = false;
        this.errorMsg = 'Error de conexión al cargar las ligas.';
      }
    });
  }

  get filteredLeagues(): TorneoInfo[] {
    if (!this.countryFilter) return this.leagues;
    return this.leagues.filter(l =>
      (l.paisName ?? 'Sin país') === this.countryFilter
    );
  }

  toggleStudied(league: TorneoInfo): void {
    league.studied = !league.studied;
  }

  saveStudied(): void {
    this.saving = true;
    this.saveMsg = null;
    this.saveError = false;
    const studiedIds = this.leagues.filter(l => l.studied).map(l => l.id);
    this.fixtureService.updateStudiedLeagues(studiedIds).subscribe({
      next: (result) => {
        this.saving = false;
        this.saveMsg = result.description;
        this.saveError = !result.ok;
      },
      error: () => {
        this.saving = false;
        this.saveMsg = 'Error de conexión al guardar.';
        this.saveError = true;
      }
    });
  }

  studiedCount(): number {
    return this.leagues.filter(l => l.studied).length;
  }
}
