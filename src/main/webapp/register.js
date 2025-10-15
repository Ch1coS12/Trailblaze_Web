document.addEventListener('DOMContentLoaded', function() {
    const registerForm = document.getElementById('register-form');
    const loading = document.getElementById('loading');
    const message = document.getElementById('message');
    const messageText = document.getElementById('message-text');
    const closeMessage = document.getElementById('close-message');
    const BASE_URL = window.location.pathname.includes("Firstwebapp") ? "/Firstwebapp/rest" : "/rest";

    function showLoading() {
        loading.classList.remove('hidden');
    }

    function hideLoading() {
        loading.classList.add('hidden');
    }

    function showMessage(text, type = 'error') {
        messageText.textContent = text;
        message.className = `message ${type}`;
        message.classList.remove('hidden');
        
        setTimeout(() => {
            message.classList.add('hidden');
        }, 5000);
    }

    closeMessage.addEventListener('click', () => {
        message.classList.add('hidden');
    });

    registerForm.addEventListener('submit', async function(e) {
        e.preventDefault();
        
        const formData = new FormData(registerForm);
        const password = formData.get('password');
        const confirmPassword = formData.get('confirmPassword');
        
        // Validação básica
        if (password !== confirmPassword) {
            showMessage('Passwords do not match.');
            return;
        }

        const strongPwd = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^a-zA-Z0-9]).{8,}$/;
        if (!strongPwd.test(password)) {
            showMessage('Password is not strong enough. It must contain at least 8 characters, including at least one uppercase letter, one lowercase letter, one number and one special character.');
            return;
        }

        const registerData = {
            username: formData.get('username'),
            email: formData.get('email'),
            password: password,
            fullName: formData.get('fullName'),
            phone: formData.get('phone') || '',
            address: formData.get('address') || '',
            nif: formData.get('nif') || '',
            cc: formData.get('cc') || '',
            nationality: formData.get('nationality') || '',
            residenceCountry: formData.get('residenceCountry') || '',
            publicProfile: document.getElementById('isPublic').checked
        };

        showLoading();

        try {
			const response = await fetch(`${BASE_URL}/register/civic`, {
			    method: 'POST',
			    headers: {
			        'Content-Type': 'application/json',
			    },
			    body: JSON.stringify(registerData)
			});

            const data = await response.text();

            if (response.ok) {
                showMessage('Account created successfully! ', 'success');
                
                setTimeout(() => {
                    window.location.href = 'login.html';
                }, 2000);
            } else {
                showMessage(data || 'Error creating account. Please try again.');
            }
        } catch (error) {
            console.error('Erro:', error);
            showMessage('Connection error. Please try again.');
        } finally {
            hideLoading();
        }
    });
});