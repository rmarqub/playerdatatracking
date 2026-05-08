import { Component } from '@angular/core';
import { FixtureService, AnalysisHistoryData } from '../services/fixture.service';

@Component({
  selector: 'app-manage-indexal-db',
  templateUrl: './manage-indexal-db.component.html',
  styleUrls: ['./manage-indexal-db.component.css']
})
export class ManageIndexalDbComponent {

  regenerating = false;
  regenerateMessage: string | null = null;
  regenerateError = false;

  historyLoading = false;
  historyError: string | null = null;
  historyData: AnalysisHistoryData | null = null;
  historyLoaded = false;

  constructor(private fixtureService: FixtureService) {}

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
