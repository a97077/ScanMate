package com.example.scanmate;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;

import java.io.InputStream;

final class BitmapOrientationHelper {

    private BitmapOrientationHelper() {
    }

    static Bitmap applyExifAndPortrait(ContentResolver resolver, Uri uri, Bitmap bitmap, boolean forcePortrait) {
        if (bitmap == null) {
            return null;
        }

        Bitmap oriented = applyExifOrientation(bitmap, readExifOrientation(resolver, uri));
        if (forcePortrait && oriented.getWidth() > oriented.getHeight()) {
            Matrix matrix = new Matrix();
            matrix.postRotate(90);
            return createTransformedBitmap(oriented, matrix);
        }
        return oriented;
    }

    private static int readExifOrientation(ContentResolver resolver, Uri uri) {
        if (resolver == null || uri == null) {
            return ExifInterface.ORIENTATION_NORMAL;
        }

        try (InputStream inputStream = resolver.openInputStream(uri)) {
            if (inputStream == null) {
                return ExifInterface.ORIENTATION_NORMAL;
            }
            ExifInterface exif = new ExifInterface(inputStream);
            return exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
            );
        } catch (Exception ignored) {
            return ExifInterface.ORIENTATION_NORMAL;
        }
    }

    private static Bitmap applyExifOrientation(Bitmap bitmap, int orientation) {
        Matrix matrix = new Matrix();
        boolean needsTransform = true;

        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.postRotate(180);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.postRotate(180);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.postRotate(90);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.postRotate(90);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.postRotate(270);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.postRotate(270);
                break;
            default:
                needsTransform = false;
                break;
        }

        return needsTransform ? createTransformedBitmap(bitmap, matrix) : bitmap;
    }

    private static Bitmap createTransformedBitmap(Bitmap bitmap, Matrix matrix) {
        try {
            return Bitmap.createBitmap(
                    bitmap,
                    0,
                    0,
                    bitmap.getWidth(),
                    bitmap.getHeight(),
                    matrix,
                    true
            );
        } catch (Exception ignored) {
            return bitmap;
        }
    }
}
