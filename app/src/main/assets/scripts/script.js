const safetyDialog = new mdc.dialog.MDCDialog(document.querySelector('#safetyDialog'));
  const videoDialog = new mdc.dialog.MDCDialog(document.querySelector('#videoDialog'));
  const favoritesDialog = new mdc.dialog.MDCDialog(document.querySelector('#favoritesDialog'));

  // Show safety dialog only once - using localStorage for persistence
  const safetyShown = localStorage.getItem('safetyShown');
  if (!safetyShown) {
    safetyDialog.open();
    safetyDialog.listen('MDCDialog:closing', () => localStorage.setItem('safetyShown', 'true'));
  }

  let fireworks = [];
  let filtered = [];
  let currentPage = 1;
  const perPage = 10;

  // File handling
  const fileInput = document.getElementById('jsonFile');
  const dropZone = document.getElementById('fileDropZone');

  fileInput.addEventListener('change', handleFileSelect);

  // Drag and drop functionality
  dropZone.addEventListener('dragover', (e) => {
    e.preventDefault();
    dropZone.classList.add('drag-over');
  });

  dropZone.addEventListener('dragleave', () => {
    dropZone.classList.remove('drag-over');
  });

  dropZone.addEventListener('drop', (e) => {
    e.preventDefault();
    dropZone.classList.remove('drag-over');
    const files = e.dataTransfer.files;
    if (files.length > 0) {
      handleFile(files[0]);
    }
  });

  function handleFileSelect(event) {
    const file = event.target.files[0];
    if (file) {
      handleFile(file);
    }
  }

  function handleFile(file) {
    if (!file.name.toLowerCase().endsWith('.json')) {
      showError('Please select a JSON file.');
      return;
    }

    const reader = new FileReader();
    reader.onload = function(e) {
      try {
        fireworks = JSON.parse(e.target.result);
        initializeCatalog();
      } catch (error) {
        showError('Error parsing JSON file: ' + error.message);
      }
    };
    reader.onerror = function() {
      showError('Error reading file.');
    };
    reader.readAsText(file);
  }

  // Load default data from fireworks.js
  function loadDefaultData() {
    const script = document.createElement('script');
    script.src = 'scripts/fireworks.js';
    script.onload = function() {
      // The script should call window.loadFireworksData
      if (!fireworks.length) {
        showError('Default data not available. Please ensure fireworks.js exists and contains valid data.');
      }
    };
    script.onerror = function() {
      showError('Error loading default fireworks data. Please ensure fireworks.js exists.');
    };
    document.head.appendChild(script);
  }

  // Global function called by the data script
  window.loadFireworksData = function(data) {
    fireworks = data;
    initializeCatalog();
  };

  function initializeCatalog() {
    showFilterControls();
    populateFilters();
    applyFilters();
  }

  function showFilterControls() {
    document.getElementById('favoritesBtn').style.display = 'inline-block';
    document.getElementById('filterControls').style.display = 'flex';
  }

  function showError(message) {
    const container = document.getElementById('results');
    container.innerHTML = `<div class="error">${message}</div>`;
  }

  function populateFilters() {
    const brands = [...new Set(fireworks.map(f => f.Brand))];
    const types = [...new Set(fireworks.map(f => f.Category))];
    const brandFilter = document.getElementById('brandFilter');
    const typeFilter = document.getElementById('typeFilter');
    brandFilter.innerHTML = '<option value="">All Brands</option>' + brands.map(b => `<option value="${b}">${b}</option>`).join('');
    typeFilter.innerHTML = '<option value="">All Types</option>' + types.map(t => `<option value="${t}">${t}</option>`).join('');
  }

  function applyFilters() {
    const query = document.getElementById('searchQuery').value.toLowerCase();
    const brand = document.getElementById('brandFilter').value;
    const type = document.getElementById('typeFilter').value;
    const minShots = parseInt(document.getElementById('minShots').value || '0');
    const duration = parseInt(document.getElementById('durationFilter').value || '');
    filtered = fireworks.filter(f =>
      (!brand || f.Brand === brand) &&
      (!type || f.Category === type) &&
      (!isNaN(duration) ? parseInt(f.Duration) === duration : true) &&
      (f.Shots >= minShots) &&
      (!query || f.Name.toLowerCase().includes(query))
    );
    currentPage = 1;
    renderResults();
  }

  function renderResults() {
    const container = document.getElementById('results');
    container.innerHTML = '';
    const start = (currentPage - 1) * perPage;
    const paginated = filtered.slice(start, start + perPage);
    for (const item of paginated) {
      const card = document.createElement('div');
      card.className = 'mdc-layout-grid__cell mdc-layout-grid__cell--span-4';
      card.innerHTML = `
        <div class="mdc-card">
          <img src="${item.imgurl}" alt="${item.Name}" onerror="this.style.display='none'">
          <div class="mdc-card__primary-action" tabindex="0">
            <div class="mdc-card__ripple"></div>
            <div class="mdc-typography mdc-typography--headline6" style="padding: 0.5rem">${item.Name}</div>
            <div class="mdc-typography mdc-typography--body2" style="padding: 0 0.5rem">${item.Performance || 'No description'}</div>
            <div class="mdc-typography mdc-typography--body2" style="padding: 0 0.5rem 0.5rem"><strong>Shots:</strong> ${item.Shots}</div>
            <div class="mdc-typography mdc-typography--body2" style="padding: 0 0.5rem 0.5rem"><strong>Duration:</strong> ${item.Duration}s</div>
          </div>
          ${item.demo ? `<button class="mdc-button mdc-button--outlined" onclick='showVideo("${item.demo}")'><span class="mdc-button__label">Watch Demo</span></button>` : ''}
          <button class="mdc-icon-button" aria-label="Add to favorites" onclick='toggleFavorite(${item.ID})'>
            <i class="material-icons ${isFavorite(item.ID) ? 'favorite' : ''}">star</i>
          </button>
        </div>`;
      container.appendChild(card);
    }
    renderPagination();
  }

  function renderPagination() {
    const total = Math.ceil(filtered.length / perPage);
    const container = document.getElementById('pagination');
    container.innerHTML = '';
    
    if (total <= 1) return;

    // Smart pagination for mobile
    const isMobile = window.innerWidth <= 768;
    const maxVisible = isMobile ? 5 : 7;
    
    // Always show first page
    if (currentPage > 1) {
      const btn = createPageButton(1);
      container.appendChild(btn);
    }
    
    // Show ellipsis if there's a gap
    if (currentPage > 3 && !isMobile) {
      const ellipsis = document.createElement('button');
      ellipsis.textContent = '...';
      ellipsis.className = 'ellipsis';
      container.appendChild(ellipsis);
    }
    
    // Show pages around current page
    const start = Math.max(1, currentPage - Math.floor(maxVisible / 2));
    const end = Math.min(total, start + maxVisible - 1);
    
    for (let i = start; i <= end; i++) {
      const btn = createPageButton(i);
      if (i === currentPage) {
        btn.className += ' active';
      }
      container.appendChild(btn);
    }
    
    // Show ellipsis if there's a gap
    if (end < total - 1 && !isMobile) {
      const ellipsis = document.createElement('button');
      ellipsis.textContent = '...';
      ellipsis.className = 'ellipsis';
      container.appendChild(ellipsis);
    }
    
    // Always show last page
    if (currentPage < total && end < total) {
      const btn = createPageButton(total);
      container.appendChild(btn);
    }
    
    // Previous/Next buttons for mobile
    if (isMobile) {
      if (currentPage > 1) {
        const prevBtn = document.createElement('button');
        prevBtn.textContent = '‹';
        prevBtn.onclick = () => gotoPage(currentPage - 1);
        container.insertBefore(prevBtn, container.firstChild);
      }
      
      if (currentPage < total) {
        const nextBtn = document.createElement('button');
        nextBtn.textContent = '›';
        nextBtn.onclick = () => gotoPage(currentPage + 1);
        container.appendChild(nextBtn);
      }
    }
  }

  function createPageButton(pageNum) {
    const btn = document.createElement('button');
    btn.textContent = pageNum;
    btn.onclick = () => gotoPage(pageNum);
    return btn;
  }

  function gotoPage(p) {
    currentPage = p;
    renderResults();
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  function showVideo(url) {
    const iframe = document.getElementById('videoFrame');
    iframe.src = url.includes('youtube.com') ? url : '';
    videoDialog.open();
  }

  function toggleFavorite(id) {
    let favs = JSON.parse(localStorage.getItem('favorites') || '[]');
    if (favs.includes(id)) {
      favs = favs.filter(f => f !== id);
    } else {
      favs.push(id);
    }
    localStorage.setItem('favorites', JSON.stringify(favs));
    renderResults();
  }

  function isFavorite(id) {
    const favs = JSON.parse(localStorage.getItem('favorites') || '[]');
    return favs.includes(id);
  }

  function toggleFavoriteAndRefreshFavorites(id) {
    toggleFavorite(id);
    showFavorites();
  }

  function showFavorites() {
    const favIds = JSON.parse(localStorage.getItem('favorites') || '[]');
    const favItems = fireworks.filter(f => favIds.includes(f.ID));
    const container = document.getElementById('favoritesContent');

    if (favItems.length === 0) {
      container.innerHTML = '<p>No favorites selected.</p>';
    } else {
      container.innerHTML = favItems.map(f => `
        <div style="margin-bottom: 1rem; padding: 0.5rem; border: 1px solid #ddd; border-radius: 4px;">
          <strong>${f.Name}</strong><br>
          <small>${f.Brand} - ${f.Shots} shots - ${f.Duration}s</small><br>
          <button class="mdc-button mdc-button--outlined" onclick="toggleFavoriteAndRefreshFavorites(${f.ID})" style="margin-top: 0.5rem;">
            <span class="mdc-button__label">Remove</span>
          </button>
        </div>
      `).join('');
    }
    favoritesDialog.open();
  }

  function exportFavoritesToCSV() {
    const favIds = JSON.parse(localStorage.getItem('favorites') || '[]');
    const favItems = fireworks.filter(f => favIds.includes(f.ID));

    if (favItems.length === 0) {
      alert('No favorites to export!');
      return;
    }

    const headers = ['Name', 'Brand', 'Category', 'Shots', 'Duration', 'Price'];
    const csvRows = [headers.join(',')];

    for (const item of favItems) {
      const values = [
        item.Name,
        item.Brand,
        item.Category,
        item.Shots,
        item.Duration,
        item.Price || 'N/A'
      ].map(value => `"${value}"`);
      csvRows.push(values.join(','));
    }

    const csvContent = csvRows.join('\n');
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.setAttribute('href', url);
    link.setAttribute('download', 'firework_favorites.csv');
    link.style.visibility = 'hidden';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  }

  // Handle window resize for responsive pagination
  window.addEventListener('resize', () => {
    if (filtered.length > 0) {
      renderPagination();
    }
  });
