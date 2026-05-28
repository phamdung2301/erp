// rescuer.js - Điều khiển bản đồ, định vị và luồng xử lý của thợ cứu hộ

let map;
let rescuerMarker;
let customerMarker = null;
let routeLine = null;

let activeRequestId = null;
let activeRequestPollInterval = null;
let incomingRequestsPollInterval = null;
let rescuerPhone = null;
let chatInitialized = false;
let inventoryLoaded = false;

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
                pollActiveTripStatus();
            } else if (msg.type === 'INCOMING_REQUESTS_UPDATE') {
                loadIncomingRequests();
            } else if (msg.type === 'PAYMENT_REMINDER') {
                showToast(msg.message, 'info');
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
    setupEventHandlers();
    
    // Khởi tạo trạng thái dựa trên toggle online
    const onlineToggle = document.getElementById('onlineToggle');
    if (onlineToggle && onlineToggle.checked) {
        startWorkingMode();
    } else {
        stopWorkingMode();
    }
    connectWebSocket();
    initRevenueCalculator();
});

// 1. KHỞI TẠO BẢN ĐỒ
function initMap() {
    // Lấy tọa độ khởi tạo (nếu thợ đã có sẵn trong db hoặc lấy mặc định)
    let initLat = DEFAULT_LAT;
    let initLng = DEFAULT_LNG;

    map = L.map('map').setView([initLat, initLng], 14);

    // Dark Mode Tile Layer
    L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors &copy; <a href="https://carto.com/attributions">CARTO</a>',
        subdomains: 'abcd',
        maxZoom: 20
    }).addTo(map);

    // Marker đại diện cho Thợ cứu hộ (màu xanh lá)
    const rescuerIcon = L.icon({
        iconUrl: 'https://cdn-icons-png.flaticon.com/512/3246/3246813.png',
        iconSize: [40, 40],
        iconAnchor: [20, 40],
        popupAnchor: [0, -40]
    });

    rescuerMarker = L.marker([initLat, initLng], {
        icon: rescuerIcon,
        draggable: true
    }).addTo(map);

    rescuerMarker.bindPopup("Vị trí làm việc của bạn (Kéo thả để di chuyển)").openPopup();

    // Đồng bộ tọa độ khi thợ kéo thả marker để thử nghiệm/di chuyển thực tế
    rescuerMarker.on('dragend', function(e) {
        const pos = rescuerMarker.getLatLng();
        updateLocationOnServer(pos.lat, pos.lng);
        
        // Vẽ lại đường dẫn nếu đang thực hiện cuốc
        if (activeRequestId && customerMarker) {
            updateRouteOnMap();
        }
    });

    // Cố gắng định vị bằng GPS thực tế của trình duyệt
    if (navigator.geolocation) {
        navigator.geolocation.getCurrentPosition(function(position) {
            const lat = position.coords.latitude;
            const lng = position.coords.longitude;
            map.setView([lat, lng], 15);
            rescuerMarker.setLatLng([lat, lng]);
            updateLocationOnServer(lat, lng);
        });
    }
}

// 2. CẬP NHẬT TỌA ĐỘ LÊN SERVER (THROTTLE 5 GIÂY)
let lastUpdateLocationTime = 0;
let pendingLocationUpdateTimeout = null;

function updateLocationOnServer(lat, lng) {
    const now = Date.now();
    const onlineToggle = document.getElementById('onlineToggle');
    if (!onlineToggle || !onlineToggle.checked) return; // Chỉ cập nhật vị trí nếu online

    // Nếu khoảng cách giữa 2 lần cập nhật ít hơn 5 giây, thực hiện Throttle
    if (now - lastUpdateLocationTime < 5000) {
        if (pendingLocationUpdateTimeout) {
            clearTimeout(pendingLocationUpdateTimeout);
        }
        pendingLocationUpdateTimeout = setTimeout(function() {
            updateLocationOnServerActual(lat, lng);
        }, 5000 - (now - lastUpdateLocationTime));
    } else {
        updateLocationOnServerActual(lat, lng);
    }
}

