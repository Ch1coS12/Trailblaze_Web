import { getRole, logout } from './auth.js';
import { loadPartial } from './render.js';
import * as FO from './fo.js';
import * as FE from './fe.js';

const routes = {
  '/overview': { file: 'pages/overview.html', init: () => {} },
  '/fo':       { file: 'pages/fo-list.html',  init: FO.initList },
  '/fo/:id':   { file: 'pages/fo-detail.html', init: FO.initDetail },
  '/fo/:id/edit': { file: 'pages/fo-edit.html', init: FO.initEdit },
  '/fo/import':   { file: 'pages/fo-import.html', init: FO.initImport },
  '/fe':       { file: 'pages/fe-list.html',  init: FE.initList },
  '/fe/new':   { file: 'pages/fe-new.html',   init: FE.initNew },
  '/fe/:id':   { file: 'pages/fe-detail.html', init: FE.initDetail },
  '/fe/:id/edit': { file: 'pages/fe-edit.html', init: FE.initEdit },
};

function parseRoute(hash) {
  const clean = hash.replace('#', '');
  const parts = clean.split('/');
  for (const [pattern, conf] of Object.entries(routes)) {
    const pParts = pattern.split('/');
    if (pParts.length !== parts.length) continue;
    const params = {};
    let match = true;
    for (let idx = 0; idx < pParts.length; idx++) {
      const p = pParts[idx];
      if (p.startsWith(':')) {
        params[p.slice(1)] = parts[idx];
      } else if (p !== parts[idx]) {
        match = false;
        break;
      }
    }
    if (match) return { conf, params };
  }
  return null;
}

async function router() {
  const hash = location.hash || '#/overview';
  const parsed = parseRoute(hash);
  if (!parsed) {
    location.hash = '#/overview';
    return;
  }
  const { conf, params } = parsed;

  try {
    await loadPartial(conf.file);
    conf.init && conf.init(params);
  } catch (err) {
    console.error(err);
    document.getElementById('app').innerHTML = '<p>Erro ao carregar página</p>';
  }
}

window.addEventListener('hashchange', router);
window.addEventListener('DOMContentLoaded', () => {
  document.getElementById('logoutBtn').addEventListener('click', logout);
  router();
});
