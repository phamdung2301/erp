/**
 * e-Rescue Platform — Auth JavaScript
 * Registration flow, OTP, Profile management
 */

let selectedRole = 'CUSTOMER';

// === Role Selection ===
function selectRole(role) {
    selectedRole = role;
    document.getElementById('roleCustomer').classList.toggle('selected', role === 'CUSTOMER');
    document.getElementById('roleRescuer').classList.toggle('selected', role === 'RESCUER');
}

// === Step Navigation ===
function goToStep(step) {
    document.getElementById('step1').style.display = step === 1 ? 'block' : 'none';
    document.getElementById('step2').style.display = step === 2 ? 'block' : 'none';
    document.getElementById('step3').style.display = step === 3 ? 'block' : 'none';

    for (let i = 1; i <= 3; i++) {
        const el = document.getElementById('step-indicator-' + i);
        if (el) {
            el.classList.remove('active', 'completed');
            if (i < step) el.classList.add('completed');
            if (i === step) el.classList.add('active');
        }
    }
}

// === Step 1: Send OTP ===
async function sendOtp() {
    const phone = document.getElementById('regPhone').value.trim();

    if (!phone || phone.length < 9) {
        showToast('Vui lòng nhập số điện thoại hợp lệ!', 'error');
        return;
    }

    try {
        const response = await fetch('/api/auth/send-otp?phone=' + encodeURIComponent(phone), {
            method: 'POST'
        });
        const data = await response.json();

        if (data.success) {
            document.getElementById('otpPhone').textContent = phone;
            goToStep(2);
            showToast('OTP đã gửi! Mã: ' + data.data, 'success');
        } else {
            showToast(data.message, 'error');
        }
    } catch (error) {
        showToast('Lỗi kết nối server!', 'error');
    }
}

// === Step 2: Verify OTP ===
async function verifyOtp() {
    const phone = document.getElementById('regPhone').value.trim();
    const otp = document.getElementById('otpCode').value.trim();

    if (!otp || otp.length !== 6) {
        showToast('Vui lòng nhập đủ 6 số OTP!', 'error');
        return;
    }

    try {
        const response = await fetch('/api/auth/verify-otp?phone=' + encodeURIComponent(phone) + '&otp=' + encodeURIComponent(otp), {
            method: 'POST'
        });
        const data = await response.json();

        if (data.success) {
            goToStep(3);
            showToast('Xác nhận OTP thành công!', 'success');
        } else {
            showToast(data.message, 'error');
        }
    } catch (error) {
        showToast('Lỗi kết nối server!', 'error');
    }
}

// === Step 3: Complete Registration ===
async function completeRegister() {
    const phone = document.getElementById('regPhone').value.trim();
    const fullName = document.getElementById('regName').value.trim();
    const password = document.getElementById('regPassword').value;
    const confirmPassword = document.getElementById('regConfirmPassword').value;

    if (!fullName) {
        showToast('Vui lòng nhập họ và tên!', 'error');
        return;
    }
    if (password.length < 6) {
        showToast('Mật khẩu phải có ít nhất 6 ký tự!', 'error');
        return;
    }
    if (password !== confirmPassword) {
        showToast('Mật khẩu xác nhận không khớp!', 'error');
        return;
    }

    const endpoint = selectedRole === 'RESCUER' ? '/api/auth/register-rescuer' : '/api/auth/register';

    try {
        const data = await apiCall(endpoint, 'POST', {
            phone: phone,
            fullName: fullName,
            password: password,
            confirmPassword: confirmPassword
        });

        showToast(data.message, 'success');
        setTimeout(() => {
            window.location.href = '/login';
        }, 2000);
    } catch (error) {
        showToast(error.message, 'error');
    }
}

// === Profile Update ===
async function updateProfile() {
    const fullName = document.getElementById('profileName').value.trim();
    const email = document.getElementById('profileEmail').value.trim();
    const currentPassword = document.getElementById('currentPassword').value;
    const newPassword = document.getElementById('newPassword').value;

    try {
        const data = await apiCall('/api/auth/profile', 'PUT', {
            fullName: fullName,
            email: email,
            currentPassword: currentPassword || null,
            newPassword: newPassword || null
        });

        showToast(data.message, 'success');
        // Clear password fields
        document.getElementById('currentPassword').value = '';
        document.getElementById('newPassword').value = '';
    } catch (error) {
        showToast(error.message, 'error');
    }
}

// === Avatar Upload ===
async function uploadAvatar(input) {
    if (!input.files || !input.files[0]) return;

    const formData = new FormData();
    formData.append('file', input.files[0]);

    try {
        const data = await apiFormCall('/api/auth/avatar', formData);
        showToast(data.message, 'success');
        // Reload to show new avatar
        setTimeout(() => location.reload(), 1000);
    } catch (error) {
        showToast(error.message, 'error');
    }
}

// === Toggle 2FA ===
async function toggle2FA() {
    try {
        const response = await fetch('/api/auth/toggle-2fa', { method: 'POST' });
        const data = await response.json();

        if (data.success) {
            showToast(data.message, 'success');
        } else {
            showToast(data.message, 'error');
            // Revert toggle
            const toggle = document.getElementById('toggle2fa');
            if (toggle) toggle.checked = !toggle.checked;
        }
    } catch (error) {
        showToast('Lỗi kết nối server!', 'error');
    }
}
