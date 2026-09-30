/**
 * RIMS - Rental Item Management System
 * Full-Stack Client Controller & REST API Integration
 */

// Application State
const state = {
    authToken: localStorage.getItem("rims_auth_token") || null,
    currentUser: null, // { userId, name, email, role, phone, createdAt }
    catalogItems: [],
    activeCategory: "ALL",
    searchQuery: "",
    availableOnly: false,
    selectedItemForRent: null,
    paymentSimulationConfirmed: false,
    currentView: "explore"
};

const CATEGORY_ICONS = {
    "Photography": "📷",
    "Electronics": "📽️",
    "Audio & Sound": "🔊",
    "Tools": "🔧",
    "Outdoor & Camping": "⛺",
    "Sports & Mobility": "🚲",
    "Gaming": "🎮",
    "Other": "📦"
};

// ==========================================================================
// Initialization
// ==========================================================================

document.addEventListener("DOMContentLoaded", async () => {
    initDefaultDates();
    if (state.authToken) {
        await verifyCurrentSession();
    } else {
        renderNavigation();
        loadCatalog();
    }
});

// Format Indian Rupee currency (INR)
function formatINR(amount) {
    if (amount === null || amount === undefined || isNaN(amount)) return "₹0";
    return new Intl.NumberFormat("en-IN", {
        style: "currency",
        currency: "INR",
        maximumFractionDigits: 0
    }).format(amount);
}

function initDefaultDates() {
    const today = new Date();
    const tomorrow = new Date();
    tomorrow.setDate(today.getDate() + 1);

    const fmt = d => d.toISOString().split("T")[0];
    const startEl = document.getElementById("rental-start-date");
    const endEl = document.getElementById("rental-end-date");

    if (startEl && endEl) {
        startEl.min = fmt(today);
        startEl.value = fmt(today);
        endEl.min = fmt(today);
        endEl.value = fmt(tomorrow);
    }
}

// ==========================================================================
// Session & Role-Based Navigation
// ==========================================================================

async function verifyCurrentSession() {
    try {
        const res = await apiRequest("/api/auth/me");
        if (res.user) {
            state.currentUser = res.user;
            renderNavigation();

            if (state.currentUser.role === "ADMIN") {
                showAdminView("dashboard");
            } else {
                showCustomerView("explore");
            }
            return;
        }
    } catch (err) {
        console.warn("Session expired or invalid:", err);
    }
    logout(false);
}

function renderNavigation() {
    const custNav = document.getElementById("customer-nav");
    const adminNav = document.getElementById("admin-nav");
    const guestNav = document.getElementById("guest-nav");
    const brandTitle = document.getElementById("header-brand-title");

    custNav.style.display = "none";
    adminNav.style.display = "none";
    guestNav.style.display = "none";

    if (brandTitle) {
        brandTitle.textContent = "Rental Item Management System";
    }

    if (!state.currentUser) {
        guestNav.style.display = "flex";
        return;
    }

    if (state.currentUser.role === "ADMIN") {
        adminNav.style.display = "flex";
    } else {
        custNav.style.display = "flex";
    }
}

function handleBrandClick() {
    if (state.currentUser && state.currentUser.role === "ADMIN") {
        showAdminView("dashboard");
    } else {
        showCustomerView("explore");
    }
}

// ==========================================================================
// Authentication Flows (Login, Register, Logout)
// ==========================================================================

function openLoginModal(mode = "login") {
    setAuthMode(mode);
    openModal("modal-auth");
}

function setAuthMode(mode) {
    const isLogin = mode === "login";
    document.getElementById("auth-panel-login").style.display = isLogin ? "block" : "none";
    document.getElementById("auth-panel-register").style.display = isLogin ? "none" : "block";
    document.getElementById("tab-btn-login").classList.toggle("active", isLogin);
    document.getElementById("tab-btn-register").classList.toggle("active", !isLogin);
    document.getElementById("auth-modal-title").textContent = isLogin ? "Sign In to Your Account" : "Register Customer Account";
}

async function submitLogin(e) {
    e.preventDefault();
    const email = document.getElementById("auth-email").value.trim();
    const password = document.getElementById("auth-password").value;
    const btn = document.getElementById("btn-submit-login");

    btn.disabled = true;
    btn.textContent = "Verifying...";

    try {
        const res = await fetch("/api/auth/login", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ email, password })
        });
        const data = await res.json();

        if (data.success && data.user) {
            state.authToken = data.token;
            state.currentUser = data.user;
            localStorage.setItem("rims_auth_token", data.token);

            closeModal("modal-auth");
            renderNavigation();
            showToast("Login successful", "success");

            if (data.user.role === "ADMIN") {
                showAdminView("dashboard");
            } else {
                showCustomerView("explore");
            }
        } else {
            showToast(data.error || "Invalid email or password", "error");
        }
    } catch (err) {
        showToast("Connection to server failed. Please try again.", "error");
    } finally {
        btn.disabled = false;
        btn.textContent = "Sign In";
    }
}

async function submitRegister(e) {
    e.preventDefault();
    const name = document.getElementById("reg-name").value.trim();
    const email = document.getElementById("reg-email").value.trim();
    const phone = document.getElementById("reg-phone").value.trim();
    const password = document.getElementById("reg-password").value;
    const btn = document.getElementById("btn-submit-register");

    btn.disabled = true;
    btn.textContent = "Creating Account...";

    try {
        const res = await fetch("/api/auth/register", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ name, email, phone, password })
        });
        const data = await res.json();

        if (data.success && data.user) {
            state.authToken = data.token;
            state.currentUser = data.user;
            localStorage.setItem("rims_auth_token", data.token);

            closeModal("modal-auth");
            renderNavigation();
            showToast(`Welcome, ${data.user.name}! Account registered successfully.`, "success");
            showCustomerView("explore");
        } else {
            showToast(data.error || "Failed to register account", "error");
        }
    } catch (err) {
        showToast("Connection to server failed", "error");
    } finally {
        btn.disabled = false;
        btn.textContent = "Create Customer Account";
    }
}

