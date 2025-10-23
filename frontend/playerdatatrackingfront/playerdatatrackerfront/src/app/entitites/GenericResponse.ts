export interface GenericResponse<T> {
  code: number;
  description: string;
  entity: T | null;
  entityList: T[] | null;
}
