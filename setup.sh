#!/bin/bash
set -e

# ============================================================
# GitLab Setup Script
# Creates a group, 3 projects, pushes all branches, opens MRs
# ============================================================

GITLAB_URL="https://gitlab.com"
GITLAB_TOKEN="${GITLAB_TOKEN:?Please set GITLAB_TOKEN environment variable}"
GROUP_PATH="${GITLAB_GROUP:-unlimit-test-agent}"

API="$GITLAB_URL/api/v4"
HEADER="PRIVATE-TOKEN: $GITLAB_TOKEN"

echo "============================================"
echo "  ADR Agent Test Assignment — GitLab Setup"
echo "============================================"
echo ""

# --- Get current user ---
echo "[1/6] Getting GitLab user info..."
USER_INFO=$(curl -s --header "$HEADER" "$API/user")
USER_ID=$(echo "$USER_INFO" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])" 2>/dev/null)
USERNAME=$(echo "$USER_INFO" | python3 -c "import sys,json; print(json.load(sys.stdin)['username'])" 2>/dev/null)

if [ -z "$USER_ID" ]; then
    echo "ERROR: Could not authenticate. Check your GITLAB_TOKEN."
    echo "Response: $USER_INFO"
    exit 1
fi
echo "  Authenticated as: $USERNAME (id: $USER_ID)"