async function logout(callApi = true) {
    if (callApi && state.authToken) {
        try {
            await apiRequest("/api/auth/logout", "POST");
        } catch (ignored) {}
    }
    state.authToken = null;
    state.currentUser = null;
    localStorage.removeItem("rims_auth_token");
    renderNavigation();
    showCustomerView("explore");
    showToast("Signed out successfully", "info");
}

// ==========================================================================
// Customer Interface Views
// ==========================================================================

function showCustomerView(viewName) {
    state.currentView = viewName;

    // Hide all views
    document.querySelectorAll(".app-view").forEach(v => v.classList.remove("active"));
    document.querySelectorAll("#customer-nav .nav-link").forEach(l => l.classList.remove("active"));

    if (viewName === "explore") {
        document.getElementById("view-customer-explore").classList.add("active");
        const btn = document.getElementById("c-nav-explore");
        if (btn) btn.classList.add("active");
        loadCatalog();
    } else if (viewName === "rentals") {
        if (!state.currentUser) {
            openLoginModal("login");
            return;
        }
        document.getElementById("view-customer-rentals").classList.add("active");
        const btn = document.getElementById("c-nav-rentals");
        if (btn) btn.classList.add("active");
        loadMyRentals();
    } else if (viewName === "profile") {
        if (!state.currentUser) {
            openLoginModal("login");
            return;
        }
        document.getElementById("view-customer-profile").classList.add("active");
        const btn = document.getElementById("c-nav-profile");
        if (btn) btn.classList.add("active");
        loadProfileData();
    }
}

// Load catalog items from DB
async function loadCatalog() {
    const grid = document.getElementById("items-grid-container");
    const countLabel = document.getElementById("catalog-results-count");

    try {
        let url = `/api/items?`;
        if (state.activeCategory !== "ALL") {
            url += `category=${encodeURIComponent(state.activeCategory)}&`;
        }
        if (state.searchQuery) {
            url += `search=${encodeURIComponent(state.searchQuery)}&`;
        }

        const items = await apiRequest(url);
        state.catalogItems = items;

        let filtered = items;
        if (state.availableOnly) {
            filtered = filtered.filter(i => i.available);
        }

        countLabel.textContent = `Showing ${filtered.length} equipment item${filtered.length === 1 ? '' : 's'}`;

        if (!filtered || filtered.length === 0) {
            grid.innerHTML = `
                <div class="empty-state">
                    <h3>No items found</h3>
                    <p>Try searching for a different keyword or select another category.</p>
                </div>
            `;
            return;
        }

        grid.innerHTML = filtered.map(item => {
            const icon = CATEGORY_ICONS[item.category] || "📦";
            const isAvail = item.available;
            const statusBadge = isAvail
                ? `<span class="badge badge-success">Available today</span>`
                : `<span class="badge badge-warning">Check selected dates</span>`;

            return `
                <div class="rental-card" id="card-${item.itemId}">
                    <div class="card-media-banner">
                        <div class="card-top-meta">
                            <span class="card-cat-name">${item.category}</span>
                            ${statusBadge}
                        </div>
                        <div class="card-item-icon-box" title="${item.category}">${icon}</div>
                    </div>
                    <div class="card-content-body">
                        <div class="card-sku">ID: ${item.itemId}</div>
                        <h3 class="card-item-title" title="${item.name}">${item.name}</h3>
                        <p class="card-item-desc">${item.description || "Professional equipment available for immediate rental."}</p>
                    </div>
                    <div class="card-footer-action">
                        <div class="card-price-block">
                            <span class="card-price-value">${formatINR(item.rentalPrice)}</span>
                            <span class="card-price-unit">/ day</span>
                        </div>
                        <button class="btn ${isAvail ? 'btn-primary' : 'btn-secondary'} btn-sm" 
                                onclick="openRentModal('${item.itemId}')">
                            ${isAvail ? "Rent Now" : "Select Dates"}
                        </button>
                    </div>
                </div>
            `;
        }).join("");
    } catch (err) {
        console.error("Error loading catalog:", err);
        grid.innerHTML = `<div class="empty-state"><p>Failed to load rental items from server.</p></div>`;
    }
}

function handleCatalogSearch() {
    state.searchQuery = document.getElementById("catalog-search-input").value.trim();
    loadCatalog();
}

function handleCatalogFilter() {
    state.availableOnly = document.getElementById("filter-available-checkbox").checked;
    loadCatalog();
}

function filterByCategory(cat) {
    state.activeCategory = cat;
    document.querySelectorAll(".category-btn").forEach(btn => {
        btn.classList.toggle("active", btn.dataset.cat === cat);
    });
    loadCatalog();
}

// --------------------------------------------------------------------------
// Customer Rental Flow
// --------------------------------------------------------------------------

// --------------------------------------------------------------------------
// Customer Rental Flow
// --------------------------------------------------------------------------

