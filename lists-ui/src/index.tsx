import React from 'react';
import ReactDOM from 'react-dom/client';
import Main from './Main.tsx';
import axe from '@axe-core/react';

if (import.meta.env.DEV) setTimeout(() => axe(React, ReactDOM, 1000), 2000);

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <Main />
  </React.StrictMode>
);
