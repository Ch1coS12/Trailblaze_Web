
'use strict';

document.addEventListener('DOMContentLoaded', () => {

  /* ---------- CONSTANTES ---------- */
  const token    = localStorage.getItem('authToken');
  const authType = localStorage.getItem('authType');
  const username = localStorage.getItem('username');
  const BASE_URL = location.pathname.includes('Firstwebapp')
                 ? '/Firstwebapp/rest'
                 : '/rest';

    const shortUsername = username && username.includes('@')
        ? username.split('@')[0]
        : username;

    const origFetch = window.fetch;
    window.fetch = async (...args) => {
        const resp = await origFetch(...args);
        if (resp.status === 401 || resp.status === 403) {
            ['authToken','authType','username'].forEach(localStorage.removeItem.bind(localStorage));
            location.href = 'login.html';
        }
        return resp;
    };

  if (!token || !username || authType !== 'jwt') {
    location.href = 'login.html';
    return;
  }

  /* ---------- VARIÁVEIS DE ESTADO ---------- */
  let userRoles   = [];
  let primaryRole = 'RU';
  let currentWorksheet = null;

  /* ---------- SHORT-CUT DOM ---------- */
  const $  = id => document.getElementById(id) || {};      // evita crashes
  const usernameDisplay = $('username-display');
  const welcomeUsername = $('welcome-username');
  const userInitial     = $('user-initial');
  const userRoleEl      = $('user-role');
  const roleDisplay     = $('role-display');

  const logoutBtn   = $('logout-btn');

  /* overlay / modal */
  const modalOverlay   = $('modal-overlay');
  const modalTitle     = $('modal-title');
  const modalBody      = $('modal-body');
  const modalCloseBtn  = $('modal-close');

  /* ui feedback */
  const loading          = $('loading');
  const messageBox       = $('message');
  const messageText      = $('message-text');
  const closeMessageBtn  = $('close-message');

  /* ---------- HELPERS ---------- */
  const authHeaders = extra => ({
    'Authorization': `Bearer ${token}`,
    'Content-Type' : 'application/json',
    ...extra
  });
  const hasRole = r   => userRoles.includes(r);
  const anyRole = arr => arr.some(hasRole);

  const show    = el => el && (el.style.display = 'block');
  const hide    = el => el && (el.style.display = 'none');

  
  const showLoading = () => loading .classList.remove('hidden');
  const hideLoading = () => loading .classList.add   ('hidden');

  const showMessage = (txt, type='error', ms=4000) => {
    messageText.textContent = txt;
    messageBox .className   = `message ${type}`;
    messageBox .classList.remove('hidden');
    clearTimeout(showMessage._t);
    showMessage._t = setTimeout(() => messageBox.classList.add('hidden'), ms);
  };

  /* =========================================================== */
  /*                           INIT                              */
  /* =========================================================== */
  (function init() {
    const short = username.includes('@') ? username.split('@')[0] : username;
    usernameDisplay.textContent = welcomeUsername.textContent = short;
    userInitial.textContent     = short[0]?.toUpperCase() || '?';

    decodeRoles();
    buildInterface();
    attachListeners();
    loadUserData();
  })();

  function decodeRoles() {
    try {
      const payload   = JSON.parse(atob(token.split('.')[1]));
      userRoles       = payload.roles || (payload.role ? [payload.role] : []);
      if (userRoles.length === 0) userRoles = ['RU'];
      primaryRole     = userRoles[0];
    } catch (e) {
      console.warn('[userpage] falha a decifrar JWT:', e);
      userRoles   = ['RU'];
      primaryRole = 'RU';
    }
    userRoleEl .textContent =
    roleDisplay.textContent = primaryRole;
  }

  /* =========================================================== */
  /*                  INTERFACE dependente de ROLE               */
  /* =========================================================== */
  function buildInterface() {

    // -- secções principais
    const admin      = $('admin-actions');
    const actions       = $('your-actions');

    [admin,actions].forEach(hide);

      switch (primaryRole) {
          case 'SYSADMIN':
              show(admin);      // full admin access
              show(actions);
              break;

          case 'SYSBO':
              show(admin);      // admin without affecting SYSADMIN (handled server side)
              show(actions);
              break;

          case 'SMBO':
              show(actions);
              break;

          case 'PRBO':
              show(actions);
              break;

          case 'PO':
              show(actions);
              break;

          case 'RU':
              show(actions);
              break;

          case 'ADLU':
              show(actions);
              break;
          default:
              // outros papeis herdam apenas visualização de folhas de obra
              show(actions);
              break;
      }

    /* ---------- Cartões específicos ---------- */
    setupRoleCards();
  }

  function setupRoleCards() {
      const wsCard   = $('access-worksheets-card');
      const esCard   = $('access-execution-sheets-card');
      const actCard  = $('access-activities-card');
      const advSettings = $('advanced-settings-card');

      [wsCard, esCard, actCard, advSettings].forEach(hide);

      switch (primaryRole) {
          case 'SYSADMIN':
          case 'SYSBO':
              show(wsCard);
              show(esCard);
              show(actCard);
              show(advSettings);
              break;
          case 'SMBO':
              show(wsCard);
              show(esCard);
              break;
          case 'PRBO':
              show(wsCard);
              show(esCard);
              break;
          case 'PO':
              show(wsCard);  // Adicionado acesso às worksheets para POs
              show(esCard);
              show(actCard);
              break;
          case 'ADLU':
              show(wsCard);
              show(advSettings)
              break;
          case 'RU':
              show(wsCard);
              break;
          default:
              show(wsCard);
              break;
      }
  }

  /* =========================================================== */
  /*                      EVENT LISTENERS                        */
  /* =========================================================== */
  function attachListeners(){
    logoutBtn  .addEventListener('click', logout);
    modalCloseBtn.addEventListener('click', closeModal);
    modalOverlay.addEventListener('click', e => { if (e.target === modalOverlay) closeModal(); });
    closeMessageBtn.addEventListener('click', () => messageBox.classList.add('hidden'));
  }

  /* =========================================================== */
  /*                    CARREGAR DADOS DO UTIL                   */
  /* =========================================================== */
  async function loadUserData() {
    try {
      const shortUser = username.includes('@') ? username.split('@')[0] : username;
      const hdr       = authHeaders({'username':shortUser});

      const prof  = await fetch(`${BASE_URL}/account/profile/${shortUser}`, {headers:hdr});
      $('profile-status').textContent =
          prof.ok ? (await prof.json()).profile : 'Private';

      const state = await fetch(`${BASE_URL}/account/state/${shortUser}`,   {headers:hdr});
      $('account-state').textContent =
          state.ok ? (await state.json()).estado : 'Active';

    } catch (e) {
      console.error(e);
      $('profile-status').textContent = $('account-state').textContent = 'Error';
    }
  }

  /* =========================================================== */
  /*                            MODAL                            */
  /* =========================================================== */
  function openModal(title, html) {
    modalTitle.textContent = title;
    modalBody .innerHTML   = html;
    modalOverlay.classList.remove('hidden');
  }
  function closeModal(){ modalOverlay.classList.add('hidden'); }

  /* =========================================================== */
  /*                            LOGOUT                           */
  /* =========================================================== */
  async function logout() {
    showLoading();
    try { await fetch(`${BASE_URL}/logout/jwt`, {method:'POST', headers:authHeaders()}); }
    finally {
      ['authToken','authType','username'].forEach(localStorage.removeItem.bind(localStorage));
      location.href = 'login.html';
    }
  }

  // Load user data
  async function loadUserData() {
      try {
          const actualUsername = username.includes('@') ? username.split('@')[0] : username;
          const headers = {
              'Authorization': `Bearer ${token}`,
              'username': actualUsername
          };

          // Get profile status
          const profileResponse = await fetch(`${BASE_URL}/account/profile/${actualUsername}`, {
              headers: headers
          });

          if (profileResponse.ok) {
              const profileData = await profileResponse.json();
              document.getElementById('profile-status').textContent = profileData.profile || 'Unknown';
          } else {
              document.getElementById('profile-status').textContent = 'Private';
          }

          // Get account state
          const stateResponse = await fetch(`${BASE_URL}/account/state/${actualUsername}`, {
              headers: headers
          });

          if (stateResponse.ok) {
              const stateData = await stateResponse.json();
              document.getElementById('account-state').textContent = stateData.estado || 'Active';
          } else {
              document.getElementById('account-state').textContent = 'Active';
          }

      } catch (error) {
          console.error('Error loading user data:', error);
          document.getElementById('profile-status').textContent = 'Error loading';
          document.getElementById('account-state').textContent = 'Error loading';
      }
  }

    // Global functions for action buttons
    window.showUpdateProfileModal = function() {
        const modalTitle = document.getElementById('modal-title');
        const modalBody = document.getElementById('modal-body');

        modalTitle.textContent = 'Update Profile';
        modalBody.innerHTML = `
            <form id="update-profile-form">
                <div class="form-row">
                    <div class="form-group">
                        <label for="fullName">Full Name</label>
                        <input type="text" id="fullName" name="fullName">
                    </div>
                    <div class="form-group">
                        <label for="phone">Phone</label>
                        <input type="tel" id="phone" name="phone">
                    </div>
                </div>
                
                <div class="form-group">
                    <label for="address">Address</label>
                    <textarea id="address" name="address" rows="3"></textarea>
                </div>

                <div class="form-row">
                    <div class="form-group">
                        <label for="nationality">Nationality</label>
                        <input type="text" id="nationality" name="nationality">
                    </div>
                    <div class="form-group">
                        <label for="residenceCountry">Residence Country</label>
                        <input type="text" id="residenceCountry" name="residenceCountry">
                    </div>
                </div>

                <div class="form-row">
                    <div class="form-group">
                        <label for="nif">NIF</label>
                        <input type="text" id="nif" name="nif">
                    </div>
                    <div class="form-group">
                        <label for="cc">CC</label>
                        <input type="text" id="cc" name="cc">
                    </div>
                </div>

                <div class="form-actions">
                    <button type="button" onclick="closeModal()" class="btn-secondary">Cancel</button>
                    <button type="submit" class="action-btn">Update Profile</button>
                </div>
            </form>
        `;

        const form = document.getElementById('update-profile-form');
        form.addEventListener('submit', updateProfile);

        modalOverlay.classList.remove('hidden');
    };

    window.toggleProfileVisibility = async function() {
        showLoading();

        try {
            const response = await fetch(`${BASE_URL}/profile`, {
                method: 'POST',
                headers: {
                    'Authorization': `Bearer ${token}`
                }
            });

            const data = await response.text();

            if (response.ok) {
                showMessage(data, 'success');
                loadUserData(); // Refresh user data
            } else {
                showMessage(data || 'Error toggling profile visibility.');
            }
        } catch (error) {
            console.error('Error toggling profile:', error);
            showMessage('Connection error. Please try again.');
        } finally {
            hideLoading();
        }
    };

    window.requestAccountRemoval = async function() {
        if (!confirm('Are you sure you want to request account removal? This action cannot be undone.')) {
            return;
        }

        showLoading();

        try {
            const response = await fetch(`${BASE_URL}/account/remove-request`, {
                method: 'PATCH',
                headers: {
                    'Authorization': `Bearer ${token}`
                }
            });

            const data = await response.text();

            if (response.ok) {
                showMessage('Account removal request submitted successfully!', 'success');
                loadUserData(); // Refresh user data
            } else {
                showMessage(data || 'Error requesting account removal.');
            }
        } catch (error) {
            console.error('Error requesting removal:', error);
            showMessage('Connection error. Please try again.');
        } finally {
            hideLoading();
        }
    };

    // Admin functions
    window.showCreateUserModal = function() {
        const modalTitle = document.getElementById('modal-title');
        const modalBody = document.getElementById('modal-body');

        modalTitle.textContent = 'Create Institutional User';
        modalBody.innerHTML = `
            <form id="create-user-form">
                <div class="form-row">
                    <div class="form-group">
                        <label for="new-username">Username</label>
                        <input type="text" id="new-username" name="username" required>
                    </div>
                    <div class="form-group">
                        <label for="new-email">Email</label>
                        <input type="email" id="new-email" name="email" required>
                    </div>
                </div>
                <div class="form-group">
                    <label for="new-fullName">Full Name</label>
                    <input type="text" id="new-fullName" name="fullName" required>
                </div>
                <div class="form-group">
                    <label for="new-password">Password</label>
                    <input type="password" id="new-password" name="password" required>
                </div>
                <div class="form-group">
                    <label for="new-role">Role</label>
                    <select id="new-role" name="role" required>
                        <option value="SYSBO">System Operator</option>
                        <option value="SMBO">SM Operator</option>
                       
                        <option value="PRBO">PR Operator</option>
                        <option value="PO">Partner</option>
                        <option value="ADLU">Advanced User</option>
                    </select>
                </div>
                <div class="form-actions">
                    <button type="button" onclick="closeModal()" class="btn-secondary">Cancel</button>
                    <button type="submit" class="action-btn">Create User</button>
                </div>
            </form>
        `;

        const form = document.getElementById('create-user-form');
        form.addEventListener('submit', createInstitutionalUser);

        modalOverlay.classList.remove('hidden');
    };

    window.showSessionsModal = async function() {
        const modalTitle = document.getElementById('modal-title');
        const modalBody = document.getElementById('modal-body');

        modalTitle.textContent = 'Active Sessions';
        modalBody.innerHTML = '<p>Loading sessions...</p>';
        modalOverlay.classList.remove('hidden');

        try {
            const actualUsername = username.includes('@') ? username.split('@')[0] : username;
            const response = await fetch(`${BASE_URL}/list/logged`, {
                headers: {
                    'username': actualUsername
                }
            });

            if (response.ok) {
                const sessions = await response.json();
                
                if (sessions.length === 0) {
                    modalBody.innerHTML = '<p>No active sessions found.</p>';
                } else {
                    modalBody.innerHTML = `
                        <div class="sessions-list">
                            ${sessions.map(session => `
                                <div class="session-item">
                                    <span>${session}</span>
                                    <button class="action-btn btn-danger" onclick="forceLogout('${session}')">Force Logout</button>
                                </div>
                            `).join('')}
                        </div>
                        <style>
                            .sessions-list { display: flex; flex-direction: column; gap: 10px; }
                            .session-item { display: flex; justify-content: space-between; align-items: center; padding: 10px; background: #f8f9fa; border-radius: 5px; }
                        </style>
                    `;
                }
            } else {
                modalBody.innerHTML = '<p>Error loading sessions.</p>';
            }
        } catch (error) {
            modalBody.innerHTML = '<p>Connection error.</p>';
        }
    };

    window.showReportsModal = function() {
        const modalTitle = document.getElementById('modal-title');
        const modalBody = document.getElementById('modal-body');

        modalTitle.textContent = 'System Reports';
        modalBody.innerHTML = `
            <p>Access the full dashboard for detailed reports and analytics.</p>
            <div class="form-actions">
                <button type="button" onclick="closeModal()" class="btn-secondary">Close</button>
                <button type="button" onclick="window.location.href='dashboard.html'" class="action-btn">Go to Dashboard</button>
            </div>
        `;

        modalOverlay.classList.remove('hidden');
    };

    // Operator functions
    window.showUsersModal = function() {
        const modalTitle = document.getElementById('modal-title');
        const modalBody = document.getElementById('modal-body');

        modalTitle.textContent = 'View Users';
        modalBody.innerHTML = `
            <p>You can view users within your permission scope. Access the dashboard for full user management.</p>
            <div class="form-actions">
                <button type="button" onclick="closeModal()" class="btn-secondary">Close</button>
                <button type="button" onclick="window.location.href='dashboard.html'" class="action-btn">Go to Dashboard</button>
            </div>
        `;

        modalOverlay.classList.remove('hidden');
    };

    window.showLimitedReportsModal = function() {
        const modalTitle = document.getElementById('modal-title');
        const modalBody = document.getElementById('modal-body');

        modalTitle.textContent = 'Reports';
        modalBody.innerHTML = `
            <p>Access available reports for your role through the dashboard.</p>
            <div class="form-actions">
                <button type="button" onclick="closeModal()" class="btn-secondary">Close</button>
                <button type="button" onclick="window.location.href='dashboard.html'" class="action-btn">Go to Dashboard</button>
            </div>
        `;

        modalOverlay.classList.remove('hidden');
    };

    // Advanced user functions
    window.showAdvancedSettings = function() {
        showMessage('Advanced settings functionality coming soon!', 'success');
    };

    window.forceLogout = async function(targetUsername) {
        if (!confirm(`Are you sure you want to force logout for ${targetUsername}?`)) {
            return;
        }

        showLoading();

        try {
            const response = await fetch(`${BASE_URL}/force-logout`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${token}`
                },
                body: JSON.stringify({ targetUsername })
            });

            const data = await response.text();

            if (response.status === 401 || response.status === 403) {
                await logout();
                return;
            }

            if (response.ok) {
                showMessage('User logged out successfully!', 'success');
                closeModal();
                if (targetUsername === shortUsername) {
                    await logout();
                    return;
                }
                // Refresh sessions if modal is still open
                setTimeout(() => {
                    if (!modalOverlay.classList.contains('hidden')) {
                        showSessionsModal();
                    }
                }, 1000);
            } else {
                showMessage(data || 'Error forcing logout.');
            }
        } catch (error) {
            console.error('Error forcing logout:', error);
            showMessage('Connection error. Please try again.');
        } finally {
            hideLoading();
        }
    };

    // Update profile function
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
            isPublic: false // Will be handled separately
        };

        showLoading();
        closeModal();

        try {
            const response = await fetch(`${BASE_URL}/account/update`, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${token}`
                },
                body: JSON.stringify(profileData)
            });

            const data = await response.text();

            if (response.ok) {
                showMessage('Profile updated successfully!', 'success');
                loadUserData(); // Refresh user data
            } else {
                showMessage(data || 'Error updating profile.');
            }
        } catch (error) {
            console.error('Error updating profile:', error);
            showMessage('Connection error. Please try again.');
        } finally {
            hideLoading();
        }
    }

    window.closeModal = closeModal;

	
});