function openRentModal(itemId) {
    if (!state.currentUser) {
        showToast("Please sign in or register to rent items", "info");
        openLoginModal("login");
        return;
    }

    const item = state.catalogItems.find(i => i.itemId === itemId);
    if (!item) return;

    state.selectedItemForRent = item;
    state.paymentSimulationConfirmed = false;

    document.getElementById("rental-item-id").value = item.itemId;
    document.getElementById("rental-preview-name").textContent = item.name;
    document.getElementById("rental-preview-category").textContent = item.category;
    document.getElementById("rental-preview-price").textContent = formatINR(item.rentalPrice);
    document.getElementById("rental-preview-icon").textContent = CATEGORY_ICONS[item.category] || "📦";

    // Reset card fields
    const cardName = document.getElementById("card-holder-name");
    const cardNum = document.getElementById("card-number");
    const cardExp = document.getElementById("card-expiry");
    const cardCvv = document.getElementById("card-cvv");
    if (cardName) cardName.value = "Demo User";
    if (cardNum) cardNum.value = "0000 0000 0000 0000";
    if (cardExp) cardExp.value = "12/30";
    if (cardCvv) cardCvv.value = "000";
    const paymentOutcome = document.getElementById("demo-payment-outcome");
    if (paymentOutcome) paymentOutcome.value = "SUCCESS";

    // Reset UPI state
    const upiBanner = document.getElementById("upi-confirmed-banner");
    if (upiBanner) upiBanner.style.display = "none";
    const upiBtn = document.getElementById("btn-upi-pay");
    if (upiBtn) {
        upiBtn.disabled = false;
        upiBtn.classList.remove("btn-success");
        upiBtn.classList.add("btn-primary");
        upiBtn.textContent = "Pay via UPI";
    }

    initDefaultDates();
    handlePaymentMethodChange();
    updateRentalCalculation();
    openModal("modal-rental");
}

function handlePaymentMethodChange() {
    resetPaymentSimulation();
    const methodSelect = document.getElementById("rental-payment-method");
    if (!methodSelect) return;
    const method = methodSelect.value;

    const upiSec = document.getElementById("pay-section-upi");
    const cardSec = document.getElementById("pay-section-card");
    const cashSec = document.getElementById("pay-section-cash");
    const outcomeGroup = document.getElementById("demo-payment-outcome-group");
    const cardTitle = document.getElementById("card-method-title");

    if (upiSec) upiSec.style.display = "none";
    if (cardSec) cardSec.style.display = "none";
    if (cashSec) cashSec.style.display = "none";

    if (method === "UPI" || method === "NET_BANKING") {
        if (upiSec) upiSec.style.display = "block";
        if (outcomeGroup) outcomeGroup.style.display = "block";
    } else if (method === "CREDIT_CARD" || method === "DEBIT_CARD") {
        if (cardSec) cardSec.style.display = "block";
        if (outcomeGroup) outcomeGroup.style.display = "block";
        if (cardTitle) {
            cardTitle.textContent = method === "CREDIT_CARD" ? "Credit Card Test Fields" : "Debit Card Test Fields";
        }
    } else if (method === "CASH_ON_PICKUP") {
        if (cashSec) cashSec.style.display = "block";
        if (outcomeGroup) outcomeGroup.style.display = "none";
    }
}

function confirmUpiPayment() {
    state.paymentSimulationConfirmed = true;

    const banner = document.getElementById("upi-confirmed-banner");
    if (banner) banner.style.display = "flex";

    const btn = document.getElementById("btn-upi-pay");
    if (btn) {
        btn.disabled = true;
        btn.classList.remove("btn-primary");
        btn.classList.add("btn-success");
        btn.textContent = "✓ Simulation ready";
    }

    showToast("Simulated payment ready. No money will be moved.", "info");
}

function resetPaymentSimulation() {
    state.paymentSimulationConfirmed = false;
    const banner = document.getElementById("upi-confirmed-banner");
    const btn = document.getElementById("btn-upi-pay");
    if (banner) banner.style.display = "none";
    if (btn) {
        btn.disabled = false;
        btn.classList.remove("btn-success");
        btn.classList.add("btn-primary");
        btn.textContent = "Simulate payment result";
    }
}

function formatCardNumber(input) {
    let value = input.value.replace(/\D/g, "");
    if (value.length > 16) value = value.slice(0, 16);
    let parts = [];
    for (let i = 0; i < value.length; i += 4) {
        parts.push(value.substring(i, i + 4));
    }
    input.value = parts.join(" ");
}

function formatCardExpiry(input) {
    let value = input.value.replace(/\D/g, "");
    if (value.length > 4) value = value.slice(0, 4);
    if (value.length >= 2) {
        input.value = value.substring(0, 2) + "/" + value.substring(2);
    } else {
        input.value = value;
    }
}