function updateLocationOnServerActual(lat, lng) {
    lastUpdateLocationTime = Date.now();
    if (pendingLocationUpdateTimeout) {
        clearTimeout(pendingLocationUpdateTimeout);
        pendingLocationUpdateTimeout = null;
    }

    const formData = new FormData();
    formData.append("lat", parseFloat(lat).toFixed(6));
    formData.append("lng", parseFloat(lng).toFixed(6));

    fetch('/api/rescuer/location', {
        method: 'POST',
        body: formData
    })
    .then(res => res.json())
    .then(data => {
        console.log("Location updated (throttled):", data.message);
    });
}

// 3. THIẾT LẬP CÁC SỰ KIỆN TƯƠNG TÁC
function setupEventHandlers() {
    // Toggle Online Switch
    const onlineToggle = document.getElementById('onlineToggle');
    if (onlineToggle) {
        onlineToggle.addEventListener('change', function() {
            const online = this.checked;
            const label = document.getElementById('onlineLabel');
            label.innerText = online ? "Đang làm việc" : "Đang nghỉ";

            fetch(`/api/rescuer/toggle-online?online=${online}`, {
                method: 'POST'
            })
            .then(res => res.json())
            .then(res => {
                if (res.success) {
                    showToast(res.message, "success");
                    if (online) {
                        startWorkingMode();
                    } else {
                        stopWorkingMode();
                    }
                } else {
                    showToast(res.message, "error");
                    this.checked = !online;
                    label.innerText = !online ? "Đang làm việc" : "Đang nghỉ";
                }
            });
        });
    }

    // Modal Payout Submit
    document.getElementById('btn-submit-payout').addEventListener('click', function() {
        const amountInput = document.getElementById('input-payout-amount');
        const amount = amountInput.value.trim();
        const bankSelect = document.getElementById('select-payout-bank');
        const bankName = bankSelect ? bankSelect.value.trim() : "";
        const accountNoInput = document.getElementById('input-payout-account-no');
        const bankAccountNo = accountNoInput ? accountNoInput.value.trim() : "";
        const accountNameInput = document.getElementById('input-payout-account-name');
        const bankAccountName = accountNameInput ? accountNameInput.value.trim().toUpperCase() : "";

        if (!amount || isNaN(amount) || parseFloat(amount) <= 0) {
            showToast("Vui lòng nhập số tiền rút hợp lệ!", "error");
            return;
        }

        if (!bankName) {
            showToast("Vui lòng chọn ngân hàng thụ hưởng!", "error");
            return;
        }

        if (!bankAccountNo) {
            showToast("Vui lòng nhập số tài khoản!", "error");
            return;
        }

        if (!bankAccountName) {
            showToast("Vui lòng nhập tên chủ tài khoản!", "error");
            return;
        }

        const formData = new FormData();
        formData.append("amount", amount);
        formData.append("bankName", bankName);
        formData.append("bankAccountNo", bankAccountNo);
        formData.append("bankAccountName", bankAccountName);

        fetch('/api/rescuer/wallet/payout', {
            method: 'POST',
            body: formData
        })
        .then(res => res.json())
        .then(res => {
            if (res.success) {
                showToast("Yêu cầu rút tiền đã được thực hiện!", "success");
                // Tải lại trang sau 1.5s để cập nhật số dư hiển thị
                setTimeout(() => location.reload(), 1500);
            } else {
                showToast(res.message, "error");
            }
        });
    });

    // Modal Admin Payment dynamic QR
    const adminPaymentModalEl = document.getElementById('adminPaymentModal');
    if (adminPaymentModalEl) {
        adminPaymentModalEl.addEventListener('show.bs.modal', function(event) {
            const button = event.relatedTarget;
            const adminBankName = button.getAttribute('data-admin-bank-name') || 'MBBank';
            const adminBankAccountNo = button.getAttribute('data-admin-bank-account-no') || '0900000000';
            const adminBankAccountName = button.getAttribute('data-admin-bank-account-name') || 'E RESCUE ADMIN';

            document.getElementById('admin-pay-bank-name').innerText = adminBankName;
            document.getElementById('admin-pay-acc-no').innerText = adminBankAccountNo;
            document.getElementById('admin-pay-acc-name').innerText = adminBankAccountName;

            const amountInput = document.getElementById('input-admin-pay-amount');
            updateAdminPayQr(amountInput.value, adminBankName, adminBankAccountNo, adminBankAccountName);
        });

        const adminPayAmountInput = document.getElementById('input-admin-pay-amount');
        if (adminPayAmountInput) {
            adminPayAmountInput.addEventListener('input', function() {
                const adminBankName = document.getElementById('admin-pay-bank-name').innerText || 'MBBank';
                const adminBankAccountNo = document.getElementById('admin-pay-acc-no').innerText || '0900000000';
                const adminBankAccountName = document.getElementById('admin-pay-acc-name').innerText || 'E RESCUE ADMIN';
                updateAdminPayQr(this.value, adminBankName, adminBankAccountNo, adminBankAccountName);
            });
        }
    }
}

