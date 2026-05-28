// customer.js - Xử lý bản đồ Leaflet và luồng nghiệp vụ Cứu hộ phía khách hàng

let map;
let customerMarker;
let rescuerMarkers = {};
let activeRouteLine;
let activeRequestPollInterval;
let activeRequestId = null;
let customerPhone = null;
let chatInitialized = false;

// Tọa độ mặc định (Hà Nội)
const DEFAULT_LAT = 21.0285;
const DEFAULT_LNG = 105.8542;
let webSocket = null;

function connectWebSocket() {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = `${protocol}//${window.location.host}/ws`;
    
    console.log("Connecting to WebSocket:", wsUrl);
    webSocket = new WebSocket(wsUrl);
    
    webSocket.onopen = function() {
        console.log("WebSocket connected successfully.");
    };
    
    webSocket.onmessage = function(event) {
        try {
            const msg = JSON.parse(event.data);
            console.log("WebSocket message received:", msg);
            if (msg.type === 'REQUEST_UPDATE') {
                pollRequestStatus();
            } else if (msg.type === 'LOCATION_UPDATE') {
                if (msg.lat && msg.lng) {
                    updateRescuerOnMap(msg.lat, msg.lng, msg.rescuerName || "Thợ cứu hộ");
                }
            } else if (msg.type === 'CHAT_MESSAGE') {
                if (msg.requestId === activeRequestId) {
                    appendChatMessage(msg);
                    scrollToBottom();
                }
            }
        } catch (e) {
            console.error("Error parsing WebSocket message:", e);
        }
    };
    
    webSocket.onclose = function() {
        console.log("WebSocket connection closed. Reconnecting in 5 seconds...");
        setTimeout(connectWebSocket, 5000);
    };
}

document.addEventListener('DOMContentLoaded', function() {
    initMap();
    checkActiveRequest();
    setupEventHandlers();
    connectWebSocket();
});

// 1. KHỞI TẠO BẢN ĐỒ
function initMap() {
    // Khởi tạo bản đồ
    map = L.map('map').setView([DEFAULT_LAT, DEFAULT_LNG], 14);

    // Sử dụng tile Dark Mode của CartoDB (đẹp hơn so với OSM gốc, hợp tông màu tối của web)
    L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors &copy; <a href="https://carto.com/attributions">CARTO</a>',
        subdomains: 'abcd',
        maxZoom: 20
    }).addTo(map);

    // Marker đại diện cho Khách hàng
    const customerIcon = L.icon({
        iconUrl: 'https://cdn-icons-png.flaticon.com/512/854/854866.png', // Icon định vị màu đỏ
        iconSize: [38, 38],
        iconAnchor: [19, 38],
        popupAnchor: [0, -38]
    });

    customerMarker = L.marker([DEFAULT_LAT, DEFAULT_LNG], {
        icon: customerIcon,
        draggable: true
    }).addTo(map);

    customerMarker.bindPopup("Vị trí sự cố của bạn").openPopup();

    // Thiết lập tọa độ mặc định vào form
    updateCoordinates(DEFAULT_LAT, DEFAULT_LNG);
    reverseGeocode(DEFAULT_LAT, DEFAULT_LNG);

    // Lắng nghe sự kiện kéo thả marker của khách hàng
    customerMarker.on('dragend', function(e) {
        const position = customerMarker.getLatLng();
        updateCoordinates(position.lat, position.lng);
        reverseGeocode(position.lat, position.lng);
    });

    // Lắng nghe sự kiện click trên bản đồ để chọn vị trí mới
    map.on('click', function(e) {
        // Chỉ cho phép thay đổi tọa độ nếu đang ở màn hình khởi tạo
        if (document.getElementById('panel-request-init').classList.contains('d-none')) {
            return;
        }
        const lat = e.latlng.lat;
        const lng = e.latlng.lng;
        customerMarker.setLatLng([lat, lng]);
        updateCoordinates(lat, lng);
        reverseGeocode(lat, lng);
    });

    // Cố gắng định vị bằng GPS thực tế của trình duyệt
    tryRelocate();
}

function updateCoordinates(lat, lng) {
    document.getElementById('input-lat').value = parseFloat(lat).toFixed(6);
    document.getElementById('input-lng').value = parseFloat(lng).toFixed(6);
}

