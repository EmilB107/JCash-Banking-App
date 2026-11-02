import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { TransferService } from './transfer.service';

describe('TransferService', () => {
  it('posts the transfer with the Idempotency-Key header', () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const backend = TestBed.inject(HttpTestingController);

    TestBed.inject(TransferService)
      .send({ recipient: 'juan@example.com', amount: 250 }, 'key-1')
      .subscribe();

    const req = backend.expectOne({ method: 'POST', url: '/api/transfers' });
    expect(req.request.headers.get('Idempotency-Key')).toBe('key-1');
    expect(req.request.body).toEqual({ recipient: 'juan@example.com', amount: 250 });
    req.flush({});
    backend.verify();
  });
});
