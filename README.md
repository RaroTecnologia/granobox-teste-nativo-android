# Impressora Térmica Bluetooth - Android

Um aplicativo Android nativo em Kotlin para impressão em impressoras térmicas via Bluetooth usando comandos CPCL (Common Printer Command Language).

## 🚀 Funcionalidades

- **Conexão Bluetooth**: Descoberta e conexão com dispositivos Bluetooth
- **Impressão Térmica**: Suporte a comandos CPCL para impressoras Zebra e similares
- **Interface Moderna**: UI Material Design com ViewBinding
- **Gerenciamento de Permissões**: Solicitação automática de permissões Bluetooth e localização
- **Logs em Tempo Real**: Monitoramento de todas as operações
- **Templates Pré-definidos**: Páginas de teste, recibos, tickets e rótulos

## 📱 Requisitos

- Android 5.0 (API 21) ou superior
- Dispositivo com suporte a Bluetooth
- Impressora térmica compatível com CPCL

## 🛠️ Tecnologias Utilizadas

- **Kotlin**: Linguagem principal
- **AndroidX**: Bibliotecas de suporte modernas
- **ViewBinding**: Binding de views
- **Coroutines**: Programação assíncrona
- **Material Design**: Componentes de UI

## 📁 Estrutura do Projeto

```
app/src/main/java/com/example/thermalprinter/
├── MainActivity.kt              # Activity principal
├── BluetoothActivity.kt         # Lista de dispositivos Bluetooth
├── BluetoothManager.kt          # Gerenciamento de conexões
├── BluetoothDeviceManager.kt    # Descoberta de dispositivos
├── BluetoothDeviceAdapter.kt    # Adaptador da lista
├── PermissionManager.kt         # Gerenciamento de permissões
└── CPCLCommands.kt             # Comandos CPCL utilitários
```

## 🔧 Configuração

### 1. Clone o repositório
```bash
git clone <url-do-repositorio>
cd teste_nativo_bluebooth
```

### 2. Abra no Android Studio
- Abra o Android Studio
- Selecione "Open an existing Android Studio project"
- Navegue até a pasta do projeto e selecione

### 3. Sincronize o projeto
- Aguarde a sincronização do Gradle
- Resolva qualquer dependência faltante

### 4. Execute no dispositivo
- Conecte um dispositivo Android via USB
- Habilite a depuração USB
- Clique em "Run" (▶️)

## 📋 Permissões

O aplicativo solicita automaticamente as seguintes permissões:

- **Bluetooth**: Para conexão com impressoras
- **Bluetooth Admin**: Para controle do Bluetooth
- **Localização**: Necessária para descoberta de dispositivos Bluetooth
- **Bluetooth Connect/Scan**: Para Android 12+

## 🖨️ Uso

### 1. Primeira Execução
- O app solicitará permissões necessárias
- Habilite o Bluetooth se solicitado

### 2. Conectar Impressora
- Toque em "Conectar" ou "Configurações Bluetooth"
- Selecione sua impressora térmica da lista
- Aguarde a conexão

### 3. Imprimir
- **Página de Teste**: Toque em "Impressão de Teste"
- **Texto Personalizado**: Digite o texto e toque em "Imprimir"
- **Templates**: Use a classe `CPCLCommands` para comandos avançados

## 🎯 Comandos CPCL

### Exemplo Básico
```kotlin
val commands = CPCLCommands.startForm() +
               CPCLCommands.centerText("Título", 20, 4) +
               CPCLCommands.text("Texto normal", 10, 60, 7) +
               CPCLCommands.endForm()

bluetoothManager.printCPCL(commands)
```

### Templates Disponíveis
- `generateTestPage()`: Página de teste
- `generateReceipt()`: Recibo
- `generateParkingTicket()`: Ticket de estacionamento
- `generateProductLabel()`: Rótulo de produto

## 🔌 Compatibilidade

### Impressoras Testadas
- Zebra ZQ320
- Zebra ZQ630
- Honeywell PC42t
- Outras impressoras compatíveis com CPCL

### Dispositivos Android
- Android 5.0+ (API 21+)
- Suporte a Bluetooth Classic e BLE
- Permissões de localização

## 🐛 Solução de Problemas

### Bluetooth não conecta
1. Verifique se a impressora está ligada
2. Confirme se está no modo de emparelhamento
3. Verifique as permissões do app
4. Reinicie o Bluetooth do dispositivo

### Impressão não funciona
1. Verifique a conexão Bluetooth
2. Confirme se a impressora suporta CPCL
3. Verifique o papel/etiqueta
4. Teste com a página de teste

### Permissões negadas
1. Vá em Configurações > Apps > Impressora Térmica
2. Conceda todas as permissões necessárias
3. Ou reinstale o app

## 📚 Recursos Adicionais

### Logs
- Todos os eventos são registrados na tela principal
- Use para debug e monitoramento

### Configurações
- O app salva automaticamente o último dispositivo conectado
- Configurações de impressão podem ser personalizadas

## 🤝 Contribuição

1. Fork o projeto
2. Crie uma branch para sua feature (`git checkout -b feature/AmazingFeature`)
3. Commit suas mudanças (`git commit -m 'Add some AmazingFeature'`)
4. Push para a branch (`git push origin feature/AmazingFeature`)
5. Abra um Pull Request

## 📄 Licença

Este projeto está sob a licença MIT. Veja o arquivo `LICENSE` para mais detalhes.

## 📞 Suporte

Para dúvidas ou problemas:
- Abra uma issue no GitHub
- Consulte a documentação do CPCL
- Verifique a compatibilidade da sua impressora

## 🔮 Roadmap

- [ ] Suporte a múltiplas impressoras
- [ ] Templates personalizáveis
- [ ] Histórico de impressões
- [ ] Configurações avançadas de impressão
- [ ] Suporte a imagens
- [ ] Backup/restore de configurações

---

**Desenvolvido com ❤️ para impressoras térmicas Bluetooth**
