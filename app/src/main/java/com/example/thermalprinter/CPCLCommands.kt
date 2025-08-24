package com.example.thermalprinter

object CPCLCommands {
    
    // Comandos básicos CPCL para etiquetas 60x60mm
    // 60mm = 236 dots (203 DPI)
    // 60mm = 240 dots (203 DPI)
    fun startForm(): String = "! 0 200 200 240 1\r\n"
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
    
    // Templates pré-definidos para etiquetas 60x60mm
    fun generateTestPage(): String {
        return buildString {
            append(startForm())
            // Título centralizado
            append(centerText(30, "TESTE", 16))
            append(centerText(60, "60x60mm", 12))
            
            // Linha separadora
            append(horizontalLine(20, 80, 200, 2))
            
            // Informações
            append(text(20, 100, "Data: ${java.text.SimpleDateFormat("dd/MM/yyyy").format(java.util.Date())}", 8))
            append(text(20, 115, "Hora: ${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date())}", 8))
            
            // Código de barras
            append(code128(20, 140, "TEST123", 30))
            
            // QR Code pequeno
            append(qrCode(150, 140, "TEST", 3))
            
            append(endForm())
        }
    }
    
    fun generateLabel60x60(title: String, subtitle: String = "", barcode: String = "", qrData: String = ""): String {
        return buildString {
            append(startForm())
            
            // Título principal
            if (title.isNotEmpty()) {
                append(centerText(30, title, 16))
            }
            
            // Subtítulo
            if (subtitle.isNotEmpty()) {
                append(centerText(60, subtitle, 10))
            }
            
            // Linha separadora
            append(horizontalLine(20, 80, 200, 1))
            
            // Código de barras
            if (barcode.isNotEmpty()) {
                append(code128(20, 100, barcode, 25))
            }
            
            // QR Code
            if (qrData.isNotEmpty()) {
                append(qrCode(150, 100, qrData, 3))
            }
            
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

    /**
     * Comando de teste muito simples para verificar se a impressora responde
     */
    fun generateSimpleTest(): String {
        return buildString {
            append("! 0 200 200 50 1\r\n")  // Form muito pequeno
            append("TEXT 4 0 10 20 TESTE SIMPLES\r\n")  // Texto básico
            append("FORM\r\n")
            append("PRINT\r\n")
        }
    }

    /**
     * Comando de teste com apenas texto
     */
    fun generateTextOnlyTest(text: String): String {
        return buildString {
            append("! 0 200 200 50 1\r\n")
            append("TEXT 4 0 10 20 $text\r\n")
            append("FORM\r\n")
            append("PRINT\r\n")
        }
    }

    /**
     * Comando de teste com etiqueta 60x60mm mais simples
     */
    fun generateSimpleLabel60x60(): String {
        return buildString {
            append("! 0 200 200 240 1\r\n")  // 60x60mm = 240 dots
            append("TEXT 4 0 10 30 TESTE\r\n")
            append("TEXT 4 0 10 50 60x60mm\r\n")
            append("LINE 1 10 80 230 80\r\n")
            append("FORM\r\n")
            append("PRINT\r\n")
        }
    }
}
