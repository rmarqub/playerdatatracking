import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { environment } from 'src/enviroment/environment';

interface GenericResponse<T> {
  code: number;
  description: string;
  entity?: T;
  entityList?: T[];
}

export interface Fixture {
  id: number;
  leagueId: number;
  leagueName: string;
  season: number;
  round: string;
  matchDate: string;
  matchTimestamp: number;
  statusShort: string;
  statusLong: string;
  statusElapsed: number | null;
  homeTeamId: number;
  homeTeamName: string;
  awayTeamId: number;
  awayTeamName: string;
  goalsHome: number | null;
  goalsAway: number | null;
  scoreHtHome: number | null;
  scoreHtAway: number | null;
  scoreEtHome: number | null;
  scoreEtAway: number | null;
  scorePenHome: number | null;
  scorePenAway: number | null;
  referee: string | null;
  venueName: string | null;
  venueCity: string | null;
  hasAnalysis?: boolean;
}

export interface FixtureEvent {
  id: number;
  teamId: number | null;
  timeElapsed: number | null;
  timeExtra: number | null;
  playerId: number | null;
  playerName: string | null;
  assistId: number | null;
  assistName: string | null;
  eventType: string;
  eventDetail: string | null;
  comments: string | null;
}

export interface Torneo {
  id: number;
  name: string;
  studied: boolean;
}

export interface TorneoInfo {
  id: number;
  name: string;
  studied: boolean;
  paisId: number | null;
  paisName: string | null;
}

export interface FixtureTeamStats {
  teamId: number;
  shotsOnGoal: number | null;
  shotsOffGoal: number | null;
  shotsTotal: number | null;
  shotsBlocked: number | null;
  shotsInsideBox: number | null;
  shotsOutsideBox: number | null;
  fouls: number | null;
  cornerKicks: number | null;
  offsides: number | null;
  ballPossession: number | null;
  yellowCards: number | null;
  redCards: number | null;
  goalkeeperSaves: number | null;
  totalPasses: number | null;
  passesAccurate: number | null;
  passesPct: number | null;
  expectedGoals: number | null;
  goalsPrevented: number | null;
}

export interface FixturePlayerStats {
  playerId: number;
  teamId: number;
  playerName: string | null;
  position: string | null;
  minutesPlayed: number | null;
  rating: number | null;
  captain: boolean;
  substitute: boolean;
  offsides: number | null;
  shotsTotal: number | null;
  shotsOn: number | null;
  goalsScored: number | null;
  goalsConceded: number | null;
  assists: number | null;
  saves: number | null;
  passesTotal: number | null;
  passesKey: number | null;
  passesAccuracy: number | null;
  tacklesTotal: number | null;
  tacklesBlocks: number | null;
  interceptions: number | null;
  duelsTotal: number | null;
  duelsWon: number | null;
  dribblesAtt: number | null;
  dribblesSuc: number | null;
  dribblesPast: number | null;
  foulsDrawn: number | null;
  foulsCommitted: number | null;
  yellowCards: number | null;
  redCards: number | null;
  yellowRedCards: number | null;
  penaltyWon: number | null;
  penaltyScored: number | null;
  penaltyMissed: number | null;
  penaltySaved: number | null;
  penaltyCommitted: number | null;
}

export interface FixtureLineupEntry {
  id: number;
  teamId: number;
  formation: string | null;
  coachId: number | null;
  coachName: string | null;
  playerId: number | null;
  playerName: string | null;
  playerNumber: number | null;
  position: string | null;
  grid: string | null;
  substitute: boolean;
}

export interface H2HGoalScorer {
  playerId: number | null;
  playerName: string | null;
  teamId: number | null;
  minute: number | null;
  minuteExtra: number | null;
  detail: string | null;
}

export interface H2HBestPlayer {
  playerId: number | null;
  playerName: string | null;
  teamId: number | null;
  rating: number | null;
}

