/// <reference types="vite/client" />
declare const __APP_VERSION__: string;

// Side-effect CSS import; the package maps "./styles" to a .css file, which has no declaration.
declare module '@atlasoflivingaustralia/ala-mantine/styles';