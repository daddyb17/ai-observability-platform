#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PID_FILE="${SCRIPT_DIR}/.demo-services.pids"

if [[ ! -f "${PID_FILE}" ]]; then
  echo "No PID file found at ${PID_FILE}. Nothing to stop."
  exit 0
fi

while IFS="," read -r pid service_name _port _module_path; do
  [[ -z "${pid}" ]] && continue
  if kill -0 "${pid}" >/dev/null 2>&1; then
    kill "${pid}" >/dev/null 2>&1 || true
    echo "Stopped ${service_name} (PID ${pid})"
  else
    echo "Process for ${service_name} (PID ${pid}) is not running."
  fi
done < "${PID_FILE}"

rm -f "${PID_FILE}"
echo "Demo services stop sequence complete."
