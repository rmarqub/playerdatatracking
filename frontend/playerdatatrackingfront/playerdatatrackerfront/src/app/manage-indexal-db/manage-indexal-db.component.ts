import { Component } from '@angular/core';
import { lastValueFrom } from 'rxjs';
import { FixtureService, AnalysisHistoryData } from '../services/fixture.service';

type StepStatus = 'pending' | 'running' | 'ok' | 'error';

interface FixtureStep {
  label: string;
  status: StepStatus;
  message: string | null;
}

@Component({
  selector: 'app-manage-indexal-db',
  templateUrl: './manage-indexal-db.component.html',
  styleUrls: ['./manage-indexal-db.component.css']
})
export class ManageIndexalDbComponent {

  sectionsOpen: { [key: string]: boolean } = {
    countries: false,
    leagues: false,
    clubs: false,
    players: false,
    fixtures: false,
    predictions: false
  };

  // Countries - getCountries
  countriesLoading = false;
  countriesUpdate = false;
  countriesMessage: string | null = null;
  countriesError = false;

  // Countries - updateCountries
  updateCountriesLoading = false;
  updateCountriesMessage: string | null = null;
  updateCountriesError = false;

  // Leagues - getLeagues
  leaguesLoading = false;
  leaguesUpdate = false;
  leaguesMessage: string | null = null;
  leaguesError = false;

  // Leagues - updateLeagues
  updateLeaguesLoading = false;
  updateLeaguesMessage: string | null = null;
  updateLeaguesError = false;

  // Clubs - updateClubsInfo
  clubsLoading = false;
  clubsUpdate = false;
  clubsRestUpdate = false;
  clubsMessage: string | null = null;
  clubsError = false;

  // Players - ingestión secuencial
  playerSeason = '2024';
  playerThreads = 3;
  playerPurge = false;
  playerRunning = false;
  playerSteps: FixtureStep[] = this.buildPlayerSteps();

  private buildPlayerSteps(): FixtureStep[] {
    return [
      { label: 'Actualizar jugadores',      status: 'pending', message: null },
      { label: 'Actualizar por plantillas', status: 'pending', message: null },
      { label: 'Actualizar transferidos',   status: 'pending', message: null },
      { label: 'Ingerir datos brutos',      status: 'pending', message: null },
    ];
  }

  // Fixtures - ingestión secuencial
  fixturePurge = false;
  fixtureSeason = '2024';
  fixtureRunning = false;
  fixtureSteps: FixtureStep[] = this.buildFixtureSteps();

  private buildFixtureSteps(): FixtureStep[] {
    return [
      { label: 'Ingest Fixtures',      status: 'pending', message: null },
      { label: 'Ingest Events',        status: 'pending', message: null },
      { label: 'Ingest Player Stats',  status: 'pending', message: null },
      { label: 'Ingest Team Stats',    status: 'pending', message: null },
      { label: 'Ingest Lineup',        status: 'pending', message: null },
    ];
  }

  // Predictions - regenerar análisis
  regenerating = false;
  regenerateMessage: string | null = null;
  regenerateError = false;

  // Predictions - historial
  historyLoading = false;
  historyError: string | null = null;
  historyData: AnalysisHistoryData | null = null;
  historyLoaded = false;

  // Predictions - deltas
  updatingDeltas = false;
  deltasUpdateMessage: string | null = null;
  deltasUpdateError = false;
  deltasUpdated = false;

  currentPage = 1;
  itemsPerPage = 20;
  Math = Math;

  constructor(private fixtureService: FixtureService) {}

  toggleSection(section: string): void {
    this.sectionsOpen[section] = !this.sectionsOpen[section];
  }

