const ui = {
    connectionBadge: document.querySelector("#connectionBadge"),
    statusBadge: document.querySelector("#statusBadge"),
    startButton: document.querySelector("#startButton"),
    pauseButton: document.querySelector("#pauseButton"),
    resetButton: document.querySelector("#resetButton"),
    speedRange: document.querySelector("#speedRange"),
    speedOutput: document.querySelector("#speedOutput"),
    vehicleType: document.querySelector("#vehicleType"),
    vipCheck: document.querySelector("#vipCheck"),
    addVehicleButton: document.querySelector("#addVehicleButton"),
    historyButton: document.querySelector("#historyButton"),
    parkingLevels: document.querySelector("#parkingLevels"),
    occupancyHeadline: document.querySelector("#occupancyHeadline"),
    occupiedStat: document.querySelector("#occupiedStat"),
    queueStat: document.querySelector("#queueStat"),
    completedStat: document.querySelector("#completedStat"),
    revenueStat: document.querySelector("#revenueStat"),
    rejectedStat: document.querySelector("#rejectedStat"),
    occupancyProgress: document.querySelector("#occupancyProgress"),
    queueBadge: document.querySelector("#queueBadge"),
    queueList: document.querySelector("#queueList"),
    eventList: document.querySelector("#eventList"),
    historyDialog: document.querySelector("#historyDialog"),
    closeHistoryButton: document.querySelector("#closeHistoryButton"),
    historyBody: document.querySelector("#historyBody"),
    historyEmpty: document.querySelector("#historyEmpty"),
    toast: document.querySelector("#toast")
};

const vehicleMeta = {
    CAR: { icon: "🚗", label: "Car" },
    ELECTRIC: { icon: "⚡", label: "Electric vehicle" },
    MOTORCYCLE: { icon: "🏍", label: "Motorcycle" },
    ACCESSIBLE: { icon: "♿", label: "Accessible vehicle" }
};

const spotMeta = {
    REGULAR: { icon: "P", label: "Regular", className: "regular" },
    ELECTRIC: { icon: "⚡", label: "EV charging", className: "electric" },
    MOTORCYCLE: { icon: "M", label: "Motorcycle", className: "motorcycle" },
    ACCESSIBLE: { icon: "♿", label: "Accessible", className: "accessible" }
};

let currentState = null;
let toastTimer;

async function request(path, options = {}) {
    const response = await fetch(path, {
        ...options,
        headers: {
            "Content-Type": "application/json",
            ...(options.headers || {})
        }
    });
    if (!response.ok) {
        let message = `Request failed (${response.status})`;
        try {
            const body = await response.json();
            message = body.error || message;
        } catch {
            // Keep the fallback message.
        }
        throw new Error(message);
    }
    return response.status === 204 ? null : response.json();
}

function showToast(message, isError = false) {
    clearTimeout(toastTimer);
    ui.toast.textContent = message;
    ui.toast.classList.toggle("error", isError);
    ui.toast.classList.add("show");
    toastTimer = setTimeout(() => ui.toast.classList.remove("show"), 2_800);
}

function setConnection(status) {
    const labels = {
        connected: "Live",
        connecting: "Connecting",
        disconnected: "Reconnecting"
    };
    ui.connectionBadge.className = `connection-badge ${status}`;
    ui.connectionBadge.querySelector("span").textContent = labels[status];
}

function connectStream() {
    setConnection("connecting");
    const source = new EventSource("/api/stream");
    source.addEventListener("open", () => setConnection("connected"));
    source.addEventListener("state", event => renderState(JSON.parse(event.data)));
    source.addEventListener("error", () => setConnection("disconnected"));
}