function updateRentalCalculation() {
    const item = state.selectedItemForRent;
    if (!item) return;

    const startVal = document.getElementById("rental-start-date").value;
    const endVal = document.getElementById("rental-end-date").value;

    if (!startVal || !endVal) return;

    const start = new Date(startVal);
    const end = new Date(endVal);

    let days = Math.round((end - start) / (1000 * 60 * 60 * 24));
    if (days <= 0) days = 1;

    const rentalAmount = days * item.rentalPrice;
    const lateRatePerDay = Math.round(item.rentalPrice * 1.5);
    const lateFee = 0; // Current scheduled reservation has 0 late fee
    const totalAmount = rentalAmount + lateFee;

    // Elements
    const durEl = document.getElementById("calc-duration-days");
    const rateEl = document.getElementById("calc-daily-rate");
    const rentAmtEl = document.getElementById("calc-rental-amount");
    const currLateEl = document.getElementById("rental-curr-late-fee");
    const lateRateEl = document.getElementById("rental-late-rate");
    const calcLateEl = document.getElementById("calc-late-fee");
    const formRentEl = document.getElementById("formula-rental-amt");
    const formLateEl = document.getElementById("formula-late-amt");
    const formTotEl = document.getElementById("formula-total-amt");
    const totEl = document.getElementById("calc-total-amount");
    const upiDisplayEl = document.getElementById("upi-display-amount");
    const upiBtnAmountEl = document.getElementById("upi-btn-amount");
    const cashDisplayEl = document.getElementById("cash-display-amount");

    if (durEl) durEl.textContent = `${days} day${days === 1 ? '' : 's'}`;
    if (rateEl) rateEl.textContent = `${formatINR(item.rentalPrice)} / day`;
    if (rentAmtEl) rentAmtEl.textContent = formatINR(rentalAmount);
    if (currLateEl) currLateEl.textContent = formatINR(0);
    if (lateRateEl) lateRateEl.textContent = `${formatINR(lateRatePerDay)} per extra day`;
    if (calcLateEl) calcLateEl.textContent = `₹0 (Current)`;
    if (formRentEl) formRentEl.textContent = formatINR(rentalAmount);
    if (formLateEl) formLateEl.textContent = `₹0`;
    if (formTotEl) formTotEl.textContent = formatINR(totalAmount);
    if (totEl) totEl.textContent = formatINR(totalAmount);
    if (upiDisplayEl) upiDisplayEl.textContent = formatINR(totalAmount);
    if (upiBtnAmountEl) upiBtnAmountEl.textContent = formatINR(totalAmount);
    if (cashDisplayEl) cashDisplayEl.textContent = formatINR(totalAmount);

    if (state.paymentSimulationConfirmed) {
        const upiBtn = document.getElementById("btn-upi-pay");
        if (upiBtn) upiBtn.textContent = `✓ Payment Confirmed (${formatINR(totalAmount)})`;
    }
}

async function submitRentalBooking(e) {
    e.preventDefault();
    const item = state.selectedItemForRent;
    if (!item) return;

    const startDate = document.getElementById("rental-start-date").value;
    const endDate = document.getElementById("rental-end-date").value;
    const paymentMethod = document.getElementById("rental-payment-method").value;
    const btn = document.getElementById("btn-submit-rental");

    // Dynamic Payment Validation
    if (paymentMethod === "UPI" || paymentMethod === "NET_BANKING") {
        if (!state.paymentSimulationConfirmed) {
            showToast("Choose the simulated payment result first.", "warning");
            const upiBtn = document.getElementById("btn-upi-pay");
            if (upiBtn) upiBtn.focus();
            return;
        }
    } else if (paymentMethod === "CREDIT_CARD" || paymentMethod === "DEBIT_CARD") {
        const cardName = document.getElementById("card-holder-name").value.trim();
        const cardNum = document.getElementById("card-number").value.replace(/\s+/g, "");
        const cardExp = document.getElementById("card-expiry").value.trim();
        const cardCvv = document.getElementById("card-cvv").value.trim();

        if (!cardName) {
            showToast("Please enter the cardholder name", "error");
            document.getElementById("card-holder-name").focus();
            return;
        }
        if (cardNum !== "0000000000000000") {
            showToast("Use the test-only card number shown in this demo.", "error");
            document.getElementById("card-number").focus();
            return;
        }
        if (cardExp !== "12/30") {
            showToast("Use the test-only expiry date shown in this demo.", "error");
            document.getElementById("card-expiry").focus();
            return;
        }
        if (cardCvv !== "000") {
            showToast("Use the test-only security code shown in this demo.", "error");
            document.getElementById("card-cvv").focus();
            return;
        }
    }

    try {
        const availability = await apiRequest(`/api/items?id=${encodeURIComponent(item.itemId)}&startDate=${encodeURIComponent(startDate)}&endDate=${encodeURIComponent(endDate)}`);
        if (!availability.available) {
            showToast("This item is already booked for those dates. Choose another date range.", "error");
            return;
        }
    } catch (err) {
        showToast(err.message || "Could not check date availability.", "error");
        return;
    }

    btn.disabled = true;
    btn.textContent = "Processing...";

    try {
        const res = await apiRequest("/api/rentals", "POST", {
            itemId: item.itemId,
            startDate,
            endDate,
            paymentMethod,
            paymentOutcome: document.getElementById("demo-payment-outcome")?.value || "SUCCESS"
        });

        if (res.paymentFailed) {
            closeModal("modal-rental");
            showToast("Simulated payment failed. No charge was made; choose another method or try again.", "error");
            loadCatalog();
            showCustomerView("rentals");
        } else if (res.success) {
            closeModal("modal-rental");
            if (paymentMethod === "CASH_ON_PICKUP") {
                showToast("Booking confirmed. Cash-on-pickup payment is pending.", "info");
            } else {
                showToast(`Booking confirmed. Simulated payment recorded (${res.payment.demoTransactionRef}).`, "success");
            }
            loadCatalog();
            showCustomerView("rentals");
        } else {
            showToast(res.error || "This item is currently unavailable.", "error");
        }
    } catch (err) {
        showToast(err.message || "Failed to submit rental order.", "error");
    } finally {
        btn.disabled = false;
        btn.textContent = "Confirm Rental";
    }
}

// --------------------------------------------------------------------------
// Customer: My Rentals
// --------------------------------------------------------------------------

