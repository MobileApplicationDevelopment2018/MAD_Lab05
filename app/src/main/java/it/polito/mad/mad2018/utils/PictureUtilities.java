package it.polito.mad.mad2018.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.media.ExifInterface;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class PictureUtilities {

    public static final String IMAGE_CONTENT_TYPE_UPLOAD = "image/webp";

    private static CompressedImage compressImage(String imagePath, int pictureSize,
                                                 int thumbnailSize, int quality) {

        if (imagePath == null || pictureSize <= 0 || thumbnailSize < 0 ||
                quality < 0 || quality > 100) {
            return null;
        }

        Bitmap pictureBitmap = PictureUtilities.getImage(imagePath, pictureSize, pictureSize);
        if (pictureBitmap == null) {
            return null;
        }

        ByteArrayOutputStream picture = new ByteArrayOutputStream();
        pictureBitmap.compress(Bitmap.CompressFormat.WEBP, quality, picture);

        // Process the thumbnail
        ByteArrayOutputStream thumbnail = null;
        if (thumbnailSize > 0) {

            float thumbnailRatio = (float) thumbnailSize / Math.max(pictureBitmap.getWidth(), pictureBitmap.getHeight());
            if (thumbnailRatio > 1) {
                thumbnailRatio = 1;
            }
            Bitmap thumbnailBitmap = Bitmap.createScaledBitmap(pictureBitmap,
                    (int) (pictureBitmap.getWidth() * thumbnailRatio),
                    (int) (pictureBitmap.getHeight() * thumbnailRatio),
                    true);

            if (thumbnailBitmap == null) {
                return null;
            }

            thumbnail = new ByteArrayOutputStream();
            thumbnailBitmap.compress(Bitmap.CompressFormat.WEBP, quality / 2, thumbnail);
        }

        return new CompressedImage(picture, thumbnail);
    }

    private static Bitmap getImage(String imagePath, int targetWidth, int targetHeight) {

        // Get the dimensions of the bitmap
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        bmOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(imagePath, bmOptions);
        int photoW = bmOptions.outWidth;
        int photoH = bmOptions.outHeight;

        // Determine how much to scale down the image
        int scaleFactor = 0;
        if (targetWidth != 0 && targetHeight != 0) {
            scaleFactor = Math.min(photoW / targetWidth, photoH / targetHeight);
        }

        // Decode the image file into a Bitmap sized to fill the View
        bmOptions.inJustDecodeBounds = false;
        bmOptions.inSampleSize = scaleFactor;
        bmOptions.inPurgeable = true;

        Bitmap image = BitmapFactory.decodeFile(imagePath, bmOptions);
        if (image == null) {
            return null;
        }

        return rotateImage(image, getRotation(imagePath));
    }

    private static int getRotation(@NonNull String imagePath) {
        ExifInterface exifInterface;

        try {
            exifInterface = new ExifInterface(imagePath);
        } catch (IOException e) {
            return 0;
        }


        int orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_UNDEFINED);
        switch (orientation) {

            case ExifInterface.ORIENTATION_ROTATE_90:
                return 90;

            case ExifInterface.ORIENTATION_ROTATE_180:
                return 180;

            case ExifInterface.ORIENTATION_ROTATE_270:
                return 270;

            case ExifInterface.ORIENTATION_NORMAL:
            default:
                return 0;
        }
    }

    private static Bitmap rotateImage(@NonNull Bitmap source, float angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(),
                matrix, true);
    }

    public static class CompressedImage {
        private final ByteArrayOutputStream picture;
        private final ByteArrayOutputStream thumbnail;

        CompressedImage(@NonNull ByteArrayOutputStream picture,
                        ByteArrayOutputStream thumbnail) {
            this.picture = picture;
            this.thumbnail = thumbnail;
        }

        public ByteArrayOutputStream getPicture() {
            return picture;
        }

        public ByteArrayOutputStream getThumbnail() {
            return thumbnail;
        }
    }

    public static class CompressImageAsync extends AsyncTask<Void, Void, CompressedImage> {

        private final String imagePath;
        private final int pictureSize;
        private final int thumbnailSize;
        private final int quality;
        private final OnCompleteListener onCompleteListener;

        public CompressImageAsync(String imagePath, int pictureSize, int thumbnailSize, int quality,
                                  @NonNull OnCompleteListener onCompleteListener) {
            this.imagePath = imagePath;
            this.pictureSize = pictureSize;
            this.thumbnailSize = thumbnailSize;
            this.quality = quality;
            this.onCompleteListener = onCompleteListener;
        }

        @Override
        protected CompressedImage doInBackground(Void... input) {
            return PictureUtilities.compressImage(imagePath, pictureSize, thumbnailSize, quality);
        }

        @Override
        protected void onPostExecute(CompressedImage data) {
            onCompleteListener.onComplete(data);
        }

        public interface OnCompleteListener {
            void onComplete(CompressedImage data);
        }
    }
}