// Gọi API Nominatim để dịch tọa độ ra địa chỉ chữ thực tế
function reverseGeocode(lat, lng) {
    document.getElementById('input-address').value = "Đang xác định địa chỉ...";
    fetch(`https://nominatim.openstreetmap.org/reverse?format=json&lat=${lat}&lon=${lng}&zoom=18&addressdetails=1`, {
        headers: {
            'Accept-Language': 'vi,en;q=0.9'
        }
    })
    .then(response => response.json())
    .then(data => {
        if (data && data.display_name) {
            document.getElementById('input-address').value = data.display_name;
        } else {
            document.getElementById('input-address').value = `Tọa độ: ${parseFloat(lat).toFixed(6)}, ${parseFloat(lng).toFixed(6)}`;
        }
    })
    .catch(err => {
        document.getElementById('input-address').value = `Tọa độ: ${parseFloat(lat).toFixed(6)}, ${parseFloat(lng).toFixed(6)}`;
    });
}

function tryRelocate() {
    if (navigator.geolocation) {
        navigator.geolocation.getCurrentPosition(
            function(position) {
                const lat = position.coords.latitude;
                const lng = position.coords.longitude;
                map.setView([lat, lng], 15);
                customerMarker.setLatLng([lat, lng]);
                updateCoordinates(lat, lng);
                reverseGeocode(lat, lng);
                loadNearbyRescuers(lat, lng);
            },
            function(error) {
                console.log("GPS geolocation failed, using default center.");
                loadNearbyRescuers(DEFAULT_LAT, DEFAULT_LNG);
            }
        );
    } else {
        loadNearbyRescuers(DEFAULT_LAT, DEFAULT_LNG);
    }
}

// 2. TẢI THỢ CỨU HỘ XUNG QUANH
function loadNearbyRescuers(lat, lng) {
    // Xóa marker cũ
    Object.values(rescuerMarkers).forEach(marker => map.removeLayer(marker));
    rescuerMarkers = {};

    fetch(`/api/customer/nearby-rescuers?lat=${lat}&lng=${lng}`)
    .then(res => res.json())
    .then(res => {
        if (res.success && res.data) {
            const rescuerIcon = L.icon({
                iconUrl: 'https://cdn-icons-png.flaticon.com/512/3246/3246813.png', // Icon thợ/xe sửa chữa màu xanh lá
                iconSize: [34, 34],
                iconAnchor: [17, 34],
                popupAnchor: [0, -34]
            });

            res.data.forEach(rescuer => {
                const marker = L.marker([rescuer.lat, rescuer.lng], {
                    icon: rescuerIcon
                }).addTo(map);

                marker.bindPopup(`
                    <div style="color:#fff;">
                        <strong>${rescuer.fullName}</strong><br/>
                        <span style="font-size:0.8rem;color:#ccc;">${rescuer.specialty}</span><br/>
                        <span style="font-size:0.8rem;color:#00e5ff;">SĐT: ${rescuer.phone}</span>
                    </div>
                `);
                
                rescuerMarkers[rescuer.rescuerId] = marker;
            });
        }
    });
}

