import os
import sqlite3
import sys
import threading
import webbrowser
from datetime import date
from decimal import Decimal, InvalidOperation
from pathlib import Path

from flask import Flask, flash, g, redirect, render_template, request, url_for


def resource_path(*parts):
    if getattr(sys, "frozen", False):
        base_path = Path(sys._MEIPASS)
    else:
        base_path = Path(__file__).resolve().parent

    return base_path.joinpath(*parts)


def data_dir():
    if getattr(sys, "frozen", False):
        base = Path(os.getenv("LOCALAPPDATA", Path.home()))
        path = base / "IncomeExpenseTracker"
    else:
        path = Path(__file__).resolve().parent / "instance"

    path.mkdir(parents=True, exist_ok=True)
    return path


app = Flask(
    __name__,
    template_folder=str(resource_path("templates")),
    static_folder=str(resource_path("static")),
)
app.secret_key = os.getenv("FLASK_SECRET_KEY", "dev-secret-key")
app.config["DATABASE"] = str(data_dir() / "income_expense_tracker.sqlite3")


def get_db():
    if "db" not in g:
        g.db = sqlite3.connect(app.config["DATABASE"])
        g.db.row_factory = sqlite3.Row

    return g.db


@app.teardown_appcontext
def close_db(error=None):
    db = g.pop("db", None)
    if db is not None:
        db.close()


def init_db():
    with app.app_context():
        db = get_db()
        db.executescript(resource_path("schema.sql").read_text(encoding="utf-8"))
        db.commit()


def money_value(raw_amount):
    try:
        amount = Decimal(raw_amount)
    except (InvalidOperation, ValueError):
        return None

    if amount <= 0:
        return None

    return f"{amount:.2f}"


def transaction_summary(selected_type="", selected_category="", selected_month=""):
    filters = []
    params = []

    if selected_type in {"income", "expense"}:
        filters.append("type = ?")
        params.append(selected_type)

    if selected_category:
        filters.append("category = ?")
        params.append(selected_category)

    if selected_month:
        filters.append("strftime('%Y-%m', transaction_date) = ?")
        params.append(selected_month)

    where_clause = f"WHERE {' AND '.join(filters)}" if filters else ""
    db = get_db()

    totals = db.execute(
        """
        SELECT
            COALESCE(SUM(CASE WHEN type = 'income' THEN amount ELSE 0 END), 0) AS total_income,
            COALESCE(SUM(CASE WHEN type = 'expense' THEN amount ELSE 0 END), 0) AS total_expense,
            COALESCE(SUM(CASE
                WHEN type = 'income' AND strftime('%Y-%m', transaction_date) = strftime('%Y-%m', 'now', 'localtime')
                THEN amount ELSE 0 END), 0) AS month_income,
            COALESCE(SUM(CASE
                WHEN type = 'expense' AND strftime('%Y-%m', transaction_date) = strftime('%Y-%m', 'now', 'localtime')
                THEN amount ELSE 0 END), 0) AS month_expense,
            COUNT(*) AS count
        FROM transactions
        """
    ).fetchone()

    filtered_totals = db.execute(
        f"""
        SELECT
            COALESCE(SUM(CASE WHEN type = 'income' THEN amount ELSE 0 END), 0) AS filtered_income,
            COALESCE(SUM(CASE WHEN type = 'expense' THEN amount ELSE 0 END), 0) AS filtered_expense,
            COUNT(*) AS filtered_count,
            COALESCE(AVG(amount), 0) AS average_amount
        FROM transactions
        {where_clause}
        """,
        params,
    ).fetchone()

    expense_categories = db.execute(
        """
        SELECT category, SUM(amount) AS total
        FROM transactions
        WHERE type = 'expense'
        GROUP BY category
        ORDER BY total DESC
        LIMIT 6
        """
    ).fetchall()

    income_categories = db.execute(
        """
        SELECT category, SUM(amount) AS total
        FROM transactions
        WHERE type = 'income'
        GROUP BY category
        ORDER BY total DESC
        LIMIT 6
        """
    ).fetchall()

    category_options = [
        row["category"]
        for row in db.execute(
            """
            SELECT DISTINCT category
            FROM transactions
            ORDER BY category
            """
        ).fetchall()
    ]

    months = db.execute(
        """
        SELECT
            strftime('%Y-%m', transaction_date) AS month,
            COALESCE(SUM(CASE WHEN type = 'income' THEN amount ELSE 0 END), 0) AS income,
            COALESCE(SUM(CASE WHEN type = 'expense' THEN amount ELSE 0 END), 0) AS expense
        FROM transactions
        GROUP BY month
        ORDER BY month DESC
        LIMIT 12
        """
    ).fetchall()

    transactions = db.execute(
        f"""
        SELECT id, type, title, category, amount, transaction_date, notes
        FROM transactions
        {where_clause}
        ORDER BY transaction_date DESC, id DESC
        """,
        params,
    ).fetchall()

    return (
        totals,
        filtered_totals,
        expense_categories,
        income_categories,
        category_options,
        months,
        transactions,
    )


