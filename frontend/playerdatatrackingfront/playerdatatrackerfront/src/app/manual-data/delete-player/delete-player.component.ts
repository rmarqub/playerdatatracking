import { Component, Input, Output, EventEmitter } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { ManualTrackedPlayer } from 'src/app/entitites/manual-tracker-player';
import { Router } from '@angular/router';
import { environment } from 'src/enviroment/environment';

@Component({
  selector: 'app-delete-player',
  templateUrl: './delete-player.component.html',
  styleUrls: ['./delete-player.component.css']
})
export class DeletePlayerComponent {

  @Input() entityList: ManualTrackedPlayer[] = [];
  selectedPlayer: ManualTrackedPlayer | null = null;
  @Output() playerDeleted: EventEmitter<void> = new EventEmitter<void>();

  constructor(private http: HttpClient, private router : Router) { }

  deletePlayer(player: ManualTrackedPlayer) {
    this.http.delete(`${environment.apiUrl}/player`, { body: { nombre: player.nombre } })
      .subscribe((response: any) => {
        if (response.code === 0) {
          console.log('operation description: ', response.description);
          this.playerDeleted.emit();
        } else {
          console.error('Error al eliminar jugador:', response.description);
        }
      }, error => {
        console.error('Error en la solicitud:', error);
      });
  }
  selectPlayer(player: ManualTrackedPlayer) {
    this.router.navigate(['/manualdataplayer', player.id]);
  }
}
