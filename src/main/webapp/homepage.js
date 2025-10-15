// Homepage JavaScript functionality
document.addEventListener('DOMContentLoaded', function() {
    // Header scroll effect
    const header = document.getElementById('header');
    
    window.addEventListener('scroll', function() {
        if (window.scrollY > 100) {
            header.classList.remove('transparent');
        } else {
            header.classList.add('transparent');
        }
    });

    // Scroll reveal animation
    const observerOptions = {
        threshold: 0.1,
        rootMargin: '0px 0px -50px 0px'
    };

    const observer = new IntersectionObserver(function(entries) {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                entry.target.classList.add('revealed');
            }
        });
    }, observerOptions);

    // Observe all scroll-reveal elements
    document.querySelectorAll('.scroll-reveal').forEach(el => {
        observer.observe(el);
    });

    // Smooth scroll for navigation links
    document.querySelectorAll('a[href^="#"]').forEach(anchor => {
        anchor.addEventListener('click', function (e) {
            e.preventDefault();
            const target = document.querySelector(this.getAttribute('href'));
            if (target) {
                target.scrollIntoView({
                    behavior: 'smooth',
                    block: 'start'
                });
            }
        });
    });

    // Parallax effect for hero section
    window.addEventListener('scroll', function() {
        const scrolled = window.pageYOffset;
        const heroSection = document.querySelector('.hero-section');
        if (heroSection) {
            heroSection.style.transform = `translateY(${scrolled * 0.5}px)`;
        }
    });

    // Add stagger animation to feature cards
    const featureCards = document.querySelectorAll('.feature-card');
    featureCards.forEach((card, index) => {
        card.style.animationDelay = `${index * 0.1}s`;
    });
});

// Scroll to features section
function scrollToFeatures() {
    const featuresSection = document.getElementById('features');
    if (featuresSection) {
        featuresSection.scrollIntoView({
            behavior: 'smooth',
            block: 'start'
        });
    }
}

// Redirect to login if user is not authenticated
function redirectToLogin(targetPage) {
    const token = localStorage.getItem('authToken');
    const authType = localStorage.getItem('authType');
    
    if (token && authType === 'jwt') {
        // User is authenticated, redirect to target page
        window.location.href = targetPage;
    } else {
        // User is not authenticated, redirect to login with return URL
        localStorage.setItem('returnUrl', targetPage);
        window.location.href = 'login.html';
    }
}

// Check for return URL after login (to be used in login.js)
function checkReturnUrl() {
    const returnUrl = localStorage.getItem('returnUrl');
    if (returnUrl) {
        localStorage.removeItem('returnUrl');
        return returnUrl;
    }
    return 'userpage.html'; // default redirect
}

// Export for use in other scripts
window.checkReturnUrl = checkReturnUrl;

// Add loading animation for buttons
document.addEventListener('click', function(e) {
    if (e.target.classList.contains('feature-button') || 
        e.target.classList.contains('cta-button') ||
        e.target.classList.contains('cta-primary')) {
        
        const button = e.target;
        const originalText = button.textContent;
        
        button.style.opacity = '0.8';
        button.textContent = 'Loading...';
        
        setTimeout(() => {
            button.style.opacity = '1';
            button.textContent = originalText;
        }, 1000);
    }
});

// Add hover effects for feature cards
document.querySelectorAll('.feature-card').forEach(card => {
    card.addEventListener('mouseenter', function() {
        this.style.transform = 'translateY(-10px) scale(1.02)';
    });
    
    card.addEventListener('mouseleave', function() {
        this.style.transform = 'translateY(0) scale(1)';
    });
});

// Add typing effect to hero title (optional enhancement)
function typeWriter(element, text, speed = 100) {
    let i = 0;
    element.textContent = '';
    
    function type() {
        if (i < text.length) {
            element.textContent += text.charAt(i);
            i++;
            setTimeout(type, speed);
        }
    }
    
    type();
}

// Uncomment to enable typing effect
// window.addEventListener('load', function() {
//     const heroTitle = document.querySelector('.hero-content h1');
//     if (heroTitle) {
//         typeWriter(heroTitle, 'Walk with us', 150);
//     }
// });