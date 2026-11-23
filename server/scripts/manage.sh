#!/usr/bin/env bash
# WhiteSecurity server process control: start / stop / restart / status / logs
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER_DIR="$(dirname "$SCRIPT_DIR")"
PID_FILE="$SERVER_DIR/whitesecurity-server.pid"
LOG_FILE="$SERVER_DIR/whitesecurity-server.log"

cd "$SERVER_DIR"

is_running() {
  [[ -f "$PID_FILE" ]] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null
}

cmd_start() {
  if is_running; then
    echo "Already running (pid $(cat "$PID_FILE"))."
    exit 0
  fi
  if [[ ! -f .env ]]; then
    echo "Missing server/.env — copy .env.example to .env and set APP_API_KEY first." >&2
    exit 1
  fi
  if [[ ! -d node_modules ]]; then
    echo "node_modules missing — run 'npm install' in $SERVER_DIR first." >&2
    exit 1
  fi
  nohup node src/index.js >> "$LOG_FILE" 2>&1 &
  echo $! > "$PID_FILE"
  sleep 0.5
  if is_running; then
    echo "Started (pid $(cat "$PID_FILE")). Logs: $LOG_FILE"
  else
    echo "Failed to start — check $LOG_FILE" >&2
    exit 1
  fi
}

cmd_stop() {
  if ! is_running; then
    echo "Not running."
    rm -f "$PID_FILE"
    exit 0
  fi
  kill "$(cat "$PID_FILE")"
  rm -f "$PID_FILE"
  echo "Stopped."
}

cmd_status() {
  if is_running; then
    echo "Running (pid $(cat "$PID_FILE"))."
  else
    echo "Not running."
  fi
}

cmd_logs() {
  touch "$LOG_FILE"
  tail -n "${1:-100}" -f "$LOG_FILE"
}

case "${1:-}" in
  start)   cmd_start ;;
  stop)    cmd_stop ;;
  restart) cmd_stop || true; cmd_start ;;
  status)  cmd_status ;;
  logs)    cmd_logs "${2:-100}" ;;
  *)
    echo "Usage: $0 {start|stop|restart|status|logs [lines]}" >&2
    exit 1
    ;;
esac
