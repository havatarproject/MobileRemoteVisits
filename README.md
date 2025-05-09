# Aplicação de Visitas Remotas com o Robô Temi

## Objetivo
Desenvolver uma aplicação que tira partido das funcionalidades do robô Temi permitindo a realização de visitas remotas, facilitando a comunicação e interação entre clientes através de videochamadas automatizadas. A aplicação será desenvolvida para a plataforma Android e incluirá funcionalidades de agendamento, aprovação e execução automatizada de visitas.

## Principais Funcionalidades

### 1. Agendamento de Visitas
- **Interface de Calendário**: Clientes podem selecionar datas e horários disponíveis para agendar uma visita remota através de uma interface intuitiva de calendário.
- **Confirmação de Agendamento**: Um sistema de notificações confirma o agendamento para os clientes.

### 2. Aprovação de Visitas
- **Painel de Administração**: Administradores têm acesso a um painel onde podem visualizar e aprovar ou rejeitar solicitações de visitas.
- **Notificações**: Clientes recebem notificações sobre o status da sua solicitação (aprovada, rejeitada, ou pendente).

### 3. Execução da Visita
- **Movimentação Automatizada do Robô**: Na hora agendada, o robô Temi movimenta-se automaticamente até o destino pré-definido.
- **Início da Videochamada**: Ao chegar no destino, o robô inicia uma videochamada com o cliente, permitindo a interação remota em tempo real.

### 4. Interface de Videochamada
- **Qualidade de Vídeo e Áudio**: Garantir alta qualidade de vídeo e áudio para uma experiência de comunicação clara e eficaz.
- **Controles Interativos**: Clientes e administradores podem controlar aspectos da chamada, como volume e câmera, diretamente na interface da aplicação.

## Tecnologias Utilizadas
- **Plataforma**: Android
- **Temi SDK**: Para integração e controlo do robô Temi.
- **Plataforma de Desenvolvimento Web**: Firebase
- **Serviços de Videochamada**: WebRTC
