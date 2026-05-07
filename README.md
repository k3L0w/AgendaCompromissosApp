# Agenda de Compromissos

[![GitHub](https://img.shields.io/badge/host-GitHub-181717?logo=github&logoColor=white)](https://github.com)
[![Deploy](https://img.shields.io/badge/deploy-Vercel-black?logo=vercel&logoColor=white)](https://vercel.com)
[![Status](https://img.shields.io/badge/status-ready-brightgreen)](https://github.com)

Bem-vindo ao **Agenda de Compromissos**, um projeto híbrido que combina uma experiência móvel Android com uma versão web leve e moderna.

Aqui você encontra:

- 📱 Uma aplicação Android em Jetpack Compose
- 🌐 Uma versão web estática com armazenamento local no navegador
- ⏰ Lembretes antecipados para seus eventos
- 💾 Dados persistidos no dispositivo/cliente, sem backend obrigatório

---

## 📌 Sumário

- [🚀 O que este projeto faz](#-o-que-este-projeto-faz)
- [🧩 Arquitetura do projeto](#-arquitetura-do-projeto)
- [🛠️ Como executar](#-como-executar)
- [🧠 Como o armazenamento funciona](#-como-o-armazenamento-funciona)
- [🔐 Segurança e privacidade](#-segurança-e-privacidade)
- [📦 Publicação no Vercel](#-publicação-no-vercel)
- [💡 Próximos passos](#-próximos-passos)

---

## 🚀 O que este projeto faz

- Cria, edita e exclui compromissos.
- Mostra apenas eventos futuros automaticamente.
- Agenda lembretes 24h e 1h antes do compromisso na versão Android.
- Remove compromissos expirados automaticamente.
- Salva os dados localmente, garantindo privacidade do usuário.

---

## 🧩 Arquitetura do projeto

### Android (`app/`)

- `app/src/main/java/com/example/agenda/MainActivity.kt` — UI principal e fluxo de interação.
- `app/src/main/java/com/example/agenda/Appointment.kt` — entidade Room para compromissos.
- `app/src/main/java/com/example/agenda/AppointmentDao.kt` — consultas e persistência.
- `app/src/main/java/com/example/agenda/AppDatabase.kt` — configuração do banco Room.
- `app/src/main/java/com/example/agenda/AppointmentAlarmScheduler.kt` — agenda notificações e cancela alarmes.
- `app/src/main/java/com/example/agenda/ReminderReceiver.kt` — gera notificações no dispositivo.
- `app/src/main/java/com/example/agenda/CleanupWorker.kt` — remove compromissos que já passaram.
- `app/src/main/java/com/example/agenda/Theme.kt` / `Color.kt` — estilos e cores do app.

### Web (`web/`)

- `web/index.html` — interface web responsiva.
- `web/styles.css` — estilo visual moderno.
- `web/app.js` — lógica de armazenamento, CRUD e notificações.

---

## 🛠️ Como executar

### Android

1. Abra o projeto no Android Studio.
2. Selecione o módulo `app`.
3. Rode em um emulador ou dispositivo físico.

> Recomendado: Android API 34 ou superior.

### Web

1. Abra `web/index.html` em um navegador moderno.
2. Clique em `+ Adicionar` para criar um compromisso.
3. Habilite notificações se quiser alertas no navegador.

> Observação: a versão web salva os dados localmente no navegador usando IndexedDB, sem envio para servidor.

---

## 🧠 Como o armazenamento funciona

### Android

A versão Android usa **Room + SQLite** para persistir dados localmente no dispositivo.

- Banco: `agenda.db`
- Tabela: `appointments`
- Dados mantidos mesmo após fechar o app

### Web

A versão web usa **IndexedDB** para guardar compromissos no navegador.

- Os dados são locais ao browser e à origem do site.
- Não há compartilhamento entre usuários.
- Há fallback para `localStorage` quando IndexedDB não está disponível.

---

## 🔐 Segurança e privacidade

- A versão web aplica **CSP** para reduzir riscos de injeção de script.
- Inputs de usuário são sanitizados antes de serem persistidos.
- Dados da versão web ficam no dispositivo do usuário e são isolados por navegador/perfil.
- A versão Android não expõe o `ReminderReceiver` externamente.
- `android:allowBackup` está definido como `false` para evitar backups automáticos indesejados.

---

## 🎯 Principais melhorias já implementadas

- UI limpa, profissional e responsiva na versão web.
- Busca rápida, filtros por categoria e resumo de compromissos.
- Campos avançados na web: descrição, categoria e data/hora.
- Armazenamento local em ambas as plataformas.
- Notificações nos lembretes (Android) e permissão de notificações no browser.
- Limpeza automática de compromissos vencidos.
- Segurança reforçada no web com validação e CSP.

---

## 🧪 Teste rápido

1. Crie um novo compromisso.
2. Defina uma data futura.
3. Salve e veja o evento na lista.
4. Edite ou exclua para validar o fluxo.
5. No Android, observe a criação de lembretes.

---

## 📦 Publicação no Vercel

A pasta `web/` é pronta para deploy como site estático no Vercel.

- Não há backend exigido.
- Não há segredos ou credenciais no repositório.
- Cada usuário mantém seus dados localmente no navegador.

---

## 💡 Próximos passos

Se quiser evoluir o projeto, você pode adicionar:

- sincronização opcional com servidor ou Firebase;
- autenticação de usuário;
- export/import de compromissos;
- UI de calendário;
- criptografia local para dados sensíveis.

---

## 📄 Observações finais

Este repositório já tem tudo para rodar localmente e publicar a versão web.
A experiência foi projetada para ser simples, segura e centrada no usuário.
