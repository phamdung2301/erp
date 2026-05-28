/**
 * e-Rescue Platform — Common JavaScript
 * Toast notifications, AJAX helpers, sidebar toggle
 */

// === Toast Notification ===
function showToast(message, type = 'success') {
    const toastEl = document.getElementById('liveToast');
    const toastMessage = document.getElementById('toastMessage');
    const toastIcon = document.getElementById('toastIcon');

    if (!toastEl || !toastMessage) {
        alert(message);
        return;
    }

    toastMessage.textContent = message;

    // Remove all toast type classes
    toastEl.classList.remove('toast-success', 'toast-error', 'toast-info');

    switch (type) {
        case 'success':
            toastEl.classList.add('toast-success');
            toastIcon.className = 'bi bi-check-circle-fill';
            break;
        case 'error':
            toastEl.classList.add('toast-error');
            toastIcon.className = 'bi bi-exclamation-triangle-fill';
            break;
        case 'info':
            toastEl.classList.add('toast-info');
            toastIcon.className = 'bi bi-info-circle-fill';
            break;
    }

    const toast = new bootstrap.Toast(toastEl, { delay: 4000 });
    toast.show();
}

// === AJAX Helper ===
async function apiCall(url, method = 'GET', body = null) {
    const options = {
        method: method,
        headers: {
            'Accept': 'application/json'
        }
    };

    if (body) {
        options.headers['Content-Type'] = 'application/json';
        options.body = JSON.stringify(body);
    }

    try {
        const response = await fetch(url, options);
        const data = await response.json();

        if (!response.ok || !data.success) {
            throw new Error(data.message || 'Có lỗi xảy ra!');
        }

        return data;
    } catch (error) {
        throw error;
    }
}

async function apiFormCall(url, formData) {
    try {
        const response = await fetch(url, {
            method: 'POST',
            body: formData
        });
        const data = await response.json();

        if (!response.ok || !data.success) {
            throw new Error(data.message || 'Có lỗi xảy ra!');
        }

        return data;
    } catch (error) {
        throw error;
    }
}

// === Sidebar Toggle (Mobile) ===
document.addEventListener('DOMContentLoaded', function() {
    const sidebarToggle = document.getElementById('sidebarToggle');
    const sidebar = document.getElementById('sidebar');

    if (sidebarToggle && sidebar) {
        sidebarToggle.addEventListener('click', function() {
            sidebar.classList.toggle('show');
        });

        // Close sidebar when clicking outside
        document.addEventListener('click', function(e) {
            if (window.innerWidth < 992 &&
                sidebar.classList.contains('show') &&
                !sidebar.contains(e.target) &&
                !sidebarToggle.contains(e.target)) {
                sidebar.classList.remove('show');
            }
        });
    }
});
