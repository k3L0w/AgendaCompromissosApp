const STORAGE_KEY = 'agendaAppointmentsWeb';
const DB_NAME = 'agendaDb';
const DB_VERSION = 1;
const STORE_NAME = 'appointments';
const REMINDER_OFFSETS = [24 * 60 * 60 * 1000, 60 * 60 * 1000];
const appointmentsContainer = document.getElementById('appointmentsContainer');
const emptyState = document.getElementById('emptyState');
const dialog = document.getElementById('appointmentDialog');
const newAppointmentButton = document.getElementById('newAppointmentButton');
const requestNotificationsButton = document.getElementById('requestNotificationsButton');
const closeDialogButton = document.getElementById('closeDialogButton');
const cancelButton = document.getElementById('cancelButton');
const appointmentForm = document.getElementById('appointmentForm');
const dialogTitle = document.getElementById('dialogTitle');
const titleInput = document.getElementById('titleInput');
const descriptionInput = document.getElementById('descriptionInput');
const categoryInput = document.getElementById('categoryInput');
const dateTimeInput = document.getElementById('dateTimeInput');
const totalCountEl = document.getElementById('totalCount');
const nextAppointmentEl = document.getElementById('nextAppointment');
const searchInput = document.getElementById('searchInput');
const categorySelect = document.getElementById('categorySelect');
const themeToggleButton = document.getElementById('themeToggleButton');
const emptyAddButton = document.getElementById('emptyAddButton');
let editAppointmentId = null;
let scheduledTimeouts = [];
let currentTheme = 'dark';

const CATEGORIES = ['Trabalho', 'Pessoal', 'Saúde', 'Reunião', 'Outro'];

function sanitizeText(text) {
  return String(text || '').replace(/[<>]/g, '').trim();
}

function normalizeAppointment(raw) {
  if (!raw || typeof raw !== 'object') return null;
  const id = Number(raw.id) || Date.now();
  const title = sanitizeText(raw.title || 'Sem título');
  const description = sanitizeText(raw.description || '');
  const category = CATEGORIES.includes(raw.category) ? raw.category : 'Outro';
  const appointmentDateTimeMs = Number(raw.appointmentDateTimeMs) || getNow();
  const createdAt = Number(raw.createdAt) || getNow();
  if (!title || !appointmentDateTimeMs) return null;
  return { id, title, description, category, appointmentDateTimeMs, createdAt };
}

function isIndexedDBSupported() {
  return 'indexedDB' in window;
}

function openDatabase() {
  return new Promise((resolve, reject) => {
    if (!isIndexedDBSupported()) {
      reject(new Error('IndexedDB não suportado'));
      return;
    }

    const request = indexedDB.open(DB_NAME, DB_VERSION);

    request.onupgradeneeded = event => {
      const db = event.target.result;
      if (!db.objectStoreNames.contains(STORE_NAME)) {
        const store = db.createObjectStore(STORE_NAME, { keyPath: 'id' });
        store.createIndex('byDate', 'appointmentDateTimeMs', { unique: false });
        store.createIndex('byCategory', 'category', { unique: false });
      }
    };

    request.onsuccess = event => resolve(event.target.result);
    request.onerror = () => reject(request.error);
  });
}

function loadAppointmentsFallback() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    const data = raw ? JSON.parse(raw) : [];
    if (!Array.isArray(data)) return [];
    return data.map(normalizeAppointment).filter(Boolean);
  } catch (error) {
    console.error('Erro ao carregar compromissos fallback:', error);
    return [];
  }
}

function saveAppointmentsFallback(appointments) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(appointments));
}

function getAllAppointmentsFromDB() {
  return new Promise(resolve => {
    openDatabase().then(db => {
      const transaction = db.transaction(STORE_NAME, 'readonly');
      const store = transaction.objectStore(STORE_NAME);
      const request = store.getAll();
      request.onsuccess = () => {
        const appointments = request.result.map(normalizeAppointment).filter(Boolean);
        resolve(appointments);
      };
      request.onerror = () => resolve(loadAppointmentsFallback());
    }).catch(() => {
      resolve(loadAppointmentsFallback());
    });
  });
}

