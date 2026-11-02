import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
export default defineConfig({ plugins: [vue()], build: { rollupOptions: { output: { manualChunks: (id: string) => id.includes('cytoscape') ? 'graph' : undefined } } }, server: { proxy: { '/api': 'http://localhost:8080' } } });