export interface H2HFixtureSummary {
  fixtureId: number;
  matchDate: string | null;
  season: number | null;
  leagueName: string | null;
  round: string | null;
  homeTeamId: number;
  homeTeamName: string | null;
  awayTeamId: number;
  awayTeamName: string | null;
  goalsHome: number | null;
  goalsAway: number | null;
  scorers: H2HGoalScorer[];
  bestPlayers: H2HBestPlayer[];
}

export interface MatchPredictionResult1x2 {
  homeWin: number;
  draw: number;
  awayWin: number;
  predicted: 'home_win' | 'draw' | 'away_win';
  confidence: number;
}

export interface MatchPredictionBinary {
  yes?: number;
  no?: number;
  predicted: string;
}

export interface MatchPredictionGoalLine {
  over: number;
  under: number;
  predicted: string;
}

export interface MatchPredictionGoals {
  expectedTotal: number;
  over05: MatchPredictionGoalLine;
  over15: MatchPredictionGoalLine;
  over25: MatchPredictionGoalLine;
  over35: MatchPredictionGoalLine;
}

export interface MatchPredictionCorners {
  expectedTotal: number;
  over3: number;
  over4: number;
  over5: number;
  over6: number;
  over7: number;
  over8: number;
  over9: number;
  over10: number;
}

export interface MatchPrediction {
  fixtureId: number;
  homeTeam: string;
  awayTeam: string;
  league: string;
  season: number;
  matchDate: string;
  status: string;
  result1x2: MatchPredictionResult1x2;
  goals: MatchPredictionGoals;
  btts: MatchPredictionBinary;
  corners: MatchPredictionCorners;
  warnings: string[];
}

export interface SquadPlayerEntry {
  playerId: number;
  playerName: string;
}

export interface FixtureSquadData {
  homePlayers: SquadPlayerEntry[];
  awayPlayers: SquadPlayerEntry[];
}

export interface ContextualAnalysisData {
  fixtureId: number;
  homeCurrentForm: number;
  homeStadiumAtmosphere: number;
  homeDefensiveBlock: number;
  homeOffensiveRhythm: number;
  homeTeamNeeds: number;
  homeSetPieces: number;
  homeFatigue: number;
  homeUnavailablePlayers: string[];
  awayCurrentForm: number;
  awayStadiumAtmosphere: number;
  awayDefensiveBlock: number;
  awayOffensiveRhythm: number;
  awayTeamNeeds: number;
  awaySetPieces: number;
  awayFatigue: number;
  awayUnavailablePlayers: string[];
  notes: string | null;
  updatedAt: string | null;
  baseHomeWin: number | null;
  baseDraw: number | null;
  baseAwayWin: number | null;
}

export interface ContextualMatchPrediction {
  fixtureId: number;
  homeTeam: string;
  awayTeam: string;
  analysisFound: boolean;
  netDelta: number;
  baseHomeWin: number;
  baseDraw: number;
  baseAwayWin: number;
  adjHomeWin: number;
  adjDraw: number;
  adjAwayWin: number;
  adjPredicted: 'home_win' | 'draw' | 'away_win';
  adjConfidence: number;
  goals: MatchPredictionGoals;
  btts: MatchPredictionBinary;
  corners: MatchPredictionCorners;
  warnings: string[];
}

export interface H2HComparisonData {
  team1Id: number;
  team1Name: string | null;
  team2Id: number;
  team2Name: string | null;
  totalMatches: number;
  team1Wins: number;
  team2Wins: number;
  draws: number;
  team1Goals: number;
  team2Goals: number;
  team1AvgPossession: number | null;
  team2AvgPossession: number | null;
  team1AvgShots: number | null;
  team2AvgShots: number | null;
  team1AvgShotsOnTarget: number | null;
  team2AvgShotsOnTarget: number | null;
  team1AvgCorners: number | null;
  team2AvgCorners: number | null;
  team1AvgFouls: number | null;
  team2AvgFouls: number | null;
  team1AvgYellowCards: number | null;
  team2AvgYellowCards: number | null;
  team1AvgxG: number | null;
  team2AvgxG: number | null;
}

