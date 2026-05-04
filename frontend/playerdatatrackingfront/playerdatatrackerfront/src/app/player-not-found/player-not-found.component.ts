import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Location } from '@angular/common';

@Component({
  selector: 'app-player-not-found',
  templateUrl: './player-not-found.component.html',
  styleUrls: ['./player-not-found.component.css']
})
export class PlayerNotFoundComponent implements OnInit {
  playerName: string | null = null;

  constructor(private route: ActivatedRoute, private location: Location) {}

  ngOnInit(): void {
    this.playerName = this.route.snapshot.queryParamMap.get('name') || null;
  }

  goBack(): void {
    this.location.back();
  }
}
