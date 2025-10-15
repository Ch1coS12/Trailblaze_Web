// dashboard.js (versão refactorizada) – inclui helper authHeaders()
// Uso: substituir o ficheiro original por este

'use strict';

document.addEventListener('DOMContentLoaded', function () {
  const BASE_URL = window.location.pathname.includes("Firstwebapp") ? "/Firstwebapp/rest" : "/rest";


  // =============== Helpers de HTTP =========================
  function authHeaders(extra = {}) {
    const actualUsername = username && username.includes('@')
      ? username.split('@')[0]
      : username;
    return {
      'Authorization': `Bearer ${token}`,
      'username': actualUsername,
      ...extra,
    };
  }

  // =============== Modal references & helper ==============
  const modalOverlay = document.getElementById('modal-overlay');
  const modalTitle = document.getElementById('modal-title');
  const modalBody = document.getElementById('modal-body');
  const modalClose = document.getElementById('modal-close');
  modalClose.addEventListener('click', () => modalOverlay.classList.add('hidden'));
  function openModal(title, innerHTML) {
    modalTitle.textContent = title;
    modalBody.innerHTML = innerHTML;
    modalOverlay.classList.remove('hidden');
  }

  // =============== Authentication check ===================
  const token = localStorage.getItem('authToken');
  const authType = localStorage.getItem('authType');
  const username = localStorage.getItem('username');
  if (!token || !username || authType !== 'jwt') {
    window.location.href = 'login.html';
    return;
  }

  const shortUsername = username && username.includes('@')
      ? username.split('@')[0]
      : username;

  const origFetch = window.fetch;
  window.fetch = async (...args) => {
    const resp = await origFetch(...args);
    if (resp.status === 401 || resp.status === 403) {
      localStorage.removeItem('authToken');
      localStorage.removeItem('authType');
      localStorage.removeItem('username');
      window.location.href = 'login.html';
    }
    return resp;
  };

  // =============== DOM shortcuts ==========================
  const navItems = document.querySelectorAll('.nav-item');
  const contentSections = document.querySelectorAll('.content-section');
  const pageTitle = document.getElementById('page-title');
  const usernameDisplay = document.getElementById('username-display');
  const userRoleEl = document.getElementById('user-role');
  const userInitial = document.getElementById('user-initial');
  const logoutBtn = document.getElementById('logout-btn');
  const loading = document.getElementById('loading');
  const message = document.getElementById('message');
  const messageText = document.getElementById('message-text');
  const closeMessage = document.getElementById('close-message');

  closeMessage.addEventListener('click', () => message.classList.add('hidden'));

  let currentUserRoles = [];
  let primaryRole = 'RU';
  let currentSection = 'overview';

  // =============== Boot up =================================
  initializeDashboard();

  async function initializeDashboard() {
    const displayName = username.includes('@') ? username.split('@')[0] : username;
    usernameDisplay.textContent = displayName;
    userInitial.textContent = displayName.charAt(0).toUpperCase();

    await getUserInfo();
    setupEventListeners();
    loadOverviewData();
  }

  async function getUserInfo() {
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      // Extrair lista de roles do payload (novo metodo):
      let roles = payload.roles || (payload.role ? [payload.role] : []);
      if (!Array.isArray(roles) || roles.length === 0) roles = ['RU'];
      currentUserRoles = roles;
      primaryRole = roles[0] || 'RU';
    } catch {
      currentUserRoles = ['RU'];
      primaryRole = 'RU';
    }
    userRoleEl.textContent = primaryRole;
    setupRoleBasedInterface();
  }

  function setupRoleBasedInterface() {
    const adminSection = document.getElementById('admin-section');
    const userSection = document.getElementById('user-section');
    const adminStats = document.getElementById('admin-stats');
    const worksheetsNav = document.getElementById('worksheets-nav');
    const worksheetsActions = document.getElementById('worksheets-actions');
    const executionSheetsNav = document.getElementById('execution-sheets-nav');
    const executionSheetsActions = document.getElementById('execution-sheets-actions');
    const eventsNav = document.getElementById('events-nav');
    const notifBtn = document.getElementById('notif-btn');

    const hide = el => el && (el.style.display = 'none');
    const show = el => el && (el.style.display = 'block');

    [adminSection, userSection, adminStats, worksheetsNav, worksheetsActions,
      executionSheetsNav, executionSheetsActions, notifBtn].forEach(hide);

    switch (primaryRole) {
      case 'SYSADMIN':
        show(adminSection);
        show(adminStats);
        show(worksheetsNav);
        show(worksheetsActions);
        show(executionSheetsNav);
        show(executionSheetsActions);
        break;
      case 'SYSBO':
        show(adminSection);
        show(adminStats);
        show(worksheetsNav);
        show(worksheetsActions);
        show(executionSheetsNav);
        show(executionSheetsActions);
        break;
      case 'SMBO':
        show(userSection);
        show(worksheetsNav);
        show(worksheetsActions);
        show(executionSheetsNav);
        show(executionSheetsActions);
        break;
      case 'SDVBO':
        show(userSection);
        show(executionSheetsNav);
        show(executionSheetsActions);
        break;
      case 'PRBO':
        show(userSection);
        show(worksheetsNav);
        show(worksheetsActions);
        show(executionSheetsNav);
        show(executionSheetsActions);
        show(notifBtn); // Apenas PRBOs veem notificações
        break;
      case 'PO':
        show(userSection);
        show(executionSheetsNav);
        show(executionSheetsActions);
        break;
      case 'RU':
        show(userSection);
        show(worksheetsNav);
        show(worksheetsActions);
        break;
      case 'ADLU':
        show(userSection);
        show(worksheetsNav);
        show(worksheetsActions);
        break;
      default:
        show(userSection);
        show(worksheetsNav);
        show(worksheetsActions);
    }
    if (eventsNav && ['ADLU', 'SMBO', 'PRBO', 'PO'].includes(primaryRole)) {
      eventsNav.style.display = 'none';
    }
  }

  // =============== Event listeners ========================
  function setupEventListeners() {
    // Navigation
    navItems.forEach(item => {
      item.addEventListener('click', e => {
		if (item.dataset.section) {
		          e.preventDefault();
		          switchSection(item.dataset.section);
		        }
      });
    });

    // Logout
    logoutBtn.addEventListener('click', logout);

    // Profile form toggle
    document.getElementById('profile-form')
      .addEventListener('submit', updateProfile);
    document.getElementById('toggle-profile-btn')
      .addEventListener('click', toggleProfileVisibility);

    // Admin operation buttons → modals
    document.querySelector('[data-operation="activate"]')
      .addEventListener('click', () => showOperationModal('activate'));
    document.querySelector('[data-operation="deactivate"]')
      .addEventListener('click', () => showOperationModal('deactivate'));
    document.querySelector('[data-operation="suspend"]')
      .addEventListener('click', () => showOperationModal('suspend'));
    document.querySelector('[data-operation="remove"]')
      .addEventListener('click', () => showOperationModal('remove'));

    // Create user, load lists
    const createUserBtn = document.getElementById('create-user-btn');
    if (createUserBtn) createUserBtn.addEventListener('click', showCreateUserModal);
    const loadUsersBtn = document.getElementById('load-users-btn');
    if (loadUsersBtn) loadUsersBtn.addEventListener('click', loadUsers);
    const loadSessionsBtn = document.getElementById('load-sessions-btn');
    if (loadSessionsBtn) loadSessionsBtn.addEventListener('click', loadSessions);

    // Request removal
    const requestRemovalBtn = document.getElementById('request-removal-btn');
    if (requestRemovalBtn) requestRemovalBtn.addEventListener('click', requestAccountRemoval);

    // Modal overlay click closes
    modalOverlay.addEventListener('click', e => {
      if (e.target === modalOverlay) modalOverlay.classList.add('hidden');
    });
  }

  // ==================== API calls =========================
  async function fetchAllUsers() {
    const res = await fetch(`${BASE_URL}/list/all`, { headers: authHeaders() });
    if (!res.ok) throw new Error('Não foi possível carregar a lista de utilizadores');
    return await res.json();
  }

  async function showOperationModal(operation) {
    let users;
    try {
      users = await fetchAllUsers();
    } catch {
      showMessage('Error fetching users', 'error');
      return;
    }

    const options = users.map(u => `<option value="${u}">${u}</option>`).join('');
    const titles = {
      activate: 'Activate Account',
      deactivate: 'Deactivate Account',
      suspend: 'Suspend Account',
      remove: 'Remove Account',
    };

    const formHtml = `
      <form id="operation-form">
        <div class="form-group">
          <label for="target-username">Choose user</label>
          <select id="target-username" name="targetUsername" required>
            <option value="" disabled selected>-- select user --</option>
            ${options}
          </select>
        </div>
        <div class="form-actions">
          <button type="button" class="btn-secondary" onclick="closeModal()">Cancel</button>
          <button type="submit" class="btn-primary">Execute</button>
        </div>
      </form>`;

    openModal(titles[operation], formHtml);

    document.getElementById('operation-form')
      .addEventListener('submit', async e => {
        e.preventDefault();
        const chosen = e.target.targetUsername.value;
        if (!chosen) return;
        closeModal();
        showLoading();
        try {
          const endpoints = {
            activate: `${BASE_URL}/activate`,
            deactivate: `${BASE_URL}/deactivate`,
            suspend: `${BASE_URL}/suspend`,
            remove: `${BASE_URL}/account/remove`,
          };
          const res = await fetch(endpoints[operation], {
            method: 'POST',
            headers: authHeaders({ 'Content-Type': 'application/json' }),
           body   : JSON.stringify({ targetUsername: chosen }), 
          });
          const txt = await res.text();
          if (res.ok) {
            showMessage(`Operation ${operation} completed!`, 'success');
            loadUsers();
          } else {
            showMessage(txt || `Error in ${operation}`, 'error');
          }
        } catch {
          showMessage('Connection error', 'error');
        } finally {
          hideLoading();
        }
      });
  }

  function showCreateUserModal() {
    // evita duplicação se o modal já estiver aberto
    if (!modalOverlay.classList.contains('hidden')) return;

    modalTitle.textContent = 'Create Institutional User';

    modalBody.innerHTML = `
      <form id="create-user-form" class="profile-form" autocomplete="off" style="max-height:65vh;overflow-y:auto;">
        <div class="form-row">
          <div class="form-group">
            <label for="cu-username">Username*</label>
            <input id="cu-username" name="username" required>
          </div>
          <div class="form-group">
            <label for="cu-email">E-mail*</label>
            <input id="cu-email" type="email" name="email" required>
          </div>
        </div>
        <div class="form-row">
          <div class="form-group">
            <label for="cu-password">Password*</label>
            <input id="cu-password" type="password" name="password" required>
          </div>
          <div class="form-group">
            <label for="cu-fullname">Full name*</label>
            <input id="cu-fullname" name="fullName" required>
          </div>
        </div>
        <div class="form-group">
          <label for="cu-role">Role*</label>
          <select id="cu-role" name="role" required>
            <option value="" disabled selected>-- choose role --</option>
            <option>SYSADMIN</option>
            <option>SYSBO</option>
            <option>SMBO</option>
           
            <option>PRBO</option>
            <option>PO</option>
            <option>ADLU</option>
          </select>
        </div>
        <div class="form-row">
          <div class="form-group">
            <label for="cu-phone">Phone</label>
            <input id="cu-phone" name="phone" placeholder="optional">
          </div>
          <div class="form-group">
            <label for="cu-address">Address</label>
            <input id="cu-address" name="address" placeholder="optional">
          </div>
        </div>
        <div class="form-row">
          <div class="form-group">
            <label for="cu-nif">NIF</label>
            <input id="cu-nif" name="nif" placeholder="optional">
          </div>
          <div class="form-group">
            <label for="cu-cc">CC</label>
            <input id="cu-cc" name="cc" placeholder="optional">
          </div>
        </div>
        <div class="form-group" style="display:flex;align-items:center;gap:8px;">
          <input id="cu-public" type="checkbox" name="isPublic">
          <label for="cu-public" style="margin:0;">Public profile</label>
        </div>
        <div class="form-actions" style="justify-content:flex-end;gap:10px;">
          <button type="button" class="btn-secondary" onclick="closeModal()">Cancel</button>
          <button type="submit" class="btn-primary">Create</button>
        </div>
      </form>`;

    modalOverlay.classList.remove('hidden');

    // usa setTimeout 0 para garantir que o DOM aplicou innerHTML
    setTimeout(() => {
      const formEl = document.getElementById('create-user-form');
      if (formEl) {
        formEl.addEventListener('submit', createInstitutionalUser, { once: true });
      } else {
        console.error('create-user-form não encontrado no DOM');
      }
    }, 0);
  }

  async function createInstitutionalUser(e) {
    e.preventDefault();
    const form = e.target;
    const data = {
      username : form.username.value.trim(),
      email    : form.email.value.trim(),
      password : form.password.value,
      fullName : form.fullName.value.trim(),
      role     : form.role.value,
      phone    : form.phone.value.trim()      || undefined,
      address  : form.address.value.trim()    || undefined,
      nif      : form.nif.value.trim()        || undefined,
      cc       : form.cc.value.trim()         || undefined,
      publicProfile : form.isPublic.checked
    };

    // validação rápida (backend já valida novamente)
    if (!data.username || !data.email || !data.password || !data.fullName || !data.role) {
      showMessage('Please fill all required fields', 'error');
      return;
    }

    showLoading();
    closeModal();
    try {
      // remove campos undefined para não enviar propriedades vazias
      Object.keys(data).forEach(k => data[k] === undefined && delete data[k]);

      const res = await fetch(`${BASE_URL}/register/institutional`, {
        method : 'POST',
        headers: authHeaders({ 'Content-Type': 'application/json' }),
        body   : JSON.stringify(data)
      });
      const txt = await res.text();
      if (res.ok) {
        showMessage('Institutional user created successfully!', 'success');
        loadUsers?.(); // refresca listagem se função existir
      } else {
        showMessage(txt || 'Error creating user.', 'error');
      }
    } catch {
      showMessage('Connection error. Please try again.', 'error');
    } finally {
      hideLoading();
    }
  }

  
  // ---------------- Navegação & secções ------------------
  function switchSection(section) {
    navItems.forEach(item => {
      item.classList.toggle('active', item.dataset.section === section);
    });
    contentSections.forEach(sec => {
      sec.classList.toggle('active', sec.id === `${section}-section`);
    });
    const titles = {
      overview: 'Overview',
      profile: 'My Profile',
      users: 'User Management',
      accounts: 'Account Operations',
      sessions: 'Session Management',
      reports: 'Reports & Analytics',
      'request-removal': 'Request Account Removal',
    };
    pageTitle.textContent = titles[section] || section;
    currentSection = section;
    loadSectionData(section);
  }

  function loadSectionData(section) {
    switch (section) {
      case 'overview':
        loadOverviewData();
        break;
      case 'profile':
        loadProfileData();
        break;
      case 'users':
        if (isAdmin()) loadUsers();
        break;
      case 'sessions':
        if (isAdmin()) loadSessions();
        break;
      case 'reports':
        if (isAdmin()) loadReportsData();
        break;
    }
  }

  function isAdmin() {
    return currentUserRoles.includes('SYSADMIN') || 
           currentUserRoles.includes('SYSBO') || 
           currentUserRoles.includes('ADMIN');
  }


  // ---------------- Overview -----------------------------
  async function loadOverviewData() {
    try {
      const actualUsername = username.includes('@') ? username.split('@')[0] : username;
      const headers = authHeaders();

      const profileResponse = await fetch(`${BASE_URL}/account/profile/${actualUsername}`, { headers });
      if (profileResponse.ok) {
        const profileData = await profileResponse.json();
        document.getElementById('profile-status').textContent = profileData.profile || 'Unknown';
      } else {
        document.getElementById('profile-status').textContent = 'Private';
      }

      const stateResponse = await fetch(`${BASE_URL}/account/state/${actualUsername}`, { headers });
      if (stateResponse.ok) {
        const stateData = await stateResponse.json();
        document.getElementById('account-state').textContent = stateData.estado || 'Active';
      } else {
        document.getElementById('account-state').textContent = 'Active';
      }

      if (isAdmin()) await loadAdminStats();
    } catch (error) {
      console.error('Error loading overview:', error);
      document.getElementById('profile-status').textContent = 'Error';
      document.getElementById('account-state').textContent = 'Error';
    }
  }

  async function loadAdminStats() {
    try {
      const response = await fetch(`${BASE_URL}/list/all`, { headers: authHeaders() });
      if (response.ok) {
        const users = await response.json();
        document.getElementById('total-users').textContent = users.length;
      }
    } catch (error) {
      console.error('Error loading admin stats:', error);
      document.getElementById('total-users').textContent = 'Error';
    }
  }

  // ---------------- Reports ------------------------------
  async function loadReportsData() {
    if (!isAdmin()) return;
    try {
      const headers = authHeaders();
      const states = ['ATIVADA', 'INATIVO', 'SUSPENSA'];
      const stateCounts = {};
      for (const state of states) {
        try {
          const res = await fetch(`${BASE_URL}/list/state/${state}`, { headers });
          stateCounts[state] = res.ok ? (await res.json()).length : 0;
        } catch {
          stateCounts[state] = 0;
        }
      }
      document.getElementById('active-users-count').textContent = stateCounts['ATIVADA'] || 0;
      document.getElementById('inactive-users-count').textContent = stateCounts['INATIVO'] || 0;
      document.getElementById('suspended-users-count').textContent = stateCounts['SUSPENSA'] || 0;

      const sessionsResponse = await fetch(`${BASE_URL}/list/logged`, { headers });
      if (sessionsResponse.ok) {
        const sessions = await sessionsResponse.json();
        document.getElementById('active-sessions-count').textContent = sessions.length;
      } else {
        document.getElementById('active-sessions-count').textContent = '0';
      }
      await loadRoleDistribution();
    } catch (error) {
      console.error('Error loading reports data:', error);
      document.getElementById('active-sessions-count').textContent = '0';
    }
  }

  async function loadRoleDistribution() {
    const roles = ['RU', 'SYSADMIN', 'SYSBO', 'SMBO', 'PRBO', 'PO', 'ADLU'];
    const roleDistribution = document.getElementById('role-distribution');
    const headers = authHeaders();
    let distributionHTML = '<div class="role-stats">';
    for (const role of roles) {
      try {
        const res = await fetch(`${BASE_URL}/list/role/${role}`, { headers });
        const users = res.ok ? await res.json() : [];
        distributionHTML += `<div class="role-stat-item"><span class="role-name">${role}</span><span class="role-count">${users.length}</span></div>`;
      } catch {
        distributionHTML += `<div class="role-stat-item"><span class="role-name">${role}</span><span class="role-count">0</span></div>`;
      }
    }
    distributionHTML += '</div>';
    roleDistribution.innerHTML = distributionHTML;
  }

  // ---------------- Profile ------------------------------
  function loadProfileData() {
    /* Caso queira preencher o formulário com dados actuais do utilizador */
  }

  async function updateProfile(e) {
    e.preventDefault();
    const formData = new FormData(e.target);
    const profileData = {
      fullName: formData.get('fullName'),
      phone: formData.get('phone'),
      address: formData.get('address'),
      nationality: formData.get('nationality'),
      residenceCountry: formData.get('residenceCountry'),
      nif: formData.get('nif'),
      cc: formData.get('cc'),
      isPublic: false,
    };
    showLoading();
    try {
      const res = await fetch(`${BASE_URL}/account/update`, {
        method: 'PUT',
        headers: authHeaders({ 'Content-Type': 'application/json' }),
        body: JSON.stringify(profileData),
      });
      const txt = await res.text();
      if (res.ok) showMessage('Profile updated successfully!', 'success');
      else showMessage(txt || 'Error updating profile.');
    } catch {
      showMessage('Connection error. Please try again.');
    } finally {
      hideLoading();
    }
  }

  async function toggleProfileVisibility() {
    showLoading();
    try {
      const res = await fetch(`${BASE_URL}/profile`, { method: 'POST', headers: authHeaders() });
      const txt = await res.text();
      if (res.ok) {
        showMessage(txt, 'success');
        loadOverviewData();
      } else {
        showMessage(txt || 'Error toggling profile visibility.');
      }
    } catch {
      showMessage('Connection error. Please try again.');
    } finally {
      hideLoading();
    }
  }

  async function requestAccountRemoval() {
    if (!confirm('Are you sure you want to request account removal? This action cannot be undone.')) return;
    showLoading();
    try {
      const res = await fetch(`${BASE_URL}/account/remove-request`, { method: 'PATCH', headers: authHeaders() });
      const txt = await res.text();
      if (res.ok) {
        showMessage('Account removal request submitted successfully!', 'success');
        loadOverviewData();
      } else {
        showMessage(txt || 'Error requesting account removal.');
      }
    } catch {
      showMessage('Connection error. Please try again.');
    } finally {
      hideLoading();
    }
  }

  // ---------------- Users & Sessions ---------------------
  async function loadUsers() {
    if (!isAdmin()) return;
    const roleFilter = document.getElementById('user-filter').value;
    const stateFilter = document.getElementById('state-filter').value;
    const profileFilter = document.getElementById('profile-filter').value;
    showLoading();
    try {
      let endpoint = `${BASE_URL}/list/all`;
      if (roleFilter !== 'all') endpoint = `${BASE_URL}/list/role/${roleFilter}`;
      else if (stateFilter) endpoint = `${BASE_URL}/list/state/${stateFilter}`;
      else if (profileFilter) endpoint = `${BASE_URL}/list/profile/${profileFilter}`;
      const res = await fetch(endpoint, { headers: authHeaders() });
      if (res.ok) displayUsers(await res.json());
      else showMessage('Error loading users.');
    } catch {
      showMessage('Connection error. Please try again.');
    } finally {
      hideLoading();
    }
  }

  function displayUsers(users) {
    const usersList = document.getElementById('users-list');
    if (!users || !users.length) {
      usersList.innerHTML = '<div class="list-item"><p>No users found.</p></div>';
      return;
    }
    usersList.innerHTML = users.map(u => `
      <div class="list-item"><div class="list-item-info"><h4>${u}</h4><p>Username: ${u}</p></div><div class="list-item-actions"><button class="btn-secondary" onclick="viewUserDetails('${u}')">View Details</button></div></div>`).join('');
  }

  async function loadSessions() {
    if (!isAdmin()) return;
    showLoading();
    try {
      const res = await fetch(`${BASE_URL}/list/logged`, { headers: authHeaders() });
      if (res.ok) displaySessions(await res.json());
      else showMessage('Error loading sessions.');
    } catch {
      showMessage('Connection error. Please try again.');
    } finally {
      hideLoading();
    }
  }

  function displaySessions(sess) {
    const sessionsList = document.getElementById('sessions-list');
    if (!sess || !sess.length) {
      sessionsList.innerHTML = '<div class="list-item"><p>No active sessions found.</p></div>';
      return;
    }
    sessionsList.innerHTML = sess.map(s => `
      <div class="list-item"><div class="list-item-info"><h4>${s}</h4><p>Active session</p></div><div class="list-item-actions"><button class="btn-danger" onclick="forceLogout('${s}')">Force Logout</button></div></div>`).join('');
  }

  // ---------------- Forced logout ------------------------
  window.forceLogout = async function (targetUsername) {
    if (!confirm(`Are you sure you want to force logout for ${targetUsername}?`)) return;
    showLoading();
    try {
      const res = await fetch(`${BASE_URL}/force-logout`, {
        method: 'POST',
        headers: authHeaders({ 'Content-Type': 'application/json' }),
        body: JSON.stringify({ targetUsername }),
      });
      const txt = await res.text();
      if (res.status === 401 || res.status === 403) {
        await logout();
        return;
      }
      if (res.ok) {
        showMessage('User logged out successfully!', 'success');
        if (targetUsername === shortUsername) {
          await logout();
          return;
        }
        loadSessions();
      } else {
        showMessage(txt || 'Error forcing logout.');
      }
    } catch {
      showMessage('Connection error. Please try again.');
    } finally {
      hideLoading();
    }
  };

  // ---------------- Modals & User details ----------------
  window.viewUserDetails = async function (u) {
    try {
      const headers = authHeaders();
      const [stateRes, profileRes] = await Promise.all([
        fetch(`${BASE_URL}/account/state/${u}`, { headers }),
        fetch(`${BASE_URL}/account/profile/${u}`, { headers }),
      ]);
      const state = stateRes.ok ? await stateRes.json() : { estado: 'Unknown' };
      const profile = profileRes.ok ? await profileRes.json() : { profile: 'Unknown' };
      openModal('User Details', `
        <div class="details-grid"><div><strong>Username:</strong> ${u}</div><div><strong>State:</strong> ${state.estado}</div><div><strong>Profile:</strong> ${profile.profile}</div></div>
        <div class="form-actions" style="justify-content:center; margin-top:1rem;"><button class="btn-primary" onclick="closeModal()">Close</button></div>`);
    } catch {
      showMessage('Error loading user details.', 'error');
    }
  };

  // ---------------- Utilitários --------------------------
  window.closeModal = closeModal;
  function closeModal() { modalOverlay.classList.add('hidden'); }
  function showLoading() { loading.classList.remove('hidden'); }
  function hideLoading() { loading.classList.add('hidden'); }
  function showMessage(text, type = 'error') {
    messageText.textContent = text;
    message.className = `message ${type}`;
    message.classList.remove('hidden');
    setTimeout(() => message.classList.add('hidden'), 5000);
  }

  async function logout() {
    showLoading();
    try {
      await fetch(`${BASE_URL}/logout/jwt`, { method: 'POST', headers: authHeaders() });
    } finally {
      localStorage.removeItem('authToken');
      localStorage.removeItem('authType');
      localStorage.removeItem('username');
      window.location.href = 'login.html';
    }
  }

  const notifBtn = document.getElementById('notif-btn');
  const notifBadge = document.getElementById('notif-badge');
  const notifModal = document.getElementById('notif-modal');
  const notifBody = document.getElementById('notif-body');

  notifBtn.addEventListener('click', loadNotifications);
  document.getElementById('notif-close-btn')?.addEventListener('click', closeNotifModal);
  document.getElementById('notif-footer-close')?.addEventListener('click', closeNotifModal);


  async function loadNotifications() {
    notifModal.classList.remove('hidden');
    notifBody.innerHTML = '<p>Carregando...</p>';

    const token = localStorage.getItem('authToken');
    const res = await fetch(`${BASE_URL}/notify-out/notifications`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    if (!res.ok) {
      const errorText = await res.text();
      if (res.status === 403) {
        notifBody.innerHTML = '<p>Apenas utilizadores PRBO podem ver notificações</p>';
      } else {
        notifBody.innerHTML = `<p>Erro ao carregar notificações: ${errorText}</p>`;
      }
      return;
    }

    const data = await res.json();
    if (!data.length) {
      notifBody.innerHTML = '<p>Sem notificações</p>';
      return;
    }

    notifBody.innerHTML = data.map(n => `
    <div class="notification-item ${n.read ? '' : 'unread'}">
      <div class="title">${n.title}</div>
      <div class="message">${n.message}</div>
      <div class="time">${new Date(n.timestamp).toLocaleString()}</div>
    </div>
  `).join('');

    const unreadCount = data.filter(n => !n.read).length;
    notifBadge.textContent = unreadCount;
    notifBadge.classList.toggle('hidden', unreadCount === 0);
  }

  function closeNotifModal() {
    notifModal.classList.add('hidden');
  }

});