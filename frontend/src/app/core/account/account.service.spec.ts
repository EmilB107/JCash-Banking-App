import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Account } from './account';
import { AccountService } from './account.service';

const ACCOUNT: Account = {
  id: 'a1',
  currency: 'PHP',
  balance: 1500,
  createdAt: '2026-10-31T00:00:00Z',
  updatedAt: '2026-10-31T00:00:00Z',
};

describe('AccountService', () => {
  let service: AccountService;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AccountService);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('loads the current account', () => {
    let result: Account | undefined;
    service.getMyAccount().subscribe((account) => (result = account));

    backend.expectOne({ method: 'GET', url: '/api/accounts/me' }).flush(ACCOUNT);
    expect(result).toEqual(ACCOUNT);
  });

  it('posts the cash-in amount and returns the updated account', () => {
    let result: Account | undefined;
    service.cashIn(250.5).subscribe((account) => (result = account));

    const req = backend.expectOne({ method: 'POST', url: '/api/accounts/me/cash-in' });
    expect(req.request.body).toEqual({ amount: 250.5 });
    req.flush({ ...ACCOUNT, balance: 1750.5 });
    expect(result?.balance).toBe(1750.5);
  });
});
