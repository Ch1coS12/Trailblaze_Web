document.addEventListener('DOMContentLoaded', init);

// Header scroll effect
const header = document.getElementById('header');
window.addEventListener('scroll', function() {
    if (window.scrollY > 100) {
        header.classList.add('scrolled');
    } else {
        header.classList.remove('scrolled');
    }
});

// Scroll to events function
function scrollToEvents() {
    const eventsMain = document.getElementById('events-main');
    if (eventsMain) {
        eventsMain.scrollIntoView({
            behavior: 'smooth',
            block: 'start'
        });
    }
}



function filterEvents(filter) {
    // This would filter events based on category
    // For now, just reload all events
    const coords = getCurrentLocation();
    loadEvents(coords);
}

function getCurrentLocation() {
    // Return cached location if available
    return window.userLocation || null;
}

async function init(){
  const coords = await getLocation();
  window.userLocation = coords;
  loadEvents(coords);
}

function getLocation(){
  return new Promise(res => {
    if(navigator.geolocation){
      navigator.geolocation.getCurrentPosition(p=>{
        res({lat:p.coords.latitude,lng:p.coords.longitude});
      },()=>res(null));
    } else res(null);
  });
}

async function loadEvents(loc){
  let url = '/rest/events/public';
  if(loc) url += `?lat=${loc.lat}&lng=${loc.lng}`;
  try{
    const r = await fetch(url);
    const list = r.ok ? await r.json() : [];
    updateEventCount(list.length);
    renderEvents(list);
  }catch(e){console.error(e);}
}

function updateEventCount(count) {
    const totalEventsEl = document.getElementById('total-events');
    if (totalEventsEl) {
        animateCounter(totalEventsEl, count);
    }
}

function animateCounter(element, target) {
    let current = 0;
    const increment = target / 30;
    const timer = setInterval(() => {
        current += increment;
        if (current >= target) {
            element.textContent = target;
            clearInterval(timer);
        } else {
            element.textContent = Math.floor(current);
        }
    }, 50);
}

function renderEvents(list){
  const container = document.getElementById('events-list');
  if(!Array.isArray(list) || !list.length){
    container.innerHTML = `
      <div class="empty-state">
        <div class="icon">🎉</div>
        <h3>No Events Available</h3>
        <p>Check back soon for exciting upcoming events in your area!</p>
      </div>`;
    return;
  }
  container.innerHTML = '';
  list.forEach((ev,i)=>{
    const card = document.createElement('div');
    card.className = 'event-card';
    
    // Determine event category based on title/description
    const category = determineEventCategory(ev);
    
    // Format date
    const eventDate = new Date(ev.dateTime);
    const formattedDate = eventDate.toLocaleDateString('en-US', {
      weekday: 'long',
      year: 'numeric',
      month: 'long',
      day: 'numeric'
    });
    const formattedTime = eventDate.toLocaleTimeString('en-US', {
      hour: '2-digit',
      minute: '2-digit'
    });

    card.innerHTML = `
      <div class="event-header">
        <div class="event-date">${formattedDate} at ${formattedTime}</div>
        <h3 class="event-title">${ev.title}</h3>
        <div class="event-location">${formatLocation(ev.location)}</div>
      </div>
      <div class="event-body">
        <p class="event-description">${ev.description || 'Join us for an exciting territorial management event!'}</p>
        <div class="event-map" id="map-${i}"></div>
        <div class="event-footer">
          <span class="event-category">${category}</span>
          <button class="register-btn" onclick="location.href='login.html'">Join Event</button>
        </div>
      </div>
    `;

    container.appendChild(card);

    // Add stagger animation delay
    card.style.animationDelay = `${i * 0.1}s`;
    
    drawMap(`map-${i}`, ev.polygons);
  });
  
  // Add scroll reveal animation
  observeElements();
}

function determineEventCategory(event) {
    const title = event.title.toLowerCase();
    const desc = (event.description || '').toLowerCase();
    
    if (title.includes('workshop') || desc.includes('workshop')) return 'Workshop';
    if (title.includes('field') || desc.includes('field')) return 'Field Activity';
    if (title.includes('community') || desc.includes('community')) return 'Community';
    if (title.includes('training') || desc.includes('training')) return 'Training';
    
    return 'General';
}

function formatLocation(location) {
    // If location is coordinates, format them nicely
    if (location && location.includes(',')) {
        const [lat, lng] = location.split(',').map(coord => parseFloat(coord.trim()));
        if (!isNaN(lat) && !isNaN(lng)) {
            return `${lat.toFixed(4)}°, ${lng.toFixed(4)}°`;
        }
    }
    return location || 'Location TBD';
}

// Scroll reveal animation
function observeElements() {
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

    document.querySelectorAll('.scroll-reveal').forEach(el => {
        observer.observe(el);
    });
}

async function drawMap(elId, polygons) {
  // 1) componentes do mapa
  const { Map, Polygon } = await google.maps.importLibrary('maps');

  // 2) utilitários de coordenadas (inclui LatLngBounds)
  const { LatLngBounds } = await google.maps.importLibrary('core');

  const map = new Map(document.getElementById(elId), {
    center: { lat: 38.7223, lng: -9.1393 },
    zoom: 8,
    gestureHandling: 'greedy',
    styles: [
      {
        featureType: 'all',
        elementType: 'geometry.fill',
        stylers: [{ color: '#f8f9fa' }]
      },
      {
        featureType: 'water',
        elementType: 'geometry.fill',
        stylers: [{ color: '#7f9e8e' }]
      },
      {
        featureType: 'landscape',
        elementType: 'geometry.fill',
        stylers: [{ color: '#EDE8DA' }]
      }
    ]
  });

  const bounds = new LatLngBounds();

  polygons.forEach(g => addGeometry(map, g, bounds, Polygon));

  // Ajusta o zoom/enquadramento apenas se existirem geometrias
  if (!bounds.isEmpty()) map.fitBounds(bounds);
}

function addGeometry(map, geometry, bounds, Polygon){
  if(!geometry) return;
  if(typeof geometry==='string'){
    try{ geometry = JSON.parse(geometry);}catch{return;}
  }
  if(!proj4.defs['EPSG:3763']){
    proj4.defs('EPSG:3763','+proj=tmerc +lat_0=39.6682583333333 +lon_0=-8.13310833333333 +k=1 +x_0=0 +y_0=0 +ellps=GRS80 +units=m +no_defs');
  }
  const conv = proj4('EPSG:3763','WGS84');
  const parts = geometry.type==='Polygon'? [geometry.coordinates]: geometry.coordinates;
  parts.forEach(rings=>{
    const path = rings[0].map(([x,y])=>{const [lon,lat]=conv.forward([x,y]);const p={lat,lng:lon};bounds.extend(p);return p;});
    new Polygon({
      paths: path,
      map: map,
      strokeColor: '#4f695B',
      strokeOpacity: 0.9,
      strokeWeight: 2,
      fillColor: '#4f695B',
      fillOpacity: 0.35
    });
  });
}

// Add loading animation for page
window.addEventListener('load', function() {
    document.body.style.opacity = '0';
    setTimeout(() => {
        document.body.style.transition = 'opacity 1s ease-in-out';
        document.body.style.opacity = '1';
    }, 100);
});

// Add parallax effect to hero section
window.addEventListener('scroll', function() {
    const scrolled = window.pageYOffset;
    const heroSection = document.querySelector('.hero-section');
    if (heroSection) {
        heroSection.style.transform = `translateY(${scrolled * 0.5}px)`;
    }
});