function saveAppointmentToDB(appointment) {
  return new Promise((resolve, reject) => {
    const normalized = normalizeAppointment(appointment);
    if (!normalized) {
      reject(new Error('Compromisso inválido')); return;
    }

    openDatabase().then(db => {
      const transaction = db.transaction(STORE_NAME, 'readwrite');
      const store = transaction.objectStore(STORE_NAME);
      const request = store.put(normalized);
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    }).catch(() => {
      const appointments = loadAppointmentsFallback();
      const existingIndex = appointments.findIndex(item => item.id === normalized.id);
      if (existingIndex >= 0) {
        appointments[existingIndex] = normalized;
      } else {
        appointments.push(normalized);
      }
      saveAppointmentsFallback(appointments);
      resolve(normalized.id);
    });
  });
}

function deleteAppointmentFromDB(id) {
  return new Promise(resolve => {
    openDatabase().then(db => {
      const transaction = db.transaction(STORE_NAME, 'readwrite');
      const store = transaction.objectStore(STORE_NAME);
      const request = store.delete(id);
      request.onsuccess = () => resolve();
      request.onerror = () => resolve();
    }).catch(() => {
      const appointments = loadAppointmentsFallback().filter(item => item.id !== id);
      saveAppointmentsFallback(appointments);
      resolve();
    });
  });
}

function cleanupExpiredAppointments() {
  return getAllAppointmentsFromDB().then(appointments => {
    const now = getNow();
    const expired = appointments.filter(item => item.appointmentDateTimeMs < now);
    return Promise.all(expired.map(item => deleteAppointmentFromDB(item.id))).then(() => appointments.filter(item => item.appointmentDateTimeMs >= now));
  });
}

function formatDateTime(ms) {
  return new Intl.DateTimeFormat('pt-BR', {
    dateStyle: 'medium',
    timeStyle: 'short'
  }).format(new Date(ms));
}

function getNow() {
  return Date.now();
}

function applyFilters(appointments) {
  const searchTerm = sanitizeText(searchInput.value).toLowerCase();
  const category = categorySelect.value;

  return appointments.filter(appointment => {
    const matchesCategory = category === 'all' || appointment.category === category;
    const matchesSearch = [appointment.title, appointment.description].some(field => field.toLowerCase().includes(searchTerm));
    return matchesCategory && matchesSearch;
  });
}

function updateSummary(appointments) {
  const upcoming = appointments.filter(item => item.appointmentDateTimeMs >= getNow());
  totalCountEl.textContent = upcoming.length;

  const next = upcoming.sort((a, b) => a.appointmentDateTimeMs - b.appointmentDateTimeMs)[0];
  nextAppointmentEl.textContent = next ? `${next.title} • ${formatDateTime(next.appointmentDateTimeMs)}` : 'Nenhum agendado';
}

async function renderAppointments() {
  if (isIndexedDBSupported()) {
    await cleanupExpiredAppointments();
  }

  const rawAppointments = await getAllAppointmentsFromDB();
  const upcoming = cleanupExpired(rawAppointments).sort((a, b) => a.appointmentDateTimeMs - b.appointmentDateTimeMs);
  const filteredAppointments = applyFilters(upcoming);

  if (!isIndexedDBSupported()) {
    saveAppointmentsFallback(upcoming);
  }

  updateSummary(upcoming);
  appointmentsContainer.innerHTML = '';

  if (!filteredAppointments.length) {
    emptyState.classList.remove('hidden');
    return;
  }

  emptyState.classList.add('hidden');

  filteredAppointments.forEach(appointment => {
    const card = document.createElement('article');
    card.className = 'card';

    const header = document.createElement('div');
    header.className = 'card-header';

    const info = document.createElement('div');

    const title = document.createElement('h3');
    title.className = 'card-title';
    title.textContent = appointment.title;

    const time = document.createElement('p');
    time.className = 'card-meta';
    time.textContent = formatDateTime(appointment.appointmentDateTimeMs);

    info.append(title, time);

    const badge = document.createElement('span');
    badge.className = `badge ${appointment.category}`;
    badge.textContent = appointment.category;

    header.append(info, badge);

    const description = document.createElement('p');
    description.className = 'card-description';
    description.textContent = appointment.description || 'Sem descrição adicional.';

    const buttonRow = document.createElement('div');
    buttonRow.className = 'card-actions';

    const editButton = document.createElement('button');
    editButton.type = 'button';
    editButton.className = 'secondary-button';
    editButton.textContent = 'Editar';
    editButton.addEventListener('click', () => openDialog(appointment));

    const deleteButton = document.createElement('button');
    deleteButton.type = 'button';
    deleteButton.className = 'secondary-button';
    deleteButton.textContent = 'Excluir';
    deleteButton.addEventListener('click', () => deleteAppointment(appointment.id));

    buttonRow.append(editButton, deleteButton);

    card.append(header, description, buttonRow);
    appointmentsContainer.append(card);
  });
}

