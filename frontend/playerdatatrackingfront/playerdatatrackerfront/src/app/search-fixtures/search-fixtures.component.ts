import { Component, OnInit } from '@angular/core';
import { FixtureService, Fixture, Torneo } from '../services/fixture.service';

@Component({
  selector: 'app-search-fixtures',
  templateUrl: './search-fixtures.component.html',
  styleUrls: ['./search-fixtures.component.css']
})
export class SearchFixturesComponent implements OnInit {
  teamName: string = '';
  selectedLeagueId: number | null = null;
  studiedLeagues: Torneo[] = [];
  fixtures: Fixture[] = [];
  liveFixtures: Fixture[] = [];
  isLoadingLive: boolean = false;
  isLoadingSearch: boolean = false;
  liveLoaded: boolean = false;
  searchPerformed: boolean = false;

  constructor(private fixtureService: FixtureService) {}

  ngOnInit(): void {
    this.fixtureService.getStudiedLeagues().subscribe(leagues => {
      this.studiedLeagues = leagues;
    });
  }

  loadLiveFixtures(): void {
    this.isLoadingLive = true;
    this.liveLoaded = false;
    this.fixtureService.getLiveFixtures().subscribe(fixtures => {
      this.liveFixtures = fixtures;
      this.isLoadingLive = false;
      this.liveLoaded = true;
    });
  }

  searchByTeam(): void {
    if (!this.teamName.trim()) return;
    this.isLoadingSearch = true;
    this.searchPerformed = false;
    this.fixtureService.searchByTeam(this.teamName.trim()).subscribe(fixtures => {
      this.fixtures = fixtures;
      this.isLoadingSearch = false;
      this.searchPerformed = true;
    });
  }

  searchByLeague(): void {
    if (!this.selectedLeagueId) return;
    this.isLoadingSearch = true;
    this.searchPerformed = false;
    this.fixtureService.searchByLeague(this.selectedLeagueId).subscribe(fixtures => {
      this.fixtures = fixtures;
      this.isLoadingSearch = false;
      this.searchPerformed = true;
    });
  }

  formatDate(dateString: string): string {
    if (!dateString) return '';
    const date = new Date(dateString);
    const day = String(date.getDate()).padStart(2, '0');
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const year = date.getFullYear();
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');
    return `${day}/${month}/${year} ${hours}:${minutes}`;
  }

  getStatusLabel(fixture: Fixture): string {
    const s = fixture.statusShort;
    if (s === '1H' || s === '2H' || s === 'ET') return `${fixture.statusElapsed ?? 0}'`;
    if (s === 'HT') return 'HT';
    if (s === 'FT') return 'FT';
    if (s === 'NS') return 'No iniciado';
    if (s === 'PST') return 'Aplazado';
    if (s === 'CANC') return 'Cancelado';
    return fixture.statusLong || s;
  }

  isLive(fixture: Fixture): boolean {
    return ['1H', 'HT', '2H', 'ET', 'BT', 'P', 'LIVE'].includes(fixture.statusShort);
  }
}