// 3. THIẾT LẬP CÁC SỰ KIỆN NÚT BẤM
function setupEventHandlers() {
    // Nút định vị lại
    document.getElementById('btn-relocate').addEventListener('click', tryRelocate);

    // Kích hoạt nút Gửi khi người dùng nhập mô tả sự cố
    document.getElementById('input-desc').addEventListener('input', function() {
        const btn = document.getElementById('btn-submit-request');
        if (this.value.trim().length > 0) {
            btn.removeAttribute('disabled');
        } else {
            btn.setAttribute('disabled', 'true');
        }
    });

    // Nút chẩn đoán AI
    document.getElementById('btn-ai-diagnose').addEventListener('click', function() {
        const desc = document.getElementById('input-desc').value;
        if (!desc.trim()) {
            showToast("Vui lòng nhập mô tả sự cố trước khi chạy AI chẩn đoán!", "error");
            return;
        }

        const btn = document.getElementById('btn-ai-diagnose');
        btn.disabled = true;
        btn.innerHTML = `<span class="spinner-border spinner-border-sm me-2" role="status"></span>AI đang phân tích...`;

        fetch(`/api/customer/diagnose?issueDesc=${encodeURIComponent(desc)}`, {
            method: 'POST'
        })
        .then(res => res.json())
        .then(res => {
            btn.disabled = false;
            btn.innerHTML = `<i class="bi bi-robot me-2"></i>Chẩn đoán sự cố bằng AI`;

            if (res.success && res.data) {
                const diag = res.data;
                document.getElementById('ai-issue-name').innerText = diag.issueName;
                document.getElementById('ai-recommendation').innerText = diag.recommendation;
                document.getElementById('ai-est-price').innerText = formatVND(diag.estimatedBasePrice);

                // Set severity badge
                const badge = document.getElementById('ai-severity-badge');
                badge.innerText = diag.severity;
                badge.className = "badge";
                if (diag.severity === "Thấp") {
                    badge.classList.add("bg-success-soft", "text-success");
                } else if (diag.severity === "Trung bình") {
                    badge.classList.add("bg-warning-soft", "text-warning");
                } else {
                    badge.classList.add("bg-danger-soft", "text-danger");
                }

                document.getElementById('ai-diag-result').classList.remove('d-none');
                document.getElementById('btn-submit-request').removeAttribute('disabled');
                showToast("AI đã chẩn đoán xong!", "success");
            }
        })
        .catch(err => {
            btn.disabled = false;
            btn.innerHTML = `<i class="bi bi-robot me-2"></i>Chẩn đoán sự cố bằng AI`;
            showToast("Có lỗi xảy ra khi gọi AI chẩn đoán!", "error");
        });
    });

    // Nút gửi yêu cầu
    document.getElementById('btn-submit-request').addEventListener('click', function() {
        const desc = document.getElementById('input-desc').value;
        const address = document.getElementById('input-address').value;
        const lat = document.getElementById('input-lat').value;
        const lng = document.getElementById('input-lng').value;

        if (!lat || !lng || !address || address === "Đang xác định địa chỉ...") {
            showToast("Vui lòng chọn vị trí sự cố hợp lệ trên bản đồ!", "error");
            return;
        }

        const formData = new FormData();
        formData.append("issueDesc", desc);
        formData.append("lat", lat);
        formData.append("lng", lng);
        formData.append("address", address);

        fetch('/api/customer/request', {
            method: 'POST',
            body: formData
        })
        .then(res => res.json())
        .then(res => {
            if (res.success && res.data) {
                showToast("Gửi yêu cầu thành công!", "success");
                activeRequestId = res.data.requestId;
                startPollingRequest();
            } else {
                showToast(res.message, "error");
            }
        });
    });

    // Nút hủy yêu cầu (Chờ thợ)
    document.getElementById('btn-cancel-pending').addEventListener('click', function() {
        if (confirm("Bạn có chắc chắn muốn hủy yêu cầu cứu hộ này không?")) {
            cancelActiveRequest();
        }
    });

    // Nút hủy yêu cầu (Đang di chuyển)
    document.getElementById('btn-cancel-active').addEventListener('click', function() {
        if (confirm("Thợ đã nhận cuốc xe. Hủy lúc này có thể tính phí. Bạn vẫn muốn hủy?")) {
            cancelActiveRequest();
        }
    });

    // Đánh giá sao
    const stars = document.querySelectorAll('#star-rating i');
    stars.forEach(star => {
        star.addEventListener('mouseover', function() {
            const val = parseInt(this.getAttribute('data-value'));
            highlightStars(val);
        });

        star.addEventListener('mouseout', function() {
            const currentVal = parseInt(document.getElementById('input-rating-value').value);
            highlightStars(currentVal);
        });

        star.addEventListener('click', function() {
            const val = parseInt(this.getAttribute('data-value'));
            document.getElementById('input-rating-value').value = val;
            highlightStars(val);
        });
    });

    // Lắng nghe thay đổi phương thức thanh toán trên panel active
    const activeSelectPayment = document.getElementById('active-select-payment-method');
    if (activeSelectPayment) {
        activeSelectPayment.addEventListener('change', function() {
            const method = this.value;
            const container = document.getElementById('active-admin-qr-container');
            if (method === 'BANK_TRANSFER') {
                container.classList.remove('d-none');
                updateActiveAdminQrCode();
            } else if (method === 'CASH') {
                container.classList.add('d-none');
                saveActivePaymentMethod('CASH');
            } else {
                container.classList.add('d-none');
            }
        });
    }

    // Nút xác nhận chuyển khoản trên panel active
    const btnConfirmTransfer = document.getElementById('btn-confirm-transfer');
    if (btnConfirmTransfer) {
        btnConfirmTransfer.addEventListener('click', function() {
            saveActivePaymentMethod('BANK_TRANSFER');
        });
    }

    // Gửi thanh toán và đánh giá
    document.getElementById('btn-submit-payment').addEventListener('click', function() {
        const method = document.getElementById('select-payment-method').value;
        const rating = document.getElementById('input-rating-value').value;
        const feedback = document.getElementById('input-feedback').value;

        const formData = new FormData();
        formData.append("paymentMethod", method);
        formData.append("rating", rating);
        formData.append("feedback", feedback);

        fetch(`/api/customer/request/${activeRequestId}/pay-rate`, {
            method: 'POST',
            body: formData
        })
        .then(res => res.json())
        .then(res => {
            if (res.success) {
                showToast("Giao dịch hoàn tất! Cảm ơn bạn đã sử dụng dịch vụ.", "success");
                // Reset form
                resetDashboard();
            } else {
                showToast(res.message, "error");
            }
        });
    });
}