async function loadMyRentals() {
    const listEl = document.getElementById("my-rentals-list");
    const badgeEl = document.getElementById("customer-active-badge");

    try {
        const rentals = await apiRequest("/api/rentals/my");
        const activeCount = rentals.filter(r => ["CONFIRMED", "ACTIVE", "APPROVED"].includes(r.status)).length;
        if (badgeEl) badgeEl.textContent = activeCount;

        if (!rentals || rentals.length === 0) {
            listEl.innerHTML = `
                <div class="empty-state">
                    <h3>No rental bookings found</h3>
                    <p>You haven't rented any equipment yet. Explore our inventory to make your first booking!</p>
                    <button class="btn btn-primary btn-sm mt-4" onclick="showCustomerView('explore')">Explore Items</button>
                </div>
            `;
            return;
        }

        listEl.innerHTML = rentals.map(r => {
            const icon = (r.item && CATEGORY_ICONS[r.item.category]) || "📦";
            const itemName = r.item ? r.item.name : "Equipment";
            const isActive = ["CONFIRMED", "ACTIVE", "APPROVED"].includes(r.status);

            let badgeClass = "badge-neutral";
            if (["CONFIRMED", "ACTIVE", "APPROVED"].includes(r.status)) badgeClass = "badge-success";
            else if (r.status === "PENDING") badgeClass = "badge-warning";
            else if (r.status === "RETURNED") badgeClass = "badge-info";
            else if (r.status === "CANCELLED" || r.status === "REJECTED") badgeClass = "badge-error";

            return `
                <div class="rental-record-card">
                    <div class="record-main-info">
                        <div class="record-icon-badge">${icon}</div>
                        <div>
                            <span class="record-id">RENTAL ID: ${r.rentalId}</span>
                            <h3 class="record-name">${itemName}</h3>
                            <div class="record-dates">
                                📅 ${r.startDate} to ${r.endDate} (${r.numberOfDays} days)
                            </div>
                            <div class="record-dates">Payment: ${r.paymentMethod || "—"} · ${r.paymentStatus || "PENDING"}</div>
                        </div>
                    </div>
                    <div class="record-financials">
                        <div class="record-total">${formatINR(r.totalAmount)}</div>
                        <div class="record-rate">Rental amount</div>
                        <div class="record-rate">Late fee: ${formatINR(r.lateFee || 0)}</div>
                        <div class="record-rate"><strong>Total due: ${formatINR(r.totalDue ?? r.totalAmount)}</strong></div>
                    </div>
                    <div>
                        <span class="badge ${badgeClass}">${r.status}</span>
                    </div>
                    <div>
                        ${isActive ? `
                            <button class="btn btn-primary btn-sm" onclick="returnRental('${r.rentalId}')">
                                Return Item
                            </button>
                        ` : `
                            <button class="btn btn-outline btn-sm" disabled>Completed</button>
                        `}
                        ${r.lateFeeStatus === "PENDING" ? `<button class="btn btn-secondary btn-sm mt-4" onclick="simulateLateFeePayment('${r.rentalId}')">Pay Late Fee (Test)</button>` : ""}
                    </div>
                </div>
            `;
        }).join("");
    } catch (err) {
        console.error("Error loading my rentals:", err);
    }
}

async function returnRental(rentalId) {
    if (!confirm("Are you sure you want to return this rented equipment?")) return;

    try {
        const res = await apiRequest("/api/rentals/status", "PUT", {
            rentalId,
            status: "RETURNED"
        });
        if (res.success) {
            showToast(res.rental && res.rental.lateFee > 0
                ? `Returned. Late fee due: ${formatINR(res.rental.lateFee)}.`
                : "Equipment marked as returned. No late fee is due.", "success");
            loadMyRentals();
            loadCatalog();
        } else {
            showToast(res.error || "Failed to process return.", "error");
        }
    } catch (err) {
        showToast(err.message || "Error processing return.", "error");
    }
}

async function simulateLateFeePayment(rentalId) {
    try {
        const res = await apiRequest("/api/payments/simulate", "POST", { rentalId, paymentMethod: "UPI" });
        showToast(`Late fee paid in test mode (${res.payment.demoTransactionRef}). No money moved.`, "success");
        loadMyRentals();
        loadCatalog();
    } catch (err) {
        showToast(err.message || "Could not simulate late-fee payment.", "error");
    }
}

// --------------------------------------------------------------------------
// Customer: Profile Page
// --------------------------------------------------------------------------

async function loadProfileData() {
    try {
        const data = await apiRequest("/api/profile");
        const u = data.user;

        document.getElementById("profile-display-name").textContent = u.name;
        document.getElementById("profile-display-email").textContent = u.email;
        document.getElementById("profile-display-phone").textContent = u.phone || "Not set";
        document.getElementById("profile-display-date").textContent = u.createdAt || "2026-09-29";

        const initials = u.name.split(" ").map(n => n[0]).join("").substring(0, 2).toUpperCase();
        document.getElementById("profile-avatar-letters").textContent = initials || "U";

        document.getElementById("profile-stat-total").textContent = data.totalRentals || 0;
        document.getElementById("profile-stat-active").textContent = data.activeRentals || 0;
        document.getElementById("profile-stat-spent").textContent = formatINR(data.totalSpent || 0);

        document.getElementById("edit-profile-name").value = u.name;
        document.getElementById("edit-profile-phone").value = u.phone || "";
        document.getElementById("edit-profile-email").value = u.email;
    } catch (err) {
        console.error("Error loading profile:", err);
    }
}