function openDialog(appointment = null) {
  dialog.classList.remove('hidden');
  if (appointment) {
    editAppointmentId = appointment.id;
    dialogTitle.textContent = 'Editar compromisso';
    titleInput.value = appointment.title;
    descriptionInput.value = appointment.description;
    categoryInput.value = appointment.category;
    dateTimeInput.value = toDateTimeLocal(appointment.appointmentDateTimeMs);
  } else {
    editAppointmentId = null;
    dialogTitle.textContent = 'Novo compromisso';
    titleInput.value = '';
    descriptionInput.value = '';
    categoryInput.value = 'Trabalho';
    dateTimeInput.value = toDateTimeLocal(getNow() + 60 * 60 * 1000);
  }
  titleInput.focus();
}

function closeDialog() {
  dialog.classList.add('hidden');
  appointmentForm.reset();
}

function toDateTimeLocal(value) {
  const date = new Date(value);
  const offset = date.getTimezoneOffset();
  const localDate = new Date(date.getTime() - offset * 60 * 1000);
  return localDate.toISOString().slice(0, 16);
}

function parseDateTimeLocal(value) {
  const date = new Date(value);
  return date.getTime();
}

async function saveAppointment(event) {
  event.preventDefault();
  const title = sanitizeText(titleInput.value.trim());
  const description = sanitizeText(descriptionInput.value.trim());
  const category = CATEGORIES.includes(categoryInput.value) ? categoryInput.value : 'Outro';
  const dateTimeMs = parseDateTimeLocal(dateTimeInput.value);
  const now = getNow();

  if (!title) {
    alert('Informe um título válido para o compromisso.');
    return;
  }

  if (Number.isNaN(dateTimeMs) || dateTimeMs <= now) {
    alert('Informe uma data e hora futura para o compromisso.');
    return;
  }

  const appointment = {
    id: editAppointmentId !== null ? editAppointmentId : Date.now(),
    title,
    description,
    category,
    appointmentDateTimeMs: dateTimeMs,
    createdAt: getNow()
  };

  await saveAppointmentToDB(appointment);
  await cleanupExpiredAppointments();
  closeDialog();
  await renderAppointments();
  scheduleAllReminders();
}

async function deleteAppointment(id) {
  await deleteAppointmentFromDB(id);
  await renderAppointments();
}

function requestNotificationPermission() {
  if (!('Notification' in window)) {
    alert('Seu navegador não suporta notificações.');
    return;
  }

  Notification.requestPermission().then(permission => {
    updateNotificationButton(permission);
    if (permission === 'granted') {
      scheduleAllReminders();
    }
  });
}

function updateNotificationButton(permission = Notification.permission) {
  if (permission === 'granted') {
    requestNotificationsButton.textContent = 'Lembretes ativados';
    requestNotificationsButton.disabled = true;
  } else if (permission === 'denied') {
    requestNotificationsButton.textContent = 'Notificações negadas';
    requestNotificationsButton.disabled = true;
  } else {
    requestNotificationsButton.textContent = 'Habilitar lembretes';
    requestNotificationsButton.disabled = false;
  }
}