// 4. QUẢN LÝ TIẾN TRÌNH LÀM VIỆC (WORKING STATE)
function startWorkingMode() {
    showPanel('waiting');
    
    // Gửi tọa độ ngay lập tức để định vị thợ trên bản đồ
    const pos = rescuerMarker.getLatLng();
    updateLocationOnServer(pos.lat, pos.lng);

    // Bắt đầu kiểm tra xem thợ có cuốc nào đang chạy sẵn không
    checkActiveTrip();

    // Bắt đầu polling tìm việc xung quanh (mỗi 30 giây làm fallback heartbeat)
    if (incomingRequestsPollInterval) clearInterval(incomingRequestsPollInterval);
    incomingRequestsPollInterval = setInterval(loadIncomingRequests, 30000);
}

function stopWorkingMode() {
    showPanel('offline');
    
    if (incomingRequestsPollInterval) clearInterval(incomingRequestsPollInterval);
    if (activeRequestPollInterval) clearInterval(activeRequestPollInterval);
    
    clearActiveTripFromMap();
}

function showPanel(panelName) {
    document.getElementById('panel-offline').classList.add('d-none');
    document.getElementById('panel-waiting').classList.add('d-none');
    document.getElementById('panel-active').classList.add('d-none');

    if (panelName === 'offline') {
        document.getElementById('panel-offline').classList.remove('d-none');
    } else if (panelName === 'waiting') {
        document.getElementById('panel-waiting').classList.remove('d-none');
    } else if (panelName === 'active') {
        document.getElementById('panel-active').classList.remove('d-none');
    }
}

// 5. TẢI CUỐC XE CHỜ (PENDING REQUESTS)
function loadIncomingRequests() {
    if (activeRequestId) return; // Đang chạy cuốc thì không hiển thị danh sách cuốc chờ

    fetch('/api/rescuer/incoming-requests')
    .then(res => res.json())
    .then(res => {
        const container = document.getElementById('incoming-list');
        if (res.success && res.data && res.data.length > 0) {
            let html = '';
            res.data.forEach(req => {
                const distanceKm = calculateDistance(
                    rescuerMarker.getLatLng().lat, rescuerMarker.getLatLng().lng,
                    req.lat, req.lng
                ).toFixed(2);

                html += `
                <div class="card request-card mb-3">
                    <div class="card-body p-3">
                        <div class="d-flex justify-content-between mb-2">
                            <span class="badge bg-warning-soft text-warning"><i class="bi bi-clock-history me-1"></i>Đang chờ</span>
                            <span class="text-info font-monospace small"><i class="bi bi-geo-alt me-1"></i>${distanceKm} km</span>
                        </div>
                        <h6 class="text-white mb-1">${req.customerName}</h6>
                        <p class="small text-muted mb-2"><i class="bi bi-geo-alt-fill text-danger me-1"></i>${req.customerAddress}</p>
                        <div class="bg-dark-soft p-2 rounded mb-3 small">
                            <strong class="text-white">Lỗi xe:</strong> ${req.issueDesc}<br/>
                            <strong class="text-primary"><i class="bi bi-robot me-1"></i>AI khuyên:</strong> ${req.aiDiagnosis.split('\n')[1] || ''}
                        </div>
                        <div class="d-flex justify-content-between align-items-center">
                            <span class="text-success fw-bold font-monospace">${formatVND(req.basePrice)}</span>
                            <button type="button" class="btn btn-primary btn-sm px-3" onclick="acceptRequest('${req.requestId}')">
                                <i class="bi bi-check2-circle me-1"></i>Nhận cuốc
                            </button>
                        </div>
                    </div>
                </div>`;
            });
            container.innerHTML = html;
        } else {
            container.innerHTML = `
            <div class="text-center py-4 text-muted" id="no-request-msg">
                <i class="bi bi-broadcast fs-3 mb-2 d-block"></i>
                Đang quét tìm sự cố cứu hộ gần bạn...
            </div>`;
        }
    });
}