function renderState(state) {
    currentState = state;
    const parking = state.parking;
    const occupancy = parking.capacity === 0
        ? 0
        : Math.round((parking.occupied / parking.capacity) * 100);

    ui.statusBadge.textContent = titleCase(state.status);
    ui.statusBadge.className = `status-badge ${state.status.toLowerCase()}`;
    ui.startButton.disabled = state.status === "RUNNING";
    ui.startButton.innerHTML = state.status === "PAUSED"
        ? "<span>▶</span> Resume simulation"
        : "<span>▶</span> Start simulation";
    ui.pauseButton.disabled = state.status !== "RUNNING";

    ui.speedRange.value = state.speed;
    ui.speedOutput.value = `${Number(state.speed).toFixed(1)}×`;
    ui.speedOutput.textContent = `${Number(state.speed).toFixed(1)}×`;
    ui.occupancyHeadline.textContent = `${occupancy}%`;
    ui.occupiedStat.textContent = `${parking.occupied} / ${parking.capacity}`;
    ui.queueStat.textContent = parking.queue.length;
    ui.completedStat.textContent = parking.completed;
    ui.revenueStat.textContent = Number(parking.revenue).toFixed(2);
    ui.rejectedStat.textContent = parking.rejected;
    ui.occupancyProgress.style.width = `${occupancy}%`;
    ui.queueBadge.textContent = parking.queue.length;

    renderParking(parking.spots);
    renderQueue(parking.queue);
    renderEvents(state.recentEvents);
}

function renderParking(spots) {
    const levels = spots.reduce((groups, spot) => {
        if (!groups.has(spot.level)) {
            groups.set(spot.level, []);
        }
        groups.get(spot.level).push(spot);
        return groups;
    }, new Map());
    ui.parkingLevels.innerHTML = [...levels.entries()].map(([level, levelSpots]) => `
        <article class="level-card">
            <header class="level-heading">
                <h2>LEVEL ${escapeHtml(level)}</h2>
                <span>${levelSpots.filter(spot => spot.occupied).length} occupied · ${levelSpots.length} total</span>
            </header>
            <div class="spot-grid">
                ${levelSpots.map(spotTemplate).join("")}
            </div>
        </article>
    `).join("");
}

function spotTemplate(spot) {
    const type = spotMeta[spot.type];
    const vehicle = spot.vehicleType ? vehicleMeta[spot.vehicleType] : null;
    const classes = [
        "spot",
        spot.occupied ? "occupied" : "free",
        type.className,
        spot.vip ? "vip" : ""
    ].filter(Boolean).join(" ");
    const tooltip = spot.occupied
        ? `${spot.licensePlate} · double-click to check out`
        : `${type.label} · available`;
    return `
        <div class="${classes}" data-spot-id="${escapeHtml(spot.id)}"
             data-occupied="${spot.occupied}" title="${escapeHtml(tooltip)}">
            <span>
                <span class="spot-icon">${vehicle ? vehicle.icon : type.icon}</span>
                <span class="spot-id">${escapeHtml(spot.id)}</span>
                <span class="spot-detail">${escapeHtml(spot.occupied ? spot.licensePlate : type.label)}</span>
            </span>
        </div>
    `;
}

function renderQueue(queue) {
    if (queue.length === 0) {
        ui.queueList.innerHTML = '<div class="empty-state">No vehicles are waiting</div>';
        return;
    }
    ui.queueList.innerHTML = queue.map(vehicle => {
        const meta = vehicleMeta[vehicle.type];
        return `
            <article class="queue-item">
                <span class="queue-icon">${meta.icon}</span>
                <span class="queue-copy">
                    <strong>${escapeHtml(vehicle.licensePlate)}</strong>
                    <small>${escapeHtml(meta.label)}</small>
                </span>
                <span class="queue-tag ${vehicle.vip ? "vip" : ""}">${vehicle.vip ? "VIP" : "WAIT"}</span>
            </article>
        `;
    }).join("");
}