function highlightStars(val) {
    const stars = document.querySelectorAll('#star-rating i');
    stars.forEach(star => {
        const starVal = parseInt(star.getAttribute('data-value'));
        if (starVal <= val) {
            star.classList.add('selected');
        } else {
            star.classList.remove('selected');
        }
    });
}

// 4. KIỂM TRA ĐĂNG KÝ HOẠT ĐỘNG BAN ĐẦU
function checkActiveRequest() {
    fetch('/api/customer/request/active')
    .then(res => res.json())
    .then(res => {
        if (res.success && res.data) {
            activeRequestId = res.data.requestId;
            startPollingRequest();
        } else {
            showPanel('init');
        }
    });
}

// 5. VÒNG LẶP POLLING THEO DÕI HÀNH TRÌNH
function startPollingRequest() {
    if (activeRequestPollInterval) clearInterval(activeRequestPollInterval);
    
    pollRequestStatus();
    // Tăng chu kỳ polling thành 30s đóng vai trò là heartbeat fallback khi WebSocket bị gián đoạn
    activeRequestPollInterval = setInterval(pollRequestStatus, 30000);
}

function pollRequestStatus() {
    if (!activeRequestId) return;

    fetch('/api/customer/request/active')
    .then(res => res.json())
    .then(res => {
        if (!res.success || !res.data) {
            // Không tìm thấy cuốc hoạt động, reset về màn hình khởi tạo
            resetDashboard();
            return;
        }

        const req = res.data;
        const status = req.status;

        if (status === 'PENDING') {
            showPanel('pending');
            document.getElementById('pending-info-desc').innerText = req.issueDesc;
            document.getElementById('pending-info-price').innerText = "AI dự tính: " + formatVND(req.basePrice);
        } 
        else if (status === 'ACCEPTED' || status === 'ARRIVED' || status === 'IN_PROGRESS') {
            showPanel('active');
            updateStepper(status);
            
            // Khởi tạo Chat nếu chưa khởi tạo
            customerPhone = req.customerPhone;
            if (!chatInitialized) {
                setupChat();
                loadChatHistory(activeRequestId);
                chatInitialized = true;
            }

            // Hiển thị ETA & Lộ trình đường bộ uốn lượn di chuyển
            if (req.etaMinutes && req.roadDistance) {
                const widget = document.getElementById('eta-floating-widget');
                if (widget) {
                    widget.classList.remove('d-none');
                    document.getElementById('eta-countdown-text').innerText = req.etaMinutes + " phút";
                    document.getElementById('eta-distance-text').innerText = "Khoảng cách: " + parseFloat(req.roadDistance).toFixed(1) + " km";
                    startEtaCountdown(req.etaMinutes, req.roadDistance);
                }
            } else {
                const widget = document.getElementById('eta-floating-widget');
                if (widget) widget.classList.add('d-none');
            }
            
            // Điền mã OTP
            document.getElementById('active-otp-code').innerText = req.otpCode;

            // Xử lý ẩn/hiện OTP và lựa chọn phương thức thanh toán
            const activeSelector = document.getElementById('active-payment-selector-container');
            const activeOtpBox = document.getElementById('active-otp-container');
            
            if (activeSelector && activeOtpBox) {
                if (req.paymentMethod) {
                    activeSelector.classList.add('d-none');
                    activeOtpBox.classList.remove('d-none');
                } else {
                    activeSelector.classList.remove('d-none');
                    activeOtpBox.classList.add('d-none');
                    
                    // Lưu lại tổng số tiền và ID cuốc xe để sinh mã QR
                    const activeTotalAmount = req.basePrice + (req.extraPrice || 0);
                    window.activeRequestTotalAmount = activeTotalAmount;
                    window.activeRequestCode = req.requestId;
                    
                    // Cập nhật QR Code nếu dropdown đang chọn BANK_TRANSFER
                    const activeSelectPaymentEl = document.getElementById('active-select-payment-method');
                    if (activeSelectPaymentEl && activeSelectPaymentEl.value === 'BANK_TRANSFER') {
                        updateActiveAdminQrCode();
                    }
                }
            }

            // Hiển thị thông tin thợ cứu hộ
            if (req.rescuer) {
                document.getElementById('active-rescuer-name').innerText = req.rescuer.fullName;
                document.getElementById('active-rescuer-specialty').innerText = req.rescuer.specialty || "Chuyên viên cứu hộ";
                document.getElementById('active-rescuer-phone').href = "tel:" + req.rescuer.phone;
                
                if (req.rescuer.avatar) {
                    document.getElementById('active-rescuer-avatar').src = req.rescuer.avatar;
                }

                // Cập nhật vị trí thợ cứu hộ trên bản đồ
                if (req.rescuer.lat && req.rescuer.lng) {
                    updateRescuerOnMap(req.rescuer.lat, req.rescuer.lng, req.rescuer.fullName);
                }
            }

            // Cho phép hủy chỉ khi ở trạng thái ACCEPTED
            const cancelBtn = document.getElementById('btn-cancel-active');
            if (status === 'ACCEPTED') {
                cancelBtn.removeAttribute('disabled');
            } else {
                cancelBtn.setAttribute('disabled', 'true');
            }
        } 
        else if (status === 'COMPLETED') {
            clearInterval(activeRequestPollInterval);
            showPanel('complete');
            document.getElementById('complete-total-amount').innerText = formatVND(req.totalAmount);
            window.activeRequestTotalAmount = req.totalAmount;
            window.activeRequestCode = req.requestId;

            const pmTextEl = document.getElementById('complete-payment-method-text');
            if (pmTextEl) {
                pmTextEl.innerText = req.paymentMethod === 'BANK_TRANSFER' ? 'Chuyển khoản ngân hàng (Đã xác nhận)' : 'Tiền mặt (Đã xác nhận)';
            }
            const pmInput = document.getElementById('select-payment-method');
            if (pmInput) {
                pmInput.value = req.paymentMethod || 'CASH';
            }
        }
        else if (status === 'CANCELED') {
            resetDashboard();
            showToast("Cuốc cứu hộ đã bị hủy.", "info");
        }
    })
    .catch(err => {
        console.error("Polling error: ", err);
    });
}

