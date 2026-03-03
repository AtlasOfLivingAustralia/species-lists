import react from '@vitejs/plugin-react';
import { resolve } from 'path';
import { defineConfig, loadEnv } from 'vite';
import svgr from 'vite-plugin-svgr';

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
    // server: {
    //   https: {
    //     key: fs.readFileSync('./localhost-key.pem'),
    //     cert: fs.readFileSync('./localhost.pem'),
    //   },
    //   port: 5173
    // },
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