async function submitProfileUpdate(e) {
    e.preventDefault();
    const name = document.getElementById("edit-profile-name").value.trim();
    const phone = document.getElementById("edit-profile-phone").value.trim();
    const btn = document.getElementById("btn-save-profile");

    btn.disabled = true;
    btn.textContent = "Saving...";

    try {
        const res = await apiRequest("/api/profile", "PUT", { name, phone });
        if (res.success && res.user) {
            state.currentUser = res.user;
            renderNavigation();
            showToast("Profile updated successfully.", "success");
            loadProfileData();
        } else {
            showToast(res.error || "Failed to update profile", "error");
        }
    } catch (err) {
        showToast("Error updating profile", "error");
    } finally {
        btn.disabled = false;
        btn.textContent = "Save Changes";
    }
}

// ==========================================================================
// Admin Interface Views (Strict Separation)
// ==========================================================================

function showAdminView(viewName) {
    if (!state.currentUser || state.currentUser.role !== "ADMIN") {
        showToast("Unauthorized: Admin privileges required", "error");
        showCustomerView("explore");
        return;
    }

    state.currentView = `admin-${viewName}`;

    document.querySelectorAll(".app-view").forEach(v => v.classList.remove("active"));
    document.querySelectorAll("#admin-nav .nav-link").forEach(l => l.classList.remove("active"));

    if (viewName === "dashboard") {
        document.getElementById("view-admin-dashboard").classList.add("active");
        document.getElementById("a-nav-dashboard").classList.add("active");
        loadAdminDashboardData();
    } else if (viewName === "items") {
        document.getElementById("view-admin-items").classList.add("active");
        document.getElementById("a-nav-items").classList.add("active");
        loadAdminItems();
    } else if (viewName === "customers") {
        document.getElementById("view-admin-customers").classList.add("active");
        document.getElementById("a-nav-customers").classList.add("active");
        loadAdminCustomers();
    } else if (viewName === "rentals") {
        document.getElementById("view-admin-rentals").classList.add("active");
        document.getElementById("a-nav-rentals").classList.add("active");
        loadAdminRentals();
    } else if (viewName === "analytics") {
        document.getElementById("view-admin-analytics").classList.add("active");
        document.getElementById("a-nav-analytics").classList.add("active");
        loadAdminAnalytics();
    }
}

// --------------------------------------------------------------------------
// Admin: Dashboard
// --------------------------------------------------------------------------

async function loadAdminDashboardData() {
    try {
        const stats = await apiRequest("/api/admin/analytics");

        document.getElementById("kpi-total-revenue").textContent = formatINR(stats.totalRevenue || 0);
        document.getElementById("kpi-total-items").textContent = stats.totalItems || 0;
        document.getElementById("kpi-available-items").textContent = stats.availableItems || 0;
        document.getElementById("kpi-active-rentals").textContent = stats.activeRentals || 0;
        document.getElementById("kpi-total-customers").textContent = stats.totalCustomers || 0;
        document.getElementById("kpi-total-rentals").textContent = stats.totalRentals || 0;
        document.getElementById("kpi-pending-payments").textContent = stats.pendingPayments || 0;
        document.getElementById("kpi-late-fees-due").textContent = formatINR(stats.lateFeesDue || 0);

        // Load recent rentals
        const rentals = await apiRequest("/api/rentals");
        const tbody = document.getElementById("dashboard-recent-rentals-tbody");

        const recent = rentals.slice(0, 5);
        if (!recent || recent.length === 0) {
            tbody.innerHTML = `<tr><td colspan="10" class="text-center text-muted">No rental activity recorded yet.</td></tr>`;
            return;
        }

        tbody.innerHTML = recent.map(r => {
            const customerName = r.customer ? r.customer.name : "N/A";
            const itemName = r.item ? r.item.name : "N/A";
            const isActive = ["CONFIRMED", "ACTIVE", "APPROVED"].includes(r.status);

            return `
                <tr>
                    <td><code>${r.rentalId}</code></td>
                    <td><strong>${customerName}</strong></td>
                    <td>${itemName}</td>
                    <td>${r.startDate} to ${r.endDate} (${r.numberOfDays}d)</td>
                    <td>${formatINR(r.dailyRate)}</td>
                    <td><strong>${formatINR(r.totalAmount)}</strong></td>
                    <td>${r.paymentMethod || "—"}<div class="text-sm text-muted">${r.paymentStatus || "PENDING"}</div></td>
                    <td>${formatINR(r.lateFee || 0)}<div class="text-sm text-muted">Due: ${formatINR(r.totalDue ?? r.totalAmount)}</div></td>
                    <td><span class="badge ${isActive ? 'badge-success' : 'badge-neutral'}">${r.status}</span></td>
                    <td>
                        ${isActive ? `
                            <button class="btn btn-outline btn-sm" onclick="setAdminRentalStatus('${r.rentalId}', 'RETURNED')">Mark Returned</button>
                        ` : `<span class="text-muted text-sm">—</span>`}
                    </td>
                </tr>
            `;
        }).join("");
    } catch (err) {
        console.error("Error loading admin dashboard:", err);
    }
}

// --------------------------------------------------------------------------
// Admin: Manage Items
// --------------------------------------------------------------------------