function renderEvents(events) {
    if (!events || events.length === 0) {
        ui.eventList.innerHTML = '<div class="empty-state">Events appear when the simulation starts</div>';
        return;
    }
    ui.eventList.innerHTML = events.map(event => {
        const marker = {
            INFO: "●",
            ARRIVAL: "→",
            PARKED: "✓",
            DEPARTURE: "←",
            WARNING: "!"
        }[event.type];
        return `
            <div class="event-item ${event.type.toLowerCase()}">
                <time>${formatTime(event.time)}</time>
                <span class="event-marker">${marker}</span>
                <span>${escapeHtml(event.message)}</span>
            </div>
        `;
    }).join("");
}

async function startSimulation() {
    await performAction(
        () => request("/api/simulation/start", { method: "POST" }),
        "Simulation is running"
    );
}

async function pauseSimulation() {
    await performAction(
        () => request("/api/simulation/pause", { method: "POST" }),
        "Simulation paused"
    );
}

async function resetSimulation() {
    await performAction(
        () => request("/api/simulation/reset", { method: "POST" }),
        "Parking facility reset"
    );
}

async function addVehicle() {
    await performAction(
        () => request("/api/vehicles", {
            method: "POST",
            body: JSON.stringify({
                type: ui.vehicleType.value,
                vip: ui.vipCheck.checked
            })
        }),
        `${vehicleMeta[ui.vehicleType.value].label} added`
    );
}

async function releaseSpot(spotId) {
    await performAction(
        () => request(`/api/spots/${encodeURIComponent(spotId)}/release`, { method: "POST" }),
        `Vehicle checked out from ${spotId}`
    );
}

async function performAction(action, successMessage) {
    try {
        const state = await action();
        if (state?.parking) {
            renderState(state);
        }
        showToast(successMessage);
    } catch (error) {
        showToast(error.message, true);
    }
}

async function showHistory() {
    try {
        const history = await request("/api/history");
        ui.historyBody.innerHTML = history.map(ticket => `
            <tr>
                <td>${escapeHtml(ticket.licensePlate)}</td>
                <td>${escapeHtml(vehicleMeta[ticket.vehicleType].label)}</td>
                <td>${escapeHtml(ticket.spotId)}</td>
                <td>${ticket.simulatedMinutes} min</td>
                <td>${Number(ticket.cost).toFixed(2)} GEL</td>
            </tr>
        `).join("");
        ui.historyEmpty.hidden = history.length > 0;
        ui.historyDialog.showModal();
    } catch (error) {
        showToast(error.message, true);
    }
}

function formatTime(isoTime) {
    return new Intl.DateTimeFormat("en", {
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
        hour12: false
    }).format(new Date(isoTime));
}

function titleCase(value) {
    return value.charAt(0) + value.slice(1).toLowerCase();
}

function escapeHtml(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}

ui.startButton.addEventListener("click", startSimulation);
ui.pauseButton.addEventListener("click", pauseSimulation);
ui.resetButton.addEventListener("click", resetSimulation);
ui.addVehicleButton.addEventListener("click", addVehicle);
ui.historyButton.addEventListener("click", showHistory);
ui.closeHistoryButton.addEventListener("click", () => ui.historyDialog.close());
ui.historyDialog.addEventListener("click", event => {
    if (event.target === ui.historyDialog) {
        ui.historyDialog.close();
    }
});
ui.speedRange.addEventListener("input", () => {
    ui.speedOutput.textContent = `${Number(ui.speedRange.value).toFixed(1)}×`;
});
ui.speedRange.addEventListener("change", () => performAction(
    () => request("/api/simulation/speed", {
        method: "POST",
        body: JSON.stringify({ speed: Number(ui.speedRange.value) })
    }),
    `Traffic speed set to ${Number(ui.speedRange.value).toFixed(1)}×`
));
ui.parkingLevels.addEventListener("dblclick", event => {
    const spot = event.target.closest("[data-spot-id]");
    if (spot?.dataset.occupied === "true") {
        releaseSpot(spot.dataset.spotId);
    }
});

request("/api/state")
    .then(state => {
        renderState(state);
        connectStream();
    })
    .catch(error => showToast(error.message, true));
