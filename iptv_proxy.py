#!/usr/bin/env python3
"""
本地代理：把 192.168.0.110:8088/iptv.m3u8 和 xmltv.xml 暴露给模拟器，
并把 192.168.0.110:7088/rtp/* 直播流转发到本机 7088，
让 Android 模拟器通过 adb reverse 访问。
"""
import http.server
import socketserver
import threading
import urllib.request
import urllib.parse
import sys

IPTV_HOST = "192.168.0.110"
M3U_PORT = 8088
STREAM_PORT = 7088


def _proxy_headers(upstream_resp, downstream_handler, extra=None):
    for k, v in upstream_resp.headers.items():
        if k.lower() in ("transfer-encoding", "content-length", "connection"):
            continue
        downstream_handler.send_header(k, v)
    if extra:
        for k, v in extra.items():
            downstream_handler.send_header(k, v)


def _stream(upstream_url, handler):
    req = urllib.request.Request(
        upstream_url,
        headers={
            "Host": f"{IPTV_HOST}:{STREAM_PORT}",
            "User-Agent": "ExoPlayer",
            "Accept": "*/*",
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            handler.send_response(resp.status)
            _proxy_headers(resp, handler)
            handler.end_headers()
            while True:
                chunk = resp.read(8192)
                if not chunk:
                    break
                handler.wfile.write(chunk)
    except Exception as e:
        print(f"[stream] proxy error: {e}")
        try:
            handler.send_error(502, "proxy error")
        except Exception:
            pass


class M3uHandler(http.server.BaseHTTPRequestHandler):
    """8088:  playlist / EPG"""

    def log_message(self, fmt, *args):
        print(f"[m3u] {self.client_address[0]} - {fmt % args}")

    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path

        if path == "/iptv.m3u8":
            try:
                url = f"http://{IPTV_HOST}:{M3U_PORT}/iptv.m3u8"
                req = urllib.request.Request(url, headers={"Host": f"{IPTV_HOST}:{M3U_PORT}"})
                with urllib.request.urlopen(req, timeout=15) as resp:
                    body = resp.read().decode("utf-8", errors="replace")
                    # 让模拟器请求本机 7088，通过 adb reverse 转到宿主机的 stream 代理
                    body = body.replace(
                        f"http://{IPTV_HOST}:{STREAM_PORT}/",
                        f"http://127.0.0.1:{STREAM_PORT}/",
                    )
                    data = body.encode("utf-8")
                    self.send_response(resp.status)
                    self.send_header("Content-Type", "application/vnd.apple.mpegurl")
                    self.send_header("Content-Length", str(len(data)))
                    _proxy_headers(resp, self)
                    self.end_headers()
                    self.wfile.write(data)
            except Exception as e:
                print(f"[m3u] fetch playlist failed: {e}")
                self.send_error(502, "fetch playlist failed")
            return

        if path == "/xmltv.xml":
            try:
                url = f"http://{IPTV_HOST}:{M3U_PORT}/xmltv.xml"
                req = urllib.request.Request(url, headers={"Host": f"{IPTV_HOST}:{M3U_PORT}"})
                with urllib.request.urlopen(req, timeout=30) as resp:
                    data = resp.read()
                    self.send_response(resp.status)
                    self.send_header("Content-Type", "application/xml")
                    self.send_header("Content-Length", str(len(data)))
                    _proxy_headers(resp, self)
                    self.end_headers()
                    self.wfile.write(data)
            except Exception as e:
                print(f"[m3u] fetch epg failed: {e}")
                self.send_error(502, "fetch epg failed")
            return

        self.send_error(404)


class StreamHandler(http.server.BaseHTTPRequestHandler):
    """7088: 直播流转发"""

    def log_message(self, fmt, *args):
        print(f"[stream] {self.client_address[0]} - {fmt % args}")

    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        upstream = f"http://{IPTV_HOST}:{STREAM_PORT}{parsed.path}"
        if parsed.query:
            upstream += "?" + parsed.query
        _stream(upstream, self)

    def do_HEAD(self):
        parsed = urllib.parse.urlparse(self.path)
        upstream = f"http://{IPTV_HOST}:{STREAM_PORT}{parsed.path}"
        if parsed.query:
            upstream += "?" + parsed.query
        req = urllib.request.Request(
            upstream,
            method="HEAD",
            headers={"Host": f"{IPTV_HOST}:{STREAM_PORT}"},
        )
        try:
            with urllib.request.urlopen(req, timeout=10) as resp:
                self.send_response(resp.status)
                _proxy_headers(resp, self)
                self.end_headers()
        except Exception as e:
            print(f"[stream] proxy head error: {e}")
            self.send_error(502, "proxy head error")


class ThreadedHTTPServer(socketserver.ThreadingMixIn, http.server.HTTPServer):
    daemon_threads = True
    allow_reuse_address = True


def serve():
    m3u = ThreadedHTTPServer(("127.0.0.1", M3U_PORT), M3uHandler)
    stream = ThreadedHTTPServer(("127.0.0.1", STREAM_PORT), StreamHandler)
    t1 = threading.Thread(target=m3u.serve_forever, daemon=True)
    t2 = threading.Thread(target=stream.serve_forever, daemon=True)
    t1.start()
    t2.start()
    print(f"Proxy running: 127.0.0.1:{M3U_PORT} -> {IPTV_HOST}:{M3U_PORT}")
    print(f"Stream proxy:  127.0.0.1:{STREAM_PORT} -> {IPTV_HOST}:{STREAM_PORT}")
    try:
        while True:
            threading.Event().wait()
    except KeyboardInterrupt:
        pass
    finally:
        m3u.shutdown()
        stream.shutdown()


if __name__ == "__main__":
    serve()
