package it.polito.mad.mad2018.barcodereader;

import android.util.SparseArray;

import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.FocusingProcessor;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.barcode.Barcode;

class BarcodeFocusingProcessor extends FocusingProcessor<Barcode> {
    public BarcodeFocusingProcessor(Detector<Barcode> barcodeDetector, BarcodeGraphicTracker tracker) {
        super(barcodeDetector, tracker);
    }

    @Override
    public int selectFocus(Detector.Detections<Barcode> detections) {
        SparseArray<Barcode> barcodes = detections.getDetectedItems();
        Frame.Metadata frameMetadata = detections.getFrameMetadata();
        float w = frameMetadata.getWidth() / 2;
        float h = frameMetadata.getHeight() / 2;

        double minDist = -1;
        int id = -1;

        for (int i = 0; i < barcodes.size(); i++) {
            int currId = barcodes.keyAt(i);
            Barcode barcode = barcodes.get(currId);
            float dx = w - barcode.getBoundingBox().centerX();
            float dy = h - barcode.getBoundingBox().centerY();

            double dist = Math.sqrt((dx * dx) + (dy * dy));

            if (dist < minDist || minDist == -1) {
                id = currId;
                minDist = dist;
            }
        }
        return id;
    }
}
