#!/usr/bin/env python3
"""Simple HTTP server with no-cache headers for dev."""
import http.server
import os
import sys

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 3000

class NoCacheHandler(http.server.SimpleHTTPRequestHandler):
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