export interface ApiFixtureStatus {
  long: string;
  short: string;
  elapsed: number | null;
  extra: number | null;
}

export interface ApiFixtureTeam {
  id: number;
  name: string;
  logo: string;
  winner: boolean | null;
}

export interface ApiFixtureItem {
  fixture: {
    id: number;
    referee: string | null;
    timezone: string;
    date: string;
    timestamp: number;
    periods: { first: number | null; second: number | null };
    venue: { id: number | null; name: string | null; city: string | null };
    status: ApiFixtureStatus;
  };
  league: {
    id: number;
    name: string;
    country: string;
    logo: string;
    flag: string | null;
    season: number;
    round: string;
  };
  teams: { home: ApiFixtureTeam; away: ApiFixtureTeam };
  goals: { home: number | null; away: number | null };
  score: {
    halftime: { home: number | null; away: number | null };
    fulltime: { home: number | null; away: number | null };
    extratime: { home: number | null; away: number | null };
    penalty: { home: number | null; away: number | null };
  };
  events: any[];
  lineups: any[];
  statistics: any[];
  players: any[];
}

export interface ContextualWeightsSnapshot {
  // Home-Away asymmetry weights
  wForma: number | null;
  wNeeds: number | null;
  wDef: number | null;
  wOff: number | null;
  wFatigue: number | null;
  wSetPieces: number | null;
  wAtm: number | null;
  wUnavail: number | null;
  // Draw-affinity weights
  wFormaD: number | null;
  wNeedsD: number | null;
  wDefD: number | null;
  wOffD: number | null;
  wFatigueD: number | null;
  wSetPiecesD: number | null;
  wAtmD: number | null;
  wUnavailD: number | null;
  calibrationDate: string | null;
  nSamples: number | null;
  notes: string | null;
  fromDb: boolean;
}

export interface AnalysisMatchResult {
  fixtureId: number;
  homeTeamName: string;
  awayTeamName: string;
  matchDate: string | null;
  goalsHome: number | null;
  goalsAway: number | null;
  actualResult: string;
  baseHomeWin: number;
  baseDraw: number;
  baseAwayWin: number;
  adjHomeWin: number;
  adjDraw: number;
  adjAwayWin: number;
  adjPredicted: string;
  netDelta: number;
  correct: boolean;
  baseCorrect: boolean;
  deltaHelpful: boolean;
  brierScore: number | null;
  logLoss: number | null;
}

export interface AnalysisHistoryData {
  totalAnalysed: number;
  processedAnalyses: number;
  correctPredictions: number;
  incorrectPredictions: number;
  accuracyRate: number;
  avgBrierScore: number;
  avgLogLoss: number;
  calibrationRun: boolean;
  weightsUpdated: boolean;
  calibrationMessage: string;
  currentWeights: ContextualWeightsSnapshot;
  matchResults: AnalysisMatchResult[];
}

@Injectable({
  providedIn: 'root'
})
export class FixtureService {
  private base = environment.apiUrl;

  constructor(private http: HttpClient) { }

  getLiveFixtures(): Observable<Fixture[]> {
    return this.http.post<GenericResponse<Fixture>>(`${this.base}/liveFixtures`, {}).pipe(
      map(r => r.code === 0 ? (r.entityList || []) : []),
      catchError(() => of([]))
    );
  }

  getStudiedLeagues(): Observable<Torneo[]> {
    return this.http.post<GenericResponse<Torneo>>(`${this.base}/studiedLeagues`, {}).pipe(
      map(r => r.code === 0 ? (r.entityList || []) : []),
      catchError(() => of([]))
    );
  }

