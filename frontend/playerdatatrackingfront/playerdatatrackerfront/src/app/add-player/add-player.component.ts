import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { ToastrService } from 'ngx-toastr'; // Importar ToastrService
import { ManualTrackedPlayer } from '../entitites/manual-tracker-player';
import { environment } from 'src/enviroment/environment';

@Component({
  selector: 'app-add-player',
  templateUrl: './add-player.component.html',
  styleUrls: ['./add-player.component.css']
})
export class AddPlayerComponent {
  newPlayer: Partial<ManualTrackedPlayer> = {};
  qualitiesInput: string = '';
  birthDateError: string | null = null;
  formErrors: { [key: string]: string } = {};
  todayDate: string;

  constructor(
    private router: Router,
    private http: HttpClient,
    private toastr: ToastrService
  ) {
    const today = new Date();
    const year = today.getFullYear();
    const month = String(today.getMonth() + 1).padStart(2, '0'); // Los meses son 0-indexados
    const day = String(today.getDate()).padStart(2, '0');
    this.todayDate = `${year}-${month}-${day}`;

        // PRE-FILL desde state
    const nav = this.router.getCurrentNavigation();
    const prefill = nav?.extras?.state?.['prefill'];
    if (prefill) {
      this.newPlayer = { ...this.newPlayer, ...prefill };
    }

  }

  onSubmit() {
    this.clearErrors();

    // Validación del formulario
    if (this.isFormInvalid()) { return; }

    // Parsear las cualidades
    if (this.qualitiesInput) {
      this.newPlayer.qualities = this.qualitiesInput.split(',').map(quality => quality.trim());
    }

    // Rellenar fecha actual y calcular edad
    const currentDate = new Date();
    this.newPlayer.date = this.formatDate(currentDate); // Formatea la fecha
    if (this.newPlayer.birth) {
      const birthDate = new Date(this.newPlayer.birth);
      if (birthDate > currentDate) {
        this.birthDateError = 'La fecha de nacimiento no puede ser una fecha futura';
        this.newPlayer.birth = ''; // Limpiar el valor incorrecto
        return;
      }
      const age = currentDate.getFullYear() - birthDate.getFullYear();
      this.newPlayer.age = age;
      this.newPlayer.birth = this.formatDate(birthDate); // Formatea la fecha de nacimiento
    }

    // Hacer la petición POST
    console.log('payload a enviar', this.newPlayer);
    this.http.post<any>(`${environment.apiUrl}/player`, this.newPlayer, { withCredentials: true }).subscribe(
      (response) => {
        console.log(response.description);
        if (response.code === 0) {
          this.toastr.success('Jugador añadido correctamente', ' ', {
            timeOut: 3000,
            progressBar: true,
            closeButton: true,
          });
          this.router.navigate(['/manualdata']); // Redirigir después de agregar el jugador
        } else {
          this.toastr.error(response.description, '', {
            timeOut: 3000,
            progressBar: true,
            closeButton: true
          });
        }
      },
      (error) => {
        console.error('Error al procesar la petición en el sistema');
        this.toastr.error('Error al procesar la petición en el sistema', 'Error', {
          timeOut: 3000,
          progressBar: true,
          closeButton: true
        });
      }
    );
  }

  private formatDate(date: Date): string {
    const day = String(date.getDate()).padStart(2, '0');
    const month = String(date.getMonth() + 1).padStart(2, '0'); // Los meses son 0-indexados
    const year = date.getFullYear();
    return `${year}/${month}/${day}`;
  }

  private clearErrors() {
    this.formErrors = {};
    this.birthDateError = null;
  }

  private isFormInvalid(): boolean {
    let invalid = false;

    // Validaciones de campos requeridos
    ['nombre', 'nota', 'club', 'posicion', 'birth'].forEach(field => {
      if (!(this.newPlayer as any)[field]) {
        this.formErrors[field] = 'Este campo es obligatorio';
        invalid = true;
      }
    });

    // Validación de caracteres para nombre
    const namePattern = /^[A-Za-zÁÉÍÓÚáéíóúÑñÜü\s]+$/;
    if (this.newPlayer.nombre && !namePattern.test(this.newPlayer.nombre)) {
      this.formErrors['nombre'] = 'El nombre solo puede contener letras y espacios';
      invalid = true;
    }

    return invalid;
  }

}
