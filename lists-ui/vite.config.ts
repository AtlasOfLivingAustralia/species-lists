import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import svgr from 'vite-plugin-svgr' 

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
  });
}