function updateStepper(status) {
    const stepAccept = document.getElementById('step-accept');
    const stepArrive = document.getElementById('step-arrive');
    const stepRepair = document.getElementById('step-repair');
    const progressBar = document.getElementById('active-progress-bar');

    // Reset
    stepAccept.className = "step-item";
    stepArrive.className = "step-item";
    stepRepair.className = "step-item";

    if (status === 'ACCEPTED') {
        stepAccept.classList.add('active');
        progressBar.style.width = '0%';
    } else if (status === 'ARRIVED') {
        stepAccept.classList.add('completed');
        stepArrive.classList.add('active');
        progressBar.style.width = '50%';
    } else if (status === 'IN_PROGRESS') {
        stepAccept.classList.add('completed');
        stepArrive.classList.add('completed');
        stepRepair.classList.add('active');
        progressBar.style.width = '100%';
    }
}

function updateRescuerOnMap(lat, lng, name) {
    const rescuerLat = parseFloat(lat);
    const rescuerLng = parseFloat(lng);

    // Cập nhật marker đại diện thợ
    const activeRescuerIcon = L.icon({
        iconUrl: 'https://cdn-icons-png.flaticon.com/512/3246/3246813.png',
        iconSize: [40, 40],
        iconAnchor: [20, 40]
    });

    if (rescuerMarkers['active_trip']) {
        rescuerMarkers['active_trip'].setLatLng([rescuerLat, rescuerLng]);
    } else {
        // Xóa hết các marker thợ tĩnh khác trước đó
        Object.keys(rescuerMarkers).forEach(key => {
            map.removeLayer(rescuerMarkers[key]);
            delete rescuerMarkers[key];
        });

        rescuerMarkers['active_trip'] = L.marker([rescuerLat, rescuerLng], {
            icon: activeRescuerIcon
        }).addTo(map);
        rescuerMarkers['active_trip'].bindPopup(`Thợ ${name} đang di chuyển`).openPopup();
    }

    // Vẽ đường đi uốn lượn đường bộ thực tế (Bezier) nối thợ cứu hộ và khách hàng
    const customerLatLng = customerMarker.getLatLng();
    const curvedPath = generateCurvedPath([rescuerLat, rescuerLng], [customerLatLng.lat, customerLatLng.lng]);

    if (activeRouteLine) {
        activeRouteLine.setLatLngs(curvedPath);
    } else {
        activeRouteLine = L.polyline(curvedPath, {
            color: '#00e5ff',
            weight: 4,
            dashArray: '5, 10',
            opacity: 0.8
        }).addTo(map);
    }

    // Tự động fit bounds hiển thị cả 2 marker
    const group = new L.featureGroup([customerMarker, rescuerMarkers['active_trip']]);
    map.fitBounds(group.getBounds().pad(0.15));
}

