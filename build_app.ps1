$ErrorActionPreference = "Stop"

$Python = ".\.venv\Scripts\python.exe"

if (-not (Test-Path $Python)) {
    python -m venv .venv
}

& $Python -m pip install -r requirements.txt

& $Python -m PyInstaller `
    --noconfirm `
    --clean `
    --windowed `
    --name "Income Expense Tracker" `
    --add-data "templates;templates" `
    --add-data "static;static" `
    --add-data "schema.sql;." `
    app.py

Write-Host ""
Write-Host "Build complete:"
Write-Host "dist\Income Expense Tracker\Income Expense Tracker.exe"
