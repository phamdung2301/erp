/**
 * e-Rescue Platform — Admin JavaScript
 * User management, Rescuer verification AJAX operations
 */

// === Lock User ===
async function lockUser(userId) {
    if (!confirm('Bạn có chắc muốn KHÓA tài khoản này?')) return;

    try {
        const data = await apiCall('/api/admin/users/' + userId + '/lock', 'PUT');
        showToast(data.message, 'success');
        setTimeout(() => location.reload(), 1000);
    } catch (error) {
        showToast(error.message, 'error');
    }
}

// === Unlock User ===
async function unlockUser(userId) {
    if (!confirm('Bạn có chắc muốn MỞ KHÓA tài khoản này?')) return;

    try {
        const data = await apiCall('/api/admin/users/' + userId + '/unlock', 'PUT');
        showToast(data.message, 'success');
        setTimeout(() => location.reload(), 1000);
    } catch (error) {
        showToast(error.message, 'error');
    }
}

// === Verify Rescuer ===
async function verifyRescuer(event, rescuerId) {
    if (event) event.preventDefault();
    if (!confirm('Bạn có chắc muốn DUYỆT hồ sơ đối tác này?')) return;

    const btn = event ? event.currentTarget : null;
    let originalHtml = "";
    if (btn) {
        originalHtml = btn.innerHTML;
        const parentDiv = btn.parentNode;
        if (parentDiv) {
            parentDiv.querySelectorAll('button').forEach(b => b.disabled = true);
        }
        btn.innerHTML = '<span class="spinner-border spinner-border-sm me-1" role="status" aria-hidden="true"></span> Đang xử lý...';
    }

    try {
        const data = await apiCall('/api/admin/rescuers/' + rescuerId + '/verify', 'PUT');
        showToast(data.message, 'success');

        // Hide the card with animation
        const card = document.getElementById('rescuer-card-' + rescuerId);
        if (card) {
            const col = card.closest('.col-md-6, .col-lg-4');
            if (col) {
                col.style.transition = 'all 0.5s ease';
                col.style.opacity = '0';
                col.style.transform = 'scale(0.8)';
                setTimeout(() => {
                    const row = col.parentNode;
                    col.remove();
                    
                    // Update verification badge
                    const badge = document.querySelector('.menu-item[href*="rescuer-verification"] .menu-badge');
                    if (badge) {
                        let count = parseInt(badge.textContent) || 0;
                        let newCount = Math.max(0, count - 1);
                        if (newCount > 0) {
                            badge.textContent = newCount;
                        } else {
                            badge.remove();
                        }
                    }

                    if (row && row.children.length === 0) {
                        location.reload();
                    }
                }, 500);
            } else {
                setTimeout(() => location.reload(), 1000);
            }
        } else {
            setTimeout(() => location.reload(), 1000);
        }
    } catch (error) {
        showToast(error.message, 'error');
        if (btn) {
            const parentDiv = btn.parentNode;
            if (parentDiv) {
                parentDiv.querySelectorAll('button').forEach(b => b.disabled = false);
            }
            btn.innerHTML = originalHtml;
        }
    }
}

