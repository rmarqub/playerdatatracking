import { Component, OnInit } from '@angular/core';
import { FormBuilder, Validators, AbstractControl, ValidationErrors } from '@angular/forms';
import { AuthService, UserInfo } from '../core/auth.service';

function passwordMatchValidator(control: AbstractControl): ValidationErrors | null {
  const pwd = control.get('password')?.value;
  const confirm = control.get('confirm')?.value;
  return pwd && confirm && pwd !== confirm ? { mismatch: true } : null;
}

@Component({
  selector: 'app-user-management',
  templateUrl: './user-management.component.html',
  styleUrls: ['./user-management.component.css']
})
export class UserManagementComponent implements OnInit {

  users: UserInfo[] = [];
  loading = false;
  errorMsg: string | null = null;

  showForm = false;
  formLoading = false;
  formError: string | null = null;
  formSuccess: string | null = null;

  visiblePasswords = new Set<number>();

  registerForm = this.fb.group({
    username: ['', [Validators.required, Validators.minLength(3)]],
    password: ['', [Validators.required, Validators.minLength(8)]],
    confirm: ['', Validators.required],
    role: ['user', Validators.required]
  }, { validators: passwordMatchValidator });

  constructor(private auth: AuthService, private fb: FormBuilder) {}

  ngOnInit(): void {
    this.loadUsers();
  }

  loadUsers(): void {
    this.loading = true;
    this.errorMsg = null;
    this.auth.getUsers().subscribe({
      next: (data) => {
        this.loading = false;
        this.users = data;
      },
      error: () => {
        this.loading = false;
        this.errorMsg = 'Error al cargar los usuarios.';
      }
    });
  }

  togglePasswordVisibility(userId: number): void {
    if (this.visiblePasswords.has(userId)) {
      this.visiblePasswords.delete(userId);
    } else {
      this.visiblePasswords.add(userId);
    }
  }

  isPasswordVisible(userId: number): boolean {
    return this.visiblePasswords.has(userId);
  }

  openForm(): void {
    this.showForm = true;
    this.formError = null;
    this.formSuccess = null;
    this.registerForm.reset({ role: 'user' });
  }

  closeForm(): void {
    this.showForm = false;
  }

  submitForm(): void {
    if (this.registerForm.invalid) return;
    this.formLoading = true;
    this.formError = null;
    this.formSuccess = null;

    const { username, password, role } = this.registerForm.value as any;

    this.auth.createUser(username, password, role).subscribe({
      next: () => {
        this.formLoading = false;
        this.formSuccess = `Usuario "${username}" creado correctamente.`;
        this.registerForm.reset({ role: 'user' });
        this.loadUsers();
      },
      error: (e) => {
        this.formLoading = false;
        if (e?.status === 409) {
          this.formError = 'El nombre de usuario ya existe.';
        } else if (e?.status === 400 && e?.error?.error === 'weak_password') {
          this.formError = 'La contraseña debe tener al menos 8 caracteres.';
        } else {
          this.formError = 'Error al crear el usuario.';
        }
      }
    });
  }

  roleLabel(role: string | null): string {
    if (role === 'admin') return 'Admin';
    if (role === 'user') return 'Usuario';
    return role ?? '—';
  }
}
