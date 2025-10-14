import { Routes, RouterModule } from '@angular/router';
import { HomeComponent } from './home/home.component';
import { ManualDataComponent } from './manual-data/manual-data.component';
import { AddPlayerComponent } from './add-player/add-player.component';
import { PlayerDetailComponent } from './player-detail/player-detail.component';
import { TrackedDataComponent } from './tracked-data/tracked-data.component';
import { SearchPlayersComponent } from './search-players/search-players.component';
import { ManageIndexalDbComponent } from './manage-indexal-db/manage-indexal-db.component';
import { ManageApikeysComponent } from './manage-apikeys/manage-apikeys.component';
import { IndexPlayerComponent } from './index-player/index-player.component';
import { LoginComponent } from './features/login/login.component';
import { AuthGuard } from './core/guards/auth.guard';
import { RegisterComponent } from './features/register/register.component';

const appRoutes : Routes = [
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: 'register', component: RegisterComponent },
  { path: 'home', component: HomeComponent, canActivate: [AuthGuard]},
  { path: 'manualData', component: ManualDataComponent, canActivate: [AuthGuard]},
  { path: 'addplayer', component: AddPlayerComponent, canActivate: [AuthGuard]},
  { path: 'manualdataplayer/:id', component: PlayerDetailComponent, canActivate: [AuthGuard]},
  { path: 'trackedData', component: TrackedDataComponent, canActivate: [AuthGuard]},
  { path: 'searchPlayers', component: SearchPlayersComponent, canActivate: [AuthGuard]},
  { path: 'manageIndexalDB', component: ManageIndexalDbComponent, canActivate: [AuthGuard]},
  { path: 'manageApikeys', component: ManageApikeysComponent, canActivate: [AuthGuard]},
  { path: 'player/:id', component: IndexPlayerComponent, canActivate: [AuthGuard]},
  { path: 'login', component: LoginComponent },
  { path: '**', redirectTo: '' },
  { path: '', component: HomeComponent, canActivate: [AuthGuard] },
]

export const routing = RouterModule.forRoot(appRoutes);
