import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { DeletePlayerComponent } from './delete-player/delete-player.component';
import { environment } from 'src/enviroment/environment';

@Component({
  selector: 'app-manual-data',
  templateUrl: './manual-data.component.html',
  styleUrls: ['./manual-data.component.css']
})
export class ManualDataComponent {

  constructor(private router: Router, private http: HttpClient) {

    this.fetchPlayers();
    this.isDataVisible = true;
  }

  players: any[] = [];
  isDataVisible: boolean = false;
  isDeleteMode: boolean = false;

  fetchPlayers(){
    this.http.get(`${environment.apiUrl}/players`)
    .subscribe((response: any) => {
    if (response.code === 0) {
      this.players = response.entityList;
      console.log(this.players);
      this.isDataVisible = true;
    } else {
      console.error('Error al obtener jugadores:', response.description);
    }
    }, error => {
      console.error('Error en la solicitud:', error);
    });
  }

  togglePlayers() {
    if (this.isDataVisible) {
      this.players = [];
      this.isDataVisible = false;
      this.isDeleteMode = false;
    } else {
      this.fetchPlayers();
    }
  }

  navigateAddPlayer() {
    this.router.navigate(['/addplayer']);
  }

  toggleDeleteMode() {
    if (this.isDataVisible) {
      this.isDeleteMode = !this.isDeleteMode;
    }
  }

  onPlayerDeleted() {
    this.fetchPlayers();
  }
}
