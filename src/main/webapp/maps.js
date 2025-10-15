/* maps.js ------------------------------------------------------------ */
// Arranca quando o DOM estiver pronto
document.addEventListener('DOMContentLoaded', initMap);

let drawnPolygons = [];

async function initMap() {
  /* 1. Carregar classes Google Maps */
  const { Map, Polygon } = await google.maps.importLibrary('maps');
  const { LatLngBounds } = await google.maps.importLibrary('core');

  /* 2. Criar o mapa */
  const map = new Map(document.getElementById('map'), {
    center: { lat: 38.7223, lng: -9.1393 }, // Lisboa
    zoom  : 8
  });

  /* 3. Bounds para auto-zoom */
  const select = document.getElementById('worksheet-select');

  /* 4. End-points REST (públicos) */
  const BASE_URL = '/rest';

  try {
    /* 4.1. Lista de folhas de obra */
    const res = await fetch(`${BASE_URL}/fo/search/generic`);
    if (!res.ok) throw new Error('GET /fo/search/generic falhou');

    const worksheets = await res.json();

    /* 4.2. Detalhe + polígonos de cada folha */
	worksheets.forEach(ws => {
	   const opt = document.createElement('option');
	   opt.value = ws.id;
	   opt.textContent = `Worksheet ${ws.id}`;
	   select.appendChild(opt);
	 });

	 } catch (err) {
	    console.error('Erro ao carregar folhas de obra:', err);
	  }

	  select.addEventListener('change', async () => {
	    clearPolygons();
	    const id = select.value;
	    if (!id) return;
	    try {
	      const res = await fetch(`${BASE_URL}/fo/${id}/detail`);
	      if (!res.ok) throw new Error('GET /fo/${id}/detail falhou');
	      const detail = await res.json();
	      const bounds = new LatLngBounds();
		  
      (detail.parcels ?? []).forEach(p =>
        addGeometry(map, p.geometry, bounds, Polygon)
      );
	  if (!bounds.isEmpty()) map.fitBounds(bounds);
	      } catch(err) {
	        console.error('Erro ao carregar detalhe da folha:', err);
    }

	});
}

/* ------------------------------------------------------------------ */
/*  Converte GeoJSON (EPSG:3763) → Polygon Google Maps (WGS84)        */
/* ------------------------------------------------------------------ */
function addGeometry(map, geometry, bounds, Polygon) {
  if (!geometry) return;

  /* Aceita string ou objecto */
  if (typeof geometry === 'string') {
    try { geometry = JSON.parse(geometry); }
    catch { return; }
  }

  if (!proj4.defs['EPSG:3763']) {
    proj4.defs(
      'EPSG:3763',
      '+proj=tmerc +lat_0=39.6682583333333 ' +
      '+lon_0=-8.13310833333333 +k=1 ' +
      '+x_0=0 +y_0=0 +ellps=GRS80 +units=m +no_defs'
    );
  }

  const toWGS84 = proj4('EPSG:3763', 'WGS84');

  /* Garante compatibilidade Polygon vs MultiPolygon */
  const parts = geometry.type === 'Polygon'
              ? [geometry.coordinates]
              : geometry.coordinates;          // MultiPolygon

  parts.forEach(rings => {
    /* Usamos apenas o anel exterior (rings[0]) */
    const path = rings[0].map(([x, y]) => {
      const [lon, lat] = toWGS84.forward([x, y]); // m → graus
      const pt = { lat, lng: lon };
      bounds.extend(pt);
      return pt;
    });

  const poly = new Polygon({
      paths        : path,
      map          : map,
      strokeColor  : '#006400',
      strokeOpacity: 0.8,
      strokeWeight : 2,
       fillColor    : '#006400',
      fillOpacity  : 0.35
    });
	drawnPolygons.push(poly);
  });
}
function clearPolygons() {
  drawnPolygons.forEach(p => p.setMap(null));
  drawnPolygons = [];
}
