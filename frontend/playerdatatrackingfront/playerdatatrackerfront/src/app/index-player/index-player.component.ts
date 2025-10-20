import { Component, OnInit, ChangeDetectorRef  } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Player } from '../entitites/player';
import { PlayerService } from '../services/player-service.service';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';


    const fmtDate = new Intl.DateTimeFormat('es-ES', {
    timeZone: 'Europe/Madrid',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  });

  const fmtDateTime = new Intl.DateTimeFormat('es-ES', {
    timeZone: 'Europe/Madrid',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false
  });
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

    this.isFavorite = !this.isFavorite;


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

    private toDate(value: any): Date | null {
    if (!value) return null;
    return value instanceof Date ? value : new Date(value);
  }

  ngOnDestroy(): void { this.sub?.unsubscribe(); }

    get lastUpdatedPretty(): string {
    const d = this.toDate(this.player?.lastUpdated);
    return d ? fmtDateTime.format(d) : '';
  }

}
