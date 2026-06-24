import react from '@vitejs/plugin-react';
import { readFileSync } from 'fs';
import { resolve } from 'path';
import { defineConfig, loadEnv } from 'vite';
import svgr from 'vite-plugin-svgr';

const { version } = JSON.parse(readFileSync('./package.json', 'utf-8'))

export default ({ mode }: { mode: string }) => {

  process.env = { ...process.env, ...loadEnv(mode, './config') };

  // https://vitejs.dev/config/
  return defineConfig({
    plugins: [react(), svgr()],
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
          //'/Users/dos009/Documents/Github/ala-mantine'
          resolve(__dirname, '../../ala-mantine')
        ]
      }
    },
  });
}