  searchFixturesCombined(teamName: string, leagueIds: number[]): Observable<Fixture[]> {
    const body: any = {};
    if (teamName?.trim()) body.nombre = teamName.trim();
    if (leagueIds?.length > 0) body.leagueIds = leagueIds;
    return this.http.post<GenericResponse<Fixture>>(`${this.base}/searchFixtures`, body).pipe(
      map(r => r.code === 0 ? (r.entityList || []) : []),
      catchError(() => of([]))
    );
  }

  getFixtureById(fixtureId: number): Observable<Fixture | null> {
    return this.http.post<GenericResponse<Fixture>>(`${this.base}/fixtureById`, { id: fixtureId }).pipe(
      map(r => r.code === 0 ? (r.entity || null) : null),
      catchError(() => of(null))
    );
  }

  getFixtureEventsFromDb(fixtureId: number): Observable<FixtureEvent[]> {
    return this.http.post<GenericResponse<FixtureEvent>>(`${this.base}/fixtureEvents`, { id: fixtureId }).pipe(
      map(r => r.code === 0 ? (r.entityList || []) : []),
      catchError(() => of([]))
    );
  }

  getLiveFixturesFromApi(): Observable<ApiFixtureItem[]> {
    return this.http.post<GenericResponse<ApiFixtureItem>>(`${this.base}/liveFixturesApi`, {}).pipe(
      map(r => r.code === 0 ? (r.entityList || []) : []),
      catchError(() => of([]))
    );
  }

  getFixtureDetailFromApi(fixtureId: number): Observable<ApiFixtureItem | null> {
    return this.http.post<GenericResponse<ApiFixtureItem>>(`${this.base}/fixtureDetailApi`, { id: fixtureId }).pipe(
      map(r => r.code === 0 ? (r.entity || null) : null),
      catchError(() => of(null))
    );
  }

  getPlayerIdByIndexId(indexId: number): Observable<number | null> {
    return this.http.post<GenericResponse<number>>(`${this.base}/playerIdByIndexId`, { indexId }).pipe(
      map(r => r.code === 0 && r.entity != null ? r.entity : null),
      catchError(() => of(null))
    );
  }

  getFixtureTeamStats(fixtureId: number): Observable<FixtureTeamStats[]> {
    return this.http.post<GenericResponse<FixtureTeamStats>>(`${this.base}/fixtureTeamStats`, { id: fixtureId }).pipe(
      map(r => r.code === 0 ? (r.entityList || []) : []),
      catchError(() => of([]))
    );
  }

  getFixturePlayerStats(fixtureId: number): Observable<FixturePlayerStats[]> {
    return this.http.post<GenericResponse<FixturePlayerStats>>(`${this.base}/fixturePlayerStats`, { id: fixtureId }).pipe(
      map(r => r.code === 0 ? (r.entityList || []) : []),
      catchError(() => of([]))
    );
  }

  getFixtureLineup(fixtureId: number): Observable<FixtureLineupEntry[]> {
    return this.http.post<GenericResponse<FixtureLineupEntry>>(`${this.base}/fixtureLineup`, { id: fixtureId }).pipe(
      map(r => r.code === 0 ? (r.entityList || []) : []),
      catchError(() => of([]))
    );
  }

  getH2HFixtures(fixtureId: number): Observable<H2HFixtureSummary[]> {
    return this.http.post<GenericResponse<H2HFixtureSummary>>(`${this.base}/h2hFixtures`, { id: fixtureId }).pipe(
      map(r => r.code === 0 ? (r.entityList || []) : []),
      catchError(() => of([]))
    );
  }

  getH2HComparison(fixtureId: number): Observable<H2HComparisonData | null> {
    return this.http.post<GenericResponse<H2HComparisonData>>(`${this.base}/h2hComparison`, { id: fixtureId }).pipe(
      map(r => r.code === 0 ? (r.entity || null) : null),
      catchError(() => of(null))
    );
  }

  getMatchPrediction(fixtureId: number): Observable<MatchPrediction | null> {
    return this.http.post<GenericResponse<MatchPrediction>>(`${this.base}/matchPrediction`, { id: fixtureId }).pipe(
      map(r => r.code === 0 ? (r.entity || null) : null),
      catchError(() => of(null))
    );
  }