  fetchCountries(): void {
    this.countriesLoading = true;
    this.countriesMessage = null;
    this.countriesError = false;
    this.fixtureService.getCountries(this.countriesUpdate).subscribe({
      next: (result) => {
        this.countriesLoading = false;
        this.countriesMessage = result.description;
        this.countriesError = !result.ok;
      },
      error: () => {
        this.countriesLoading = false;
        this.countriesError = true;
        this.countriesMessage = 'Error de conexión con el servidor';
      }
    });
  }

  updateCountriesDb(): void {
    this.updateCountriesLoading = true;
    this.updateCountriesMessage = null;
    this.updateCountriesError = false;
    this.fixtureService.updateCountriesFromJson().subscribe({
      next: (result) => {
        this.updateCountriesLoading = false;
        this.updateCountriesMessage = result.description;
        this.updateCountriesError = !result.ok;
      },
      error: () => {
        this.updateCountriesLoading = false;
        this.updateCountriesError = true;
        this.updateCountriesMessage = 'Error de conexión con el servidor';
      }
    });
  }

  fetchLeagues(): void {
    this.leaguesLoading = true;
    this.leaguesMessage = null;
    this.leaguesError = false;
    this.fixtureService.getLeagues(this.leaguesUpdate).subscribe({
      next: (result) => {
        this.leaguesLoading = false;
        this.leaguesMessage = result.description;
        this.leaguesError = !result.ok;
      },
      error: () => {
        this.leaguesLoading = false;
        this.leaguesError = true;
        this.leaguesMessage = 'Error de conexión con el servidor';
      }
    });
  }

  updateLeaguesDb(): void {
    this.updateLeaguesLoading = true;
    this.updateLeaguesMessage = null;
    this.updateLeaguesError = false;
    this.fixtureService.updateLeagues().subscribe({
      next: (result) => {
        this.updateLeaguesLoading = false;
        this.updateLeaguesMessage = result.description;
        this.updateLeaguesError = !result.ok;
      },
      error: () => {
        this.updateLeaguesLoading = false;
        this.updateLeaguesError = true;
        this.updateLeaguesMessage = 'Error de conexión con el servidor';
      }
    });
  }

  updateClubsInfo(): void {
    this.clubsLoading = true;
    this.clubsMessage = null;
    this.clubsError = false;
    this.fixtureService.updateClubsInfo(this.clubsUpdate, this.clubsRestUpdate).subscribe({
      next: (result) => {
        this.clubsLoading = false;
        this.clubsMessage = result.description;
        this.clubsError = !result.ok;
      },
      error: () => {
        this.clubsLoading = false;
        this.clubsError = true;
        this.clubsMessage = 'Error de conexión con el servidor';
      }
    });
  }

  async runPlayerIngest(): Promise<void> {
    this.playerRunning = true;
    this.playerSteps = this.buildPlayerSteps();

    const calls = [
      () => this.fixtureService.updatePlayers(this.playerSeason),
      () => this.fixtureService.updatePlayerBySquads(),
      () => this.fixtureService.updateTransferedPlayers(),
      () => this.fixtureService.ingestRawData(this.playerSeason, this.playerThreads, this.playerPurge),
    ];

    for (let i = 0; i < calls.length; i++) {
      this.playerSteps[i].status = 'running';
      try {
        const result = await lastValueFrom(calls[i]());
        this.playerSteps[i].status = result.ok ? 'ok' : 'error';
        this.playerSteps[i].message = result.description;
        if (!result.ok) break;
      } catch {
        this.playerSteps[i].status = 'error';
        this.playerSteps[i].message = 'Error de conexión con el servidor';
        break;
      }
    }

    this.playerRunning = false;
  }

