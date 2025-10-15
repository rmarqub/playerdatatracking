import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Player } from '../entitites/player';
import { PlayerService } from '../services/player-service.service';
import { Router } from '@angular/router';

@Component({
  selector: 'app-index-player',
  templateUrl: './index-player.component.html',
  styleUrls: ['./index-player.component.css']
})
export class IndexPlayerComponent implements OnInit {
  player: Player | null = null;
  isFavorite: boolean = false;

  constructor(
    private route: ActivatedRoute,
    private playerService: PlayerService,
    private router: Router
  ) {}

  ngOnInit(): void {
    const playerId = Number(this.route.snapshot.paramMap.get('id'));
    if (playerId) {
      this.playerService.getIndxPlayer(playerId).subscribe(
        data => {
          if (data) {
            this.player = data;
          } else {
            console.error('No se pudo obtener la información del jugador.');
          }
        },
        error => console.error('Error en la solicitud:', error)
      );
    }
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
}
