"""Private HTTP adapter; the scientific worker runs in a bounded child process."""
from http.server import BaseHTTPRequestHandler
import hmac
import json
import os
from pathlib import Path
import subprocess
import sys
import threading

active = threading.Lock()

class handler(BaseHTTPRequestHandler):
    def reply(self, status, value):
        data = json.dumps(value).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        self.reply(200, {"app": "Portal stitch worker", "status": "ready"})

    def do_POST(self):
        secret = os.environ.get("PORTAL_WORKER_SECRET", "")
        if not secret or not hmac.compare_digest(self.headers.get("Authorization", ""), "Bearer " + secret):
            self.reply(401, {"error": "Worker authorization required."})
            return
        if not active.acquire(blocking=False):
            self.reply(202, {"status": "busy"})
            return
        try:
            job = Path(__file__).resolve().parent.parent / "worker" / "cloud_job.py"
            environment = os.environ.copy()
            environment["PYTHONPATH"] = os.pathsep.join(sys.path)
            result = subprocess.run([sys.executable, str(job)], capture_output=True, text=True, timeout=270, env=environment)
            if result.returncode:
                # Keep credentials, backend error details, and photo paths out of responses.
                print("Portal worker invocation failed", flush=True)
                last_line = result.stderr.splitlines()[-1] if result.stderr else ""
                if last_line.startswith(("ModuleNotFoundError:", "ImportError:")):
                    print(last_line, flush=True)
                self.reply(503, {"status": "retry"})
            else:
                self.reply(200, json.loads(result.stdout.splitlines()[-1]))
        except subprocess.TimeoutExpired:
            self.reply(503, {"status": "retry", "reason": "Processing time limit reached; queue lease will recover."})
        except Exception:
            self.reply(503, {"status": "retry"})
        finally:
            active.release()
