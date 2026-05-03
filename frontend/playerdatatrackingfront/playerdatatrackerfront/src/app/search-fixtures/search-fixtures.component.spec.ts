import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { of } from 'rxjs';
import { SearchFixturesComponent } from './search-fixtures.component';
import { FixtureService, Fixture, Torneo } from '../services/fixture.service';

describe('SearchFixturesComponent', () => {
  let component: SearchFixturesComponent;
  let fixture: ComponentFixture<SearchFixturesComponent>;
  let fixtureServiceSpy: jasmine.SpyObj<FixtureService>;

  const mockLeagues: Torneo[] = [
    { id: 140, name: 'La Liga', studied: true },
    { id: 39, name: 'Premier League', studied: true }
  ];

  const mockFixtures: Fixture[] = [{
    id: 1,
    leagueId: 140,
    leagueName: 'La Liga',
    season: 2024,
    round: 'Regular Season - 10',
    matchDate: '2024-10-20T15:00:00Z',
    matchTimestamp: 0,
    statusShort: 'FT',
    statusLong: 'Match Finished',
    statusElapsed: 90,
    homeTeamId: 541,
    homeTeamName: 'Real Madrid',
    awayTeamId: 529,
    awayTeamName: 'Barcelona',
    goalsHome: 3,
    goalsAway: 1,
    venueName: 'Bernabeu',
    venueCity: 'Madrid'
  }];

  beforeEach(async () => {
    fixtureServiceSpy = jasmine.createSpyObj('FixtureService', [
      'getStudiedLeagues', 'getLiveFixtures', 'searchByTeam', 'searchByLeague'
    ]);
    fixtureServiceSpy.getStudiedLeagues.and.returnValue(of(mockLeagues));

    await TestBed.configureTestingModule({
      declarations: [SearchFixturesComponent],
      imports: [FormsModule],
      providers: [{ provide: FixtureService, useValue: fixtureServiceSpy }]
    }).compileComponents();

    fixture = TestBed.createComponent(SearchFixturesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  // ngOnInit

  it('loads studied leagues on init', () => {
    expect(fixtureServiceSpy.getStudiedLeagues).toHaveBeenCalledTimes(1);
    expect(component.studiedLeagues).toEqual(mockLeagues);
  });

  it('starts with empty fixtures and liveFixtures', () => {
    expect(component.fixtures).toEqual([]);
    expect(component.liveFixtures).toEqual([]);
  });

  it('starts with liveLoaded and searchPerformed as false', () => {
    expect(component.liveLoaded).toBeFalse();
    expect(component.searchPerformed).toBeFalse();
  });

  // loadLiveFixtures

  it('loadLiveFixtures - sets liveFixtures and liveLoaded', () => {
    fixtureServiceSpy.getLiveFixtures.and.returnValue(of(mockFixtures));

    component.loadLiveFixtures();

    expect(component.liveFixtures).toEqual(mockFixtures);
    expect(component.liveLoaded).toBeTrue();
    expect(component.isLoadingLive).toBeFalse();
  });

  it('loadLiveFixtures - sets liveLoaded true even when list is empty', () => {
    fixtureServiceSpy.getLiveFixtures.and.returnValue(of([]));

    component.loadLiveFixtures();

    expect(component.liveLoaded).toBeTrue();
    expect(component.liveFixtures.length).toBe(0);
  });

  it('loadLiveFixtures - calls service once', () => {
    fixtureServiceSpy.getLiveFixtures.and.returnValue(of([]));

    component.loadLiveFixtures();

    expect(fixtureServiceSpy.getLiveFixtures).toHaveBeenCalledTimes(1);
  });

  // searchByTeam

  it('searchByTeam - does nothing when teamName is blank', () => {
    component.teamName = '   ';
    component.searchByTeam();
    expect(fixtureServiceSpy.searchByTeam).not.toHaveBeenCalled();
  });

  it('searchByTeam - calls service with trimmed name', () => {
    component.teamName = '  Real Madrid  ';
    fixtureServiceSpy.searchByTeam.and.returnValue(of(mockFixtures));

    component.searchByTeam();

    expect(fixtureServiceSpy.searchByTeam).toHaveBeenCalledWith('Real Madrid');
  });

  it('searchByTeam - updates fixtures and searchPerformed', () => {
    component.teamName = 'Arsenal';
    fixtureServiceSpy.searchByTeam.and.returnValue(of(mockFixtures));

    component.searchByTeam();

    expect(component.fixtures).toEqual(mockFixtures);
    expect(component.searchPerformed).toBeTrue();
    expect(component.isLoadingSearch).toBeFalse();
  });

  // searchByLeague

  it('searchByLeague - does nothing when selectedLeagueId is null', () => {
    component.selectedLeagueId = null;
    component.searchByLeague();
    expect(fixtureServiceSpy.searchByLeague).not.toHaveBeenCalled();
  });

  it('searchByLeague - calls service with leagueId', () => {
    component.selectedLeagueId = 140;
    fixtureServiceSpy.searchByLeague.and.returnValue(of(mockFixtures));

    component.searchByLeague();

    expect(fixtureServiceSpy.searchByLeague).toHaveBeenCalledWith(140);
    expect(component.fixtures).toEqual(mockFixtures);
    expect(component.searchPerformed).toBeTrue();
  });

  // formatDate

  it('formatDate - returns empty string for empty input', () => {
    expect(component.formatDate('')).toBe('');
  });

  it('formatDate - formats date as DD/MM/YYYY HH:mm', () => {
    const result = component.formatDate('2024-10-20T15:00:00Z');
    expect(result).toMatch(/^\d{2}\/\d{2}\/\d{4} \d{2}:\d{2}$/);
  });

  // getStatusLabel

  it('getStatusLabel - 1H shows elapsed minutes', () => {
    const f = { ...mockFixtures[0], statusShort: '1H', statusElapsed: 35 };
    expect(component.getStatusLabel(f as Fixture)).toBe("35'");
  });

  it('getStatusLabel - 2H shows elapsed minutes', () => {
    const f = { ...mockFixtures[0], statusShort: '2H', statusElapsed: 78 };
    expect(component.getStatusLabel(f as Fixture)).toBe("78'");
  });

  it('getStatusLabel - HT returns HT', () => {
    const f = { ...mockFixtures[0], statusShort: 'HT' };
    expect(component.getStatusLabel(f as Fixture)).toBe('HT');
  });

  it('getStatusLabel - FT returns FT', () => {
    const f = { ...mockFixtures[0], statusShort: 'FT' };
    expect(component.getStatusLabel(f as Fixture)).toBe('FT');
  });

  it('getStatusLabel - NS returns No iniciado', () => {
    const f = { ...mockFixtures[0], statusShort: 'NS' };
    expect(component.getStatusLabel(f as Fixture)).toBe('No iniciado');
  });

  it('getStatusLabel - PST returns Aplazado', () => {
    const f = { ...mockFixtures[0], statusShort: 'PST' };
    expect(component.getStatusLabel(f as Fixture)).toBe('Aplazado');
  });

  it('getStatusLabel - unknown status falls back to statusLong', () => {
    const f = { ...mockFixtures[0], statusShort: 'XYZ', statusLong: 'Custom Status' };
    expect(component.getStatusLabel(f as Fixture)).toBe('Custom Status');
  });

  // isLive

  it('isLive - returns true for first-half status', () => {
    expect(component.isLive({ ...mockFixtures[0], statusShort: '1H' } as Fixture)).toBeTrue();
  });

  it('isLive - returns true for half-time', () => {
    expect(component.isLive({ ...mockFixtures[0], statusShort: 'HT' } as Fixture)).toBeTrue();
  });

  it('isLive - returns true for second-half status', () => {
    expect(component.isLive({ ...mockFixtures[0], statusShort: '2H' } as Fixture)).toBeTrue();
  });

  it('isLive - returns false for finished match', () => {
    expect(component.isLive({ ...mockFixtures[0], statusShort: 'FT' } as Fixture)).toBeFalse();
  });

  it('isLive - returns false for not-started match', () => {
    expect(component.isLive({ ...mockFixtures[0], statusShort: 'NS' } as Fixture)).toBeFalse();
  });
});
