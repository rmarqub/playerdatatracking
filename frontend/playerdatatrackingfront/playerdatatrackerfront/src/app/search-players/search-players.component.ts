import { Component } from '@angular/core';
import { Player } from '../entitites/player';

@Component({
  selector: 'app-search-players',
  templateUrl: './search-players.component.html',
  styleUrls: ['./search-players.component.css']
})
export class SearchPlayersComponent {
  playerName: string = '';
  teamName: string = '';
  players: Player[] = [];

  searchByPlayerName() {
    console.log('Buscar jugador:', this.playerName);
  }

  searchByTeam() {
    console.log('Buscar equipo:', this.teamName);
  }
}
