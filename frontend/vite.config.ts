import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig(({ mode }) => {
  const configuration = loadEnv(mode, process.cwd(), 'VITE_');
  const proxyTarget =
    configuration.VITE_DEV_PROXY_TARGET || 'http://localhost:8080';

  return {
    plugins: [react(), tailwindcss()],
    server: {
      port: 5173,
      strictPort: true,
      proxy: {
        '/api': {
          target: proxyTarget,
          changeOrigin: true,
        },
      },
    },
    preview: {
      port: 4173,
      strictPort: true,
    },
    build: {
      outDir: '../zkdoc-api/src/main/resources/static',
      emptyOutDir: true,
      sourcemap: false,
      target: 'es2022',
    },
    test: {
      globals: true,
      environment: 'jsdom',
      setupFiles: ['./tests/setup.ts'],
      css: true,
      restoreMocks: true,
      clearMocks: true,
    },
  };
});
