import react from '@vitejs/plugin-react';
import { readFileSync } from 'fs';
import { resolve } from 'path';
import { defineConfig, loadEnv } from 'vite';
import svgr from 'vite-plugin-svgr';
import { runtimeConfigPlugin } from './viteRuntimeConfigPlugin';

const { version } = JSON.parse(readFileSync('./package.json', 'utf-8'))

export default ({ mode }: { mode: string }) => {

  // The community build is the production build plus config/.env.community on top
  const baseMode = mode === 'community' ? 'production' : mode;
  process.env = {
    ...process.env,
    ...loadEnv(baseMode, './config'),
    ...loadEnv(mode, './config'),
  };

  // https://vitejs.dev/config/
  return defineConfig({
    plugins: [react(), svgr(), runtimeConfigPlugin()],
    resolve: {
      alias: {
        '#': '/src',
      },
    },
    optimizeDeps: {
      exclude: ['@atlasoflivingaustralia/ala-mantine']
    },
    define: {
      __APP_VERSION__: JSON.stringify(version),
    },
    server: {
      fs: {
        allow: [
          // Your existing project
          '.',
          // Add your linked library path
          resolve(__dirname, '../../ala-mantine')
        ]
      }
    },
  });
}
