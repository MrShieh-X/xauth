package com.mrshiehx.xauth.qrcode.encode;

import android.graphics.Bitmap;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;

import java.util.Arrays;

public final class QRCodeCreator {
    /**
     * 生成QRCode（二维码）
     */
    public static Bitmap createQRCode(String content, int width, int height) throws WriterException {
        if (content == null || content.equals("")) {
            return null;
        }

        // 生成二维矩阵,编码时指定大小,不要生成了图片以后再进行缩放,这样会模糊导致识别失败
        BitMatrix matrix = new MultiFormatWriter().encode(content,
                BarcodeFormat.QR_CODE, width, height);

        int width2 = matrix.getWidth();
        int height2 = matrix.getHeight();

        // 二维矩阵转为一维像素数组,也就是一直横着排了
        int[] pixels = new int[width2 * height2];
        Arrays.fill(pixels, -1);

        for (int y = 0; y < height2; y++) {
            for (int x = 0; x < width2; x++) {
                if (matrix.get(x, y)) {
                    pixels[y * width2 + x] = 0xff000000;
                }

            }
        }

        Bitmap bitmap = Bitmap.createBitmap(width2, height2,
                Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, width2, 0, 0, width2, height2);
        return bitmap;
    }
}
