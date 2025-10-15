/* =========================================================
 *                       worksheet.js
 * =========================================================
 * Interface de importação, pesquisa, detalhe, edição e
 * remoção de Folhas de Obra (FO) com controlo de permissões
 * multi-role:
 *   • SMBO  → tudo (importar, pesquisar detalhe, editar, apagar)
 *   • SDVBO → pesquisa + detalhe
 *   • SGVBO → pesquisa básica
 *   • Outros→ apenas pesquisa básica
 * ========================================================= */
'use strict';

document.addEventListener('DOMContentLoaded', () => {

  /* ---------- 1) Autenticação básica ---------- */
  const token    = localStorage.getItem('authToken');
  const authType = localStorage.getItem('authType');
  const username = localStorage.getItem('username');
  if (!token || !username || authType !== 'jwt') {
    location.href = 'login.html';
    return;
  }

  const BASE_URL = location.pathname.includes('Firstwebapp')
        ? '/Firstwebapp/rest' : '/rest';

  /* ---------- 2) Roles extraídos do JWT ---------- */
  let  roles = [], primaryRole = 'RU';
  decodeRoles();

  function hasRole(r){ return roles.includes(r); }
  function hasAny(arr){ return roles.some(r=>arr.includes(r)); }

  function decodeRoles(){
    try{
      const payload = JSON.parse(atob(token.split('.')[1]));
      roles = payload.roles || (payload.role ? [payload.role] : []);
    }catch{ roles=['RU']; }
    if (!roles.length) roles=['RU'];
    primaryRole = roles[0];
  }

  const execSheetsNav = document.getElementById('execution-sheets-nav');
  if (primaryRole === 'RU' && execSheetsNav) execSheetsNav.style.display = 'none';

  
  /* ---------- 3) Short-cuts DOM ---------- */
  const $  = q=>document.querySelector(q);
  const $$ = q=>document.querySelectorAll(q);

  const usernameDisplay = $('#username-display');
  const userInitial     = $('#user-initial');
  const userRoleLabel   = $('#user-role');

  const modalOverlay = $('#modal-overlay');
  const modalTitle   = $('#modal-title');
  const modalBody    = $('#modal-body');

  const loading  = $('#loading');
  const message  = $('#message');
  const msgTxt   = $('#message-text');

  const importBtn = $('#import-worksheet-btn');

  /* ---------- 4) UI helpers ---------- */
  const hdrs = extra => Object.assign({
    'Authorization': `Bearer ${token}`
  }, extra);

  const showLoad = () => loading.classList.remove('hidden');
  const hideLoad = () => loading.classList.add('hidden');

  const isWsManager = () => ['SYSADMIN','SYSBO','SMBO'].includes(primaryRole);
  const canDetailRole = () => isWsManager() || primaryRole==='SDVBO';

  function showMessage(txt, type='error'){
    msgTxt.textContent = txt;
    message.className  = `message ${type}`;
    message.classList.remove('hidden');
    setTimeout(()=>message.classList.add('hidden'), 5000);
  }
  $('#close-message')?.addEventListener('click', ()=>message.classList.add('hidden'));

  function openModal(title, html){
    modalTitle.textContent = title;
    modalBody.innerHTML    = html;
    modalOverlay.classList.remove('hidden');
  }
  function closeModal(){ modalOverlay.classList.add('hidden'); }
  $('#modal-close')?.addEventListener('click', closeModal);
  modalOverlay?.addEventListener('click', e=>{
    if (e.target.id==='modal-overlay') closeModal();
  });
  window.closeModal = closeModal;

  /* ---------- 5) Arranque ---------- */
  init();
  function init(){
    const short = username.split('@')[0];
    usernameDisplay.textContent = short;
    userInitial.textContent     = short[0].toUpperCase();
    userRoleLabel.textContent   = primaryRole;

    setupInterfaceByRole();
    bindEvents();
    showSearchSection();           // vista inicial
  }

  /* ---------- 6) Interface condicional ---------- */
  function setupInterfaceByRole(){
    const show = (sel,on) => { const el=$(sel); if(el)el.style.display=on?'block':'none'; };

    const isManager = ['SYSADMIN','SYSBO','SMBO'].includes(primaryRole);
    const canDetail = isManager || primaryRole==='SDVBO';

    // botões/acções
    show('#import-action', isManager);
    show('#detailed-view-action', canDetail);
    $('#search-type option[value="detailed"]').style.display = canDetail ? 'block' : 'none';

    importBtn.style.display = isManager ? 'block':'none';
  }

  /* ---------- 7) Eventos ---------- */
  function bindEvents(){
    $('#logout-btn')?.addEventListener('click', logout);
    $('#refresh-btn')?.addEventListener('click', ()=>performSearch());
    importBtn?.addEventListener('click', showImportModal);
  }

  /* ======================================================
   *                 IMPORTAÇÃO  (SMBO)
   * ====================================================== */
  window.showImportModal = showImportModal;
  function showImportModal(){
    if (!isWsManager()){ showMessage('Only managers can import'); return; }

    openModal('Import Worksheet',`
      <div class="import-options">
        <div class="import-option">
          <button class="action-btn" onclick="showFileImport()">📁 Import from File</button>
          <p>Upload an existing GeoJSON file</p>
        </div>
        <div class="import-option">
          <button class="action-btn" onclick="showCreateForm()">📝 Create New Worksheet</button>
          <p>Fill out a form to create a new worksheet</p>
        </div>
      </div>
      <style>
        .import-options { display: flex; gap: 20px; justify-content: center; }
        .import-option { text-align: center; padding: 20px; border: 1px solid #ddd; border-radius: 8px; }
        .import-option button { margin-bottom: 10px; width: 200px; }
        .import-option p { margin: 0; color: #666; font-size: 14px; }
      </style>`);
  }

  window.showFileImport = showFileImport;
  function showFileImport(){
    openModal('Import from File',`
      <form id="import-form" enctype="multipart/form-data">
        <div class="form-group">
          <label>GeoJSON File*</label>
          <input type="file" id="geojson-file" accept=".json,.geojson" required>
        </div>
        <div class="form-actions">
          <button type="button" class="btn-secondary" onclick="closeModal()">Cancel</button>
          <button type="submit" class="action-btn">Import</button>
        </div>
      </form>`);

    $('#import-form').addEventListener('submit', async e=>{
      e.preventDefault();
      const file = $('#geojson-file').files[0];
      if (!file){ showMessage('Select a file'); return; }

      showLoad(); closeModal();
      try{
        const res = await fetch(`${BASE_URL}/fo/import`, {
          method:'POST',
          headers: hdrs({'Content-Type':'application/geo+json'}),
          body: await file.text()
        });
        showMessage(await res.text(), res.ok?'success':'error');
        if (res.ok) performSearch();
      }finally{ hideLoad(); }
    });
  }

  window.showCreateForm = showCreateForm;
  function showCreateForm(){
    openModal('Create New Worksheet',`
      <form id="create-worksheet-form" style="max-height: 70vh; overflow-y: auto;">
        
        <!-- Basic Information -->
        <div class="form-section">
          <h3>📋 Basic Information</h3>
          <div class="form-row">
            <div class="form-group">
              <label>Worksheet ID*</label>
              <input type="number" name="id" required min="1" placeholder="e.g., 100001">
              <small>Must be a unique positive number</small>
            </div>
            <div class="form-group">
              <label>Service Provider ID*</label>
              <input type="number" name="service_provider_id" required min="1" placeholder="e.g., 123">
            </div>
          </div>
        </div>

        <!-- Dates -->
        <div class="form-section">
          <h3>📅 Important Dates</h3>
          <div class="form-row">
            <div class="form-group">
              <label>Issue Date*</label>
              <input type="date" name="issue_date" required>
            </div>
            <div class="form-group">
              <label>Award Date</label>
              <input type="date" name="award_date">
            </div>
          </div>
          <div class="form-row">
            <div class="form-group">
              <label>Starting Date*</label>
              <input type="date" name="starting_date" required>
            </div>
            <div class="form-group">
              <label>Finishing Date</label>
              <input type="date" name="finishing_date">
            </div>
          </div>
        </div>

        <!-- POSA Information -->
        <div class="form-section">
          <h3>🗺️ POSA Information</h3>
          <div class="form-row">
            <div class="form-group">
              <label>POSA Code*</label>
              <input type="text" name="posa_code" required placeholder="e.g., POSA001">
            </div>
            <div class="form-group">
              <label>POSA Description*</label>
              <input type="text" name="posa_description" required placeholder="e.g., Área de Proteção Especial">
            </div>
          </div>
        </div>

        <!-- POSP Information -->
        <div class="form-section">
          <h3>📋 POSP Information</h3>
          <div class="form-row">
            <div class="form-group">
              <label>POSP Code</label>
              <input type="text" name="posp_code" placeholder="e.g., POSP001">
            </div>
            <div class="form-group">
              <label>POSP Description</label>
              <input type="text" name="posp_description" placeholder="e.g., Plano de Ordenamento">
            </div>
          </div>
        </div>

        <!-- Operations -->
        <div class="form-section">
          <h3>⚙️ Operations (1-5 required)</h3>
          <div id="operations-container">
            <div class="operation-form">
              <h4>Operation 1</h4>
              <div class="form-row">
                <div class="form-group">
                  <label>Operation Code*</label>
                  <input type="text" name="operation_code_1" required placeholder="e.g., OP001">
                </div>
                <div class="form-group">
                  <label>Area (ha)*</label>
                  <input type="number" name="area_ha_1" required min="0" step="0.1" placeholder="e.g., 15.5">
                </div>
              </div>
              <div class="form-group">
                <label>Operation Description*</label>
                <input type="text" name="operation_description_1" required placeholder="e.g., Limpeza de vegetação">
              </div>
            </div>
          </div>
          <button type="button" onclick="addOperation()" class="btn-secondary">+ Add Operation</button>
          <button type="button" onclick="removeOperation()" class="btn-secondary">- Remove Operation</button>
        </div>

        <!-- Parcels -->
        <div class="form-section">
          <h3>📍 Parcels (At least 1 required)</h3>
          <div id="parcels-container">
            <div class="parcel-form">
              <h4>Parcel 1</h4>
              <div class="form-row">
                <div class="form-group">
                  <label>Polygon ID*</label>
                  <input type="number" name="polygon_id_1" required min="1" value="1">
                </div>
                <div class="form-group">
                  <label>AIGP</label>
                  <input type="text" name="aigp_1" placeholder="e.g., AIGP-2025-001">
                </div>
              </div>
              <div class="form-group">
                <label>Rural Property ID</label>
                <input type="text" name="rural_property_id_1" placeholder="e.g., RP-Norte-001">
              </div>
              
              <!-- Geometry -->
              <div class="geometry-section">
                <label>Geometry (Polygon)*</label>
                <div class="coordinate-input">
                  <p>Enter at least 4 coordinates (last should match first for closed polygon)</p>
                  <div id="coordinates-1">
                    <div class="coordinate-pair">
                      <input type="number" step="any" placeholder="Longitude" name="coord_lng_1_1" required>
                      <input type="number" step="any" placeholder="Latitude" name="coord_lat_1_1" required>
                      <button type="button" onclick="removeCoordinate(1, 1)">×</button>
                    </div>
                  </div>
                  <button type="button" onclick="addCoordinate(1)" class="btn-secondary">+ Add Coordinate</button>
                  <button type="button" onclick="useExampleCoordinates(1)" class="btn-secondary">📍 Use Example</button>
                </div>
              </div>
            </div>
          </div>
          <button type="button" onclick="addParcel()" class="btn-secondary">+ Add Parcel</button>
          <button type="button" onclick="removeParcel()" class="btn-secondary">- Remove Parcel</button>
        </div>

        <div class="form-actions">
          <button type="button" class="btn-secondary" onclick="closeModal()">Cancel</button>
          <button type="button" onclick="previewGeoJSON()" class="btn-secondary">👁️ Preview JSON</button>
          <button type="submit" class="action-btn">✅ Create & Import</button>
        </div>
      </form>

      <style>
        .form-section { margin-bottom: 25px; padding: 15px; border: 1px solid #e0e0e0; border-radius: 8px; }
        .form-section h3 { margin: 0 0 15px 0; color: #333; border-bottom: 2px solid #4CAF50; padding-bottom: 5px; }
        .form-row { display: flex; gap: 15px; margin-bottom: 15px; }
        .form-group { flex: 1; }
        .form-group label { display: block; margin-bottom: 5px; font-weight: bold; color: #555; }
        .form-group input, .form-group textarea { width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px; }
        .form-group small { color: #666; font-size: 12px; }
        .operation-form, .parcel-form { border: 1px solid #ddd; padding: 15px; margin-bottom: 15px; border-radius: 6px; background: #fafafa; }
        .operation-form h4, .parcel-form h4 { margin: 0 0 10px 0; color: #666; }
        .coordinate-pair { display: flex; gap: 10px; margin-bottom: 10px; align-items: center; }
        .coordinate-pair input { flex: 1; }
        .coordinate-pair button { background: #ff4444; color: white; border: none; border-radius: 3px; padding: 5px 10px; cursor: pointer; }
        .geometry-section { margin-top: 15px; }
        .coordinate-input { border: 1px solid #ddd; padding: 10px; border-radius: 4px; background: white; }
        .btn-secondary { background: #6c757d; color: white; border: none; padding: 8px 16px; border-radius: 4px; cursor: pointer; margin-right: 10px; }
        .btn-secondary:hover { background: #5a6268; }
      </style>`);

    // Initialize form
    initializeCreateForm();
  }

  function initializeCreateForm(){
    // Set default example coordinates for first parcel
    useExampleCoordinates(1);
    
    // Bind form submission
    $('#create-worksheet-form').addEventListener('submit', async e=>{
      e.preventDefault();
      const geoJSON = generateGeoJSONFromForm();
      if (!geoJSON) return;
      
      await importGeneratedGeoJSON(geoJSON);
    });
  }

  let operationCount = 1;
  let parcelCount = 1;

  window.addOperation = addOperation;
  function addOperation(){
    if (operationCount >= 5) {
      showMessage('Maximum 5 operations allowed', 'error');
      return;
    }
    operationCount++;
    const container = $('#operations-container');
    const newOp = document.createElement('div');
    newOp.className = 'operation-form';
    newOp.innerHTML = `
      <h4>Operation ${operationCount}</h4>
      <div class="form-row">
        <div class="form-group">
          <label>Operation Code*</label>
          <input type="text" name="operation_code_${operationCount}" required placeholder="e.g., OP00${operationCount}">
        </div>
        <div class="form-group">
          <label>Area (ha)*</label>
          <input type="number" name="area_ha_${operationCount}" required min="0" step="0.1" placeholder="e.g., 10.0">
        </div>
      </div>
      <div class="form-group">
        <label>Operation Description*</label>
        <input type="text" name="operation_description_${operationCount}" required placeholder="e.g., Construção de aceiros">
      </div>`;
    container.appendChild(newOp);
  }

  window.removeOperation = removeOperation;
  function removeOperation(){
    if (operationCount <= 1) {
      showMessage('At least 1 operation is required', 'error');
      return;
    }
    const container = $('#operations-container');
    container.removeChild(container.lastElementChild);
    operationCount--;
  }

  window.addParcel = addParcel;
  function addParcel(){
    parcelCount++;
    const container = $('#parcels-container');
    const newParcel = document.createElement('div');
    newParcel.className = 'parcel-form';
    newParcel.innerHTML = `
      <h4>Parcel ${parcelCount}</h4>
      <div class="form-row">
        <div class="form-group">
          <label>Polygon ID*</label>
          <input type="number" name="polygon_id_${parcelCount}" required min="1" value="${parcelCount}">
        </div>
        <div class="form-group">
          <label>AIGP</label>
          <input type="text" name="aigp_${parcelCount}" placeholder="e.g., AIGP-2025-00${parcelCount}">
        </div>
      </div>
      <div class="form-group">
        <label>Rural Property ID</label>
        <input type="text" name="rural_property_id_${parcelCount}" placeholder="e.g., RP-Zone-00${parcelCount}">
      </div>
      
      <div class="geometry-section">
        <label>Geometry (Polygon)*</label>
        <div class="coordinate-input">
          <p>Enter at least 4 coordinates (last should match first for closed polygon)</p>
          <div id="coordinates-${parcelCount}">
            <div class="coordinate-pair">
              <input type="number" step="any" placeholder="Longitude" name="coord_lng_${parcelCount}_1" required>
              <input type="number" step="any" placeholder="Latitude" name="coord_lat_${parcelCount}_1" required>
              <button type="button" onclick="removeCoordinate(${parcelCount}, 1)">×</button>
            </div>
          </div>
          <button type="button" onclick="addCoordinate(${parcelCount})" class="btn-secondary">+ Add Coordinate</button>
          <button type="button" onclick="useExampleCoordinates(${parcelCount})" class="btn-secondary">📍 Use Example</button>
        </div>
      </div>`;
    container.appendChild(newParcel);
    
    // Initialize with example coordinates
    useExampleCoordinates(parcelCount);
  }

  window.removeParcel = removeParcel;
  function removeParcel(){
    if (parcelCount <= 1) {
      showMessage('At least 1 parcel is required', 'error');
      return;
    }
    const container = $('#parcels-container');
    container.removeChild(container.lastElementChild);
    parcelCount--;
  }

  window.addCoordinate = addCoordinate;
  function addCoordinate(parcelId){
    const container = $(`#coordinates-${parcelId}`);
    const coordCount = container.children.length + 1;
    const newCoord = document.createElement('div');
    newCoord.className = 'coordinate-pair';
    newCoord.innerHTML = `
      <input type="number" step="any" placeholder="Longitude" name="coord_lng_${parcelId}_${coordCount}" required>
      <input type="number" step="any" placeholder="Latitude" name="coord_lat_${parcelId}_${coordCount}" required>
      <button type="button" onclick="removeCoordinate(${parcelId}, ${coordCount})">×</button>`;
    container.appendChild(newCoord);
  }

  window.removeCoordinate = removeCoordinate;
  function removeCoordinate(parcelId, coordId){
    const container = $(`#coordinates-${parcelId}`);
    if (container.children.length <= 4) {
      showMessage('At least 4 coordinates required for a polygon', 'error');
      return;
    }
    const coordToRemove = container.querySelector(`input[name="coord_lng_${parcelId}_${coordId}"]`).parentElement;
    container.removeChild(coordToRemove);
  }

  window.useExampleCoordinates = useExampleCoordinates;
  function useExampleCoordinates(parcelId){
    const baseCoords = [
      [-8.6291, 41.1579],
      [-8.6285, 41.1579],
      [-8.6285, 41.1585],
      [-8.6291, 41.1585],
      [-8.6291, 41.1579] // Close the polygon
    ];
    
    // Slightly offset for different parcels
    const offset = (parcelId - 1) * 0.001;
    const coords = baseCoords.map(([lng, lat]) => [lng + offset, lat + offset]);
    
    const container = $(`#coordinates-${parcelId}`);
    container.innerHTML = '';
    
    coords.forEach((coord, idx) => {
      const newCoord = document.createElement('div');
      newCoord.className = 'coordinate-pair';
      newCoord.innerHTML = `
        <input type="number" step="any" placeholder="Longitude" name="coord_lng_${parcelId}_${idx + 1}" value="${coord[0]}" required>
        <input type="number" step="any" placeholder="Latitude" name="coord_lat_${parcelId}_${idx + 1}" value="${coord[1]}" required>
        <button type="button" onclick="removeCoordinate(${parcelId}, ${idx + 1})">×</button>`;
      container.appendChild(newCoord);
    });
  }

  window.previewGeoJSON = previewGeoJSON;
  function previewGeoJSON(){
    const geoJSON = generateGeoJSONFromForm();
    if (!geoJSON) return;
    
    openModal('GeoJSON Preview', `
      <div style="max-height: 60vh; overflow: auto;">
        <pre style="background: #f5f5f5; padding: 15px; border-radius: 4px; font-size: 12px; white-space: pre-wrap;">${JSON.stringify(geoJSON, null, 2)}</pre>
      </div>
      <div class="form-actions">
        <button type="button" class="btn-secondary" onclick="showCreateForm()">← Back to Form</button>
        <button type="button" onclick="downloadGeoJSON()" class="btn-secondary">💾 Download</button>
        <button type="button" onclick="importPreviewedGeoJSON()" class="action-btn">✅ Import This</button>
      </div>`);
  }

  let previewedGeoJSON = null;

  function generateGeoJSONFromForm(){
    const form = $('#create-worksheet-form');
    const formData = new FormData(form);
    
    try {
      // Basic metadata
      const metadata = {
        id: parseInt(formData.get('id')),
        starting_date: formData.get('starting_date'),
        finishing_date: formData.get('finishing_date') || undefined,
        issue_date: formData.get('issue_date'),
        award_date: formData.get('award_date') || undefined,
        service_provider_id: parseInt(formData.get('service_provider_id')),
        posa_code: formData.get('posa_code'),
        posa_description: formData.get('posa_description'),
        posp_code: formData.get('posp_code') || undefined,
        posp_description: formData.get('posp_description') || undefined,
        operations: []
      };
      
      // Clean undefined values
      Object.keys(metadata).forEach(key => {
        if (metadata[key] === undefined) delete metadata[key];
      });
      
      // Collect operations
      for (let i = 1; i <= operationCount; i++) {
        const code = formData.get(`operation_code_${i}`);
        const description = formData.get(`operation_description_${i}`);
        const area = formData.get(`area_ha_${i}`);
        
        if (code && description && area) {
          metadata.operations.push({
            operation_code: code,
            operation_description: description,
            area_ha: parseFloat(area)
          });
        }
      }
      
      if (metadata.operations.length === 0) {
        showMessage('At least one operation is required', 'error');
        return null;
      }
      
      // Collect features (parcels)
      const features = [];
      for (let i = 1; i <= parcelCount; i++) {
        const polygonId = formData.get(`polygon_id_${i}`);
        const aigp = formData.get(`aigp_${i}`);
        const ruralPropertyId = formData.get(`rural_property_id_${i}`);
        
        // Collect coordinates
        const coordinates = [];
        let coordIndex = 1;
        while (formData.get(`coord_lng_${i}_${coordIndex}`) !== null) {
          const lng = parseFloat(formData.get(`coord_lng_${i}_${coordIndex}`));
          const lat = parseFloat(formData.get(`coord_lat_${i}_${coordIndex}`));
          if (!isNaN(lng) && !isNaN(lat)) {
            coordinates.push([lng, lat]);
          }
          coordIndex++;
        }
        
        if (coordinates.length < 4) {
          showMessage(`Parcel ${i} needs at least 4 coordinates`, 'error');
          return null;
        }
        
        const properties = {
          polygon_id: parseInt(polygonId)
        };
        if (aigp) properties.aigp = aigp;
        if (ruralPropertyId) properties.rural_property_id = ruralPropertyId;
        
        features.push({
          type: "Feature",
          properties,
          geometry: {
            type: "Polygon",
            coordinates: [coordinates]
          }
        });
      }
      
      if (features.length === 0) {
        showMessage('At least one parcel is required', 'error');
        return null;
      }
      
      const geoJSON = {
        type: "FeatureCollection",
        metadata,
        features
      };
      
      previewedGeoJSON = geoJSON;
      return geoJSON;
      
    } catch (error) {
      showMessage('Error generating GeoJSON: ' + error.message, 'error');
      return null;
    }
  }

  window.downloadGeoJSON = downloadGeoJSON;
  function downloadGeoJSON(){
    if (!previewedGeoJSON) {
      showMessage('No GeoJSON to download', 'error');
      return;
    }
    
    const blob = new Blob([JSON.stringify(previewedGeoJSON, null, 2)], {
      type: 'application/geo+json'
    });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `worksheet_${previewedGeoJSON.metadata.id}.geojson`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  }

  window.importPreviewedGeoJSON = importPreviewedGeoJSON;
  function importPreviewedGeoJSON(){
    if (!previewedGeoJSON) {
      showMessage('No GeoJSON to import', 'error');
      return;
    }
    importGeneratedGeoJSON(previewedGeoJSON);
  }

  async function importGeneratedGeoJSON(geoJSON){
    showLoad(); closeModal();
    try{
      const res = await fetch(`${BASE_URL}/fo/import`, {
        method:'POST',
        headers: hdrs({'Content-Type':'application/geo+json'}),
        body: JSON.stringify(geoJSON)
      });
      showMessage(await res.text(), res.ok?'success':'error');
      if (res.ok) performSearch();
    }catch(error){
      showMessage('Network error: ' + error.message, 'error');
    }finally{ 
      hideLoad(); 
      // Reset counters for next use
      operationCount = 1;
      parcelCount = 1;
      previewedGeoJSON = null;
    }
  }

  /* ======================================================
   *                  PESQUISA  (todos)
   * ====================================================== */
  window.showSearchSection = showSearchSection;
  function showSearchSection(){
    $('#search-section').style.display  = 'block';
    $('#details-section').style.display = 'none';
    performSearch();
  }

  window.showDetailedSearch = showDetailedSearch;
  function showDetailedSearch(){
    if (!canDetailRole()){
      showMessage('Insufficient permissions for detailed search'); 
      return;
    }
    // Change to detailed search mode
    $('#search-type').value = 'detailed';
    showSearchSection();
  }

  window.performSearch = performSearch;
  async function performSearch(){
    const stype   = $('#search-type').value;            // basic | detailed
    const spId    = $('#filter-service-provider').value;
    const posa    = $('#filter-posa-code').value;

    // permissão
    if (stype==='detailed' && !canDetailRole()){
      showMessage('Insufficient permissions for detailed search'); return;
    }

    const qs = new URLSearchParams();
    if (spId) qs.append('serviceProviderId', spId);
    if (posa) qs.append('posaCode', posa);

    showLoad();
    try{
      const res = await fetch(`${BASE_URL}/fo/search/generic${qs.toString()?`?${qs}`:''}`, {
        headers: hdrs()
      });
      const list = res.ok ? await res.json() : [];
      renderSearchResults(list, stype);
      if (!res.ok) showMessage(await res.text());
    }catch(e){
      console.error(e); showMessage('Network error');
      renderSearchResults([], stype);
    }finally{ hideLoad(); }
  }

  window.clearFilters = clearFilters;
  function clearFilters(){
    $('#filter-start-date').value = '';
    $('#filter-end-date').value = '';
    $('#filter-service-provider').value = '';
    $('#filter-posa-code').value = '';
    $('#search-type').value = 'basic';
  }

  function renderSearchResults(arr, stype){
    const cont = $('#search-results');
    if (!arr.length){
      cont.innerHTML = `<div class="empty-state"><div class="icon">📄</div>
                        <h3>Nenhuma FO</h3></div>`;
      return;
    }
    cont.innerHTML = `
      <h3>${arr.length} result(s)</h3>
      <div class="worksheet-grid">
        ${arr.map(ws=>worksheetCard(ws, stype)).join('')}
      </div>`;
  }

  function worksheetCard(ws, stype){
    const canEdit   = isWsManager();
    const canDelete = isWsManager();
    const canDetail = canDetailRole();
    const viewBtn   = `<button class="btn-view"
                       onclick="viewWorksheet(${ws.id},'${stype}')">View</button>`;
    const detBtn    = canDetail ? `<button class="btn-view"
                       onclick="viewWorksheet(${ws.id},'detailed')">Details</button>` : '';
    const editBtn   = canEdit ? `<button class="btn-edit"
                       onclick="editWorksheet(${ws.id})">Edit</button>` : '';
    const delBtn    = canDelete ? `<button class="btn-delete"
                       onclick="deleteWorksheet(${ws.id})">Delete</button>` : '';
    return `
      <div class="worksheet-card" onclick="viewWorksheet(${ws.id},'${stype}')">
        <div class="worksheet-info">
          <p><strong>ID:</strong> ${ws.id}</p>
          <p><strong>ServiceProvider:</strong> ${ws.serviceProviderId}</p>
          <p><strong>POSA:</strong> ${ws.posa.code}</p>
        </div>
        <div class="worksheet-actions" onclick="event.stopPropagation()">
          ${viewBtn}${detBtn}${editBtn}${delBtn}
        </div>
      </div>`;
  }

  /* ======================================================
   *            DETALHE, EDITAR, REMOVER  (SMBO / SDVBO)
   * ====================================================== */
  let currentWorksheet = null;

  window.viewWorksheet = viewWorksheet;
  async function viewWorksheet(id, view='basic'){
    if (view==='detailed' && !canDetailRole()){
      showMessage('Insufficient permissions for details'); return;
    }
    showLoad();
    try{
      const res = await fetch(`${BASE_URL}/fo/${id}/${view==='detailed'?'detail':'generic'}`, {
        headers: hdrs()
      });
      if (!res.ok){ showMessage(await res.text()); return; }
      currentWorksheet = await res.json();
      showDetails(currentWorksheet, view);
    }finally{ hideLoad(); }
  }

  function showDetails(ws, view){
    $('#search-section').style.display  = 'none';
    $('#details-section').style.display = 'block';
    $('#edit-current-btn').style.display   = isWsManager() ? 'block':'none';
    $('#delete-current-btn').style.display = isWsManager() ? 'block':'none';

    // Format worksheet details in a readable way
    const detailsContainer = $('#worksheet-details');
    detailsContainer.innerHTML = `
      <div class="worksheet-info-grid">
        <div class="info-section">
          <h3>Basic Information</h3>
          <div class="detail-grid">
            <div class="detail-item">
              <label>ID</label>
              <span>${ws.id || 'N/A'}</span>
            </div>
            <div class="detail-item">
              <label>Service Provider ID</label>
              <span>${ws.serviceProviderId || 'N/A'}</span>
            </div>
          </div>
        </div>
        
        <div class="info-section">
          <h3>Important Dates</h3>
          <div class="detail-grid">
            <div class="detail-item">
              <label>Issue Date</label>
              <span>${ws.issueDate ? formatDate(ws.issueDate) : 'N/A'}</span>
            </div>
            <div class="detail-item">
              <label>Award Date</label>
              <span>${ws.awardDate ? formatDate(ws.awardDate) : 'N/A'}</span>
            </div>
            <div class="detail-item">
              <label>Starting Date</label>
              <span>${ws.startingDate ? formatDate(ws.startingDate) : 'N/A'}</span>
            </div>
            <div class="detail-item">
              <label>Finishing Date</label>
              <span>${ws.finishingDate ? formatDate(ws.finishingDate) : 'N/A'}</span>
            </div>
          </div>
        </div>
        
        ${ws.posa ? `
        <div class="info-section">
          <h3>POSA Information</h3>
          <div class="detail-grid">
            <div class="detail-item">
              <label>POSA Code</label>
              <span>${escapeHtml(ws.posa.code || 'N/A')}</span>
            </div>
            <div class="detail-item">
              <label>POSA Description</label>
              <span>${escapeHtml(ws.posa.description || 'N/A')}</span>
            </div>
          </div>
        </div>
        ` : ''}
        
        ${ws.posp ? `
        <div class="info-section">
          <h3>POSP Information</h3>
          <div class="detail-grid">
            <div class="detail-item">
              <label>POSP Code</label>
              <span>${escapeHtml(ws.posp.code || 'N/A')}</span>
            </div>
            <div class="detail-item">
              <label>POSP Description</label>
              <span>${escapeHtml(ws.posp.description || 'N/A')}</span>
            </div>
          </div>
        </div>
        ` : ''}
        
        ${ws.geometry ? `
        <div class="info-section">
          <h3>Geometry Information</h3>
          <div class="detail-grid">
            <div class="detail-item">
              <label>Type</label>
              <span>${ws.geometry.type || 'N/A'}</span>
            </div>
            <div class="detail-item">
              <label>Coordinates</label>
              <span>${ws.geometry.coordinates ? 'Available' : 'N/A'}</span>
            </div>
          </div>
          <div class="geometry-details">
            <h4>Coordinate Details</h4>
            <div class="geometry-data">
              <pre>${JSON.stringify(ws.geometry, null, 2)}</pre>
            </div>
          </div>
        </div>
        ` : ''}
        
        ${ws.operations && ws.operations.length > 0 ? `
        <div class="info-section">
          <h3>Operations (${ws.operations.length})</h3>
          <div class="operations-list">
            ${ws.operations.map(op => `
              <div class="operation-item">
                <div class="operation-header">
                  <strong>Operation ${op.id || 'N/A'}</strong>
                  <span class="operation-type">${op.code || 'N/A'}</span>
                </div>
                <div class="operation-details">
                  ${op.description ? `<p><strong>Description:</strong> ${escapeHtml(op.description)}</p>` : ''}
                  ${op.areaHa ? `<p><strong>Area:</strong> ${op.areaHa} ha</p>` : ''}
                  ${op.order ? `<p><strong>Order:</strong> ${op.order}</p>` : ''}
                </div>
              </div>
            `).join('')}
          </div>
        </div>
        ` : ''}
        
        ${ws.parcels && ws.parcels.length > 0 ? `
        <div class="info-section">
          <h3>Parcels (${ws.parcels.length})</h3>
          <div class="parcels-list">
            ${ws.parcels.map(parcel => `
              <div class="parcel-item">
                <div class="parcel-header">
                  <strong>Parcel ${parcel.id || parcel.polygonId || 'N/A'}</strong>
                </div>
                <div class="parcel-details">
                  ${parcel.aigp ? `<p><strong>AIGP:</strong> ${escapeHtml(parcel.aigp)}</p>` : ''}
                  ${parcel.ruralPropertyId ? `<p><strong>Rural Property ID:</strong> ${escapeHtml(parcel.ruralPropertyId)}</p>` : ''}
                  ${parcel.polygonId ? `<p><strong>Polygon ID:</strong> ${parcel.polygonId}</p>` : ''}
                  ${parcel.geometry ? `<p><strong>Geometry:</strong> ${parcel.geometry.type || 'Available'}</p>` : ''}
                </div>
              </div>
            `).join('')}
          </div>
        </div>
        ` : ''}
      </div>
    `;
  }
  window.hideDetailsSection = showSearchSection;

  /* -------- editar -------- */
  window.editWorksheet = id=>{
    if (!isWsManager()){ showMessage('Insufficient permission'); return; }
    loadWorksheetForEdit(id);
  };
  window.showEditModal = ()=>editWorksheetDropdown();

  async function editWorksheetDropdown(){
    if (!isWsManager()){ showMessage('Insufficient permission'); return; }
    openModal('Editar FO', `<p>A carregar IDs…</p>`);
    try{
      const res = await fetch(`${BASE_URL}/fo/search/generic`, {headers:hdrs()});
      const list = res.ok ? await res.json() : [];
      if (!list.length){
        modalBody.innerHTML = '<p>Nenhuma FO encontrada.</p>';
        return;
      }
      modalBody.innerHTML = `
        <form id="edit-id-form">
          <select id="edit-fo-id" required>
            ${list.map(l=>`<option value="${l.id}">${l.id}</option>`).join('')}
          </select>
          <button class="action-btn">Editar</button>
        </form>`;
      $('#edit-id-form').addEventListener('submit', e=>{
        e.preventDefault();
        loadWorksheetForEdit($('#edit-fo-id').value);
      });
    }catch{ modalBody.textContent='Erro ao carregar.'; }
  }

  async function loadWorksheetForEdit(id){
    closeModal(); showLoad();
    try{
      const r = await fetch(`${BASE_URL}/fo/${id}/detail`, {headers:hdrs()});
      if (!r.ok){ showMessage(await r.text()); return; }
      const ws = await r.json();
      showEditModal(ws);
    }finally{ hideLoad(); }
  }

  function showEditModal(ws){
    openModal(`Edit FO ${ws.id}`,`
      <form id="edit-form">
        <label>Starting Date</label><input type="date" name="startingDate"
               value="${ws.startingDate?.split('T')[0]||''}">
        <label>Finishing Date</label><input type="date" name="finishingDate"
               value="${ws.finishingDate?.split('T')[0]||''}">
        <label>ServiceProviderId</label><input name="serviceProviderId"
               value="${ws.serviceProviderId||''}">
        <div class="form-actions">
          <button type="button" class="btn-secondary" onclick="closeModal()">Cancel</button>
          <button class="action-btn">Update</button>
        </div>
      </form>`);
    $('#edit-form').addEventListener('submit', e=>updateWorksheet(e, ws.id));
  }

  async function updateWorksheet(ev, id){
    ev.preventDefault();
    const data = Object.fromEntries(new FormData(ev.target).entries());
    if (data.serviceProviderId) data.serviceProviderId = +data.serviceProviderId;
    showLoad(); closeModal();
    try{
      const r = await fetch(`${BASE_URL}/fo/${id}`, {
        method:'PUT', headers:hdrs({'Content-Type':'application/json'}),
        body:JSON.stringify(data)
      });
      showMessage(await r.text(), r.ok?'success':'error');
      if (r.ok) viewWorksheet(id,'detailed');
    }finally{ hideLoad(); }
  }

  /* -------- remover -------- */
  window.deleteWorksheet = async id=>{
    if (!isWsManager()){ showMessage('Insufficient permission'); return; }
    if (!confirm(`Remover FO ${id}?`)) return;
    showLoad();
    try{
      const r = await fetch(`${BASE_URL}/fo/${id}`, {method:'DELETE', headers:hdrs()});
      showMessage(await r.text(), r.ok?'success':'error');
      if (r.ok) showSearchSection();
    }finally{ hideLoad(); }
  };
  window.deleteCurrentWorksheet = ()=>currentWorksheet && deleteWorksheet(currentWorksheet.id);

  window.editCurrentWorksheet = editCurrentWorksheet;
  function editCurrentWorksheet(){
    if (!currentWorksheet){
      showMessage('No worksheet selected');
      return;
    }
    if (!isWsManager()){
      showMessage('Insufficient permission');
      return;
    }
    showEditModal(currentWorksheet);
  }

  /* ======================================================
   *                      LOGOUT
   * ====================================================== */
  /* ======================================================
   *                      UTILITY FUNCTIONS
   * ====================================================== */
  function escapeHtml(text) {
    const map = {
      '&': '&amp;',
      '<': '&lt;',
      '>': '&gt;',
      '"': '&quot;',
      "'": '&#039;'
    };
    return String(text || '').replace(/[&<>"']/g, (m) => map[m]);
  }

  function formatDate(dateString) {
    if (!dateString) return 'N/A';
    try {
      const date = new Date(dateString);
      return date.toLocaleDateString('pt-PT', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit'
      });
    } catch (error) {
      return dateString;
    }
  }

  /* ======================================================
   *                      LOGOUT
   * ====================================================== */
  async function logout(){
    showLoad();
    try{
      await fetch(`${BASE_URL}/logout/jwt`, {method:'POST', headers:hdrs()});
    }finally{
      ['authToken','authType','username'].forEach(localStorage.removeItem.bind(localStorage));
      location.href='login.html';
    }
  }

});