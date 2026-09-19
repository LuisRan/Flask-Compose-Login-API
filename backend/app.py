import os, secrets, datetime
from functools import wraps

import jwt
from flask import Flask, request, jsonify, g
from flask_sqlalchemy import SQLAlchemy
from flask_bcrypt import Bcrypt

DATA_DIR = os.environ.get("DATA_DIR", "/data")
os.makedirs(DATA_DIR, exist_ok=True)


def load_secret():
    """Usa JWT_SECRET si existe; si no, genera una y la persiste en el volumen."""
    env = os.environ.get("JWT_SECRET")
    if env:
        return env
    path = os.path.join(DATA_DIR, ".jwt_secret")
    if os.path.exists(path):
        with open(path) as f:
            return f.read().strip()
    secret = secrets.token_hex(32)
    with open(path, "w") as f:
        f.write(secret)
    os.chmod(path, 0o600)
    return secret


app = Flask(__name__)
app.config["SQLALCHEMY_DATABASE_URI"] = f"sqlite:///{os.path.join(DATA_DIR, 'app.db')}"
app.config["SQLALCHEMY_TRACK_MODIFICATIONS"] = False
SECRET = load_secret()
TOKEN_HOURS = int(os.environ.get("TOKEN_HOURS", "2"))

db = SQLAlchemy(app)
bcrypt = Bcrypt(app)


class User(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    username = db.Column(db.String(50), unique=True, nullable=False)
    password_hash = db.Column(db.String(128), nullable=False)


class Task(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    title = db.Column(db.String(120), nullable=False)
    description = db.Column(db.String(500), default="")
    done = db.Column(db.Boolean, default=False)
    user_id = db.Column(db.Integer, db.ForeignKey("user.id"), nullable=False)

    def to_dict(self):
        return {"id": self.id, "title": self.title,
                "description": self.description, "done": self.done}


with app.app_context():
    db.create_all()


def error(msg, code):
    return jsonify({"error": msg}), code


def auth_required(fn):
    @wraps(fn)
    def wrapper(*args, **kwargs):
        header = request.headers.get("Authorization", "")
        if not header.startswith("Bearer "):
            return error("Token requerido", 401)
        try:
            payload = jwt.decode(header[7:], SECRET, algorithms=["HS256"])
            g.user_id = int(payload["sub"])
        except jwt.ExpiredSignatureError:
            return error("Sesión expirada", 401)
        except Exception:
            return error("Token inválido", 401)
        return fn(*args, **kwargs)
    return wrapper


@app.get("/")
def health():
    return jsonify({"status": "ok"})


@app.post("/register")
def register():
    data = request.get_json(silent=True) or {}
    username = (data.get("username") or "").strip()
    password = data.get("password") or ""
    if len(username) < 3 or len(password) < 6:
        return error("Usuario mín. 3 caracteres y contraseña mín. 6", 400)
    if User.query.filter_by(username=username).first():
        return error("El usuario ya existe", 409)
    hashed = bcrypt.generate_password_hash(password).decode("utf-8")
    db.session.add(User(username=username, password_hash=hashed))
    db.session.commit()
    return jsonify({"message": "Usuario registrado"}), 201


@app.post("/login")
def login():
    data = request.get_json(silent=True) or {}
    user = User.query.filter_by(username=(data.get("username") or "").strip()).first()
    if not user or not bcrypt.check_password_hash(user.password_hash, data.get("password") or ""):
        return error("Credenciales incorrectas", 401)
    now = datetime.datetime.now(datetime.timezone.utc)
    token = jwt.encode(
        {"sub": str(user.id), "iat": now, "exp": now + datetime.timedelta(hours=TOKEN_HOURS)},
        SECRET, algorithm="HS256")
    return jsonify({"token": token, "username": user.username}), 200


def own_task(task_id):
    return Task.query.filter_by(id=task_id, user_id=g.user_id).first()


@app.get("/tasks")
@auth_required
def list_tasks():
    tasks = Task.query.filter_by(user_id=g.user_id).order_by(Task.id.desc()).all()
    return jsonify([t.to_dict() for t in tasks]), 200


@app.get("/tasks/<int:task_id>")
@auth_required
def get_task(task_id):
    t = own_task(task_id)
    return (jsonify(t.to_dict()), 200) if t else error("Tarea no encontrada", 404)


@app.post("/tasks")
@auth_required
def create_task():
    data = request.get_json(silent=True) or {}
    title = (data.get("title") or "").strip()
    if not title:
        return error("El título es obligatorio", 400)
    t = Task(title=title, description=data.get("description", ""),
             done=bool(data.get("done", False)), user_id=g.user_id)
    db.session.add(t)
    db.session.commit()
    return jsonify(t.to_dict()), 201


@app.put("/tasks/<int:task_id>")
@auth_required
def update_task(task_id):
    t = own_task(task_id)
    if not t:
        return error("Tarea no encontrada", 404)
    data = request.get_json(silent=True) or {}
    title = (data.get("title", t.title) or "").strip()
    if not title:
        return error("El título es obligatorio", 400)
    t.title = title
    t.description = data.get("description", t.description)
    t.done = bool(data.get("done", t.done))
    db.session.commit()
    return jsonify(t.to_dict()), 200


@app.delete("/tasks/<int:task_id>")
@auth_required
def delete_task(task_id):
    t = own_task(task_id)
    if not t:
        return error("Tarea no encontrada", 404)
    db.session.delete(t)
    db.session.commit()
    return jsonify({"message": "Tarea eliminada"}), 200
