const STORAGE_KEY = 'agendaAppointmentsWeb';
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
const dateTimeInput = document.getElementById('dateTimeInput');
let editAppointmentId = null;
let scheduledTimeouts = [];

function loadAppointments() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? JSON.parse(raw) : [];
  } catch (error) {
    console.error('Erro ao carregar compromissos:', error);
    return [];
  }
}

function saveAppointments(appointments) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(appointments));
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

function cleanupExpired(appointments) {
  const now = getNow();
  return appointments.filter(item => item.appointmentDateTimeMs >= now);
}

function renderAppointments() {
  const rawAppointments = loadAppointments();
  const appointments = cleanupExpired(rawAppointments).sort((a, b) => a.appointmentDateTimeMs - b.appointmentDateTimeMs);
  saveAppointments(appointments);
  appointmentsContainer.innerHTML = '';

  if (!appointments.length) {
    emptyState.classList.remove('hidden');
    return;
  }

  emptyState.classList.add('hidden');

  appointments.forEach(appointment => {
    const card = document.createElement('article');
    card.className = 'card';

    const header = document.createElement('div');
    header.className = 'card-header';

    const title = document.createElement('h3');
    title.className = 'card-title';
    title.textContent = appointment.title;

    const buttonRow = document.createElement('div');
    buttonRow.className = 'button-row';

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

    const time = document.createElement('p');
    time.className = 'card-meta';
    time.textContent = formatDateTime(appointment.appointmentDateTimeMs);

    header.append(title, buttonRow);
    card.append(header, time);
    appointmentsContainer.append(card);
  });
}

function openDialog(appointment = null) {
  dialog.classList.remove('hidden');
  if (appointment) {
    editAppointmentId = appointment.id;
    dialogTitle.textContent = 'Editar compromisso';
    titleInput.value = appointment.title;
    dateTimeInput.value = toDateTimeLocal(appointment.appointmentDateTimeMs);
  } else {
    editAppointmentId = null;
    dialogTitle.textContent = 'Novo compromisso';
    titleInput.value = '';
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

function saveAppointment(event) {
  event.preventDefault();
  const title = titleInput.value.trim();
  const dateTimeMs = parseDateTimeLocal(dateTimeInput.value);
  const now = getNow();

  if (!title) {
    alert('Informe um título para o compromisso.');
    return;
  }

  if (Number.isNaN(dateTimeMs) || dateTimeMs <= now) {
    alert('Informe uma data e hora futura para o compromisso.');
    return;
  }

  const appointments = loadAppointments();
  const updatedAppointments = [...cleanupExpired(appointments)];

  if (editAppointmentId !== null) {
    const index = updatedAppointments.findIndex(item => item.id === editAppointmentId);
    if (index >= 0) {
      updatedAppointments[index] = {
        ...updatedAppointments[index],
        title,
        appointmentDateTimeMs: dateTimeMs
      };
    }
  } else {
    updatedAppointments.push({
      id: Date.now(),
      title,
      appointmentDateTimeMs: dateTimeMs
    });
  }

  saveAppointments(updatedAppointments);
  closeDialog();
  renderAppointments();
  scheduleAllReminders();
}

function deleteAppointment(id) {
  const appointments = loadAppointments().filter(item => item.id !== id);
  saveAppointments(appointments);
  renderAppointments();
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

function scheduleAllReminders() {
  scheduledTimeouts.forEach(timeoutId => clearTimeout(timeoutId));
  scheduledTimeouts = [];

  if (Notification.permission !== 'granted') {
    return;
  }

  const appointments = loadAppointments();
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
  const body = `${appointment.title} está agendado para ${formatDateTime(appointment.appointmentDateTimeMs)} (${label} antes).`;
  new Notification(title, { body });
}

newAppointmentButton.addEventListener('click', () => openDialog());
requestNotificationsButton.addEventListener('click', requestNotificationPermission);
closeDialogButton.addEventListener('click', closeDialog);
cancelButton.addEventListener('click', closeDialog);
appointmentForm.addEventListener('submit', saveAppointment);

document.addEventListener('keydown', event => {
  if (event.key === 'Escape' && !dialog.classList.contains('hidden')) {
    closeDialog();
  }
});

document.addEventListener('DOMContentLoaded', () => {
  renderAppointments();
  updateNotificationButton();
  scheduleAllReminders();
});