// 6. CÁC HÀM TIỆN ÍCH DỌN DẸP / CHUYỂN TRẠNG THÁI PANEL
function showPanel(panelName) {
    document.getElementById('panel-request-init').classList.add('d-none');
    document.getElementById('panel-request-pending').classList.add('d-none');
    document.getElementById('panel-request-active').classList.add('d-none');
    document.getElementById('panel-request-complete').classList.add('d-none');

    if (panelName === 'init') {
        document.getElementById('panel-request-init').classList.remove('d-none');
    } else if (panelName === 'pending') {
        document.getElementById('panel-request-pending').classList.remove('d-none');
    } else if (panelName === 'active') {
        document.getElementById('panel-request-active').classList.remove('d-none');
    } else if (panelName === 'complete') {
        document.getElementById('panel-request-complete').classList.remove('d-none');
    }
}

function cancelActiveRequest() {
    if (!activeRequestId) return;

    fetch(`/api/customer/request/${activeRequestId}/cancel`, {
        method: 'POST'
    })
    .then(res => res.json())
    .then(res => {
        if (res.success) {
            showToast("Đã hủy yêu cầu cứu hộ thành công!", "success");
            resetDashboard();
        } else {
            showToast(res.message, "error");
        }
    });
}

function resetDashboard() {
    if (activeRequestPollInterval) {
        clearInterval(activeRequestPollInterval);
        activeRequestPollInterval = null;
    }
    if (etaTimer) {
        clearInterval(etaTimer);
        etaTimer = null;
    }
    activeRequestId = null;
    chatInitialized = false;
    customerPhone = null;

    // Reset panel UI
    showPanel('init');

    // Clear form inputs
    document.getElementById('input-desc').value = "";
    document.getElementById('ai-diag-result').classList.add('d-none');
    document.getElementById('btn-submit-request').setAttribute('disabled', 'true');

    // Xóa đường nối và marker thợ hoạt động trên bản đồ
    if (activeRouteLine) {
        map.removeLayer(activeRouteLine);
        activeRouteLine = null;
    }
    if (rescuerMarkers['active_trip']) {
        map.removeLayer(rescuerMarkers['active_trip']);
        delete rescuerMarkers['active_trip'];
    }

    // Tải lại các thợ cứu hộ xung quanh
    const pos = customerMarker.getLatLng();
    loadNearbyRescuers(pos.lat, pos.lng);
}

// 7. CÔNG CỤ GIẢ LẬP NHÂN VIÊN CỨU HỘ PHẢN HỒI (SIMULATOR)
window.simulateRescuer = function(action) {
    if (!activeRequestId) {
        showToast("Không có yêu cầu hoạt động nào để giả lập!", "error");
        return;
    }

    const formData = new FormData();
    formData.append("action", action);

    fetch(`/api/customer/request/${activeRequestId}/simulate-step`, {
        method: 'POST',
        body: formData
    })
    .then(res => res.json())
    .then(res => {
        if (res.success) {
            showToast(`Giả lập thành công bước: ${action}`, "success");
            // Kích hoạt cập nhật tức thì
            pollRequestStatus();
        } else {
            showToast(res.message, "error");
        }
    });
};

function formatVND(amount) {
    if (!amount) return "0đ";
    return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount);
}

