import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { FixtureService, Fixture, Torneo } from './fixture.service';

describe('FixtureService', () => {
  let service: FixtureService;
  let httpMock: HttpTestingController;

  const BASE = 'http://localhost:8080';

  const mockFixture: Fixture = {
    id: 1,
    leagueId: 140,
    leagueName: 'La Liga',
    season: 2024,
    round: 'Regular Season - 10',
    matchDate: '2024-10-20T15:00:00Z',
    matchTimestamp: 1729436400,
    statusShort: 'FT',
    statusLong: 'Match Finished',
    statusElapsed: 90,
    homeTeamId: 541,
    homeTeamName: 'Real Madrid',
    awayTeamId: 529,
    awayTeamName: 'Barcelona',
    goalsHome: 2,
    goalsAway: 1,
    venueName: 'Santiago Bernabeu',
    venueCity: 'Madrid'
  };

  const mockTorneo: Torneo = { id: 140, name: 'La Liga', studied: true };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [FixtureService]
    });
    service = TestBed.inject(FixtureService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  // getLiveFixtures

  it('getLiveFixtures - sends POST to /liveFixtures', () => {
    service.getLiveFixtures().subscribe();
    const req = httpMock.expectOne(`${BASE}/liveFixtures`);
    expect(req.request.method).toBe('POST');
    req.flush({ code: 0, entityList: [] });
  });

  it('getLiveFixtures - returns entityList on success', () => {
    let result: Fixture[] = [];
    service.getLiveFixtures().subscribe(f => (result = f));

    httpMock.expectOne(`${BASE}/liveFixtures`).flush({ code: 0, entityList: [mockFixture] });

    expect(result.length).toBe(1);
    expect(result[0].homeTeamName).toBe('Real Madrid');
  });

  it('getLiveFixtures - returns empty array when code !== 0', () => {
    let result: Fixture[] = [mockFixture];
    service.getLiveFixtures().subscribe(f => (result = f));

    httpMock.expectOne(`${BASE}/liveFixtures`).flush({ code: 2, description: 'DB error' });

    expect(result).toEqual([]);
  });

  it('getLiveFixtures - returns empty array on HTTP error', () => {
    let result: Fixture[] = [mockFixture];
    service.getLiveFixtures().subscribe(f => (result = f));

    httpMock.expectOne(`${BASE}/liveFixtures`).error(new ErrorEvent('Network error'));

    expect(result).toEqual([]);
  });

  // getStudiedLeagues

  it('getStudiedLeagues - sends POST to /studiedLeagues', () => {
    service.getStudiedLeagues().subscribe();
    const req = httpMock.expectOne(`${BASE}/studiedLeagues`);
    expect(req.request.method).toBe('POST');
    req.flush({ code: 0, entityList: [] });
  });

  it('getStudiedLeagues - returns league list on success', () => {
    let result: Torneo[] = [];
    service.getStudiedLeagues().subscribe(l => (result = l));

    httpMock.expectOne(`${BASE}/studiedLeagues`).flush({ code: 0, entityList: [mockTorneo] });

    expect(result.length).toBe(1);
    expect(result[0].name).toBe('La Liga');
  });

  it('getStudiedLeagues - returns empty array when code !== 0', () => {
    let result: Torneo[] = [mockTorneo];
    service.getStudiedLeagues().subscribe(l => (result = l));

    httpMock.expectOne(`${BASE}/studiedLeagues`).flush({ code: 3, description: 'error' });

    expect(result).toEqual([]);
  });

  // searchByTeam

  it('searchByTeam - sends nombre in request body', () => {
    service.searchByTeam('Real Madrid').subscribe();
    const req = httpMock.expectOne(`${BASE}/searchFixtures`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ nombre: 'Real Madrid' });
    req.flush({ code: 0, entityList: [] });
  });

  it('searchByTeam - returns fixtures on success', () => {
    let result: Fixture[] = [];
    service.searchByTeam('Barcelona').subscribe(f => (result = f));

    httpMock.expectOne(`${BASE}/searchFixtures`).flush({ code: 0, entityList: [mockFixture] });

    expect(result.length).toBe(1);
  });

  it('searchByTeam - returns empty array on HTTP error', () => {
    let result: Fixture[] = [mockFixture];
    service.searchByTeam('Barcelona').subscribe(f => (result = f));

    httpMock.expectOne(`${BASE}/searchFixtures`).error(new ErrorEvent('Network error'));

    expect(result).toEqual([]);
  });

  // searchByLeague

  it('searchByLeague - sends id in request body', () => {
    service.searchByLeague(140).subscribe();
    const req = httpMock.expectOne(`${BASE}/searchFixtures`);
    expect(req.request.body).toEqual({ id: 140 });
    req.flush({ code: 0, entityList: [] });
  });

  it('searchByLeague - returns fixtures on success', () => {
    let result: Fixture[] = [];
    service.searchByLeague(39).subscribe(f => (result = f));

    httpMock.expectOne(`${BASE}/searchFixtures`).flush({ code: 0, entityList: [mockFixture, mockFixture] });

    expect(result.length).toBe(2);
  });
});
