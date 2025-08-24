package com.example.thermalprinter

object CPCLCommands {
    
    // Comandos básicos CPCL
    fun startForm(): String = "! 0 200 200 210 1\r\n"
    fun endForm(): String = "FORM\r\nPRINT\r\n"
    
    // Comandos de texto
    fun text(x: Int, y: Int, text: String, fontSize: Int = 10): String {
        return "TEXT $fontSize 0 $x $y $text\r\n"
    }
    
    fun centerText(y: Int, text: String, fontSize: Int = 10): String {
        return "CENTER $fontSize 0 $y $text\r\n"
    }
    
    // Comandos de linha
    fun line(x1: Int, y1: Int, x2: Int, y2: Int, thickness: Int = 1): String {
        return "LINE $thickness $x1 $y1 $x2 $y2\r\n"
    }
    
    fun horizontalLine(x: Int, y: Int, width: Int, thickness: Int = 1): String {
        return "LINE $thickness $x $y ${x + width} $y\r\n"
    }
    
    // Comandos de forma
    fun rectangle(x: Int, y: Int, width: Int, height: Int, thickness: Int = 1): String {
        return "BOX $thickness $x $y ${x + width} ${y + height}\r\n"
    }
    
    // Comandos de código de barras
    fun code128(x: Int, y: Int, data: String, height: Int = 50): String {
        return "B 128 1 1 $height $x $y $data\r\n"
    }
    
    fun code39(x: Int, y: Int, data: String, height: Int = 50): String {
        return "B 39 1 1 $height $x $y $data\r\n"
    }
    
    // Comando QR Code
    fun qrCode(x: Int, y: Int, data: String, size: Int = 5): String {
        return "B QR $x $y $size $data\r\n"
    }
    
    // Comando de imagem (se suportado)
    fun image(x: Int, y: Int, imageData: String): String {
        return "EG $x $y $imageData\r\n"
    }
    
    // Comandos de formatação
    fun setFont(fontNumber: Int): String = "FONT $fontNumber\r\n"
    fun setLineSpacing(spacing: Int): String = "SETSP $spacing\r\n"
    fun setLeftMargin(margin: Int): String = "LEFTMARGIN $margin\r\n"
    fun setRightMargin(margin: Int): String = "RIGHTMARGIN $margin\r\n"
    fun setTopMargin(margin: Int): String = "TOPMARGIN $margin\r\n"
    fun setBottomMargin(margin: Int): String = "BOTTOMMARGIN $margin\r\n"
    
    // Templates pré-definidos
    fun generateTestPage(): String {
        return buildString {
            append(startForm())
            append(centerText(50, "PÁGINA DE TESTE", 16))
            append(centerText(80, "Impressora Térmica", 12))
            append(centerText(110, "Bluetooth", 12))
            append(horizontalLine(50, 130, 300, 2))
            append(text(50, 160, "Data: ${java.text.SimpleDateFormat("dd/MM/yyyy").format(java.util.Date())}", 10))
            append(text(50, 180, "Hora: ${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date())}", 10))
            append(text(50, 200, "Teste de impressão", 10))
            append(text(50, 220, "CPCL Commands", 10))
            append(horizontalLine(50, 250, 300, 1))
            append(code128(50, 280, "TEST123", 40))
            append(qrCode(250, 280, "https://github.com/RaroTecnologia"))
            append(endForm())
        }
    }
    
    fun generateReceipt(companyName: String, items: List<Pair<String, Double>>, total: Double): String {
        return buildString {
            append(startForm())
            append(centerText(50, companyName, 14))
            append(centerText(80, "COMPROVANTE", 12))
            append(horizontalLine(50, 100, 300, 1))
            append(text(50, 120, "Data: ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(java.util.Date())}", 10))
            append(horizontalLine(50, 140, 300, 1))
            
            var yPos = 160
            items.forEach { (item, price) ->
                append(text(50, yPos, item, 10))
                append(text(250, yPos, "R$ %.2f".format(price), 10))
                yPos += 20
            }
            
            append(horizontalLine(50, yPos, 300, 1))
            append(text(50, yPos + 20, "TOTAL:", 12))
            append(text(250, yPos + 20, "R$ %.2f".format(total), 12))
            append(centerText(yPos + 50, "Obrigado!", 12))
            append(endForm())
        }
    }
    
    fun generateParkingTicket(plate: String, entryTime: String, duration: String, amount: Double): String {
        return buildString {
            append(startForm())
            append(centerText(50, "ESTACIONAMENTO", 16))
            append(centerText(80, "COMPROVANTE", 12))
            append(horizontalLine(50, 100, 300, 2))
            append(text(50, 130, "Placa: $plate", 12))
            append(text(50, 150, "Entrada: $entryTime", 10))
            append(text(50, 170, "Duração: $duration", 10))
            append(text(50, 190, "Valor: R$ %.2f".format(amount), 12))
            append(horizontalLine(50, 210, 300, 1))
            append(qrCode(150, 240, "PARKING:$plate:$entryTime"))
            append(centerText(300, "Boa viagem!", 10))
            append(endForm())
        }
    }
    
    fun generateProductLabel(productName: String, price: Double, barcode: String): String {
        return buildString {
            append(startForm())
            append(centerText(50, productName, 12))
            append(centerText(80, "R$ %.2f".format(price), 14))
            append(code128(100, 110, barcode, 50))
            append(centerText(160, barcode, 8))
            append(endForm())
        }
    }
}