  saveContextualAnalysis(fixtureId: number, analysis: {
    homeCurrentForm: number; homeStadiumAtmosphere: number; homeDefensiveBlock: number;
    homeOffensiveRhythm: number; homeTeamNeeds: number; homeSetPieces: number; homeFatigue: number;
    homeUnavailablePlayers: string[];
    awayCurrentForm: number; awayStadiumAtmosphere: number; awayDefensiveBlock: number;
    awayOffensiveRhythm: number; awayTeamNeeds: number; awaySetPieces: number; awayFatigue: number;
    awayUnavailablePlayers: string[];
    notes: string;
    baseHomeWin?: number | null;
    baseDraw?: number | null;
    baseAwayWin?: number | null;
  }): Observable<ContextualAnalysisData | null> {
    const body = { contextualAnalysis: { fixtureId, ...analysis } };
    return this.http.post<GenericResponse<ContextualAnalysisData>>(`${this.base}/saveContextualAnalysis`, body).pipe(
      map(r => r.code === 0 ? (r.entity || null) : null),
      catchError(() => of(null))
    );
  }

  getContextualMatchPrediction(fixtureId: number): Observable<ContextualMatchPrediction | null> {
    return this.http.post<GenericResponse<ContextualMatchPrediction>>(`${this.base}/contextualMatchPrediction`, { id: fixtureId }).pipe(
      map(r => r.code === 0 ? (r.entity || null) : null),
      catchError(() => of(null))
    );
  }

  getContextualAnalysis(fixtureId: number): Observable<ContextualAnalysisData | null> {
    return this.http.post<GenericResponse<ContextualAnalysisData>>(`${this.base}/contextualAnalysis`, { id: fixtureId }).pipe(
      map(r => r.code === 0 ? (r.entity || null) : null),
      catchError(() => of(null))
    );
  }

  getFixtureSquad(fixtureId: number): Observable<FixtureSquadData | null> {
    return this.http.post<GenericResponse<FixtureSquadData>>(`${this.base}/fixtureSquad`, { id: fixtureId }).pipe(
      map(r => r.code === 0 ? (r.entity || null) : null),
      catchError(() => of(null))
    );
  }

  regenerateContextualAnalyses(scope: 'not_started' | 'finished' | 'all'): Observable<{ ok: boolean; message: string }> {
    return this.http.post<GenericResponse<string>>(`${this.base}/regenerateContextualAnalyses`, { scope }).pipe(
      map(r => ({ ok: r.code === 0, message: r.description || '' })),
      catchError(() => of({ ok: false, message: 'Error de conexión con el servidor' }))
    );
  }

  getFixturesWithAnalysis(): Observable<Fixture[]> {
    return this.http.post<GenericResponse<Fixture>>(`${this.base}/fixturesWithAnalysis`, {}).pipe(
      map(r => r.code === 0 ? (r.entityList || []) : []),
      catchError(() => of([]))
    );
  }

  getAnalysisHistory(): Observable<AnalysisHistoryData | null> {
    return this.http.post<GenericResponse<AnalysisHistoryData>>(`${this.base}/analysisHistory`, {}).pipe(
      map(r => r.code === 0 ? (r.entity || null) : null),
      catchError(() => of(null))
    );
  }

  updateContextualDeltas(): Observable<{ ok: boolean; entity: string }> {
    return this.http.post<GenericResponse<string>>(`${this.base}/updateContextualDeltas`, {}).pipe(
      map(r => ({ ok: r.code === 0, entity: r.entity || r.description || '' })),
      catchError(() => of({ ok: false, entity: 'Error de conexión con el servidor' }))
    );
  }

