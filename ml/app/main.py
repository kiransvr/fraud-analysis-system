from fastapi import FastAPI
from pydantic import BaseModel, Field


app = FastAPI(title="Fraud ML Service", version="0.1.0")


class PredictRequest(BaseModel):
    amount: float = Field(..., gt=0)
    customer_id: str
    device_id: str | None = None
    merchant_category: str | None = None
    channel: str | None = None
    country_code: str | None = None


class PredictResponse(BaseModel):
    probability: float
    risk_level: str
    model_version: str


class ExplainRequest(PredictRequest):
    pass


class ExplainResponse(BaseModel):
    model_version: str
    top_features: list[dict]


def map_risk(probability: float) -> str:
    if probability < 0.40:
        return "LOW"
    if probability < 0.75:
        return "MEDIUM"
    return "HIGH"


@app.get("/health")
def health() -> dict:
    return {"status": "ok"}


@app.post("/predict", response_model=PredictResponse)
def predict(payload: PredictRequest) -> PredictResponse:
    # Placeholder scoring logic; replace with loaded XGBoost model inference.
    probability = min(0.99, max(0.01, payload.amount / 10000.0))
    risk = map_risk(probability)
    return PredictResponse(probability=probability, risk_level=risk, model_version="baseline-0.1")


@app.post("/explain", response_model=ExplainResponse)
def explain(payload: ExplainRequest) -> ExplainResponse:
    return ExplainResponse(
        model_version="baseline-0.1",
        top_features=[
            {"feature": "amount", "impact": 0.62},
            {"feature": "country_code", "impact": 0.21},
            {"feature": "channel", "impact": 0.17},
        ],
    )
