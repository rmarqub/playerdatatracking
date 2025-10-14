// crypto.service.ts
import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class CryptoService {
  private publicKey: CryptoKey | null = null;
  private kid: string | null = null;

  async loadPublicKeyFromPem(pem: string, kid: string): Promise<void> {
    // Limpia cabeceras y convierte PEM a ArrayBuffer (SPKI DER)
    const b64 = pem.replace(/-----BEGIN PUBLIC KEY-----/, '')
                   .replace(/-----END PUBLIC KEY-----/, '')
                   .replace(/\s+/g, '');
    const raw = Uint8Array.from(atob(b64), c => c.charCodeAt(0)).buffer;

    this.publicKey = await window.crypto.subtle.importKey(
      'spki',
      raw,
      { name: 'RSA-OAEP', hash: 'SHA-256' },
      true,
      ['encrypt']
    );
    this.kid = kid;
  }

  getKid(): string | null {
    return this.kid;
  }

  async encryptPassword(password: string): Promise<string> {
    if (!this.publicKey) throw new Error('Public key not loaded');
    const enc = new TextEncoder().encode(password);
    const cipher = await window.crypto.subtle.encrypt(
      { name: 'RSA-OAEP' },
      this.publicKey,
      enc
    );
    // Base64
    const bytes = new Uint8Array(cipher);
    let binary = '';
    bytes.forEach(b => binary += String.fromCharCode(b));
    return btoa(binary);
  }
}
