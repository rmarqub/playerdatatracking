// update-tracked-player.component.ts
import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { ToastrService } from 'ngx-toastr';
import { PlayerService } from '../services/player-service.service';
import { ManualTrackedPlayer } from 'src/app/entitites/manual-tracker-player';

@Component({
  selector: 'app-update-tracked-player',
  templateUrl: './update-tracked-player.component.html',
  styleUrls: ['./update-tracked-player.component.css']
})
export class UpdateTrackedPlayerComponent implements OnInit {
  manual: ManualTrackedPlayer | null = null;
  form: any = {
    nombre: '',
    nota: null,
    posicion: '',
    likeable: '',
    birth: '',
    mostLikeDestination: ''
  };
  qualitiesInput = '';
  formErrors: { [k: string]: string } = {};
  birthDateError: string | null = null;
  todayDate!: string;

  constructor(
    private router: Router,
    private service: PlayerService,
    private toastr: ToastrService
  ) {
    const today = new Date();
    this.todayDate = this.formatDateInput(today);
  }

  ngOnInit(): void {
    const nav = this.router.getCurrentNavigation();
    const manualId = Number(nav?.extras?.state?.['manualId'] ?? 0);

    if (!manualId) {
      this.toastr.error('Falta el identificador del jugador.');
      this.router.navigate(['/manualdata']);
      return;
    }

    this.service.getPlayer(manualId).subscribe({
      next: (m) => {
        if (!m) {                               // ⬅️ guarda contra null
          this.toastr.error('No se encontró el jugador.');
          this.router.navigate(['/manualdata']);
          return;
        }
        this.manual = m;                        // ⬅️ ya no da error
        this.prefillFromManual(m);              // ⬅️ m es ManualTrackedPlayer
      },
      error: () => {
        this.toastr.error('No se pudo cargar el jugador.');
        this.router.navigate(['/manualdata']);
      }
    });
  }

  private prefillFromManual(m: ManualTrackedPlayer) {
    // Campos solicitados: todos vienen de manualdataplayer
    this.form.nombre = m.nombre ?? '';
    this.form.nota = m.nota ?? null;
    this.form.likeable = m.likeable ?? '';
    this.form.posicion = m.posicion ?? '';
    this.form.birth = this.normalizeToInputDate(m.birth); // yyyy-MM-dd
    this.form.mostLikeDestination = m.mostLikeDestination ?? '';

    // qualities array -> string con comas
    this.qualitiesInput = Array.isArray(m.qualities) ? m.qualities.join(', ') : '';
  }

  onSubmit(): void {
    this.clearErrors();

    if (this.isFormInvalid()) return;

    // Validación birth no futura
    if (this.form.birth) {
      const birthDate = new Date(this.form.birth);
      const now = new Date();
      if (birthDate > now) {
        this.birthDateError = 'La fecha de nacimiento no puede ser futura';
        return;
      }
    }

    const updated: ManualTrackedPlayer = {
      ...this.manual!,  // ⬅️ non-null assertion: seguro porque solo llegamos aquí tras cargar
      nombre: this.form.nombre,
      nota: Number(this.form.nota),
      likeable: this.form.likeable,
      posicion: this.form.posicion,
      birth: this.form.birth || null,
      mostLikeDestination: this.form.mostLikeDestination,
      qualities: this.qualitiesInput
        ? this.qualitiesInput.split(',').map(q => q.trim()).filter(q => q.length > 0)
        : []
    };

    this.service.updateManualTrackedPlayer(updated.id, updated).subscribe({
      next: (res) => {
        if (res?.code === 0 || res === true) {
          this.toastr.success('Jugador actualizado correctamente');
          this.router.navigate(['/manualdata']);
        } else {
          this.toastr.error(res?.description ?? 'No se pudo actualizar el jugador');
        }
      },
      error: () => this.toastr.error('Error al actualizar el jugador')
    });
  }

  // ---------- utils ----------
  private isFormInvalid(): boolean {
    let invalid = false;
    ['nombre', 'nota', 'posicion', 'birth'].forEach(f => {
      if (!this.form[f]) {
        this.formErrors[f] = 'Este campo es obligatorio';
        invalid = true;
      }
    });
    const namePattern = /^[A-Za-zÁÉÍÓÚáéíóúÑñÜü\s]+$/;
    if (this.form.nombre && !namePattern.test(this.form.nombre)) {
      this.formErrors['nombre'] = 'El nombre solo puede contener letras y espacios';
      invalid = true;
    }
    return invalid;
  }

  private clearErrors() {
    this.formErrors = {};
    this.birthDateError = null;
  }

  private formatDateInput(d: Date): string {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  }

  private normalizeToInputDate(value: string | Date | null | undefined): string {
    if (!value) return '';
    if (value instanceof Date) return this.formatDateInput(value);
    // soporta 'YYYY/MM/DD' o 'YYYY-MM-DD' o ISO
    const norm = value.replaceAll('/', '-');
    return norm.substring(0, 10);
  }
}
