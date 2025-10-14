import { Component } from '@angular/core';
import { Router, ActivatedRoute } from '@angular/router';
import { Location } from '@angular/common';
import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-header',
  templateUrl: './header.component.html',
  styleUrls: ['./header.component.css']
})
export class HeaderComponent {
  constructor(private router: Router, private route: ActivatedRoute, private location: Location, private auth: AuthService){}

  isHomePage: boolean = false;
  isLoginPage = false;
  ngOnInit() {
    this.router.events.subscribe(() => {
      this.isHomePage = this.router.url === '/home';
      this.isLoginPage = this.router.url === '/login';
    });
  }

  navigateToHome() {
    this.router.navigate(['/home']);
  }
  goBack(): void {
    this.location.back(); // Vuelve a la página anterior
  }
  logout(): void {
    this.auth.logout().subscribe(() => {
      this.router.navigate(['/login']);
    });
  }


}
