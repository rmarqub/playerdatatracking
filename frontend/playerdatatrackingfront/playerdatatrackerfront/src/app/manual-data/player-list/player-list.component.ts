import { Component, Input, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { ManualTrackedPlayer } from 'src/app/entitites/manual-tracker-player';


@Component({
  selector: 'app-player-list',
  templateUrl: './player-list.component.html',
  styleUrls: ['./player-list.component.css']
})
export class PlayerListComponent implements OnInit {
  sortOrder: { [key: string]: 'asc' | 'desc' } = {};
  sortedColumn: string | null = null;

  @Input() entityList: ManualTrackedPlayer[] = [];
  selectedPlayer: ManualTrackedPlayer | null = null;

  constructor(private router: Router) {}

  ngOnInit(): void {

  }

  sortData(property: keyof ManualTrackedPlayer) {
    const currentOrder = this.sortOrder[property] || 'asc';
    const newOrder = currentOrder === 'asc' ? 'desc' : 'asc';
    this.sortOrder[property] = newOrder;
    this.sortedColumn = property;

    this.entityList.sort((a, b) => {
      let comparison = 0;

      if (typeof a[property] === 'string') {
        comparison = (a[property] as string).localeCompare(b[property] as string);
      } else if (typeof a[property] === 'number') {
        comparison = (a[property] as number) - (b[property] as number);
      }

      return newOrder === 'asc' ? comparison : -comparison;
    });
  }

  getSortClass(property: keyof ManualTrackedPlayer): string {
    if (this.sortedColumn === property) {
      return this.sortOrder[property] === 'asc' ? 'asc sorted-column' : 'desc sorted-column';
    }
    return '';
  }

  isSortedAsc(property: keyof ManualTrackedPlayer): boolean {
    return this.sortedColumn === property && this.sortOrder[property] === 'asc';
  }

  isSortedDesc(property: keyof ManualTrackedPlayer): boolean {
    return this.sortedColumn === property && this.sortOrder[property] === 'desc';
  }

  selectPlayer(player: ManualTrackedPlayer) {
    this.router.navigate(['/manualdataplayer', player.id]);
  }
}
