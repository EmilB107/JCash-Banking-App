import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { onTabReturn } from './tab-return';

const refresh = vi.fn();

@Component({ template: '' })
class Host {
  constructor() {
    onTabReturn(refresh);
  }
}

function setVisibility(state: DocumentVisibilityState): void {
  Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => state });
}

describe('onTabReturn', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    refresh.mockReset();
    setVisibility('visible');
  });

  afterEach(() => {
    vi.useRealTimers();
    // Fall back to jsdom's own visibilityState.
    delete (document as { visibilityState?: unknown }).visibilityState;
  });

  it('refreshes when the tab becomes visible again', () => {
    TestBed.createComponent(Host);
    document.dispatchEvent(new Event('visibilitychange'));
    expect(refresh).toHaveBeenCalledTimes(1);
  });

  it('refreshes when the window regains focus', () => {
    TestBed.createComponent(Host);
    window.dispatchEvent(new Event('focus'));
    expect(refresh).toHaveBeenCalledTimes(1);
  });

  it('ignores the tab being hidden', () => {
    TestBed.createComponent(Host);
    setVisibility('hidden');
    document.dispatchEvent(new Event('visibilitychange'));
    expect(refresh).not.toHaveBeenCalled();
  });

  it('refreshes once when focus and visibilitychange fire together', () => {
    TestBed.createComponent(Host);
    document.dispatchEvent(new Event('visibilitychange'));
    window.dispatchEvent(new Event('focus'));
    expect(refresh).toHaveBeenCalledTimes(1);

    vi.advanceTimersByTime(1000);
    window.dispatchEvent(new Event('focus'));
    expect(refresh).toHaveBeenCalledTimes(2);
  });

  it('stops listening when the component is destroyed', () => {
    const fixture = TestBed.createComponent(Host);
    fixture.destroy();
    document.dispatchEvent(new Event('visibilitychange'));
    window.dispatchEvent(new Event('focus'));
    expect(refresh).not.toHaveBeenCalled();
  });
});
