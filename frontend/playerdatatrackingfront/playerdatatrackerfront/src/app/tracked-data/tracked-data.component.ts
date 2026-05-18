import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService, isAdmin } from '../core/auth.service';

@Component({
  selector: 'app-tracked-data',
  templateUrl: './tracked-data.component.html',
  styleUrls: ['./tracked-data.component.css']
})
export class TrackedDataComponent implements OnInit {

  isAdmin = false;

  constructor(private router: Router, private authService: AuthService) { }

  ngOnInit(): void {
    this.authService.me().subscribe({
      next: (user) => { this.isAdmin = isAdmin(user); },
      error: () => { this.isAdmin = false; }
    });
  }

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
