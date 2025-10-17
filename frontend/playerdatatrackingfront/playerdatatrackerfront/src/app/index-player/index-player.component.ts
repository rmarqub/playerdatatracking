import { Component, OnInit, ChangeDetectorRef  } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Player } from '../entitites/player';
import { PlayerService } from '../services/player-service.service';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-index-player',
  templateUrl: './index-player.component.html',
  styleUrls: ['./index-player.component.css']
})
export class IndexPlayerComponent implements OnInit {
  player: any = null;
  isFavorite: boolean = false;
  private sub?: Subscription;
  photoSrc = 'assets/images/standard-pic.jpg';

  constructor(
    private route: ActivatedRoute,
    private playerService: PlayerService,
    private cdr: ChangeDetectorRef,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.sub = this.route.paramMap.subscribe(pm => {
      const playerId = Number(pm.get('id'));
      if (!playerId) { this.player = null; this.photoSrc = 'assets/images/standard-pic.jpg'; return; }

      this.playerService.getIndxPlayer(playerId).subscribe({
        next: data => {
          this.player = data ?? null;

          // cache-busting opcional si tu API lo expone
          const v = (this.player as any)?.photoUpdatedAt || (this.player as any)?.lastUpdated || '';
          this.photoSrc = this.buildPhotoUrl(playerId, v);

          console.log('Detalle → URL foto:', this.photoSrc); // Debes verla en consola
          this.cdr.markForCheck();
        },
        error: err => console.error('Error obteniendo jugador:', err)
      });
    });
  }

  toggleFavorite(): void {
    // 1) marca local si quieres
    this.isFavorite = !this.isFavorite;

    // 2) navega a /addPlayer con prefill
    if (this.player && this.isFavorite) {
      this.router.navigate(['/addplayer'], {
        state: {
          prefill: {
            nombre: this.player.firstname + " " + this.player.lastname,
            club: this.player.team,
            birth: this.player.birth ?? '',
            age: this.player.age ?? null,
            fbrefId: this.player.fbrefId ?? null,
            indexId: this.player.id ?? null
          }
        }
      });
    }
  }

  buildPhotoUrl(id: number, v?: string): string {
    return `http://localhost:8080/players/${id}/photo`;
  }
  onImgError(ev: Event): void {
    (ev.target as HTMLImageElement).src = 'assets/images/standard-pic.jpg';
  }

  ngOnDestroy(): void { this.sub?.unsubscribe(); }
}
