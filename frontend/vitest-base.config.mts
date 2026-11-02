import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vitest/config';

// Extra Vitest settings for `ng test` (loaded via "runnerConfig" in angular.json).
export default defineConfig({
  resolve: {
    alias: {
      // See testing/lucide-angular.stub.ts for why icons are stubbed in tests.
      '@lucide/angular': fileURLToPath(
        new URL('./testing/lucide-angular.stub.ts', import.meta.url),
      ),
    },
  },
});
