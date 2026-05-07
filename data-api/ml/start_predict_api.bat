@echo off
REM Start the FastAPI prediction service (Phase 4+)
REM Run this from the data-api/ml directory or pass the path as argument.

cd /d "%~dp0"

echo Starting FastAPI prediction service on port 8001...
python -m uvicorn predict_api:app --host 0.0.0.0 --port 8001 --reload

pause
