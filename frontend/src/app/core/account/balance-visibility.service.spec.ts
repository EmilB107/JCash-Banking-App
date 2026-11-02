import { TestBed } from '@angular/core/testing';
import { BalanceVisibilityService } from './balance-visibility.service';

describe('BalanceVisibilityService', () => {
  beforeEach(() => localStorage.clear());

  it('starts visible and remembers the choice on this device', () => {
    const first = TestBed.inject(BalanceVisibilityService);
    expect(first.hidden()).toBe(false);

    first.toggle();
    expect(first.hidden()).toBe(true);

    // A fresh service (like after a page reload) reads the saved choice.
    TestBed.resetTestingModule();
    expect(TestBed.inject(BalanceVisibilityService).hidden()).toBe(true);
  });

  it('still toggles when storage is unavailable', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    const service = TestBed.inject(BalanceVisibilityService);

    service.toggle();
    expect(service.hidden()).toBe(true);
    vi.restoreAllMocks();
  });
});
