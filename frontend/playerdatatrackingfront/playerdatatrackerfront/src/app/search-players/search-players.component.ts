import { Component } from '@angular/core';
import { Player } from '../entitites/player';
import { PlayerService } from '../services/player-service.service';
import { ActivatedRoute } from '@angular/router';

@Component({
  selector: 'app-search-players',
  templateUrl: './search-players.component.html',
  styleUrls: ['./search-players.component.css']
})
export class SearchPlayersComponent {
  playerName: string = '';
  teamName: string = '';
  players: Player[] = [];

    constructor(
      private route: ActivatedRoute,
      private playerService: PlayerService
    ) {}

  searchByPlayerName(): void {
    if (this.playerName.trim()) {
      this.playerService.searchPlayers(this.playerName).subscribe(players => {
        this.players = players;
      });
    }
  }

  searchByTeam(): void {
    if (this.teamName.trim()) {
      this.playerService.searchPlayers(undefined, this.teamName).subscribe(players => {
        this.players = players;
      });
    }
  }
}
