import { DeletePlayerModule } from './manual-data/delete-player/delete-player.module';
import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { AppRoutingModule } from './app-routing.module';
import { HttpClientModule } from '@angular/common/http';
import { ManualDataModule } from './manual-data/manual-data.module';
import { AppComponent } from './app.component';
import { routing } from './app.routing';
import { FormsModule } from '@angular/forms';
import { FooterComponent } from './footer/footer.component';
import { HeaderComponent } from './header/header.component';
import { AddPlayerComponent } from './add-player/add-player.component';
import { ToastrModule } from 'ngx-toastr';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { PlayerDetailComponent } from './player-detail/player-detail.component';
import { TrackedDataComponent } from './tracked-data/tracked-data.component';
import { SearchPlayersComponent } from './search-players/search-players.component';
import { ManageIndexalDbComponent } from './manage-indexal-db/manage-indexal-db.component';
import { ManageApikeysComponent } from './manage-apikeys/manage-apikeys.component';
import { IndexPlayerComponent } from './index-player/index-player.component';

@NgModule({
  declarations: [
    AppComponent,
    FooterComponent,
    HeaderComponent,
    AddPlayerComponent,
    PlayerDetailComponent,
    TrackedDataComponent,
    SearchPlayersComponent,
    ManageIndexalDbComponent,
    ManageApikeysComponent,
    IndexPlayerComponent
  ],
  imports: [
    BrowserAnimationsModule,
    ToastrModule.forRoot({
      positionClass: 'toast-top-right',
      preventDuplicates: true,
      timeOut: 3000,
      extendedTimeOut: 1000,
      progressBar: true,
      closeButton: true
    }),
    BrowserModule,
    AppRoutingModule,
    HttpClientModule,
    ManualDataModule,
    FormsModule,
    DeletePlayerModule,
    routing
  ],
  providers: [],
  bootstrap: [AppComponent]
})
export class AppModule { }