// === Reject Rescuer ===
async function rejectRescuer(event, rescuerId) {
    if (event) event.preventDefault();
    if (!confirm('Bạn có chắc muốn TỪ CHỐI hồ sơ đối tác này?')) return;

    const btn = event ? event.currentTarget : null;
    let originalHtml = "";
    if (btn) {
        originalHtml = btn.innerHTML;
        const parentDiv = btn.parentNode;
        if (parentDiv) {
            parentDiv.querySelectorAll('button').forEach(b => b.disabled = true);
        }
        btn.innerHTML = '<span class="spinner-border spinner-border-sm me-1" role="status" aria-hidden="true"></span> Đang xử lý...';
    }

    try {
        const data = await apiCall('/api/admin/rescuers/' + rescuerId + '/reject', 'PUT');
        showToast(data.message, 'info');

        const card = document.getElementById('rescuer-card-' + rescuerId);
        if (card) {
            const col = card.closest('.col-md-6, .col-lg-4');
            if (col) {
                col.style.transition = 'all 0.5s ease';
                col.style.opacity = '0';
                col.style.transform = 'scale(0.8)';
                setTimeout(() => {
                    const row = col.parentNode;
                    col.remove();
                    
                    const badge = document.querySelector('.menu-item[href*="rescuer-verification"] .menu-badge');
                    if (badge) {
                        let count = parseInt(badge.textContent) || 0;
                        let newCount = Math.max(0, count - 1);
                        if (newCount > 0) {
                            badge.textContent = newCount;
                        } else {
                            badge.remove();
                        }
                    }

                    if (row && row.children.length === 0) {
                        location.reload();
                    }
                }, 500);
            } else {
                setTimeout(() => location.reload(), 1000);
            }
        } else {
            setTimeout(() => location.reload(), 1000);
        }
    } catch (error) {
        showToast(error.message, 'error');
        if (btn) {
            const parentDiv = btn.parentNode;
            if (parentDiv) {
                parentDiv.querySelectorAll('button').forEach(b => b.disabled = false);
            }
            btn.innerHTML = originalHtml;
        }
    }
}

// === Approve Payout ===
// === Approve Payout ===
let activePayoutButton = null;

