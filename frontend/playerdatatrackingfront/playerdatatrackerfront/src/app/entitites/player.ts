export interface Player {
  id: number;
  firstname: string;
  lastname: string;
  fullname: string;
  nacionalidad: number;
  birth: string;
  age: number;
  height: number;
  weight: number;
  injured: boolean;
  team: number;
  lastUpdated: string;
  indexId: number;
  fbrefId?: number;
}
