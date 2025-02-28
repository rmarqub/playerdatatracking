import { Component } from '@angular/core';
import { Player } from '../entitites/player';
import { PlayerService } from '../services/player-service.service';
import { ActivatedRoute } from '@angular/router';
import { Router } from '@angular/router';

@Component({
  selector: 'app-search-players',
  templateUrl: './search-players.component.html',
  styleUrls: ['./search-players.component.css']
})
export class SearchPlayersComponent {
  playerName: string = '';
  teamName: string = '';
  sortOrder: { [key: string]: 'asc' | 'desc' } = {};
  sortedColumn: string | null = null;
  players: Player[] = [];
  currentPage: number = 1;
  pageSize: number = 40;
  paginatedPlayers: Player[] = [];
    constructor(
      private route: ActivatedRoute,
      private router: Router,
      private playerService: PlayerService
    ) {}

    searchByPlayerName(): void {
      if (this.playerName.trim()) {
        this.playerService.searchPlayers(this.playerName).subscribe(players => {
          this.players = players;
          this.currentPage = 1;
          this.updatePagination();
        });
      }
    }

    searchByTeam(): void {
      if (this.teamName.trim()) {
        this.playerService.searchPlayers(undefined, this.teamName).subscribe(players => {
          this.players = players;
          this.currentPage = 1;
          this.updatePagination();
        });
      }
    }

  updatePagination(): void {
    const startIndex = (this.currentPage - 1) * this.pageSize;
    const endIndex = startIndex + this.pageSize;
    this.paginatedPlayers = this.players.slice(startIndex, endIndex);
  }

  nextPage(): void {
    if (this.currentPage * this.pageSize < this.players.length) {
      this.currentPage++;
      this.updatePagination();
    }
  }

  prevPage(): void {
    if (this.currentPage > 1) {
      this.currentPage--;
      this.updatePagination();
    }
  }

  get totalPages(): number {
    return Math.ceil(this.players.length / this.pageSize);
  }

  formatDate(dateString: string): string {
    if (!dateString) return '';

    const date = new Date(dateString);
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');
    const seconds = String(date.getSeconds()).padStart(2, '0');

    return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
  }

    sortData(property: keyof Player) {
      const currentOrder = this.sortOrder[property] || 'asc';
      const newOrder = currentOrder === 'asc' ? 'desc' : 'asc';
      this.sortOrder[property] = newOrder;
      this.sortedColumn = property;

      this.players.sort((a, b) => {
        let comparison = 0;

        if (typeof a[property] === 'string') {
          comparison = (a[property] as string).localeCompare(b[property] as string);
        } else if (typeof a[property] === 'number') {
          comparison = (a[property] as number) - (b[property] as number);
        }

        return newOrder === 'asc' ? comparison : -comparison;
      });
      this.updatePagination();
    }

      isSortedAsc(property: keyof Player): boolean {
        return this.sortedColumn === property && this.sortOrder[property] === 'asc';
      }

      isSortedDesc(property: keyof Player): boolean {
        return this.sortedColumn === property && this.sortOrder[property] === 'desc';
      }

      viewPlayer(playerId: number): void {
        this.router.navigate(['/player', playerId]);
      }
}

