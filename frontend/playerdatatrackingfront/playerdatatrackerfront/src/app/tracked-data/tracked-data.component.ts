import { Component } from '@angular/core';
import { Router } from '@angular/router';

@Component({
  selector: 'app-tracked-data',
  templateUrl: './tracked-data.component.html',
  styleUrls: ['./tracked-data.component.css']
})
export class TrackedDataComponent {

  constructor(private router: Router) { }

  navigateToSearchPlayers() {
    this.router.navigate(['/searchPlayers']);
  }

  navigateToManageIndexalDB() {
    this.router.navigate(['/manageIndexalDB']);
  }

  navigateToManageApikeys() {
    this.router.navigate(['/manageApikeys']);
  }

  navigateToSearchFixtures() {
    this.router.navigate(['/searchFixtures']);
  }
}