  async runFixtureIngest(): Promise<void> {
    this.fixtureRunning = true;
    this.fixtureSteps = this.buildFixtureSteps();

    const calls = [
      () => this.fixtureService.ingestFixtures(this.fixturePurge, this.fixtureSeason),
      () => this.fixtureService.ingestFixtureEvents(this.fixturePurge, this.fixtureSeason),
      () => this.fixtureService.ingestFixturePlayerStats(this.fixturePurge, this.fixtureSeason),
      () => this.fixtureService.ingestFixtureTeamStats(this.fixturePurge, this.fixtureSeason),
      () => this.fixtureService.ingestFixtureLineup(this.fixturePurge, this.fixtureSeason),
    ];

    for (let i = 0; i < calls.length; i++) {
      this.fixtureSteps[i].status = 'running';
      try {
        const result = await lastValueFrom(calls[i]());
        this.fixtureSteps[i].status = result.ok ? 'ok' : 'error';
        this.fixtureSteps[i].message = result.description;
        if (!result.ok) break;
      } catch {
        this.fixtureSteps[i].status = 'error';
        this.fixtureSteps[i].message = 'Error de conexión con el servidor';
        break;
      }
    }

    this.fixtureRunning = false;
  }

  regenerateAnalyses(): void {
    this.regenerating = true;
    this.regenerateMessage = null;
    this.regenerateError = false;
    this.fixtureService.regenerateContextualAnalyses().subscribe({
      next: (result) => {
        this.regenerating = false;
        this.regenerateMessage = result.message;
        this.regenerateError = !result.ok;
      },
      error: () => {
        this.regenerating = false;
        this.regenerateError = true;
        this.regenerateMessage = 'Error de conexión con el servidor';
      }
    });
  }

  loadAnalysisHistory(): void {
    this.historyLoading = true;
    this.historyError = null;
    this.historyData = null;
    this.deltasUpdated = false;
    this.currentPage = 1;
    this.fixtureService.getAnalysisHistory().subscribe({
      next: (data) => {
        this.historyLoading = false;
        this.historyLoaded = true;
        if (data) {
          this.historyData = data;
        } else {
          this.historyError = 'No se pudo obtener el historial de análisis.';
        }
      },
      error: () => {
        this.historyLoading = false;
        this.historyLoaded = true;
        this.historyError = 'Error de conexión con el servidor.';
      }
    });
  }

  updateContextualDeltas(): void {
    this.updatingDeltas = true;
    this.deltasUpdateMessage = null;
    this.deltasUpdateError = false;
    this.deltasUpdated = false;
    this.fixtureService.updateContextualDeltas().subscribe({
      next: (result) => {
        this.updatingDeltas = false;
        this.deltasUpdated = true;
        this.deltasUpdateMessage = result.entity || 'Deltas actualizadas correctamente';
        this.deltasUpdateError = !result.ok;
        if (!this.deltasUpdateError) {
          this.loadAnalysisHistory();
        }
      },
      error: () => {
        this.updatingDeltas = false;
        this.deltasUpdated = true;
        this.deltasUpdateError = true;
        this.deltasUpdateMessage = 'Error de conexión con el servidor';
      }
    });
  }

  getPaginatedResults() {
    if (!this.historyData?.matchResults) return [];
    const start = (this.currentPage - 1) * this.itemsPerPage;
    const end = start + this.itemsPerPage;
    return this.historyData.matchResults.slice(start, end);
  }

  getTotalPages(): number {
    if (!this.historyData?.matchResults) return 0;
    return Math.ceil(this.historyData.matchResults.length / this.itemsPerPage);
  }

  goToPage(page: number): void {
    const totalPages = this.getTotalPages();
    if (page >= 1 && page <= totalPages) {
      this.currentPage = page;
    }
  }

  pct(v: number | null | undefined): string {
    if (v == null) return '-';
    return `${(v * 100).toFixed(1)}%`;
  }

  formatDate(d: string | null | undefined): string {
    if (!d) return '-';
    const dt = new Date(d);
    return `${String(dt.getDate()).padStart(2,'0')}/${String(dt.getMonth()+1).padStart(2,'0')}/${dt.getFullYear()}`;
  }

  resultLabel(r: string): string {
    if (r === 'home_win') return 'Local';
    if (r === 'away_win') return 'Visitante';
    return 'Empate';
  }
}
