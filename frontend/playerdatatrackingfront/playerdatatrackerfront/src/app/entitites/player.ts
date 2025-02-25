export interface Player {
  id: number;
  firstname: string;
  lastname: string;
  fullname: string;
  nacionalidad: string;
  birth: string;
  age: number;
  height: number;
  weight: number;
  injured: boolean;
  team: string;
  lastUpdated: string;
  indexId: number;
  fbrefId?: number;
}