// 6. CHẤP NHẬN CUỐC XE
window.acceptRequest = function(requestId) {
    fetch(`/api/rescuer/request/${requestId}/accept`, {
        method: 'POST'
    })
    .then(res => res.json())
    .then(res => {
        if (res.success) {
            showToast("Đã nhận cuốc xe cứu hộ thành công!", "success");
            activeRequestId = requestId;
            
            // Xóa polling danh sách chờ
            if (incomingRequestsPollInterval) clearInterval(incomingRequestsPollInterval);

            // Bắt đầu vòng lặp polling cập nhật cuốc đang chạy
            startActiveTripPolling();
        } else {
            showToast(res.message, "error");
        }
    });
};

// 7. THEO DÕI CUỐC XE ĐANG CHẠY (ACTIVE TRIP)
function checkActiveTrip() {
    fetch('/api/rescuer/request/active')
    .then(res => res.json())
    .then(res => {
        if (res.success && res.data) {
            activeRequestId = res.data.requestId;
            if (incomingRequestsPollInterval) clearInterval(incomingRequestsPollInterval);
            startActiveTripPolling();
        }
    });
}

function startActiveTripPolling() {
    showPanel('active');
    
    if (activeRequestPollInterval) clearInterval(activeRequestPollInterval);
    
    pollActiveTripStatus();
    // Tăng chu kỳ polling thành 30s đóng vai trò là heartbeat fallback khi WebSocket bị gián đoạn
    activeRequestPollInterval = setInterval(pollActiveTripStatus, 30000);
}

function pollActiveTripStatus() {
    if (!activeRequestId) return;

    fetch('/api/rescuer/request/active')
    .then(res => res.json())
    .then(res => {
        if (!res.success || !res.data) {
            // Cuốc xe không tồn tại hoặc đã xong/hủy
            clearActiveTripFromMap();
            activeRequestId = null;
            if (activeRequestPollInterval) clearInterval(activeRequestPollInterval);
            startWorkingMode(); // Quay về chế độ tìm cuốc
            return;
        }

        const req = res.data;
        const status = req.status;

        // Điền thông tin khách hàng
        document.getElementById('active-customer-name').innerText = req.customerName;
        document.getElementById('active-customer-address').innerText = req.customerAddress;
        document.getElementById('active-customer-phone').href = "tel:" + req.customerPhone;
        document.getElementById('active-ai-issue').innerText = req.issueDesc;
        document.getElementById('active-base-price').innerText = formatVND(req.basePrice);

        // Khởi tạo Chat nếu chưa khởi tạo
        rescuerPhone = req.rescuerPhone;
        if (!chatInitialized) {
            setupChat();
            loadChatHistory(activeRequestId);
            chatInitialized = true;
        }

        // Đồng bộ tiến trình (Stepper)
        updateStepper(status);

        // Hiển thị/Cập nhật các nút hành động tương ứng trạng thái
        const btnArrive = document.getElementById('btn-status-arrive');
        const btnStart = document.getElementById('btn-status-start');
        const extraBox = document.getElementById('extra-price-box');
        const otpBox = document.getElementById('otp-complete-box');
        const inventoryBox = document.getElementById('mobile-inventory-box');

        // Reset display
        btnArrive.classList.add('d-none');
        btnStart.classList.add('d-none');
        extraBox.classList.add('d-none');
        otpBox.classList.add('d-none');
        if (inventoryBox) inventoryBox.classList.add('d-none');

        // Bỏ gán sự kiện cũ để tránh trùng lặp
        btnArrive.onclick = null;
        btnStart.onclick = null;

        if (status === 'ACCEPTED') {
            btnArrive.classList.remove('d-none');
            btnArrive.onclick = function() { updateTripStatus(req.requestId, 'arrive'); };
        } 
        else if (status === 'ARRIVED') {
            btnStart.classList.remove('d-none');
            btnStart.onclick = function() { updateTripStatus(req.requestId, 'start-repair'); };
        } 
        else if (status === 'IN_PROGRESS') {
            extraBox.classList.remove('d-none');
            otpBox.classList.remove('d-none');
            
            if (inventoryBox) {
                inventoryBox.classList.remove('d-none');
                if (!inventoryLoaded) {
                    loadMobileInventory();
                    inventoryLoaded = true;
                }
            }

            // Handler sử dụng phụ tùng từ cốp đồ
            const btnUsePart = document.getElementById('btn-use-mobile-part');
            if (btnUsePart) {
                btnUsePart.onclick = function() {
                    const selectPart = document.getElementById('select-mobile-part');
                    const partName = selectPart.value;
                    if (!partName) {
                        showToast("Vui lòng chọn phụ tùng trước!", "error");
                        return;
                    }
                    useMobilePart(req.requestId, partName);
                };
            }
            
            // Đặt mặc định giá trị phụ thu hiện tại nếu có
            if (req.extraPrice && !document.getElementById('input-extra-price').value) {
                document.getElementById('input-extra-price').value = req.extraPrice;
            }

            // Handler cập nhật extra
            document.getElementById('btn-update-extra').onclick = function() {
                const price = document.getElementById('input-extra-price').value.trim();
                if (!price || isNaN(price) || parseFloat(price) < 0) {
                    showToast("Vui lòng nhập phí phụ tùng hợp lệ!", "error");
                    return;
                }
                updateExtraPrice(req.requestId, price);
            };

            // Handler hoàn thành cuộc sửa bằng OTP
            document.getElementById('btn-status-complete').onclick = function() {
                const otp = document.getElementById('input-otp').value.trim();
                if (otp.length !== 6 || isNaN(otp)) {
                    showToast("Mã OTP hoàn thành phải gồm 6 chữ số!", "error");
                    return;
                }
                completeTripWithOtp(req.requestId, otp);
            };
        }

        // Cập nhật Marker Khách hàng trên bản đồ và vẽ đường đi
        if (req.customerLat && req.customerLng) {
            updateCustomerOnMap(req.customerLat, req.customerLng, req.customerName);
        }
    });
}

