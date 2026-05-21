import { Component, OnInit } from '@angular/core';
import { FixtureService, TorneoInfo, LeagueTierInfo, LeagueTierEntry } from '../services/fixture.service';

interface LeagueRow {
  id: number;
  name: string;
  studied: boolean;
  paisName: string | null;
  tier: number;
  tierFactor: number;
  notes: string | null;
}

const DEFAULT_FACTORS: Record<number, number> = {
  0: 0.40,
  1: 1.00,
  2: 0.72,
  3: 0.50,
  4: 0.33,
  5: 0.18,
};

@Component({
  selector: 'app-league-management',
  templateUrl: './league-management.component.html',
  styleUrls: ['./league-management.component.css']
})
export class LeagueManagementComponent implements OnInit {

  rows: LeagueRow[] = [];
  loading = false;
  saving = false;
  errorMsg: string | null = null;
  saveMsg: string | null = null;
  saveError = false;

  countryFilter = '';
  availableCountries: string[] = [];
  showOnlyStudied = false;

  readonly tierLabels: Record<number, string> = {
    0: 'Sin asignar',
    1: 'Tier 1 — Élite',
    2: 'Tier 2 — Alto',
    3: 'Tier 3 — Medio',
    4: 'Tier 4 — Bajo',
    5: 'Tier 5 — Menor',
  };

  readonly tierOptions = [0, 1, 2, 3, 4, 5];

  constructor(private fixtureService: FixtureService) {}

  ngOnInit(): void {
    this.loadData();
  }

  loadData(): void {
    this.loading = true;
    this.errorMsg = null;

    this.fixtureService.getLeagueTiers().subscribe({
      next: (tiers: LeagueTierInfo[]) => {
        this.loading = false;
        const tierMap = new Map(tiers.map(t => [t.torneoId, t]));

        this.rows = tiers.map(t => ({
          id: t.torneoId,
          name: t.torneoName,
          studied: false,
          paisName: t.paisName,
          tier: t.tier ?? 0,
          tierFactor: t.tierFactor ?? DEFAULT_FACTORS[0],
          notes: t.notes ?? null,
        }));

        this.fixtureService.getAllLeaguesForManagement().subscribe({
          next: (leagues: TorneoInfo[]) => {
            const studiedSet = new Set(leagues.filter(l => l.studied).map(l => l.id));
            this.rows.forEach(r => { r.studied = studiedSet.has(r.id); });
          }
        });

        this.availableCountries = [...new Set(
          tiers.map(t => t.paisName ?? 'Sin país').filter(Boolean)
        )].sort();
      },
      error: () => {
        this.loading = false;
        this.errorMsg = 'Error de conexión al cargar las ligas.';
      }
    });
  }

  get filteredRows(): LeagueRow[] {
    let result = this.rows;
    if (this.countryFilter) {
      result = result.filter(r => (r.paisName ?? 'Sin país') === this.countryFilter);
    }
    if (this.showOnlyStudied) {
      result = result.filter(r => r.studied);
    }
    return result;
  }

  toggleStudied(row: LeagueRow): void {
    row.studied = !row.studied;
  }

  onTierChange(row: LeagueRow): void {
    row.tierFactor = DEFAULT_FACTORS[row.tier] ?? 0.40;
  }

  saveAll(): void {
    this.saving = true;
    this.saveMsg = null;
    this.saveError = false;

    const studiedIds = this.rows.filter(r => r.studied).map(r => r.id);
    const tierEntries: LeagueTierEntry[] = this.rows.map(r => ({
      torneoId: r.id,
      tier: r.tier,
      tierFactor: r.tierFactor,
      notes: r.notes,
    }));

    let studiedDone = false;
    let tiersDone = false;
    let hasError = false;

    const tryFinish = () => {
      if (!studiedDone || !tiersDone) return;
      this.saving = false;
      this.saveError = hasError;
      this.saveMsg = hasError
        ? 'Error al guardar algunos cambios.'
        : 'Cambios guardados correctamente.';
    };

    this.fixtureService.updateStudiedLeagues(studiedIds).subscribe({
      next: result => {
        if (!result.ok) hasError = true;
        studiedDone = true;
        tryFinish();
      },
      error: () => { hasError = true; studiedDone = true; tryFinish(); }
    });

    this.fixtureService.updateLeagueTiers(tierEntries).subscribe({
      next: result => {
        if (!result.ok) hasError = true;
        tiersDone = true;
        tryFinish();
      },
      error: () => { hasError = true; tiersDone = true; tryFinish(); }
    });
  }

  studiedCount(): number {
    return this.rows.filter(r => r.studied).length;
  }

  tierBadgeClass(tier: number): string {
    return `tier-badge tier-${tier}`;
  }
}
