package com.example.util

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

object QrCodeGenerator {
    fun generateUpiQr(
        upiId: String,
        payeeName: String,
        amount: Double,
        note: String,
        sizePx: Int = 512
    ): ImageBitmap? {
        return try {
            val uri = buildUpiUri(upiId, payeeName, amount, note)
            val hints = mapOf(
                EncodeHintType.MARGIN to 1
            )
            val bitMatrix = QRCodeWriter().encode(uri, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (bitMatrix[x, y]) Color.parseColor("#090B10") else Color.WHITE
                }
            }
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            bitmap.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    fun buildUpiUri(
        upiId: String,
        payeeName: String,
        amount: Double,
        note: String
    ): String {
        val cleanAmount = if (amount % 1.0 == 0.0) amount.toLong().toString() else String.format("%.2f", amount)
        val encodedName = payeeName.replace(" ", "%20")
        val encodedNote = note.replace(" ", "%20")
        return "upi://pay?pa=$upiId&pn=$encodedName&am=$cleanAmount&cu=INR&tn=$encodedNote"
    }
}
