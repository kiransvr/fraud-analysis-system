# ML Service (FastAPI + XGBoost)

## Current Status

- FastAPI stub is available in app/main.py.
- Endpoints: /health, /predict, /explain.
- Risk mapping follows initial policy: LOW, MEDIUM, HIGH.

## Next Implementation Steps

1. Implement feature engineering pipeline.
2. Train XGBoost model and persist versioned artifacts.
3. Load model on startup and replace placeholder scoring.
4. Integrate SHAP values in /explain output.
5. Add tests for preprocessing, inference, and explainability.
