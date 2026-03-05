# OpenClaw Direct FCM Plugin (MaterialChat)

Use this on your OpenClaw server so the Android app can register FCM tokens directly on Gateway,
and receive push for final assistant responses from your dedicated `materialchat` agent.

## 1) Create plugin package on server

Create `~/.openclaw/extensions/materialchat-push/package.json`:

```json
{
  "name": "materialchat-push",
  "private": true,
  "type": "module",
  "main": "index.js",
  "dependencies": {
    "firebase-admin": "^12.7.0"
  }
}
```

Create `~/.openclaw/extensions/materialchat-push/index.js`:

```js
import fs from "node:fs";
import path from "node:path";
import admin from "firebase-admin";

const AGENT_ID = "materialchat";
const STORE_FILE = "materialchat-push-tokens.json";

function readBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    req.on("data", (chunk) => chunks.push(chunk));
    req.on("end", () => {
      try {
        const raw = Buffer.concat(chunks).toString("utf8").trim();
        resolve(raw ? JSON.parse(raw) : {});
      } catch (error) {
        reject(error);
      }
    });
    req.on("error", reject);
  });
}

function json(res, code, payload) {
  res.statusCode = code;
  res.setHeader("content-type", "application/json");
  res.end(JSON.stringify(payload));
}

function loadStore(filePath) {
  try {
    const raw = fs.readFileSync(filePath, "utf8");
    const parsed = JSON.parse(raw);
    if (parsed && typeof parsed === "object") return parsed;
  } catch (_error) {
  }
  return { tokens: {} };
}

function saveStore(filePath, store) {
  const dir = path.dirname(filePath);
  fs.mkdirSync(dir, { recursive: true });
  fs.writeFileSync(filePath, JSON.stringify(store, null, 2));
}

function getTokensForAgent(store, agentId) {
  return Object.entries(store.tokens)
    .filter(([, value]) => value?.agentId === agentId)
    .map(([token]) => token);
}

export default function register(api) {
  const logger = api.logger;
  const storePath = api.resolvePath(`~/.openclaw/${STORE_FILE}`);
  const store = loadStore(storePath);

  if (!admin.apps.length) {
    admin.initializeApp();
  }

  api.registerHttpRoute({
    path: "/materialchat/push/register-token",
    auth: "gateway",
    match: "exact",
    handler: async (req, res) => {
      if (req.method !== "POST") {
        json(res, 405, { error: "Method Not Allowed" });
        return true;
      }

      try {
        const body = await readBody(req);
        const token = String(body.token || "").trim();
        const agentId = String(body.agentId || "").trim();
        if (!token || !agentId) {
          json(res, 400, { error: "token and agentId are required" });
          return true;
        }

        store.tokens[token] = {
          token,
          agentId,
          platform: String(body.platform || "android"),
          appVersion: String(body.appVersion || "unknown"),
          updatedAt: Date.now()
        };
        saveStore(storePath, store);

        json(res, 200, { ok: true });
      } catch (error) {
        logger.warn(`register-token failed: ${String(error)}`);
        json(res, 400, { error: "invalid json" });
      }
      return true;
    }
  });

  api.registerHttpRoute({
    path: "/materialchat/push/unregister-token",
    auth: "gateway",
    match: "exact",
    handler: async (req, res) => {
      if (req.method !== "POST") {
        json(res, 405, { error: "Method Not Allowed" });
        return true;
      }

      try {
        const body = await readBody(req);
        const token = String(body.token || "").trim();
        if (!token) {
          json(res, 400, { error: "token is required" });
          return true;
        }

        delete store.tokens[token];
        saveStore(storePath, store);

        json(res, 200, { ok: true });
      } catch (error) {
        logger.warn(`unregister-token failed: ${String(error)}`);
        json(res, 400, { error: "invalid json" });
      }
      return true;
    }
  });

  api.on("llm_output", async (event, ctx) => {
    if (ctx.agentId !== AGENT_ID) return;
    const sessionKey = ctx.sessionKey;
    if (!sessionKey) return;

    const content = event.assistantTexts?.at(-1)?.trim();
    if (!content) return;

    const tokens = getTokensForAgent(store, AGENT_ID);
    if (!tokens.length) return;

    const message = {
      tokens,
      data: {
        type: "openclaw_chat",
        agentId: AGENT_ID,
        sessionKey,
        contentPreview: content.slice(0, 220)
      },
      android: { priority: "high" }
    };

    const result = await admin.messaging().sendEachForMulticast(message);
    if (!result.failureCount) return;

    const stale = [];
    result.responses.forEach((response, index) => {
      if (response.success) return;
      const code = String(response.error?.code || "");
      if (code.includes("registration-token-not-registered") || code.includes("invalid-registration-token")) {
        stale.push(tokens[index]);
      }
    });

    if (stale.length) {
      stale.forEach((token) => {
        delete store.tokens[token];
      });
      saveStore(storePath, store);
    }
  });
}
```

Install dependency:

```bash
cd ~/.openclaw/extensions/materialchat-push
npm install --omit=dev
```

## 2) Add plugin to OpenClaw config

In `~/.openclaw/openclaw.json`:

```json5
{
  plugins: {
    load: {
      paths: [
        "~/.openclaw/extensions/materialchat-push"
      ]
    }
  }
}
```

## 3) Configure Firebase credentials on server

Set Google service account credentials on the OpenClaw host:

```bash
export GOOGLE_APPLICATION_CREDENTIALS=/path/to/firebase-service-account.json
```

Then restart OpenClaw gateway.

## 4) Verify

- In app, set OpenClaw default agent to `materialchat`.
- Enable app notifications.
- Send a message and wait for final assistant response.
- You should get a push notification when app is backgrounded/killed.

## 5) Quick deploy commands (copy/paste)

```bash
# 0) Variables
export OPENCLAW_GATEWAY_TOKEN='replace-with-your-gateway-token'
export FIREBASE_CREDENTIALS_PATH='/opt/openclaw/firebase-service-account.json'

# 1) Add dedicated agent (safe to run once)
openclaw agents add materialchat || true

# 2) Ensure plugin path is loaded in config
openclaw config set plugins.load.paths[0] '~/.openclaw/extensions/materialchat-push'

# 3) Ensure Firebase credentials env var is present for gateway process
export GOOGLE_APPLICATION_CREDENTIALS="$FIREBASE_CREDENTIALS_PATH"

# 4) Restart gateway process (use your normal service manager)
openclaw gateway restart

# 5) Route smoke test: register a fake token (expects {"ok":true})
curl -sS \
  -X POST 'http://127.0.0.1:18789/materialchat/push/register-token' \
  -H "Authorization: Bearer $OPENCLAW_GATEWAY_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"token":"dry-run-token","agentId":"materialchat","platform":"android","appVersion":"dry-run"}'

# 6) Cleanup fake token
curl -sS \
  -X POST 'http://127.0.0.1:18789/materialchat/push/unregister-token' \
  -H "Authorization: Bearer $OPENCLAW_GATEWAY_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"token":"dry-run-token"}'
```
