import { Component } from '@angular/core';
import { FixtureService } from '../services/fixture.service';

@Component({
  selector: 'app-manage-indexal-db',
  templateUrl: './manage-indexal-db.component.html',
  styleUrls: ['./manage-indexal-db.component.css']
})
export class ManageIndexalDbComponent {

  regenerating = false;
  regenerateMessage: string | null = null;
  regenerateError = false;

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
}
