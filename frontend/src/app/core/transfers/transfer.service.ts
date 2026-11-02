import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { TransferRequest, TransferResponse } from './transfer';

@Injectable({ providedIn: 'root' })
export class TransferService {
  private readonly http = inject(HttpClient);

  /**
   * Sends money. `idempotencyKey` identifies this one transfer attempt: send
   * the same key when retrying (e.g. after a network error) and the backend
   * returns the original result instead of moving the money twice.
   */
  send(request: TransferRequest, idempotencyKey: string): Observable<TransferResponse> {
    const headers = new HttpHeaders({ 'Idempotency-Key': idempotencyKey });
    return this.http.post<TransferResponse>('/api/transfers', request, { headers });
  }
}