function toggleTheme() {
  const root = document.documentElement;
  if (currentTheme === 'dark') {
    currentTheme = 'light';
    root.style.setProperty('--bg', '#f8fafc');
    root.style.setProperty('--surface', '#ffffff');
    root.style.setProperty('--surface-strong', '#f8fafc');
    root.style.setProperty('--surface-soft', '#f1f5f9');
    root.style.setProperty('--text', '#0f172a');
    root.style.setProperty('--text-muted', '#475569');
    root.style.setProperty('--border', 'rgba(15, 23, 42, 0.08)');
    root.style.setProperty('--accent-soft', 'rgba(124, 58, 237, 0.12)');
    root.style.setProperty('--card', '#ffffff');
    root.style.setProperty('--card-border', 'rgba(15, 23, 42, 0.08)');
    root.style.setProperty('--shadow', '0 20px 40px rgba(15, 23, 42, 0.08)');
    document.body.style.background = 'linear-gradient(180deg, #eff4ff 0%, #ffffff 100%)';
    themeToggleButton.textContent = 'Modo noturno';
  } else {
    currentTheme = 'dark';
    root.style.setProperty('--bg', '#0b1220');
    root.style.setProperty('--surface', 'rgba(12, 18, 37, 0.94)');
    root.style.setProperty('--surface-strong', 'rgba(255, 255, 255, 0.08)');
    root.style.setProperty('--surface-soft', 'rgba(255, 255, 255, 0.1)');
    root.style.setProperty('--text', '#f8fafc');
    root.style.setProperty('--text-muted', '#cbd5e1');
    root.style.setProperty('--border', 'rgba(255, 255, 255, 0.12)');
    root.style.setProperty('--accent-soft', 'rgba(124, 58, 237, 0.16)');
    root.style.setProperty('--card', 'rgba(255, 255, 255, 0.04)');
    root.style.setProperty('--card-border', 'rgba(255, 255, 255, 0.1)');
    root.style.setProperty('--shadow', '0 30px 80px rgba(0, 0, 0, 0.22)');
    document.body.style.background = 'radial-gradient(circle at top left, rgba(124, 58, 237, 0.16), transparent 32%), radial-gradient(circle at bottom right, rgba(59, 130, 246, 0.12), transparent 28%), linear-gradient(180deg, #05080f 0%, #0b1220 100%)';
    themeToggleButton.textContent = 'Modo claro';
  }
}

async function scheduleAllReminders() {
  scheduledTimeouts.forEach(timeoutId => clearTimeout(timeoutId));
  scheduledTimeouts = [];

  if (Notification.permission !== 'granted') {
    return;
  }

  const appointments = await getAllAppointmentsFromDB();
  const now = getNow();
  appointments.forEach(appointment => {
    REMINDER_OFFSETS.forEach(offset => {
      const reminderTime = appointment.appointmentDateTimeMs - offset;
      if (reminderTime > now) {
        const delay = reminderTime - now;
        const timeoutId = window.setTimeout(() => {
          sendReminderNotification(appointment, offset);
        }, delay);
        scheduledTimeouts.push(timeoutId);
      }
    });
  });
}

function sendReminderNotification(appointment, offsetMs) {
  const label = offsetMs === REMINDER_OFFSETS[0] ? '24 horas' : '1 hora';
  const title = 'Lembrete de compromisso';
  const body = `${sanitizeText(appointment.title)} está agendado para ${formatDateTime(appointment.appointmentDateTimeMs)} (${label} antes).`;
  new Notification(title, { body });
}

newAppointmentButton.addEventListener('click', () => openDialog());
emptyAddButton.addEventListener('click', () => openDialog());
requestNotificationsButton.addEventListener('click', requestNotificationPermission);
closeDialogButton.addEventListener('click', closeDialog);
cancelButton.addEventListener('click', closeDialog);
appointmentForm.addEventListener('submit', saveAppointment);
searchInput.addEventListener('input', renderAppointments);
categorySelect.addEventListener('change', renderAppointments);
themeToggleButton.addEventListener('click', toggleTheme);

document.addEventListener('keydown', event => {
  if (event.key === 'Escape' && !dialog.classList.contains('hidden')) {
    closeDialog();
  }
});

document.addEventListener('DOMContentLoaded', async () => {
  await renderAppointments();
  updateNotificationButton();
  scheduleAllReminders();
  toggleTheme();
});