async function approvePayout(event, button) {
    if (event) event.preventDefault();
    activePayoutButton = button;

    const payoutId = button.getAttribute('data-payout-id');
    const rescuerName = button.getAttribute('data-rescuer-name');
    const bankName = button.getAttribute('data-bank-name');
    const bankAccountNo = button.getAttribute('data-bank-account-no');
    const bankAccountName = button.getAttribute('data-bank-account-name');
    const amount = button.getAttribute('data-amount');

    // Nếu yêu cầu cũ bị thiếu thông tin ngân hàng, cho phép duyệt trực tiếp không qua VietQR
    if (!bankName || !bankAccountNo || !bankAccountName) {
        if (confirm('Yêu cầu rút tiền này thiếu thông tin tài khoản ngân hàng của thợ. Bạn có muốn duyệt thủ công ngay không?')) {
            executePayoutApproval(payoutId, button);
        }
        return;
    }

    // Điền thông tin vào modal
    document.getElementById('qr-rescuer-name').textContent = bankAccountName;
    document.getElementById('qr-bank-name').textContent = bankName;
    document.getElementById('qr-account-no').textContent = bankAccountNo;

    // Định dạng số tiền tệ VND
    const formattedAmount = new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount);
    document.getElementById('qr-amount').textContent = formattedAmount;

    // Định dạng nội dung chuyển tiền VietQR
    const shortDesc = 'ERESCUE PAY ' + payoutId.substring(0, 8).toUpperCase();
    document.getElementById('qr-description').textContent = shortDesc;

    // Lấy mã VietQR Bank Code
    const bankCode = getVietQrBankCode(bankName);
    
    // Tạo link ảnh VietQR Code động
    const qrUrl = `https://img.vietqr.io/image/${bankCode}-${bankAccountNo}-compact.png?amount=${amount}&addInfo=${encodeURIComponent(shortDesc)}&accountName=${encodeURIComponent(bankAccountName)}`;
    document.getElementById('vietqr-image').src = qrUrl;

    // Hiển thị modal
    const modalEl = document.getElementById('payoutConfirmModal');
    if (modalEl) {
        const modal = new bootstrap.Modal(modalEl);
        modal.show();
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

async function executePayoutApproval(payoutId, btn) {
    let originalHtml = "";
    if (btn) {
        originalHtml = btn.innerHTML;
        const row = btn.closest('tr');
        if (row) {
            row.querySelectorAll('button').forEach(b => b.disabled = true);
        }
        btn.innerHTML = '<span class="spinner-border spinner-border-sm me-1" role="status" aria-hidden="true"></span> Đang duyệt...';
    }

    try {
        const data = await apiCall('/api/admin/payouts/' + payoutId + '/approve', 'POST');
        showToast(data.message, 'success');

        const row = document.getElementById('payout-row-' + payoutId);
        if (row) {
            row.style.transition = 'all 0.5s ease';
            row.style.opacity = '0';
            row.style.transform = 'scale(0.9)';
            setTimeout(() => {
                const tbody = row.parentNode;
                row.remove();
                
                updatePayoutBadgeCounts(-1);
                
                if (tbody && tbody.children.length === 0) {
                    const tr = document.createElement('tr');
                    tr.innerHTML = '<td colspan="5" class="text-center text-muted py-4">Không có yêu cầu rút tiền nào đang chờ duyệt.</td>';
                    tbody.appendChild(tr);
                }
            }, 500);
        } else {
            setTimeout(() => location.reload(), 1000);
        }
    } catch (error) {
        showToast(error.message, 'error');
        if (btn) {
            const row = btn.closest('tr');
            if (row) {
                row.querySelectorAll('button').forEach(b => b.disabled = false);
            }
            btn.innerHTML = originalHtml;
        }
    }
}

// Bắt sự kiện xác nhận đã chuyển khoản thành công từ Modal
document.addEventListener('DOMContentLoaded', () => {
    const confirmPaidBtn = document.getElementById('btn-confirm-payout-paid');
    if (confirmPaidBtn) {
        confirmPaidBtn.addEventListener('click', async () => {
            if (!activePayoutButton) return;
            const payoutId = activePayoutButton.getAttribute('data-payout-id');

            // Ẩn modal
            const modalEl = document.getElementById('payoutConfirmModal');
            if (modalEl) {
                const modal = bootstrap.Modal.getInstance(modalEl);
                if (modal) modal.hide();
            }

            // Gọi hàm duyệt đơn hàng thực tế
            await executePayoutApproval(payoutId, activePayoutButton);
        });
    }
});

// === Reject Payout ===
async function rejectPayout(event, payoutId) {
    if (event) event.preventDefault();
    if (!confirm('Bạn có chắc chắn muốn TỪ CHỐI yêu cầu rút tiền này?')) return;

    const btn = event ? event.currentTarget : null;
    let originalHtml = "";
    if (btn) {
        originalHtml = btn.innerHTML;
        const row = btn.closest('tr');
        if (row) {
            row.querySelectorAll('button').forEach(b => b.disabled = true);
        }
        btn.innerHTML = '<span class="spinner-border spinner-border-sm me-1" role="status" aria-hidden="true"></span> Đang từ chối...';
    }

    try {
        const data = await apiCall('/api/admin/payouts/' + payoutId + '/reject', 'POST');
        showToast(data.message, 'info');

        const row = document.getElementById('payout-row-' + payoutId);
        if (row) {
            row.style.transition = 'all 0.5s ease';
            row.style.opacity = '0';
            row.style.transform = 'scale(0.9)';
            setTimeout(() => {
                const tbody = row.parentNode;
                row.remove();
                
                updatePayoutBadgeCounts(-1);
                
                if (tbody && tbody.children.length === 0) {
                    const tr = document.createElement('tr');
                    tr.innerHTML = '<td colspan="5" class="text-center text-muted py-4">Không có yêu cầu rút tiền nào đang chờ duyệt.</td>';
                    tbody.appendChild(tr);
                }
            }, 500);
        } else {
            setTimeout(() => location.reload(), 1000);
        }
    } catch (error) {
        showToast(error.message, 'error');
        if (btn) {
            const row = btn.closest('tr');
            if (row) {
                row.querySelectorAll('button').forEach(b => b.disabled = false);
            }
            btn.innerHTML = originalHtml;
        }
    }
}

function updatePayoutBadgeCounts(delta) {
    const pendingTabBadge = document.querySelector('#pending-tab .badge');
    if (pendingTabBadge) {
        let count = parseInt(pendingTabBadge.textContent) || 0;
        let newCount = Math.max(0, count + delta);
        pendingTabBadge.textContent = newCount;
    }
    
    const sidebarBadge = document.querySelector('.menu-item[href*="payouts"] .menu-badge');
    if (sidebarBadge) {
        let count = parseInt(sidebarBadge.textContent) || 0;
        let newCount = Math.max(0, count + delta);
        if (newCount > 0) {
            sidebarBadge.textContent = newCount;
        } else {
            sidebarBadge.remove();
        }
    }
}

// === Update Settings ===
async function updateSettings(event) {
    event.preventDefault();
    const form = event.target;
    const formData = new FormData(form);

    try {
        const data = await apiFormCall('/api/admin/settings/update', formData);
        showToast(data.message, 'success');
        setTimeout(() => location.reload(), 1000);
    } catch (error) {
        showToast(error.message, 'error');
    }
}

// === Update Price List ===
async function updatePriceList(event) {
    event.preventDefault();
    const form = event.target;
    const formData = new FormData(form);

    try {
        const data = await apiFormCall('/api/admin/price-list/update', formData);
        showToast(data.message, 'success');
        setTimeout(() => location.reload(), 1000);
    } catch (error) {
        showToast(error.message, 'error');
    }
}

// === Confirm/Clear Rescuer Cash Debt ===
async function confirmClearCashDebt(rescuerId, rescuerName) {
    if (!confirm(`Bạn có chắc chắn xác nhận đã thu đủ tiền mặt từ thợ "${rescuerName}"? Công nợ của thợ này sẽ được đưa về 0.`)) {
        return;
    }

    try {
        const data = await apiCall('/api/admin/rescuers/' + rescuerId + '/clear-cash-debt', 'PUT');
        showToast(data.message, 'success');
        
        const row = document.getElementById('debt-row-' + rescuerId);
        if (row) {
            row.style.transition = 'all 0.5s ease';
            row.style.opacity = '0';
            row.style.transform = 'scale(0.9)';
            setTimeout(() => {
                const tbody = row.parentNode;
                row.remove();
                
                // Update badge count
                const badge = document.querySelector('.badge.bg-warning');
                if (badge) {
                    let count = parseInt(badge.textContent) || 0;
                    let newCount = Math.max(0, count - 1);
                    badge.textContent = newCount + ' đối tác';
                }
                
                if (tbody && tbody.children.length === 0) {
                    const tr = document.createElement('tr');
                    tr.innerHTML = '<td colspan="6" class="text-center text-muted py-4">Không có thợ nào đang giữ nợ tiền mặt.</td>';
                    tbody.appendChild(tr);
                }
            }, 500);
        } else {
            setTimeout(() => location.reload(), 1000);
        }
    } catch (error) {
        showToast(error.message, 'error');
    }
}

// === Remind Rescuer about Cash Debt Payment ===
async function remindPayment(rescuerId, button) {
    if (button) button.disabled = true;
    try {
        const data = await apiCall('/api/admin/rescuers/' + rescuerId + '/remind-payment', 'POST');
        showToast(data.message, 'success');
    } catch (error) {
        showToast(error.message, 'error');
    } finally {
        if (button) {
            setTimeout(() => { button.disabled = false; }, 3000);
        }
    }
}

// === View and Print Invoice Detail ===
async function viewInvoiceDetail(invoiceId) {
    try {
        const res = await apiCall('/api/admin/reports/invoices/' + invoiceId, 'GET');
        if (res.success) {
            const data = res.data;
            document.getElementById('print-inv-id').innerText = data.invoiceId.substring(0, 8).toUpperCase() + '...';
            
            // Format Date
            let dateStr = "N/A";
            if (data.createdAt) {
                const date = new Date(data.createdAt);
                dateStr = date.toLocaleDateString('vi-VN') + ' ' + date.toLocaleTimeString('vi-VN', {hour: '2-digit', minute:'2-digit'});
            }
            document.getElementById('print-inv-date').innerText = dateStr;
            document.getElementById('print-cust-name').innerText = data.customerName || 'N/A';
            document.getElementById('print-cust-sign').innerText = data.customerName || 'N/A';
            document.getElementById('print-cust-phone').innerText = data.customerPhone || 'N/A';
            document.getElementById('print-cust-address').innerText = data.customerAddress || 'N/A';
            document.getElementById('print-resc-name').innerText = data.rescuerName || 'Chưa nhận';
            document.getElementById('print-resc-phone').innerText = data.rescuerPhone || 'N/A';
            document.getElementById('print-issue-desc').innerText = data.issueDesc || 'N/A';
            document.getElementById('print-ai-diag').innerText = data.aiDiagnosis || 'N/A';
            
            // Format pricing
            const basePrice = data.basePrice || 0;
            const extraPrice = data.extraPrice || 0;
            const totalAmount = data.totalAmount || 0;
            
            document.getElementById('print-base-price').innerText = basePrice.toLocaleString('vi-VN') + ' ₫';
            document.getElementById('print-extra-price').innerText = extraPrice.toLocaleString('vi-VN') + ' ₫';
            document.getElementById('print-total-amount').innerText = totalAmount.toLocaleString('vi-VN') + ' ₫';
            
            document.getElementById('print-pay-method').innerText = data.paymentMethod;
            document.getElementById('print-pay-status').innerText = data.paid ? 'ĐÃ THANH TOÁN' : 'CHƯA THANH TOÁN';
            
            // Show modal using bootstrap API
            const myModal = new bootstrap.Modal(document.getElementById('invoicePrintModal'));
            myModal.show();
        } else {
            showToast(res.message, 'error');
        }
    } catch (error) {
        showToast(error.message, 'error');
    }
}

function printInvoice() {
    window.print();
}

// === Admin WebSocket Connection for Cash Debt Real-time Updates ===
let adminWebSocket = null;

function connectAdminWebSocket() {
    // Chỉ kết nối WebSocket nếu đang ở trang cash-debts
    if (!document.getElementById('debtsTable')) return;

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = `${protocol}//${window.location.host}/ws`;
    console.log("Admin connecting to WebSocket:", wsUrl);
    
    adminWebSocket = new WebSocket(wsUrl);
    
    adminWebSocket.onopen = function() {
        console.log("Admin WebSocket connected successfully.");
    };
    
    adminWebSocket.onmessage = function(event) {
        try {
            const msg = JSON.parse(event.data);
            console.log("Admin WebSocket received message:", msg);
            
            if (msg.type === 'DEBT_CLEAR') {
                const rescuerId = msg.rescuerId;
                const rescuerName = msg.rescuerName;
                const amountPaid = msg.amountPaid;
                const remainingDebt = parseFloat(msg.remainingDebt) || 0;
                
                // Phát tiếng chuông thanh toán thành công cực premium
                playSuccessSound();
                
                showToast(`Thợ cứu hộ ${rescuerName} đã thanh toán thành công ${amountPaid.toLocaleString('vi-VN')} ₫ qua Ngân hàng!`, 'success');
                
                if (remainingDebt <= 0) {
                    // Xóa dòng nợ khỏi bảng
                    const row = document.getElementById('debt-row-' + rescuerId);
                    if (row) {
                        row.style.transition = 'all 0.8s ease';
                        row.style.opacity = '0';
                        row.style.transform = 'scale(0.8) translateY(-20px)';
                        setTimeout(() => {
                            const tbody = row.parentNode;
                            row.remove();
                            
                            // Cập nhật đếm badge
                            const badge = document.getElementById('debts-count-badge');
                            if (badge) {
                                let count = tbody.children.length;
                                if (document.getElementById('no-debt-row')) count--;
                                badge.textContent = count + ' đối tác';
                            }
                            
                            if (tbody && tbody.children.length === 0) {
                                const tr = document.createElement('tr');
                                tr.id = 'no-debt-row';
                                tr.innerHTML = '<td colspan="4" class="text-center text-muted py-4">Không có thợ nào đang giữ nợ tiền mặt.</td>';
                                tbody.appendChild(tr);
                            }
                        }, 800);
                    }
                    
                    // Xóa thợ khỏi dropdown Simulator
                    const option = document.querySelector(`#sim-rescuer option[data-id="${rescuerId}"]`);
                    if (option) option.remove();
                } else {
                    // Cập nhật lại số tiền nợ hiển thị trong bảng
                    const debtValCell = document.getElementById('debt-val-' + rescuerId);
                    if (debtValCell) {
                        debtValCell.innerText = remainingDebt.toLocaleString('vi-VN') + ' ₫';
                        debtValCell.setAttribute('data-amount', remainingDebt);
                    }
                    
                    // Cập nhật lại số tiền nợ trong dropdown Simulator
                    const option = document.querySelector(`#sim-rescuer option[data-id="${rescuerId}"]`);
                    if (option) {
                        option.setAttribute('data-debt', remainingDebt);
                        option.textContent = `${rescuerName} (${option.value}) - Nợ: ${remainingDebt.toLocaleString('vi-VN')} ₫`;
                    }
                }
            }
        } catch (e) {
            console.error("Error processing Admin WebSocket message:", e);
        }
    };
    
    adminWebSocket.onclose = function() {
        console.log("Admin WebSocket closed. Reconnecting in 5 seconds...");
        setTimeout(connectAdminWebSocket, 5000);
    };
}

// === Audio Web API Success Sound Effect ===
function playSuccessSound() {
    try {
        const audioCtx = new (window.AudioContext || window.webkitAudioContext)();
        
        // Tiếng chuông 1
        const osc1 = audioCtx.createOscillator();
        const gain1 = audioCtx.createGain();
        osc1.connect(gain1);
        gain1.connect(audioCtx.destination);
        osc1.type = 'sine';
        osc1.frequency.setValueAtTime(587.33, audioCtx.currentTime); // D5
        gain1.gain.setValueAtTime(0.08, audioCtx.currentTime);
        gain1.gain.exponentialRampToValueAtTime(0.01, audioCtx.currentTime + 0.25);
        
        // Tiếng chuông 2 (tạo hòa âm "ping" sang trọng)
        const osc2 = audioCtx.createOscillator();
        const gain2 = audioCtx.createGain();
        osc2.connect(gain2);
        gain2.connect(audioCtx.destination);
        osc2.type = 'sine';
        osc2.frequency.setValueAtTime(880.00, audioCtx.currentTime + 0.08); // A5
        gain2.gain.setValueAtTime(0.08, audioCtx.currentTime + 0.08);
        gain2.gain.exponentialRampToValueAtTime(0.01, audioCtx.currentTime + 0.35);
        
        osc1.start();
        osc1.stop(audioCtx.currentTime + 0.25);
        osc2.start(audioCtx.currentTime + 0.08);
        osc2.stop(audioCtx.currentTime + 0.35);
    } catch (e) {
        console.log("Audio API blocked or not supported", e);
    }
}

// === Webhook Simulator Form Logic ===
function updateSimContent() {
    const select = document.getElementById('sim-rescuer');
    const contentInput = document.getElementById('sim-content');
    const amountInput = document.getElementById('sim-amount');
    
    if (!select || !contentInput || !amountInput) return;
    
    const phone = select.value;
    if (!phone) {
        contentInput.value = "Vui lòng chọn thợ...";
        amountInput.value = "";
        return;
    }
    
    // Tạo nội dung chuyển khoản tự động
    contentInput.value = "NOP TIEN " + phone;
    
    // Tự động điền số nợ hiện tại vào ô số tiền nộp
    const option = select.options[select.selectedIndex];
    if (option) {
        const debt = parseFloat(option.getAttribute('data-debt')) || 0;
        amountInput.value = debt;
    }
}

async function simulateBankTransfer(event) {
    event.preventDefault();
    const select = document.getElementById('sim-rescuer');
    const amountInput = document.getElementById('sim-amount');
    const contentInput = document.getElementById('sim-content');
    
    if (!select || !amountInput || !contentInput) return;
    
    const phone = select.value;
    const amount = amountInput.value;
    const content = contentInput.value;
    
    if (!phone || !amount || amount <= 0) {
        showToast("Vui lòng điền đầy đủ thông tin hợp lệ!", "error");
        return;
    }
    
    const payload = {
        amount: parseFloat(amount),
        content: content
    };
    
    try {
        const response = await fetch('/api/callback/bank-transfer', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(payload)
        });
        
        const data = await response.json();
        if (data.success) {
            showToast("Gửi tín hiệu Webhook chuyển tiền thành công!", "success");
            // Reset form
            document.getElementById('webhook-simulator-form').reset();
            updateSimContent();
        } else {
            showToast(data.message, "error");
        }
    } catch (error) {
        showToast("Lỗi gửi request giả lập: " + error.message, "error");
    }
}

// Khởi chạy WebSocket khi tải trang
document.addEventListener('DOMContentLoaded', function() {
    connectAdminWebSocket();
});

