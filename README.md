# Agenda de Compromissos Android

Aplicativo Android simples e funcional para criar, editar e alertar compromissos. Inclui:

- Cadastro de compromisso com título e data/hora.
- Lembrete um dia antes e uma hora antes do compromisso.
- Exclusão automática de compromissos vencidos.
- Interface moderna, leve e fácil de usar com Jetpack Compose.
- Persistência local com Room (SQLite) para não perder dados após fechar o app.

## Estrutura do projeto

- `app/src/main/java/com/example/agenda/MainActivity.kt` - interface principal e lógica de UI.
- `app/src/main/java/com/example/agenda/Appointment.kt` - entidade Room para compromisso.
- `app/src/main/java/com/example/agenda/AppointmentDao.kt` - acesso ao banco de dados.
- `app/src/main/java/com/example/agenda/AppDatabase.kt` - configuração do banco Room.
- `app/src/main/java/com/example/agenda/AppointmentAlarmScheduler.kt` - agenda notificações.
- `app/src/main/java/com/example/agenda/ReminderReceiver.kt` - gera notificações.
- `app/src/main/java/com/example/agenda/CleanupWorker.kt` - remove compromissos vencidos.
- `app/src/main/java/com/example/agenda/Theme.kt` / `Color.kt` - tema visual.

## Requisitos e configuração do ambiente

### 1. Instalar o Android Studio

- Baixe e instale o Android Studio mais recente.
- Instale o Android SDK Platform 34.
- Habilite os componentes do Kotlin e Jetpack Compose.

### 2. Abrir o projeto

1. No Android Studio, escolha `Open`.
2. Selecione a pasta do projeto: `C:\Users\fic\AgendaCompromissosApp`.
3. Aguarde o Gradle sincronizar.

### 3. Configurar Gradle / wrapper

O projeto já contém os arquivos:

- `settings.gradle.kts`
- `build.gradle.kts`
- `app/build.gradle.kts`

Se necessário, gere o Gradle Wrapper com:

```powershell
cd C:\Users\fic\AgendaCompromissosApp
gradle wrapper
```

Ou use o comando do Android Studio: `File > Sync Project with Gradle Files`.

### 4. Configurar dispositivo de teste

- Use um emulador Android com API 34 ou superior.
- Ou conecte um celular físico com a depuração USB habilitada.

### 5. Permissão de notificações

No Android 13 ou superior, o app pedirá permissão `POST_NOTIFICATIONS` ao iniciar.
Aceite essa permissão para receber os alertas de compromisso.

## Como executar

1. No Android Studio, selecione o módulo `app`.
2. Escolha o dispositivo ou emulador.
3. Clique em `Run`.
4. O app será instalado e iniciado.

## Banco de dados local

O app usa Room para armazenar compromissos em um banco SQLite local chamado `agenda.db`.

- O arquivo é criado automaticamente na primeira execução.
- A tabela principal é `appointments`.
- Os compromissos são mantidos no dispositivo, mesmo após fechar o app.
- Há limpeza automática de compromissos com data/hora anterior ao momento atual.

### Como o banco funciona

- `Appointment` contém `id`, `title` e `appointmentDateTimeMs`.
- `AppointmentDao` oferece métodos para:
  - listar todos os compromissos futuros
  - inserir ou atualizar um compromisso
  - excluir um compromisso
  - remover compromissos expirados
- `AppDatabase` cria uma instância singleton de Room e persiste os dados em `agenda.db`.

## Notificações e agendamento

- Ao salvar ou editar um compromisso, o app agenda dois alarmes:
  - 24 horas antes do evento
  - 1 hora antes do evento
- Os alarmes usam `AlarmManager` e são entregues pelo `ReminderReceiver`.
- As notificações aparecem no dispositivo com título e mensagem do compromisso.
- Se o compromisso for excluído, os alarmes são cancelados.

## Exclusão automática de compromissos vencidos

- O `CleanupWorker` usa WorkManager para rodar a cada 24 horas.
- Ele remove compromissos cuja data/hora já passou.
- Assim, a lista fica sempre atualizada com eventos futuros.

## Teste rápido

1. Abra o app.
2. Toque no botão `+` para criar um compromisso.
3. Informe o título e escolha data/hora futuras.
4. Salve.
5. Verifique se o compromisso aparece na lista.
6. Edite ou exclua para confirmar que funciona.

## Dicas de uso

- Use datas futuras para testar os lembretes.
- Se quiser testar notificações rapidamente, crie um compromisso para daqui a 1 hora ou menos.
- O banco `agenda.db` fica no armazenamento interno do app e não precisa ser configurado manualmente.

## Observações finais

- Este projeto já está pronto para a execução local.
- Para suporte a backup/remoto, é possível adicionar Firebase ou sincronização com servidor.

## Versão Web

Uma versão web estática foi adicionada em `web/`. Ela reproduz as principais funcionalidades da agenda:

- cadastro, edição e exclusão de compromissos;
- listagem ordenada de eventos futuros;
- limpeza automática de compromissos expirados;
- persistência local usando `localStorage`.

### Como executar a versão web

1. Abra `web/index.html` em um navegador moderno.
2. Clique em `+ Adicionar` para criar um compromisso.
3. Conceda permissão de notificações se desejar lembretes no navegador.

### Observações sobre lembretes

- A versão web solicita permissão para enviar notificações pelo navegador.
- O recurso de lembretes funciona enquanto a aba estiver aberta e o navegador suportar a API de notificações.
