import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Player } from '../entitites/player';
import { PlayerService } from '../services/player-service.service';

@Component({
  selector: 'app-index-player',
  templateUrl: './index-player.component.html',
  styleUrls: ['./index-player.component.css']
})
export class IndexPlayerComponent implements OnInit {
  player: Player | null = null;

  constructor(
    private route: ActivatedRoute,
    private playerService: PlayerService
  ) {}

  ngOnInit(): void {
    const playerId = Number(this.route.snapshot.paramMap.get('id'));
    if (playerId) {
      this.playerService.getIndxPlayer(playerId).subscribe(
        data => {
          if (data) {
            this.player = data
          } else {
            console.error('No se pudo obtener la información del jugador.');
          }
        },
        error => console.error('Error en la solicitud:', error)
      );
    }
  }
}

