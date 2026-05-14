import { DeletePlayerModule } from './manual-data/delete-player/delete-player.module';
import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { AppRoutingModule } from './app-routing.module';
import { HttpClientModule, HTTP_INTERCEPTORS } from '@angular/common/http';
import { ManualDataModule } from './manual-data/manual-data.module';
import { AppComponent } from './app.component';
import { routing } from './app.routing';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
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
import { CredentialsInterceptor } from './core/interceptors/credentials.interceptor';
import { LoginComponent } from './features/login/login.component';
import { HomeComponent } from './home/home.component';
import { RegisterComponent } from './features/register/register.component';
import { UpdateTrackedPlayerComponent } from './update-tracked-player/update-tracked-player.component';
import { SearchFixturesComponent } from './search-fixtures/search-fixtures.component';
import { FixtureDetailComponent } from './fixture-detail/fixture-detail.component';
import { PlayerNotFoundComponent } from './player-not-found/player-not-found.component';
import { ComparePlayersComponent } from './compare-players/compare-players.component';
import { LeagueManagementComponent } from './league-management/league-management.component';
import { UserManagementComponent } from './user-management/user-management.component';

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
    IndexPlayerComponent,
    LoginComponent,
    HomeComponent,
    RegisterComponent,
    UpdateTrackedPlayerComponent,
    SearchFixturesComponent,
    FixtureDetailComponent,
    PlayerNotFoundComponent,
    ComparePlayersComponent,
    LeagueManagementComponent,
    UserManagementComponent
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
    ReactiveFormsModule,
    ManualDataModule,
    FormsModule,
    DeletePlayerModule,
    routing
  ],
  providers: [
    { provide: HTTP_INTERCEPTORS, useClass: CredentialsInterceptor, multi: true }
  ],
  bootstrap: [AppComponent]
})
export class AppModule { }