function updateAdminQrCodeIfSelected() {
    const selectEl = document.getElementById('select-payment-method');
    const container = document.getElementById('admin-qr-payment-container');
    if (!selectEl || !container) return;

    const method = selectEl.value;
    if (method === 'BANK_TRANSFER') {
        const adminBankName = selectEl.getAttribute('data-admin-bank-name') || 'MBBank';
        const adminBankAccountNo = selectEl.getAttribute('data-admin-bank-account-no') || '0900000000';
        const adminBankAccountName = selectEl.getAttribute('data-admin-bank-account-name') || 'E RESCUE ADMIN';
        const amount = window.activeRequestTotalAmount || 0;
        const shortDesc = 'ERESCUE BILL ' + (window.activeRequestCode ? window.activeRequestCode.substring(0, 8).toUpperCase() : '');

        // Lấy mã VietQR Bank Code
        const bankCode = getVietQrBankCode(adminBankName);
        const qrUrl = `https://img.vietqr.io/image/${bankCode}-${adminBankAccountNo}-compact.png?amount=${amount}&addInfo=${encodeURIComponent(shortDesc)}&accountName=${encodeURIComponent(adminBankAccountName)}`;

        document.getElementById('admin-qr-image').src = qrUrl;
        document.getElementById('admin-qr-bank-name').innerText = adminBankName;
        document.getElementById('admin-qr-acc-no').innerText = adminBankAccountNo;
        document.getElementById('admin-qr-acc-name').innerText = adminBankAccountName;
        document.getElementById('admin-qr-amount').innerText = formatVND(amount);
        document.getElementById('admin-qr-desc').innerText = shortDesc;

        container.classList.remove('d-none');
    } else {
        container.classList.add('d-none');
    }
}

function getVietQrBankCode(bankName) {
    switch (bankName) {
        case 'Vietcombank': return 'VCB';
        case 'MBBank': return 'MB';
        case 'Techcombank': return 'TCB';
        case 'BIDV': return 'BIDV';
        case 'VietinBank': return 'ICB';
        case 'Agribank': return 'VBA';
        case 'TPBank': return 'TPB';
        case 'ACB': return 'ACB';
        default: return bankName;
    }
}

function updateActiveAdminQrCode() {
    const selectEl = document.getElementById('active-select-payment-method');
    const container = document.getElementById('active-admin-qr-container');
    if (!selectEl || !container) return;

    const adminBankName = selectEl.getAttribute('data-admin-bank-name') || 'MBBank';
    const adminBankAccountNo = selectEl.getAttribute('data-admin-bank-account-no') || '0900000000';
    const adminBankAccountName = selectEl.getAttribute('data-admin-bank-account-name') || 'E RESCUE ADMIN';
    const amount = window.activeRequestTotalAmount || 0;
    const shortDesc = 'ERESCUE BILL ' + (window.activeRequestCode ? window.activeRequestCode.substring(0, 8).toUpperCase() : '');

    // Lấy mã VietQR Bank Code
    const bankCode = getVietQrBankCode(adminBankName);
    const qrUrl = `https://img.vietqr.io/image/${bankCode}-${adminBankAccountNo}-compact.png?amount=${amount}&addInfo=${encodeURIComponent(shortDesc)}&accountName=${encodeURIComponent(adminBankAccountName)}`;

    document.getElementById('active-admin-qr-image').src = qrUrl;
    document.getElementById('active-admin-bank-name').innerText = adminBankName;
    document.getElementById('active-admin-acc-no').innerText = adminBankAccountNo;
    document.getElementById('active-admin-acc-name').innerText = adminBankAccountName;
    document.getElementById('active-admin-amount').innerText = formatVND(amount);
    document.getElementById('active-admin-desc').innerText = shortDesc;
}

function saveActivePaymentMethod(method) {
    if (!activeRequestId) return;
    
    const formData = new FormData();
    formData.append("paymentMethod", method);

    fetch(`/api/customer/request/${activeRequestId}/payment-method`, {
        method: 'POST',
        body: formData
    })
    .then(res => res.json())
    .then(res => {
        if (res.success) {
            showToast("Đã chọn phương thức thanh toán thành công!", "success");
            pollRequestStatus();
        } else {
            showToast(res.message, "error");
        }
    })
    .catch(err => {
        console.error("Error setting payment method: ", err);
    });
}

// --- CHAT INTERACTION & ETA WIDGET UPGRADES ---
let etaTimer = null;
function startEtaCountdown(initialMinutes, distance) {
    if (etaTimer) clearInterval(etaTimer);
    let minutesLeft = initialMinutes;
    etaTimer = setInterval(() => {
        if (minutesLeft > 2) {
            minutesLeft--;
            const countdownEl = document.getElementById('eta-countdown-text');
            if (countdownEl) countdownEl.innerText = minutesLeft + " phút";
        } else {
            clearInterval(etaTimer);
        }
    }, 60000);
}