# --- Find group ---
echo ""
echo "[2/6] Looking for group '$GROUP_PATH'..."
GROUP_ID=$(curl -s --header "$HEADER" "$API/groups?search=$GROUP_PATH&owned=true" | \
    python3 -c "
import sys,json
groups=json.load(sys.stdin)
for g in groups:
    if g['path'] == '$GROUP_PATH':
        print(g['id'])
        break
" 2>/dev/null)

if [ -z "$GROUP_ID" ]; then
    echo "ERROR: Group '$GROUP_PATH' not found."
    echo "Please create it manually at: $GITLAB_URL/groups/new"
    echo "  - Name: $GROUP_PATH"
    echo "  - Visibility: Private"
    echo ""
    echo "Or set GITLAB_GROUP to use a different group:"
    echo "  GITLAB_GROUP=my-group GITLAB_TOKEN=... ./setup.sh"
    exit 1
fi
echo "  Group ID: $GROUP_ID"
echo "  Projects will be created under: $GITLAB_URL/$GROUP_PATH/"

NAMESPACE_PATH="$GROUP_PATH"

# --- Create projects ---
echo ""
echo "[3/6] Creating projects..."

create_project() {
    local name=$1
    local desc=$2

    PROJ_RESPONSE=$(curl -s --header "$HEADER" \
        --header "Content-Type: application/json" \
        --request POST "$API/projects" \
        --data "{
            \"name\": \"$name\",
            \"path\": \"$name\",
            \"namespace_id\": $GROUP_ID,
            \"description\": \"$desc\",
            \"visibility\": \"private\",
            \"initialize_with_readme\": false
        }")

    PROJ_ID=$(echo "$PROJ_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))" 2>/dev/null)

    if [ -z "$PROJ_ID" ]; then
        # Project might already exist
        PROJ_ID=$(curl -s --header "$HEADER" "$API/projects?search=$name&owned=true" | \
            python3 -c "
import sys,json
projects=json.load(sys.stdin)
for p in projects:
    if p['path'] == '$name' and p.get('namespace',{}).get('id') == $GROUP_ID:
        print(p['id'])
        break
" 2>/dev/null)
    fi

    echo "$PROJ_ID"
}

ORDER_PROJECT_ID=$(create_project "order-service" "Order management microservice")
echo "  order-service project ID: $ORDER_PROJECT_ID"

PAYMENT_PROJECT_ID=$(create_project "payment-service" "Payment processing microservice")
echo "  payment-service project ID: $PAYMENT_PROJECT_ID"

ADR_PROJECT_ID=$(create_project "architecture-decisions" "Architecture Decision Log and Records")
echo "  architecture-decisions project ID: $ADR_PROJECT_ID"

# --- Push repos ---
echo ""
echo "[4/6] Pushing repositories..."

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
GIT_PUSH_URL_BASE="https://oauth2:${GITLAB_TOKEN}@gitlab.com/${NAMESPACE_PATH}"

push_repo() {
    local dir=$1
    local name=$2

    echo "  Pushing $name..."
    cd "$SCRIPT_DIR/$name"

    # Rename master to main if needed
    git branch -m master main 2>/dev/null || true

    # Set remote
    git remote remove origin 2>/dev/null || true
    git remote add origin "${GIT_PUSH_URL_BASE}/${name}.git"

    # Push main
    git push -u origin main --force 2>&1 | sed 's/^/    /'

    # Push all other branches (including currently checked out one)
    for branch in $(git branch | sed 's/^\* //' | grep -v '^main$' | sed 's/^ *//'); do
        echo "    Pushing branch: $branch"
        git push origin "$branch" --force 2>&1 | sed 's/^/      /'
    done

    cd "$SCRIPT_DIR"
}

push_repo "$SCRIPT_DIR" "order-service"
push_repo "$SCRIPT_DIR" "payment-service"
push_repo "$SCRIPT_DIR" "architecture-decisions"

# --- Create Merge Requests ---
echo ""
echo "[5/6] Creating Merge Requests..."

create_mr() {
    local project_id=$1
    local source=$2
    local target=$3
    local title=$4
    local description=$5

    MR_RESPONSE=$(curl -s --header "$HEADER" \
        --header "Content-Type: application/json" \
        --request POST "$API/projects/$project_id/merge_requests" \
        --data "{
            \"source_branch\": \"$source\",
            \"target_branch\": \"$target\",
            \"title\": \"$title\",
            \"description\": \"$description\",
            \"remove_source_branch\": false
        }")

    MR_URL=$(echo "$MR_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin).get('web_url','FAILED'))" 2>/dev/null)
    echo "  MR: $title"
    echo "    URL: $MR_URL"
}

# Order Service MRs
create_mr "$ORDER_PROJECT_ID" \
    "fix/ORD-142-logging-typos" "main" \
    "ORD-142: Fix logging typos and variable naming" \
    "Changes:\n- Fixed typo in log messages\n- Renamed variable for clarity\n- Added code comments"

create_mr "$ORDER_PROJECT_ID" \
    "feature/ORD-158-order-statistics-endpoint" "main" \
    "ORD-158: Add order statistics endpoint" \
    "Adds a new GET /api/orders/statistics endpoint that returns:\n- Total order count\n- Total revenue\n- Average order value\n- Orders grouped by status"

create_mr "$ORDER_PROJECT_ID" \
    "feature/PLAT-034-api-v2-workflow-rework" "main" \
    "PLAT-034: V2 API — Order workflow with state machine and cancellation" \
    "V2 API changes:\n- New /api/v2/orders endpoints\n- Order state machine (CREATED → CONFIRMED → PAID → SHIPPED → DELIVERED)\n- Order cancellation with refund integration\n- Inter-service communication for refunds"

# Payment Service MRs
create_mr "$PAYMENT_PROJECT_ID" \
    "refactor/PAY-087-extract-validation" "main" \
    "PAY-087: Extract payment validation to dedicated service" \
    "Changes:\n- Extracted validation logic to PaymentValidator component\n- Updated PaymentService to use validator"

create_mr "$PAYMENT_PROJECT_ID" \
    "feature/PAY-095-websocket-payment-notifications" "main" \
    "PAY-095: Add WebSocket support for real-time payment status notifications" \
    "Changes:\n- Added spring-boot-starter-websocket dependency\n- WebSocket endpoint at /ws/payment-status\n- Payment status push notifications\n- PaymentNotificationService for broadcasting updates"

create_mr "$PAYMENT_PROJECT_ID" \
    "feature/PLAT-034-api-v2-workflow-rework" "main" \
    "PLAT-034: V2 API — Refund processing and enhanced payment workflow" \
    "V2 API changes:\n- New /api/v2/payments endpoints\n- Refund entity and processing\n- Refund endpoint for cancelled orders\n- Updated inter-service contract"

# --- Create Review Comments ---
echo ""
echo "[6/6] Creating review comments..."

export ORDER_PROJECT_ID PAYMENT_PROJECT_ID GITLAB_TOKEN
COMMENTS_FILE="$SCRIPT_DIR/gitlab-comments.md"
export COMMENTS_FILE
if [ -f "$COMMENTS_FILE" ]; then
    python3 << 'PYEOF'
import urllib.request, json, ssl, re, sys, os, time

TOKEN = os.environ["GITLAB_TOKEN"]
API = "https://gitlab.com/api/v4"
ORDER_ID = os.environ.get("ORDER_PROJECT_ID", "")
PAYMENT_ID = os.environ.get("PAYMENT_PROJECT_ID", "")
COMMENTS_FILE = os.environ["COMMENTS_FILE"]
ctx = ssl.create_default_context()

def api_post(url, data):
    body = json.dumps(data).encode()
    req = urllib.request.Request(url, data=body, headers={
        "PRIVATE-TOKEN": TOKEN,
        "Content-Type": "application/json"
    }, method="POST")
    try:
        with urllib.request.urlopen(req, context=ctx) as resp:
            return json.loads(resp.read())
    except urllib.error.HTTPError as e:
        err_body = e.read().decode()
        print(f"    ERROR {e.code}: {err_body[:200]}")
        return {}
    except Exception as e:
        print(f"    ERROR: {e}")
        return {}

def api_get(url):
    req = urllib.request.Request(url, headers={"PRIVATE-TOKEN": TOKEN})
    with urllib.request.urlopen(req, context=ctx) as resp:
        return json.loads(resp.read())

def get_mr_versions(proj_id, mr_iid):
    """Get diff SHAs needed for inline comments."""
    versions = api_get(f"{API}/projects/{proj_id}/merge_requests/{mr_iid}/versions")
    if versions:
        return {
            "base_sha": versions[0]["base_commit_sha"],
            "start_sha": versions[0]["start_commit_sha"],
            "head_sha": versions[0]["head_commit_sha"],
        }
    return None

with open(COMMENTS_FILE) as f:
    content = f.read()

# Parse MR sections
mr_sections = re.split(r'^## ', content, flags=re.MULTILINE)[1:]

# Cache MR versions
mr_versions_cache = {}

for section in mr_sections:
    lines = section.strip()
    header_match = re.match(r'(order-service|payment-service) MR !(\d+)', lines)
    if not header_match:
        continue
    service = header_match.group(1)
    mr_iid = int(header_match.group(2))
    proj_id = ORDER_ID if service == "order-service" else PAYMENT_ID

    if not proj_id:
        print(f"  Skipping {service} MR !{mr_iid} — no project ID")
        continue

    print(f"  {service} MR !{mr_iid}:")

    # Get diff versions for inline comments
    cache_key = f"{proj_id}_{mr_iid}"
    if cache_key not in mr_versions_cache:
        mr_versions_cache[cache_key] = get_mr_versions(proj_id, mr_iid)
    versions = mr_versions_cache[cache_key]

    # Parse threads
    threads = re.split(r'^### Thread \d+', lines, flags=re.MULTILINE)[1:]

    for i, thread in enumerate(threads, 1):
        # Extract optional FILE and LINE metadata
        file_match = re.search(r'^\*\*FILE:\*\*\s*(.+)$', thread, re.MULTILINE)
        line_match = re.search(r'^\*\*LINE:\*\*\s*(\d+)$', thread, re.MULTILINE)

        file_path = file_match.group(1).strip() if file_match else None
        line_num = int(line_match.group(1)) if line_match else None

        # Extract DR_REVIEW and MR_CODER bodies
        dr_match = re.search(r'\*\*DR_REVIEW:\*\*\n(.*?)(?=\n\*\*MR_CODER:\*\*)', thread, re.DOTALL)
        coder_match = re.search(r'\*\*MR_CODER:\*\*\n(.*?)$', thread, re.DOTALL)

        if not dr_match or not coder_match:
            print(f"    Thread {i}: PARSE ERROR")
            continue

        dr_body = dr_match.group(1).strip()
        coder_body = coder_match.group(1).strip()

        # Post Dr. Review comment
        if file_path and line_num and versions:
            # Inline diff comment via discussions API
            data = {
                "body": dr_body,
                "position": {
                    "base_sha": versions["base_sha"],
                    "start_sha": versions["start_sha"],
                    "head_sha": versions["head_sha"],
                    "position_type": "text",
                    "new_path": file_path,
                    "new_line": line_num
                }
            }
            result = api_post(
                f"{API}/projects/{proj_id}/merge_requests/{mr_iid}/discussions",
                data
            )
            # discussions API returns discussion object directly
            disc_id = result.get("id")
            if not disc_id:
                # Fallback to general note
                result = api_post(
                    f"{API}/projects/{proj_id}/merge_requests/{mr_iid}/notes",
                    {"body": dr_body}
                )
                note_id = result.get("id")
                if not note_id:
                    print(f"    Thread {i}: FAILED")
                    continue
                time.sleep(0.3)
                discussions = api_get(f"{API}/projects/{proj_id}/merge_requests/{mr_iid}/discussions?per_page=100")
                disc_id = None
                for d in discussions:
                    for n in d["notes"]:
                        if n["id"] == note_id:
                            disc_id = d["id"]
                            break
                    if disc_id:
                        break
        else:
            # General MR note
            result = api_post(
                f"{API}/projects/{proj_id}/merge_requests/{mr_iid}/notes",
                {"body": dr_body}
            )
            note_id = result.get("id")
            if not note_id:
                print(f"    Thread {i}: FAILED")
                continue
            time.sleep(0.3)
            discussions = api_get(f"{API}/projects/{proj_id}/merge_requests/{mr_iid}/discussions?per_page=100")
            disc_id = None
            for d in discussions:
                for n in d["notes"]:
                    if n["id"] == note_id:
                        disc_id = d["id"]
                        break
                if disc_id:
                    break

        if not disc_id:
            print(f"    Thread {i}: no discussion found")
            continue

        # Post Mr. Coder reply
        reply_result = api_post(
            f"{API}/projects/{proj_id}/merge_requests/{mr_iid}/discussions/{disc_id}/notes",
            {"body": coder_body}
        )
        reply_id = reply_result.get("id")
        inline_tag = f" [{file_path}:{line_num}]" if file_path else ""
        status = "ok" if reply_id else "REPLY FAILED"
        print(f"    Thread {i}: {status}{inline_tag}")

print("  Done.")
PYEOF
else
    echo "  gitlab-comments.md not found, skipping comments."
fi

echo ""
echo ""
echo "Done!"
echo ""
echo "============================================"
echo "  Setup Complete!"
echo "============================================"
echo ""
echo "Projects:"
echo "  - $GITLAB_URL/$NAMESPACE_PATH/order-service"
echo "  - $GITLAB_URL/$NAMESPACE_PATH/payment-service"
echo "  - $GITLAB_URL/$NAMESPACE_PATH/architecture-decisions"
echo ""
echo "Merge Requests have been created for all feature branches."
echo "The candidate should be given access to these projects."
echo ""
