/* login.js – versão robusta */

'use strict';

document.addEventListener('DOMContentLoaded', () => {

  /* ---------- CONST ---------- */
  const BASE_URL = location.pathname.includes('Firstwebapp')
        ? '/Firstwebapp/rest' : '/rest';

  /* ---------- DOM helpers ---------- */
  const $  = id => document.getElementById(id);

  const form        = $('login-form');
  const loading     = $('loading');
  const msgBox      = $('message');
  const msgText     = $('message-text');
  const closeMsgBtn = $('close-message');

  /* ---------- aborta se algo estiver em falta ---------- */
  if (!form || !loading || !msgBox || !msgText || !closeMsgBtn) {
    console.error('[login.js] Elementos necessários não encontrados no DOM.');
    return;
  }

  /* ---------- UI helpers ---------- */
  const showLoading = () => loading.classList.remove('hidden');
  const hideLoading = () => loading.classList.add('hidden');

  const showMessage = (txt, type='error', ms=4000) => {
    msgText.textContent = txt;
    msgBox.className    = `message ${type}`;
    msgBox.classList.remove('hidden');
    clearTimeout(showMessage._timer);
    showMessage._timer = setTimeout(() => msgBox.classList.add('hidden'), ms);
  };

  closeMsgBtn.addEventListener('click', () => msgBox.classList.add('hidden'));

  /* ---------- SUBMIT ---------- */
  form.addEventListener('submit', async ev => {
    ev.preventDefault();

    const user = form.username.value.trim();
    const pass = form.password.value;

    if (!user || !pass)
      return showMessage('Please fill out both fields.');

    showLoading();
    try {
      const r = await fetch(`${BASE_URL}/login-jwt`, {
        method : 'POST',
        headers: {'Content-Type':'application/json'},
        body   : JSON.stringify({username:user, password:pass})
      });
      const body = await r.text();

      if (!r.ok)
        return showMessage(body || 'Invalid credentials.');

      /* resposta no formato {"token":"..."} */
      const {token} = JSON.parse(body);

      /* guarda sessão */
      localStorage.setItem('authToken', token);
      localStorage.setItem('username', user);
      localStorage.setItem('authType', 'jwt');

      /* check for return URL from homepage */
      const returnUrl = window.checkReturnUrl ? window.checkReturnUrl() : 'userpage.html';
      location.href = returnUrl;

    } catch (err) {
      console.error(err);
      showMessage('Connection failure. Please try again.');
    } finally {
      hideLoading();
    }
  });

});