  getCountries(update: boolean): Observable<{ ok: boolean; description: string }> {
    return this.http.post<GenericResponse<any>>(`${this.base}/countries`, { update: String(update) }).pipe(
      map(r => ({ ok: r.code === 0, description: r.description || '' })),
      catchError(() => of({ ok: false, description: 'Error de conexión con el servidor' }))
    );
  }

  updateCountriesFromJson(): Observable<{ ok: boolean; description: string }> {
    return this.http.post<GenericResponse<any>>(`${this.base}/updateCountries`, {}).pipe(
      map(r => ({ ok: r.code === 0, description: r.description || '' })),
      catchError(() => of({ ok: false, description: 'Error de conexión con el servidor' }))
    );
  }

  getLeagues(update: boolean): Observable<{ ok: boolean; description: string }> {
    return this.http.post<GenericResponse<any>>(`${this.base}/leagues`, { update: String(update) }).pipe(
      map(r => ({ ok: r.code === 0, description: r.description || '' })),
      catchError(() => of({ ok: false, description: 'Error de conexión con el servidor' }))
    );
  }

  updateLeagues(): Observable<{ ok: boolean; description: string }> {
    return this.http.post<GenericResponse<any>>(`${this.base}/updateLeagues`, {}).pipe(
      map(r => ({ ok: r.code === 0, description: r.description || '' })),
      catchError(() => of({ ok: false, description: 'Error de conexión con el servidor' }))
    );
  }

  getAllLeaguesForManagement(): Observable<TorneoInfo[]> {
    return this.http.post<GenericResponse<TorneoInfo>>(`${this.base}/allLeagues`, {}).pipe(
      map(r => r.code === 0 ? (r.entityList || []) : []),
      catchError(() => of([]))
    );
  }

  updateStudiedLeagues(studiedIds: number[]): Observable<{ ok: boolean; description: string }> {
    return this.http.post<GenericResponse<any>>(`${this.base}/updateStudiedLeagues`, { leagueIds: studiedIds }).pipe(
      map(r => ({ ok: r.code === 0, description: r.description || '' })),
      catchError(() => of({ ok: false, description: 'Error de conexión con el servidor' }))
    );
  }

  updateClubsInfo(update: boolean, restUpdate: boolean): Observable<{ ok: boolean; description: string }> {
    return this.http.post<GenericResponse<any>>(`${this.base}/updateClubsInfo`, { update: String(update), restUpdate: String(restUpdate) }).pipe(
      map(r => ({ ok: r.code === 0, description: r.description || '' })),
      catchError(() => of({ ok: false, description: 'Error de conexión con el servidor' }))
    );
  }

  updatePlayers(season: string): Observable<{ ok: boolean; description: string }> {
    return this.http.post<GenericResponse<any>>(`${this.base}/updatePlayers`, { update: 'true', restUpdate: 'true', season }).pipe(
      map(r => ({ ok: r.code === 0, description: r.description || '' })),
      catchError(() => of({ ok: false, description: 'Error de conexión con el servidor' }))
    );
  }

  updatePlayersDownload(season: string): Observable<{ ok: boolean; description: string }> {
    return this.http.post<GenericResponse<any>>(`${this.base}/updatePlayers`, { restUpdate: 'true', season }).pipe(
      map(r => ({ ok: r.code === 0, description: r.description || '' })),
      catchError(() => of({ ok: false, description: 'Error de conexión con el servidor' }))
    );
  }

  updatePlayersProcess(season: string): Observable<{ ok: boolean; description: string }> {
    return this.http.post<GenericResponse<any>>(`${this.base}/updatePlayers`, { update: 'true', season }).pipe(
      map(r => ({ ok: r.code === 0, description: r.description || '' })),
      catchError(() => of({ ok: false, description: 'Error de conexión con el servidor' }))
    );
  }

  updateTransferedPlayers(): Observable<{ ok: boolean; description: string }> {
    return this.http.post<GenericResponse<any>>(`${this.base}/updateTransferedPlayers`, {}).pipe(
      map(r => ({ ok: r.code === 0, description: r.description || '' })),
      catchError(() => of({ ok: false, description: 'Error de conexión con el servidor' }))
    );
  }