async function loadAdminItems() {
    const tbody = document.getElementById("admin-items-tbody");
    try {
        const items = await apiRequest("/api/items");

        tbody.innerHTML = items.map(item => `
            <tr>
                <td><code>${item.itemId}</code></td>
                <td>
                    <strong>${item.name}</strong>
                    <div class="text-sm text-muted">${item.category}</div>
                </td>
                <td><strong>${formatINR(item.rentalPrice)}</strong> / day</td>
                <td>
                    <span class="badge ${item.available ? 'badge-success' : 'badge-warning'}">
                        ${item.available ? 'Available' : 'Rented / Inactive'}
                    </span>
                </td>
                <td>
                    <div class="flex-gap">
                        <button class="btn btn-outline btn-sm" onclick="openEditItemModal('${item.itemId}')">Edit</button>
                        <button class="btn btn-outline btn-sm" onclick="toggleItemAvailability('${item.itemId}', ${item.available})">
                            ${item.available ? 'Set Unavailable' : 'Set Available'}
                        </button>
                        <button class="btn btn-danger btn-sm" onclick="deleteAdminItem('${item.itemId}')">Delete</button>
                    </div>
                </td>
            </tr>
        `).join("");
    } catch (err) {
        console.error("Error loading admin items:", err);
    }
}

function openAddItemModal() {
    document.getElementById("admin-item-form").reset();
    document.getElementById("item-edit-id").value = "";
    document.getElementById("item-modal-title").textContent = "Add Inventory Item";
    openModal("modal-item");
}

async function openEditItemModal(itemId) {
    try {
        const item = await apiRequest(`/api/items?id=${encodeURIComponent(itemId)}`);
        if (!item) return;

        document.getElementById("item-edit-id").value = item.itemId;
        document.getElementById("item-edit-name").value = item.name;
        document.getElementById("item-edit-category").value = item.category;
        document.getElementById("item-edit-price").value = item.rentalPrice;
        document.getElementById("item-edit-desc").value = item.description || "";
        document.getElementById("item-edit-icon").value = item.imageUrl || "other";
        document.getElementById("item-edit-available").value = String(item.available);
        document.getElementById("item-modal-title").textContent = "Edit Inventory Item";

        openModal("modal-item");
    } catch (err) {
        showToast("Failed to fetch item details", "error");
    }
}

async function submitAdminItemForm(e) {
    e.preventDefault();
    const itemId = document.getElementById("item-edit-id").value;
    const name = document.getElementById("item-edit-name").value.trim();
    const category = document.getElementById("item-edit-category").value;
    const rentalPrice = document.getElementById("item-edit-price").value;
    const description = document.getElementById("item-edit-desc").value.trim();
    const imageUrl = document.getElementById("item-edit-icon").value;
    const available = document.getElementById("item-edit-available").value;
    const btn = document.getElementById("btn-save-item");

    btn.disabled = true;
    btn.textContent = "Saving...";

    const payload = { itemId, name, category, rentalPrice, description, imageUrl, available };
    const method = itemId ? "PUT" : "POST";

    try {
        const res = await apiRequest("/api/items", method, payload);
        if (res.success) {
            closeModal("modal-item");
            showToast("Item saved successfully.", "success");
            loadAdminItems();
        } else {
            showToast(res.error || "Failed to save item.", "error");
        }
    } catch (err) {
        showToast(err.message || "Error saving item.", "error");
    } finally {
        btn.disabled = false;
        btn.textContent = "Save Item";
    }
}

async function toggleItemAvailability(itemId, currentStatus) {
    try {
        const res = await apiRequest("/api/items", "PUT", {
            itemId,
            available: String(!currentStatus)
        });
        if (res.success) {
            showToast("Availability status updated.", "success");
            loadAdminItems();
        }
    } catch (err) {
        showToast("Failed to update status", "error");
    }
}

async function deleteAdminItem(itemId) {
    if (!confirm(`Are you sure you want to delete item ${itemId}?`)) return;

    try {
        const res = await apiRequest(`/api/items?id=${encodeURIComponent(itemId)}`, "DELETE");
        if (res.success) {
            showToast("Item deleted from inventory.", "info");
            loadAdminItems();
        } else {
            showToast(res.error || "Cannot delete item with active rentals.", "error");
        }
    } catch (err) {
        showToast(err.message || "Failed to delete item", "error");
    }
}

// --------------------------------------------------------------------------
// Admin: Manage Customers
// --------------------------------------------------------------------------

async function loadAdminCustomers() {
    const tbody = document.getElementById("admin-customers-tbody");
    try {
        const customers = await apiRequest("/api/customers");

        tbody.innerHTML = customers.map(c => `
            <tr>
                <td><code>${c.customerId}</code></td>
                <td><strong>${c.name}</strong></td>
                <td>${c.email}</td>
                <td>${c.phone}</td>
                <td>${c.createdAt}</td>
                <td><strong>${c.totalRentals}</strong> orders</td>
                <td><span class="badge ${c.activeRentals > 0 ? 'badge-warning' : 'badge-neutral'}">${c.activeRentals} active</span></td>
                <td><strong>${formatINR(c.totalSpent)}</strong></td>
            </tr>
        `).join("");
    } catch (err) {
        console.error("Error loading admin customers:", err);
    }
}

// --------------------------------------------------------------------------
// Admin: Manage Rentals
// --------------------------------------------------------------------------