function updateStepper(status) {
    const stepAccept = document.getElementById('step-accept');
    const stepArrive = document.getElementById('step-arrive');
    const stepRepair = document.getElementById('step-repair');
    const progressBar = document.getElementById('active-progress-bar');

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

function updateCustomerOnMap(lat, lng, name) {
    const custLat = parseFloat(lat);
    const custLng = parseFloat(lng);

    if (customerMarker) {
        customerMarker.setLatLng([custLat, custLng]);
    } else {
        const customerIcon = L.icon({
            iconUrl: 'https://cdn-icons-png.flaticon.com/512/854/854866.png',
            iconSize: [36, 36],
            iconAnchor: [18, 36]
        });

        customerMarker = L.marker([custLat, custLng], {
            icon: customerIcon
        }).addTo(map);
        customerMarker.bindPopup(`Khách hàng: ${name}`).openPopup();
    }

    updateRouteOnMap();
}

function updateRouteOnMap() {
    if (!customerMarker) return;

    const rescuerLatLng = rescuerMarker.getLatLng();
    const customerLatLng = customerMarker.getLatLng();
    const curvedPath = generateCurvedPath([rescuerLatLng.lat, rescuerLatLng.lng], [customerLatLng.lat, customerLatLng.lng]);

    if (routeLine) {
        routeLine.setLatLngs(curvedPath);
    } else {
        routeLine = L.polyline(curvedPath, {
            color: '#ef4444',
            weight: 4,
            dashArray: '5, 10',
            opacity: 0.8
        }).addTo(map);
    }

    // Zoom vừa vặn 2 marker
    const group = new L.featureGroup([rescuerMarker, customerMarker]);
    map.fitBounds(group.getBounds().pad(0.15));
}

function clearActiveTripFromMap() {
    if (customerMarker) {
        map.removeLayer(customerMarker);
        customerMarker = null;
    }
    if (routeLine) {
        map.removeLayer(routeLine);
        routeLine = null;
    }
    
    // Đặt lại text inputs
    document.getElementById('input-otp').value = '';
    document.getElementById('input-extra-price').value = '';
    
    // Reset chat và inventory di động
    chatInitialized = false;
    inventoryLoaded = false;
    rescuerPhone = null;
}

// 8. CÁC API HÀNH ĐỘNG CỦA THỢ CỨU HỘ
function updateTripStatus(requestId, actionEndpoint) {
    fetch(`/api/rescuer/request/${requestId}/${actionEndpoint}`, {
        method: 'POST'
    })
    .then(res => res.json())
    .then(res => {
        if (res.success) {
            showToast(res.message, "success");
            pollActiveTripStatus();
        } else {
            showToast(res.message, "error");
        }
    });
}

function updateExtraPrice(requestId, extraPrice) {
    const formData = new FormData();
    formData.append("extraPrice", extraPrice);

    fetch(`/api/rescuer/request/${requestId}/update-extra`, {
        method: 'POST',
        body: formData
    })
    .then(res => res.json())
    .then(res => {
        if (res.success) {
            showToast(res.message, "success");
        } else {
            showToast(res.message, "error");
        }
    });
}

function completeTripWithOtp(requestId, otp) {
    const formData = new FormData();
    formData.append("otp", otp);

    fetch(`/api/rescuer/request/${requestId}/complete`, {
        method: 'POST',
        body: formData
    })
    .then(res => res.json())
    .then(res => {
        if (res.success) {
            showToast(res.message, "success");
            clearActiveTripFromMap();
            activeRequestId = null;
            if (activeRequestPollInterval) clearInterval(activeRequestPollInterval);
            
            // Làm mới số dư hiển thị hoặc tải lại
            setTimeout(() => {
                location.reload();
            }, 1500);
        } else {
            showToast(res.message, "error");
        }
    });
}

// 9. CÁC HÀM TRỢ GIÚP (HELPERS)
function calculateDistance(lat1, lon1, lat2, lon2) {
    const R = 6371; // Bán kính Trái Đất (km)
    const dLat = deg2rad(lat2 - lat1);
    const dLon = deg2rad(lon2 - lon1);
    const a = 
        Math.sin(dLat / 2) * Math.sin(dLat / 2) +
        Math.cos(deg2rad(lat1)) * Math.cos(deg2rad(lat2)) * 
        Math.sin(dLon / 2) * Math.sin(dLon / 2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return R * c; // Khoảng cách theo km
}

function deg2rad(deg) {
    return deg * (Math.PI / 185);
}

function formatVND(amount) {
    if (!amount) return "0đ";
    return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount);
}

function updateAdminPayQr(amountVal, bankName, accountNo, accountName) {
    const amount = parseFloat(amountVal) || 0;
    const dataEl = document.getElementById('rescuer-data');
    const rescuerPhone = dataEl ? dataEl.getAttribute('data-rescuer-phone') : 'RESCUER';
    const shortDesc = 'NOP TIEN ' + rescuerPhone.toUpperCase();

    // Lấy mã VietQR Bank Code
    const bankCode = getVietQrBankCode(bankName);
    const qrUrl = `https://img.vietqr.io/image/${bankCode}-${accountNo}-compact.png?amount=${amount}&addInfo=${encodeURIComponent(shortDesc)}&accountName=${encodeURIComponent(accountName)}`;

    document.getElementById('admin-pay-qr-image').src = qrUrl;
    document.getElementById('admin-pay-amount-text').innerText = formatVND(amount);
    document.getElementById('admin-pay-desc').innerText = shortDesc;
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

// === PHÂN HỆ THỐNG KÊ DOANH THU THỢ CỨU HỘ ===
function initRevenueCalculator() {
    const select = document.getElementById('revenue-month-select');
    if (!select) return;

    // Tạo danh sách 3 tháng gần nhất gồm tháng hiện tại
    const now = new Date();
    select.innerHTML = '';
    
    for (let i = 0; i < 3; i++) {
        const d = new Date(now.getFullYear(), now.getMonth() - i, 1);
        const monthVal = (d.getMonth() + 1).toString().padStart(2, '0') + '/' + d.getFullYear();
        const option = document.createElement('option');
        option.value = monthVal;
        option.text = `Tháng ${d.getMonth() + 1}/${d.getFullYear()}`;
        select.appendChild(option);
    }

    // Tính doanh thu tháng hiện tại mặc định
    calculateMonthlyRevenue();
    
    // Tính tổng doanh thu tháng hiện tại đưa lên Stats Card thứ 4 "Doanh thu tháng này"
    calculateMonthlyRevenueForBadge();
}

function calculateMonthlyRevenue() {
    const select = document.getElementById('revenue-month-select');
    if (!select) return;

    const selectedMonth = select.value; // Dạng: MM/YYYY
    const [month, year] = selectedMonth.split('/');

    let totalEarned = 0;
    let netReceived = 0;
    let systemFee = 0;
    let tripsCount = 0;

    const rows = document.querySelectorAll('#completedTripsTable .trip-row');
    rows.forEach(row => {
        const timestampStr = row.getAttribute('data-timestamp'); // Dạng LocalDateTime ISO (ví dụ: 2026-05-28T20:39:30)
        if (!timestampStr) return;

        const date = new Date(timestampStr);
        const tripMonth = (date.getMonth() + 1).toString().padStart(2, '0');
        const tripYear = date.getFullYear().toString();

        if (tripMonth === month && tripYear === year) {
            const total = parseFloat(row.getAttribute('data-total')) || 0;
            const net = parseFloat(row.getAttribute('data-net')) || 0;

            totalEarned += total;
            netReceived += net;
            tripsCount++;
        }
    });

    systemFee = totalEarned - netReceived;

    // Cập nhật kết quả lên UI
    document.getElementById('calc-trips-count').innerText = tripsCount;
    document.getElementById('calc-total-amount').innerText = formatVND(totalEarned);
    document.getElementById('calc-system-fee').innerText = formatVND(systemFee);
    document.getElementById('calc-net-received').innerText = formatVND(netReceived);
}

function calculateMonthlyRevenueForBadge() {
    // Tính toán doanh thu tháng này để hiển thị trên Stats Card thứ 4
    const now = new Date();
    const currentMonth = (now.getMonth() + 1).toString().padStart(2, '0');
    const currentYear = now.getFullYear().toString();

    let currentMonthRevenue = 0;

    const rows = document.querySelectorAll('#completedTripsTable .trip-row');
    rows.forEach(row => {
        const timestampStr = row.getAttribute('data-timestamp');
        if (!timestampStr) return;

        const date = new Date(timestampStr);
        const tripMonth = (date.getMonth() + 1).toString().padStart(2, '0');
        const tripYear = date.getFullYear().toString();

        if (tripMonth === currentMonth && tripYear === currentYear) {
            const total = parseFloat(row.getAttribute('data-total')) || 0;
            currentMonthRevenue += total;
        }
    });

    const badge = document.getElementById('monthly-revenue-badge');
    if (badge) {
        badge.innerText = formatVND(currentMonthRevenue);
    }
}

// --- CHAT INTERACTION & MOBILE INVENTORY UPGRADES ---
function loadMobileInventory() {
    fetch('/api/rescuer/inventory')
    .then(res => res.json())
    .then(res => {
        if (res.success && res.data) {
            const select = document.getElementById('select-mobile-part');
            if (!select) return;
            select.innerHTML = '<option value="">-- Chọn phụ tùng từ cốp đồ --</option>';
            
            res.data.forEach(item => {
                const opt = document.createElement('option');
                opt.value = item.partName;
                opt.text = `${item.partName} (Còn ${item.quantity}) - ${formatVND(item.unitPrice)}`;
                if (item.quantity < 1) {
                    opt.disabled = true;
                }
                select.appendChild(opt);
            });
        }
    });
}

function useMobilePart(requestId, partName) {
    const formData = new FormData();
    formData.append("partName", partName);

    fetch(`/api/rescuer/request/${requestId}/use-part`, {
        method: 'POST',
        body: formData
    })
    .then(res => res.json())
    .then(res => {
        if (res.success) {
            showToast(res.message, "success");
            // Tải lại danh sách phụ tùng để cập nhật tồn kho mới
            loadMobileInventory();
            // Cập nhật lại thông tin cuốc xe để hiển thị extraPrice mới
            pollActiveTripStatus();
        } else {
            showToast(res.message, "error");
        }
    });
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
    
    const isOutgoing = (msg.senderPhone === rescuerPhone);
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
