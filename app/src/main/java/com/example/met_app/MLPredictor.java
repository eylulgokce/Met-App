package com.example.met_app;

import android.content.Context;
import android.content.res.AssetManager;
import ai.onnxruntime.*;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class MLPredictor {
    private OrtEnvironment env;
    private OrtSession session;
    private static final float MIN_CONFIDENCE_THRESHOLD = 0.6f;

    public void init(Context context) throws Exception {
        if (session != null) return;
        env = OrtEnvironment.getEnvironment();
        session = env.createSession(loadModel(context, "rf.onnx"),
                new OrtSession.SessionOptions());
    }

    private byte[] loadModel(Context ctx, String assetName) throws Exception {
        AssetManager am = ctx.getAssets();
        try (InputStream is = am.open(assetName)) {
            byte[] modelBytes = new byte[is.available()];
            is.read(modelBytes);
            return modelBytes;
        }
    }

    public MetClass predict(float[] features) throws OrtException {
        if (session == null || !validateFeatures(features)) {
            return MetClass.SEDENTARY;
        }

        // 1 x N
        float[][] input2d = new float[1][features.length];
        System.arraycopy(features, 0, input2d[0], 0, features.length);

        String inputName = session.getInputInfo().keySet().iterator().next();
        try (OnnxTensor tensor = OnnxTensor.createTensor(env, input2d)) {
            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put(inputName, tensor);

            try (OrtSession.Result result = session.run(inputs)) {
                PredictionResult predResult = extractPrediction(result);

                // confidence thresholding
                if (predResult.confidence < MIN_CONFIDENCE_THRESHOLD) {
                    return MetClass.SEDENTARY;
                }

                return predResult.metClass;
            }
        }
    }

    public PredictionResult predictWithConfidence(float[] features) throws OrtException {
        if (session == null || !validateFeatures(features)) {
            return new PredictionResult(MetClass.SEDENTARY, 0.0f);
        }

        float[][] input2d = new float[1][features.length];
        System.arraycopy(features, 0, input2d[0], 0, features.length);

        String inputName = session.getInputInfo().keySet().iterator().next();
        try (OnnxTensor tensor = OnnxTensor.createTensor(env, input2d)) {
            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put(inputName, tensor);

            try (OrtSession.Result result = session.run(inputs)) {
                return extractPrediction(result);
            }
        }
    }

    private boolean validateFeatures(float[] features) {
        if (features == null || features.length == 0) {
            return false;
        }

        for (float feature : features) {
            if (Float.isNaN(feature) || Float.isInfinite(feature)) {
                return false;
            }
        }
        return true;
    }

    private PredictionResult extractPrediction(OrtSession.Result result) throws OrtException {
        OnnxValue out0 = result.get(0);
        if (out0.getInfo() instanceof TensorInfo &&
                ((TensorInfo) out0.getInfo()).type == OnnxJavaType.INT64) {

            long[] label = (long[]) out0.getValue();
            int idx = (int) label[0];
            return new PredictionResult(MetClass.fromIndex(idx), 1.0f);
        }

        // Probability handling
        if (out0.getInfo() instanceof TensorInfo &&
                ((TensorInfo) out0.getInfo()).type == OnnxJavaType.FLOAT) {

            float[][] probs = (float[][]) out0.getValue();
            int idx = argmax(probs[0]);
            float confidence = probs[0][idx];
            return new PredictionResult(MetClass.fromIndex(idx), confidence);
        }

        if (result.size() > 1) {
            OnnxValue out1 = result.get(1);
            if (out1.getInfo() instanceof TensorInfo &&
                    ((TensorInfo) out1.getInfo()).type == OnnxJavaType.FLOAT) {
                float[][] probs = (float[][]) out1.getValue();
                int idx = argmax(probs[0]);
                float confidence = probs[0][idx];
                return new PredictionResult(MetClass.fromIndex(idx), confidence);
            }

            // map-based probability output
            if (out1 instanceof OnnxMap) {
                @SuppressWarnings("unchecked")
                Map<String, Float> probMap = (Map<String, Float>) out1.getValue();
                int idx = indexOfMax(probMap);
                float confidence = getMaxValueFromMap(probMap);
                return new PredictionResult(MetClass.fromIndex(idx), confidence);
            }
        }

        return new PredictionResult(MetClass.SEDENTARY, 0.0f);
    }

    private int argmax(float[] a) {
        int best = 0;
        float b = a[0];
        for (int i = 1; i < a.length; i++) {
            if (a[i] > b) {
                b = a[i];
                best = i;
            }
        }
        return best;
    }

    private int indexOfMax(Map<String, Float> m) {
        String[] order = {"Sedentary", "Light", "Moderate", "Vigorous"};
        int bestIdx = 0;
        float best = -Float.MAX_VALUE;

        for (int i = 0; i < order.length; i++) {
            Float v = m.get(order[i]);
            if (v != null && v > best) {
                best = v;
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    private float getMaxValueFromMap(Map<String, Float> m) {
        String[] order = {"Sedentary", "Light", "Moderate", "Vigorous"};
        float maxValue = 0.0f;

        for (String className : order) {
            Float v = m.get(className);
            if (v != null && v > maxValue) {
                maxValue = v;
            }
        }
        return maxValue;
    }

    public void cleanup() {
        try {
            if (session != null) {
                session.close();
                session = null;
            }
            if (env != null) {
                env.close();
                env = null;
            }
        } catch (OrtException e) {
            e.printStackTrace();
        }
    }

    public static class PredictionResult {
        public final MetClass metClass;
        public final float confidence;

        public PredictionResult(MetClass metClass, float confidence) {
            this.metClass = metClass;
            this.confidence = confidence;
        }
    }
}