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

const appRoutes : Routes = [
  { path: '', redirectTo: 'home', pathMatch: 'full' },
  { path: 'home', component: HomeComponent  },
  { path: 'manualData', component: ManualDataComponent },
  { path: 'addplayer', component: AddPlayerComponent},
  { path: 'manualdataplayer/:id', component: PlayerDetailComponent },
  { path: 'trackedData', component: TrackedDataComponent },
  { path: 'searchPlayers', component: SearchPlayersComponent },
  { path: 'manageIndexalDB', component: ManageIndexalDbComponent },
  { path: 'manageApikeys', component: ManageApikeysComponent },
  { path: 'player/:id', component: IndexPlayerComponent}
]
export const routing = RouterModule.forRoot(appRoutes);
