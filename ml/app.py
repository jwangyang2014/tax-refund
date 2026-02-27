import os, json
from datetime import datetime, timezone, timedelta
from typing import Optional

import pandas as pd
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from sqlalchemy import create_engine, text
from sklearn.ensemble import GradientBoostingRegressor
from sklearn.preprocessing import OneHotEncoder
from sklearn.compose import ColumnTransformer
from sklearn.pipeline import Pipeline
import joblib

DB_URL = os.environ["ML_DB_URL"]  # e.g. postgresql+psycopg2://user:pass@db:5432/refund
MODEL_PATH = os.environ.get("ML_MODEL_PATH", "/models/eta_model.joblib")
MODEL_META_PATH = os.environ.get("ML_MODEL_META_PATH", "/models/eta_model_meta.json")

app = FastAPI(title="Refund ETA ML Service")

class PredictRequest(BaseModel):
    userId: int
    taxYear: int
    status: str
    filingState: Optional[str] = None
    expectedAmount: Optional[float] = None

class PredictResponse(BaseModel):
    etaDays: int = Field(ge=0, le=3650)
    modelName: str
    modelVersion: str
    features: dict

def load_model():
    if not os.path.exists(MODEL_PATH):
        return None
    return joblib.load(MODEL_PATH)

def load_meta():
    if not os.path.exists(MODEL_META_PATH):
        return {"modelName": "gbrt", "modelVersion": "untrained"}
    return json.load(open(MODEL_META_PATH, "r"))

engine = create_engine(DB_URL, pool_pre_ping=True)

def build_training_frame(limit=200000):
    # Target: remaining days until AVAILABLE from each event row
    q = text("""
    with ev as (
        select
        user_id,
        tax_year,
        filing_state,
        to_status as status,
        expected_amount,
        occurred_at AT TIME ZONE 'UTC' as occurred_at,
        max(
            case when to_status = 'AVAILABLE'
            then occurred_at AT TIME ZONE 'UTC'
            end
        ) over (partition by user_id, tax_year) as available_at
        from refund_status_event
    )
    select
        user_id,
        tax_year,
        filing_state,
        status,
        coalesce(expected_amount, 0) as expected_amount,
        extract(epoch from (available_at - occurred_at))/86400.0 as days_to_available,
        occurred_at
    from ev
    where available_at is not null
        and occurred_at is not null
        and extract(epoch from (available_at - occurred_at)) >= 0
    order by occurred_at desc
    limit :limit
    """)
    df = pd.read_sql(q, engine, params={"limit": limit})
    if df.empty:
        return df
    df["status"] = df["status"].astype(str)
    df["filing_state"] = df.get("filing_state", "NA").fillna("NA").astype(str).str.upper()
    df["expected_amount"] = df["expected_amount"].astype(float)
    df["days_to_available"] = df["days_to_available"].astype(float)
    # Optional seasonality
    df["occurred_at"] = pd.to_datetime(df["occurred_at"], utc=True)
    df["dow"] = df["occurred_at"].dt.dayofweek
    df["month"] = df["occurred_at"].dt.month

    return df

def train_and_save():
    df = build_training_frame()
    if df.empty or len(df) < 50:
        raise RuntimeError("Not enough training data (need >= 50 rows with AVAILABLE outcomes).")

    X = df[["status", "filing_state", "expected_amount", "dow", "month"]]
    y = df["days_to_available"]

    pre = ColumnTransformer(
        transformers=[
            ("cat", OneHotEncoder(handle_unknown="ignore"), ["status", "filing_state"]),
            ("num", "passthrough", ["expected_amount", "dow", "month"]),
        ]
    )

    model = GradientBoostingRegressor(random_state=42)
    pipe = Pipeline([("pre", pre), ("model", model)])
    pipe.fit(X, y)

    os.makedirs(os.path.dirname(MODEL_PATH), exist_ok=True)
    joblib.dump(pipe, MODEL_PATH)

    meta = {
        "modelName": "gbrt",
        "modelVersion": datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ"),
        "trainedAt": datetime.now(timezone.utc).isoformat(),
        "rows": int(len(df)),
        "features": ["status", "filing_state", "expected_amount", "dow", "month"]
    }
    json.dump(meta, open(MODEL_META_PATH, "w"))
    return meta

@app.get("/health")
def health():
    return {"ok": True}

@app.get("/model/info")
def model_info():
    return load_meta()

@app.post("/train")
def train():
    try:
        return train_and_save()
    except Exception as e:
        # Return the actual reason instead of a generic 500
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/predict", response_model=PredictResponse)
def predict(req: PredictRequest):
    pipe = load_model()
    meta = load_meta()
    if pipe is None:
        raise HTTPException(503, "Model not trained yet. Call /train first.")

    now = datetime.now(timezone.utc)
    dow = now.weekday()
    month = now.month
    expected = float(req.expectedAmount or 0.0)

    filing_state = (req.filingState or "NA").upper()

    X = pd.DataFrame([{
        "status": req.status,
        "filing_state": filing_state,
        "expected_amount": expected,
        "dow": dow,
        "month": month
    }])

    yhat = float(pipe.predict(X)[0])
    eta_days = max(0, min(3650, int(round(yhat))))

    return PredictResponse(
        etaDays=eta_days,
        modelName=meta.get("modelName", "gbrt"),
        modelVersion=meta.get("modelVersion", "unknown"),
        features={"status": req.status, "filing_state": filing_state, "expected_amount": expected, "dow": dow, "month": month}
    )