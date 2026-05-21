import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ManualTrackedPlayer } from 'src/app/entitites/manual-tracker-player';
import { PlayerService, PlayerMarketValue } from '../services/player-service.service';
import { Router } from '@angular/router';
import { environment } from 'src/enviroment/environment';

@Component({
  selector: 'app-player-detail',
  templateUrl: './player-detail.component.html',
  styleUrls: ['./player-detail.component.css']
})
export class PlayerDetailComponent implements OnInit {
  player: ManualTrackedPlayer | null = null;
  photoSrc = 'assets/images/standard-pic.jpg';
  private detailId!: number;

  valuationLoading = false;
  valuationResult: PlayerMarketValue | null = null;
  valuationError: string | null = null;

  readonly TIER_LABELS: Record<number, string> = {
    0: 'Sin tier',
    1: 'Tier 1 — Élite',
    2: 'Tier 2 — Alto',
    3: 'Tier 3 — Medio',
    4: 'Tier 4 — Bajo',
    5: 'Tier 5 — Menor',
  };

  readonly POSITION_LABELS: Record<string, string> = {
    G: 'Portero',
    D: 'Defensa',
    M: 'Centrocampista',
    F: 'Delantero',
  };

  constructor(
    private route: ActivatedRoute,
    private playerService: PlayerService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.detailId = Number(this.route.snapshot.paramMap.get('id'));
    if (this.detailId) {
      this.playerService.getPlayer(this.detailId).subscribe({
        next: data => {
          this.player = data ?? null;
          const photoPlayerId = this.player?.indexID ?? this.detailId;
          this.photoSrc = this.getPlayerPhotoUrl(photoPlayerId);
        },
        error: err => console.error('Error en la solicitud:', err)
      });
    }
  }

  onImgError(ev: Event): void {
    (ev.target as HTMLImageElement).src = 'assets/images/standard-pic.jpg';
  }

  ngOnDestroy(): void {}

  goToUpdate(): void {
    if (!this.player) return;
    this.router.navigate(['/updateTrackedPlayer'], {
      state: { manualId: this.player.id }
    });
  }

  calculateMarketValue(): void {
    if (!this.player?.indexID) {
      this.valuationError = 'Este jugador no tiene indexID asociado.';
      return;
    }
    this.valuationLoading = true;
    this.valuationResult = null;
    this.valuationError = null;

    this.playerService.getPlayerMarketValue(this.player.indexID).subscribe({
      next: result => {
        this.valuationLoading = false;
        if (result) {
          this.valuationResult = result;
        } else {
          this.valuationError = 'No se pudo calcular el valor. Comprueba que la API de valoración está activa.';
        }
      },
      error: () => {
        this.valuationLoading = false;
        this.valuationError = 'Error de conexión con la API de valoración.';
      }
    });
  }

  getPlayerPhotoUrl(player: any): string {
    return `${environment.apiUrl}/players/${player}/photo`;
  }

  tierBadgeClass(tier: number): string {
    return `mv-tier-badge tier-${tier}`;
  }
}