function generateCurvedPath(p1, p2, numPoints = 20) {
    const points = [];
    const midLat = (p1[0] + p2[0]) / 2;
    const midLng = (p1[1] + p2[1]) / 2;
    const diffLat = p2[0] - p1[0];
    const diffLng = p2[1] - p1[1];
    
    // Orthogonal deviation to generate winding urban road curve
    const controlLat = midLat - diffLng * 0.25;
    const controlLng = midLng + diffLat * 0.25;

    for (let i = 0; i <= numPoints; i++) {
        const t = i / numPoints;
        const lat = (1 - t) * (1 - t) * p1[0] + 2 * (1 - t) * t * controlLat + t * t * p2[0];
        const lng = (1 - t) * (1 - t) * p1[1] + 2 * (1 - t) * t * controlLng + t * t * p2[1];
        points.push([lat, lng]);
    }
    return points;
}

function setupChat() {
    const btnSend = document.getElementById('btn-chat-send');
    const inputMsg = document.getElementById('chat-input-message');
    
    if (!btnSend || !inputMsg) return;
    
    // Remove old listeners to prevent duplicates
    btnSend.replaceWith(btnSend.cloneNode(true));
    inputMsg.replaceWith(inputMsg.cloneNode(true));
    
    const newBtnSend = document.getElementById('btn-chat-send');
    const newInputMsg = document.getElementById('chat-input-message');
    
    newBtnSend.addEventListener('click', sendChatMessage);
    newInputMsg.addEventListener('keypress', function(e) {
        if (e.key === 'Enter') {
            sendChatMessage();
        }
    });
}

function sendChatMessage() {
    const inputMsg = document.getElementById('chat-input-message');
    if (!inputMsg) return;
    
    const text = inputMsg.value.trim();
    if (!text) return;
    
    if (webSocket && webSocket.readyState === WebSocket.OPEN) {
        const payload = {
            type: 'CHAT_MESSAGE',
            requestId: activeRequestId,
            messageContent: text
        };
        webSocket.send(JSON.stringify(payload));
        inputMsg.value = '';
    } else {
        showToast("Lỗi kết nối. Không thể gửi tin nhắn!", "error");
    }
}

function loadChatHistory(requestId) {
    fetch(`/api/requests/${requestId}/chat-history`)
    .then(res => res.json())
    .then(res => {
        if (res.success && res.data) {
            const box = document.getElementById('chat-messages-box');
            if (!box) return;
            box.innerHTML = '';
            
            if (res.data.length === 0) {
                box.innerHTML = '<div class="text-center text-muted small py-4">Chưa có tin nhắn nào. Hãy gửi lời chào!</div>';
                return;
            }
            
            res.data.forEach(msg => appendChatMessage(msg));
            scrollToBottom();
        }
    });
}

function appendChatMessage(msg) {
    const box = document.getElementById('chat-messages-box');
    if (!box) return;
    
    // Clear initial greeting if first message
    if (box.querySelector('.text-muted')) {
        box.innerHTML = '';
    }
    
    // Prevent duplicate render if already exists
    if (document.getElementById(`msg-${msg.messageId}`)) return;
    
    const isOutgoing = (msg.senderPhone === customerPhone);
    const msgDiv = document.createElement('div');
    if (msg.messageId) {
        msgDiv.id = `msg-${msg.messageId}`;
    }
    msgDiv.style.display = 'flex';
    msgDiv.style.flexDirection = 'column';
    msgDiv.style.alignItems = isOutgoing ? 'flex-end' : 'flex-start';
    msgDiv.style.width = '100%';
    
    const timeStr = msg.createdAt ? new Date(msg.createdAt).toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'}) : new Date().toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'});
    
    msgDiv.innerHTML = `
        <div class="p-2 rounded shadow-sm text-white" style="
            max-width: 80%; 
            font-size: 0.8rem;
            background: ${isOutgoing ? 'rgba(0, 229, 255, 0.2)' : 'rgba(255, 255, 255, 0.05)'};
            border: 1px solid ${isOutgoing ? 'rgba(0, 229, 255, 0.3)' : 'rgba(255, 255, 255, 0.1)'};
            border-radius: ${isOutgoing ? '12px 12px 0 12px' : '12px 12px 12px 0'};
            word-break: break-word;
        ">
            ${escapeHtml(msg.messageContent)}
        </div>
        <span class="text-muted" style="font-size: 0.65rem; margin-top: 2px; padding: 0 4px;">${timeStr}</span>
    `;
    box.appendChild(msgDiv);
}

function scrollToBottom() {
    const box = document.getElementById('chat-messages-box');
    if (box) {
        box.scrollTop = box.scrollHeight;
    }
}

function escapeHtml(text) {
    if (!text) return '';
    return text
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}
