#!/usr/bin/env python3
"""Simple HTTP server with no-cache headers for dev."""
import http.server
import os
import sys
from pathlib import Path

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 3000
VERSION = str(int(Path(__file__).stat().st_mtime))

class NoCacheHandler(http.server.SimpleHTTPRequestHandler):
    def do_GET(self):
        path = self.translate_path(self.path)
        if path.endswith(".html") and os.path.isfile(path):
            with open(path, "rb") as f:
                content = f.read().replace(b"__APP_VERSION__", VERSION.encode("ascii"))
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(content)))
            self.end_headers()
            self.wfile.write(content)
            return
        super().do_GET()

    def end_headers(self):
        self.send_header("Cache-Control", "no-cache, no-store, must-revalidate")
        self.send_header("Pragma", "no-cache")
        self.send_header("Expires", "0")
        super().end_headers()

    def log_message(self, format, *args):
        print(f"[{self.log_date_time_string()}] {args[0]}")

os.chdir(os.path.dirname(os.path.abspath(__file__)))
print(f"Serving {os.getcwd()} on http://localhost:{PORT}")
print("Cache-Control: no-cache (enabled)")
http.server.HTTPServer(("", PORT), NoCacheHandler).serve_forever()