@app.before_request
def ensure_database():
    if not getattr(app, "_db_ready", False):
        init_db()
        app._db_ready = True


@app.route("/")
def index():
    selected_type = request.args.get("type", "").strip()
    selected_category = request.args.get("category", "").strip()
    selected_month = request.args.get("month", "").strip()
    (
        totals,
        filtered_totals,
        expense_categories,
        income_categories,
        category_options,
        months,
        transactions,
    ) = transaction_summary(selected_type, selected_category, selected_month)

    balance = totals["total_income"] - totals["total_expense"]
    month_balance = totals["month_income"] - totals["month_expense"]
    filtered_balance = filtered_totals["filtered_income"] - filtered_totals["filtered_expense"]

    return render_template(
        "index.html",
        totals=totals,
        filtered_totals=filtered_totals,
        expense_categories=expense_categories,
        income_categories=income_categories,
        category_options=category_options,
        months=months,
        transactions=transactions,
        balance=balance,
        month_balance=month_balance,
        filtered_balance=filtered_balance,
        selected_type=selected_type,
        selected_category=selected_category,
        selected_month=selected_month,
        today=date.today().isoformat(),
    )


@app.post("/transactions")
def add_transaction():
    transaction_type = request.form.get("type", "expense").strip()
    title = request.form.get("title", "").strip()
    category = request.form.get("category", "General").strip() or "General"
    amount = money_value(request.form.get("amount", "").strip())
    transaction_date = request.form.get("transaction_date", "").strip() or date.today().isoformat()
    notes = request.form.get("notes", "").strip() or None

    if transaction_type not in {"income", "expense"}:
        flash("Choose income or expense.", "error")
        return redirect(url_for("index"))

    if not title:
        flash("Transaction name is required.", "error")
        return redirect(url_for("index"))

    if amount is None:
        flash("Amount must be greater than zero.", "error")
        return redirect(url_for("index"))

    db = get_db()
    db.execute(
        """
        INSERT INTO transactions (type, title, category, amount, transaction_date, notes)
        VALUES (?, ?, ?, ?, ?, ?)
        """,
        (transaction_type, title, category, amount, transaction_date, notes),
    )
    db.commit()

    flash("Transaction saved. Balance updated.", "success")
    return redirect(url_for("index"))


@app.post("/transactions/<int:transaction_id>/delete")
def delete_transaction(transaction_id):
    db = get_db()
    db.execute("DELETE FROM transactions WHERE id = ?", (transaction_id,))
    db.commit()

    flash("Transaction deleted. Totals recalculated.", "success")
    return redirect(url_for("index"))


def open_browser():
    webbrowser.open_new("http://127.0.0.1:5000")


if __name__ == "__main__":
    if os.getenv("FLASK_SKIP_BROWSER") != "1":
        threading.Timer(1.0, open_browser).start()

    app.run(host="127.0.0.1", port=5000, debug=False, use_reloader=False)