  updatePlayerBySquads(): Observable<{ ok: boolean; description: string }> {
    return this.http.post<GenericResponse<any>>(`${this.base}/updatePlayerBySquads`, {}).pipe(
      map(r => ({ ok: r.code === 0, description: r.description || '' })),
      catchError(() => of({ ok: false, description: 'Error de conexión con el servidor' }))
    );
  }

  ingestRawData(season: string, threads: number, purge: boolean): Observable<{ ok: boolean; description: string }> {
    return this.http.post<GenericResponse<any>>(`${this.base}/ingestRawData`, { season, threads, purgeBeforeRun: String(purge) }).pipe(
      map(r => ({ ok: r.code === 0, description: r.description || '' })),
      catchError(() => of({ ok: false, description: 'Error de conexión con el servidor' }))
    );
  }

  ingestFixtures(purge: boolean, season: string): Observable<{ ok: boolean; description: string }> {
    return this.http.post<GenericResponse<any>>(`${this.base}/ingestFixtures`, { purgeBeforeRun: String(purge), season }).pipe(
      map(r => ({ ok: r.code === 0, description: r.description || '' })),
      catchError(() => of({ ok: false, description: 'Error de conexión con el servidor' }))
    );
  }

  ingestFixtureEvents(purge: boolean, season: string): Observable<{ ok: boolean; description: string }> {
    return this.http.post<GenericResponse<any>>(`${this.base}/ingestFixtureEvents`, { purgeBeforeRun: String(purge), season }).pipe(
      map(r => ({ ok: r.code === 0, description: r.description || '' })),
      catchError(() => of({ ok: false, description: 'Error de conexión con el servidor' }))
    );
  }

  ingestFixturePlayerStats(purge: boolean, season: string): Observable<{ ok: boolean; description: string }> {
    return this.http.post<GenericResponse<any>>(`${this.base}/ingestFixturePlayerStats`, { purgeBeforeRun: String(purge), season }).pipe(
      map(r => ({ ok: r.code === 0, description: r.description || '' })),
      catchError(() => of({ ok: false, description: 'Error de conexión con el servidor' }))
    );
  }

  ingestFixtureTeamStats(purge: boolean, season: string): Observable<{ ok: boolean; description: string }> {
    return this.http.post<GenericResponse<any>>(`${this.base}/ingestFixtureTeamStats`, { purgeBeforeRun: String(purge), season }).pipe(
      map(r => ({ ok: r.code === 0, description: r.description || '' })),
      catchError(() => of({ ok: false, description: 'Error de conexión con el servidor' }))
    );
  }

  ingestFixtureLineup(purge: boolean, season: string): Observable<{ ok: boolean; description: string }> {
    return this.http.post<GenericResponse<any>>(`${this.base}/ingestFixtureLineup`, { purgeBeforeRun: String(purge), season }).pipe(
      map(r => ({ ok: r.code === 0, description: r.description || '' })),
      catchError(() => of({ ok: false, description: 'Error de conexión con el servidor' }))
    );
  }

  generatePlayerPercentilesApi(season: string): Observable<{ ok: boolean; description: string }> {
    return this.http.post<GenericResponse<string>>(`${this.base}/generatePlayerPercentiles`, { season }).pipe(
      map(r => ({ ok: r.code === 0, description: r.description || '' })),
      catchError(() => of({ ok: false, description: 'Error de conexión con el servidor' }))
    );
  }

  generatePlayerPercentilesFrontend(season: string): Observable<{ ok: boolean; description: string }> {
    return this.http.post<GenericResponse<string>>(`${this.base}/generatePlayerPercentilesFrontend`, { season }).pipe(
      map(r => ({ ok: r.code === 0, description: r.description || r.entity || '' })),
      catchError(() => of({ ok: false, description: 'Error de conexión con el servidor' }))
    );
  }
}
