import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ManualTrackedPlayer } from 'src/app/entitites/manual-tracker-player';
import { PlayerService } from '../services/player-service.service';

@Component({
  selector: 'app-player-detail',
  templateUrl: './player-detail.component.html',
  styleUrls: ['./player-detail.component.css']
})
export class PlayerDetailComponent implements OnInit {
  player: ManualTrackedPlayer | null = null;
  photoSrc = 'assets/images/standard-pic.jpg';
  private detailId!: number;

  constructor(
    private route: ActivatedRoute,
    private playerService: PlayerService
  ) {}


  ngOnInit(): void {
    this.detailId = Number(this.route.snapshot.paramMap.get('id'));

    if (this.detailId) {
      this.playerService.getPlayer(this.detailId).subscribe({
        next: data => {
          this.player = data ?? null;

          const photoPlayerId = this.player?.id ?? this.detailId;
          this.photoSrc = this.getPlayerPhotoUrl(photoPlayerId);
        },
        error: err => console.error('Error en la solicitud:', err)
      });
    }
  }


  onImgError(ev: Event): void {
    (ev.target as HTMLImageElement).src = 'assets/images/standard-pic.jpg';
  }

  ngOnDestroy(): void {

  }

  getPlayerPhotoUrl(player: any): string {
    const base = 'http://localhost:8080';
    console.log(base + '/players/' + `${player}` + '/photo');
    return `${base}/players/${player}/photo`;
  }
}