async function loadAdminRentals() {
    const tbody = document.getElementById("admin-rentals-table").querySelector("tbody");
    try {
        const rentals = await apiRequest("/api/rentals");

        tbody.innerHTML = rentals.map(r => {
            const customerName = r.customer ? r.customer.name : "N/A";
            const customerEmail = r.customer ? r.customer.email : "";
            const itemName = r.item ? r.item.name : "N/A";
            const isActive = ["CONFIRMED", "ACTIVE", "APPROVED"].includes(r.status);

            return `
                <tr>
                    <td><code>${r.rentalId}</code></td>
                    <td>
                        <strong>${customerName}</strong>
                        <div class="text-sm text-muted">${customerEmail}</div>
                    </td>
                    <td>${itemName}</td>
                    <td>${r.startDate}</td>
                    <td>${r.endDate}</td>
                    <td>${r.numberOfDays} days</td>
                    <td>${formatINR(r.dailyRate)}</td>
                    <td><strong>${formatINR(r.totalAmount)}</strong></td>
                    <td>${r.paymentMethod || "—"}<div class="text-sm text-muted">${r.paymentStatus || "PENDING"}</div></td>
                    <td>${formatINR(r.lateFee || 0)}<div class="text-sm text-muted">Due: ${formatINR(r.totalDue ?? r.totalAmount)}</div></td>
                    <td><span class="badge ${isActive ? 'badge-success' : 'badge-neutral'}">${r.status}</span></td>
                    <td>
                        <div class="flex-gap">
                            ${isActive ? `
                                <button class="btn btn-outline btn-sm" onclick="setAdminRentalStatus('${r.rentalId}', 'RETURNED')">Return</button>
                                <button class="btn btn-outline btn-sm" onclick="setAdminRentalStatus('${r.rentalId}', 'CANCELLED')">Cancel</button>
                            ` : `<span class="text-muted text-sm">—</span>`}
                        </div>
                    </td>
                </tr>
            `;
        }).join("");
    } catch (err) {
        console.error("Error loading admin rentals:", err);
    }
}

async function setAdminRentalStatus(rentalId, status) {
    if (!confirm(`Confirm updating rental ${rentalId} to status: ${status}?`)) return;

    try {
        const res = await apiRequest("/api/rentals/status", "PUT", { rentalId, status });
        if (res.success) {
            showToast(`Rental ${rentalId} updated to ${status}.`, "success");
            loadAdminRentals();
            loadAdminDashboardData();
        } else {
            showToast(res.error || "Failed to update status", "error");
        }
    } catch (err) {
        showToast(err.message || "Error updating rental status", "error");
    }
}

// --------------------------------------------------------------------------
// Admin: Analytics
// --------------------------------------------------------------------------

async function loadAdminAnalytics() {
    try {
        const data = await apiRequest("/api/admin/analytics");

        document.getElementById("analytics-total-revenue").textContent = formatINR(data.totalRevenue || 0);
        document.getElementById("analytics-active-rentals").textContent = data.activeRentals || 0;
        document.getElementById("analytics-returned-rentals").textContent = data.returnedRentals || 0;
        document.getElementById("analytics-cancelled-rentals").textContent = data.cancelledRentals || 0;

        // Category stats table
        const catTbody = document.getElementById("analytics-category-tbody");
        const catStats = data.categoryStats || {};
        catTbody.innerHTML = Object.entries(catStats).map(([cat, info]) => `
            <tr>
                <td><strong>${cat}</strong></td>
                <td>${info.itemCount} items</td>
                <td>${info.rentalCount} orders</td>
                <td><strong>${formatINR(info.revenue)}</strong></td>
            </tr>
        `).join("");

        // Top rented items table
        const topTbody = document.getElementById("analytics-top-items-tbody");
        const topItems = data.topItems || [];
        if (topItems.length === 0) {
            topTbody.innerHTML = `<tr><td colspan="2" class="text-center text-muted">No rental data accumulated yet.</td></tr>`;
        } else {
            topTbody.innerHTML = topItems.map(item => `
                <tr>
                    <td><strong>${item.name}</strong></td>
                    <td><span class="badge badge-info">${item.count} rentals</span></td>
                </tr>
            `).join("");
        }
    } catch (err) {
        console.error("Error loading analytics:", err);
    }
}

// ==========================================================================
// REST API Client & Helper Utilities
// ==========================================================================

async function apiRequest(url, method = "GET", body = null) {
    const headers = { "Content-Type": "application/json" };
    if (state.authToken) {
        headers["Authorization"] = `Bearer ${state.authToken}`;
    }

    const options = { method, headers };
    if (body) {
        options.body = JSON.stringify(body);
    }

    const res = await fetch(url, options);
    const data = await res.json();

    if (!res.ok && res.status === 401) {
        logout(false);
        throw new Error(data.error || "Authentication required");
    }

    if (!res.ok) {
        throw new Error(data.error || "Request failed");
    }

    return data;
}

function openModal(id) {
    const el = document.getElementById(id);
    if (el) el.style.display = "flex";
}

function closeModal(id) {
    const el = document.getElementById(id);
    if (el) el.style.display = "none";
}

function showToast(message, type = "info") {
    const wrapper = document.getElementById("toast-wrapper");
    if (!wrapper) return;

    const toast = document.createElement("div");
    toast.className = `toast ${type}`;

    let iconSvg = `<svg class="toast-svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="16" x2="12" y2="12"></line><line x1="12" y1="8" x2="12.01" y2="8"></line></svg>`;
    if (type === "success") {
        iconSvg = `<svg class="toast-svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path><polyline points="22 4 12 14.01 9 11.01"></polyline></svg>`;
    } else if (type === "error") {
        iconSvg = `<svg class="toast-svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><circle cx="12" cy="12" r="10"></circle><line x1="15" y1="9" x2="9" y2="15"></line><line x1="9" y1="9" x2="15" y2="15"></line></svg>`;
    }

    toast.innerHTML = `<span class="toast-icon-wrap">${iconSvg}</span><span class="toast-msg-text">${message}</span>`;

    wrapper.appendChild(toast);
    setTimeout(() => {
        toast.classList.add("toast-hiding");
        setTimeout(() => toast.remove(), 250);
    }, 3500);
}
