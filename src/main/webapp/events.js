'use strict';

document.addEventListener('DOMContentLoaded', () => {

    /* ---------- DOM shortcuts ---------- */
    const $ = q => document.querySelector(q);
    const $$ = q => document.querySelectorAll(q);
    const eventsGrid = $('#events-grid');
    const createBtn = $('#create-event-btn');
    const refreshBtn = $('#refresh-btn');
    const logoutBtn = $('#logout-btn');

    const usernameDisplayEl = $('#username-display');
    const userRoleEl = $('#user-role');
    const userInitialEl = $('#user-initial');

    /* ---------- Auth e roles ---------- */
    const token = localStorage.getItem('authToken');
    const authType = localStorage.getItem('authType');
    const username = localStorage.getItem('username');
    const BASE_URL = location.pathname.includes('Firstwebapp')
        ? '/Firstwebapp/rest' : '/rest';

    if (!token || !username || authType !== 'jwt') {
        location.href = 'login.html';
        return;
    }

    let roles = [];
    let primaryRole = 'RU';
    try {
        const payload = JSON.parse(atob(token.split('.')[1]));
        roles = payload.roles || (payload.role ? [payload.role] : []);
        if (!Array.isArray(roles) || roles.length === 0) roles = ['RU'];
        primaryRole = roles[0];
    } catch {
        roles = ['RU'];
        primaryRole = 'RU';
    }
	
	const execSheetsNav = document.getElementById('execution-sheets-nav');
	    if (primaryRole === 'RU' && execSheetsNav) execSheetsNav.style.display = 'none';
		
		
    const isAdmin = ['SYSADMIN', 'SYSBO'].includes(primaryRole);

    const myBtn = document.querySelector('[data-mode="my"]');
    if (isAdmin && myBtn) myBtn.remove();

    if (isAdmin) {
        createBtn.classList.remove('hidden');
        createBtn.addEventListener('click', showCreateModal);
    } else {
        createBtn.remove();             // ou createBtn.classList.add('hidden');
    }

	
    let viewMode = 'all';
	let eventsData = [];
	
    const displayName = username.includes('@') ? username.split('@')[0] : username;

    if (usernameDisplayEl) usernameDisplayEl.textContent = displayName;
    if (userInitialEl) userInitialEl.textContent = displayName.charAt(0).toUpperCase();
    if (userRoleEl) userRoleEl.textContent = primaryRole;

    const modalOverlay = $('#modal-overlay');
    const modalTitle = $('#modal-title');
    const modalBody = $('#modal-body');
    const closeModalBtn = $('#modal-close');
    const loading = $('#loading');
    const message = $('#message');
    const msgTxt = $('#message-text');
    $('#close-message')?.addEventListener('click', () => message.classList.add('hidden'));

    closeModalBtn?.addEventListener('click', closeModal);
    modalOverlay?.addEventListener('click', e => {
        if (e.target.id === 'modal-overlay') closeModal();
    });
    logoutBtn?.addEventListener('click', logout);
    refreshBtn?.addEventListener('click', loadEvents);

    if (isAdmin) {
        createBtn.classList.remove('hidden');
        createBtn.addEventListener('click', showCreateModal);
    }

    loadEvents();

    /* ---------- Helpers ---------- */
    function showLoad() {
        loading.classList.remove('hidden');
    }

    function hideLoad() {
        loading.classList.add('hidden');
    }

    function showMessage(txt, type = 'error') {
        msgTxt.textContent = txt;
        message.className = `message ${type}`;
        message.classList.remove('hidden');
        setTimeout(() => message.classList.add('hidden'), 4000);
    }

    function openModal(t, html) {
        modalTitle.textContent = t;
        modalBody.innerHTML = html;
        modalOverlay.classList.remove('hidden');
    }

    function closeModal() {
        modalOverlay.classList.add('hidden');
    }

    async function logout() {
        localStorage.removeItem('authToken');
        localStorage.removeItem('authType');
        localStorage.removeItem('username');
        location.href = 'login.html';
    }

    document.querySelectorAll('.view-mode-btn').forEach(btn => {
        btn.addEventListener('click', e => {
            document.querySelectorAll('.view-mode-btn')
                .forEach(b => b.classList.toggle('active', b === btn));
            viewMode = btn.dataset.mode;
            loadEvents();           // recarrega lista
        });
    });

    /* ---------- Load events ---------- */
    async function loadEvents() {
        showLoad();
        try {
            const url = viewMode === 'my'
                ? `${BASE_URL}/events/registered`
                : `${BASE_URL}/events`;
            const res = await fetch(url, {headers: {'Authorization': `Bearer ${token}`}});
            if (!res.ok) {
                showMessage('Error loading events');
                return;
            }
            const data = await res.json();
			eventsData = Array.isArray(data) ? data : [];
			            renderEvents(eventsData);
						
        } catch {
            showMessage('Network error');
        } finally {
            hideLoad();
        }
    }

    function renderEvents(list) {
        if (!Array.isArray(list) || !list.length) {
            eventsGrid.innerHTML = '<p>No events found.</p>';
            return;
        }
		eventsGrid.innerHTML = list.map((ev,idx) => `
		                       <div class="event-card">
		                               <h3>${ev.title}</h3>
		                               <p class="ev-date">${new Date(ev.dateTime).toLocaleString()}</p>
		                               <p class="ev-location">${ev.location}</p>
		                               <p class="ev-desc">${ev.description || ''}</p>
		                               ${isAdmin ? `
		                                   <button class="btn-edit" onclick="editEvent(${idx})">Edit</button>
		                                   <button class="btn-delete" onclick="deleteEvent('${ev.id}')">Delete</button>
		                                   <button class="action-btn registrations-btn" onclick="viewRegistrations('${ev.id}')">Registrations</button>
		                               ` : isAdmin ? `
		                                   <button class="action-btn registrations-btn" onclick="viewRegistrations('${ev.id}')">Registrations</button>
		                               ` : (viewMode === 'all'
		                                   ? `<button class="action-btn register-btn" onclick="registerEvent('${ev.id}')">Register</button>`
		                                   : `<button class="action-btn register-btn" onclick="unregisterEvent('${ev.id}')">Unregister</button>`)}
		                                   </div>
		               `).join('');
    }

    /* ---------- Create Event (admin) ---------- */
    function showCreateModal() {
        openModal('Create Event', `
			<form id="event-form">
				<div class="form-group">
					<label>Title</label>
					<input type="text" id="ev-title" required>
				</div>
				<div class="form-group">
					<label>Description</label>
					<textarea id="ev-desc" rows="3"></textarea>
				</div>
				<div class="form-group">
					<label>Date</label>
					<input type="datetime-local" id="ev-date" required>
				</div>
				<div class="form-group">
					<label>Location (lat,lng)</label>
					<input type="text" id="ev-loc" required>
				</div>
				<div class="form-group">
					<label>Worksheet ID</label>
					<input type="text" id="ev-ws" required>
				</div>
				<div class="form-actions">
					<button type="button" class="btn-secondary" onclick="closeModal()">Cancel</button>
					<button type="submit" class="action-btn">Create</button>
				</div>
			</form>`);
        $('#event-form').addEventListener('submit', submitEvent);
    }

    async function submitEvent(e) {
        e.preventDefault();
        showLoad();
        try {
            const body = {
                title: $('#ev-title').value,
                description: $('#ev-desc').value,
                dateTimeMillis: Date.parse($('#ev-date').value),
                location: $('#ev-loc').value,
                workSheetId: $('#ev-ws').value
            };
            const res = await fetch(`${BASE_URL}/events`, {
                method: 'POST',
                headers: {'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json'},
                body: JSON.stringify(body)
            });
            showMessage(await res.text(), res.ok ? 'success' : 'error');
            if (res.ok) {
                closeModal();
                loadEvents();
            }
        } catch {
            showMessage('Error creating event');
        } finally {
            hideLoad();
        }
    }

	/* ---------- Edit Event (super admin) ---------- */
	  window.editEvent = function(idx) {
	      const ev = eventsData[idx];
	      if (!ev) return;
	      openModal('Edit Event', `
	                      <form id="event-form">
	                              <div class="form-group">
	                                      <label>Title</label>
	                                      <input type="text" id="ev-title" value="${ev.title}" required>
	                              </div>
	                              <div class="form-group">
	                                      <label>Description</label>
	                                      <textarea id="ev-desc" rows="3">${ev.description||''}</textarea>
	                              </div>
	                              <div class="form-group">
	                                      <label>Date</label>
	                                      <input type="datetime-local" id="ev-date" value="${new Date(ev.dateTime).toISOString().slice(0,16)}" required>
	                              </div>
	                              <div class="form-group">
	                                      <label>Location (lat,lng)</label>
	                                      <input type="text" id="ev-loc" value="${ev.location}" required>
	                              </div>
	                              <div class="form-group">
	                                      <label>Worksheet ID</label>
	                                      <input type="text" id="ev-ws" value="${ev.workSheetId}" required>
	                              </div>
	                              <div class="form-actions">
	                                      <button type="button" class="btn-secondary" onclick="closeModal()">Cancel</button>
	                                      <button type="submit" class="action-btn">Update</button>
	                              </div>
	                      </form>`);
	      $('#event-form').addEventListener('submit', e => submitUpdateEvent(e, ev.id));
	  };

	  async function submitUpdateEvent(e, id) {
	      e.preventDefault();
	      showLoad();
	      try {
	          const body = {
	              title: $('#ev-title').value,
	              description: $('#ev-desc').value,
	              dateTimeMillis: Date.parse($('#ev-date').value),
	              location: $('#ev-loc').value,
	              workSheetId: $('#ev-ws').value
	          };
	          const res = await fetch(`${BASE_URL}/events/${id}`, {
	              method: 'PUT',
	              headers: {'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json'},
	              body: JSON.stringify(body)
	          });
	          showMessage(await res.text(), res.ok ? 'success' : 'error');
	          if (res.ok) {
	              closeModal();
	              loadEvents();
	          }
	      } catch {
	          showMessage('Error updating event');
	      } finally {
	          hideLoad();
	      }
	  }

	  /* ---------- Delete Event (super admin) ---------- */
	  window.deleteEvent = async function(id) {
	      if (!confirm('Delete event?')) return;
	      showLoad();
	      try {
	          const res = await fetch(`${BASE_URL}/events/${id}`, {
	              method: 'DELETE',
	              headers: {'Authorization': `Bearer ${token}`}
	          });
	          showMessage(await res.text(), res.ok ? 'success' : 'error');
	          if (res.ok) loadEvents();
	      } catch {
	          showMessage('Network error');
	      } finally {
	          hideLoad();
	      }
	  };

	  
    /* ---------- Register Event (RU) ---------- */
    window.registerEvent = async function (id) {
        showLoad();
        try {
            const res = await fetch(`${BASE_URL}/events/${id}/register`, {
                method: 'POST',
                headers: {'Authorization': `Bearer ${token}`}
            });
            showMessage(await res.text(), res.ok ? 'success' : 'error');
            if (res.ok) loadEvents();
        } catch {
            showMessage('Network error');
        } finally {
            hideLoad();
        }
    };

	
	/* ---------- Unregister Event (RU) ---------- */
	window.unregisterEvent = async function (id) {
	    showLoad();
	    try {
	        const res = await fetch(`${BASE_URL}/events/${id}/register`, {
	            method: 'DELETE',
	            headers: {'Authorization': `Bearer ${token}`}
	        });
	        showMessage(await res.text(), res.ok ? 'success' : 'error');
	        if (res.ok) loadEvents();
	    } catch {
	        showMessage('Network error');
	    } finally {
	        hideLoad();
	    }
	};
	
    /* ---------- View Registrations (admin) ---------- */
    window.viewRegistrations = async function (id) {
        showLoad();
        try {
            const res = await fetch(`${BASE_URL}/events/${id}/registrations`, {headers: {'Authorization': `Bearer ${token}`}});
            if (!res.ok) {
                showMessage(await res.text());
                return;
            }
            const users = await res.json();
            openModal('Registrations', `<ul>${users.map(u => `<li>${u}</li>`).join('')}</ul>`);
        } catch {
            showMessage('Error loading registrations');
        } finally {
            hideLoad();
        }
    };
});