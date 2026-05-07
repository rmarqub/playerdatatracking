#!/usr/bin/env bash
# Start the FastAPI prediction service (Phase 4+)
set -e
cd "$(dirname "$0")"
echo "Starting FastAPI prediction service on port 8001..."
python -m uvicorn predict_api:app --host 0.0.0.0 --port 8001 --reload
