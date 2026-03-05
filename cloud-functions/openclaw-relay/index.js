/*
 * Minimal OpenClaw -> FCM relay example.
 * Deploy with Firebase Functions (Node 20).
 */

const { onRequest } = require("firebase-functions/v2/https")
const logger = require("firebase-functions/logger")
const admin = require("firebase-admin")

admin.initializeApp()
const db = admin.firestore()

const RELAY_KEY = process.env.RELAY_KEY || ""

function isAuthorized(req) {
  const key = req.get("x-relay-key") || ""
  return RELAY_KEY && key === RELAY_KEY
}

exports.registerToken = onRequest(async (req, res) => {
  if (req.method !== "POST") return res.status(405).send("Method Not Allowed")
  if (!isAuthorized(req)) return res.status(401).send("Unauthorized")

  const { token, agentId, platform, appVersion } = req.body || {}
  if (!token || !agentId) return res.status(400).send("token and agentId are required")

  await db.collection("deviceTokens").doc(token).set(
    {
      token,
      agentId,
      platform: platform || "android",
      appVersion: appVersion || "unknown",
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    },
    { merge: true }
  )

  res.status(200).json({ ok: true })
})

exports.unregisterToken = onRequest(async (req, res) => {
  if (req.method !== "POST") return res.status(405).send("Method Not Allowed")
  if (!isAuthorized(req)) return res.status(401).send("Unauthorized")

  const { token } = req.body || {}
  if (!token) return res.status(400).send("token is required")

  await db.collection("deviceTokens").doc(token).delete()
  res.status(200).json({ ok: true })
})

exports.openClawEvent = onRequest(async (req, res) => {
  if (req.method !== "POST") return res.status(405).send("Method Not Allowed")
  if (!isAuthorized(req)) return res.status(401).send("Unauthorized")

  const { agentId, sessionKey, content, title } = req.body || {}
  if (!agentId || !sessionKey || !content) {
    return res.status(400).send("agentId, sessionKey, and content are required")
  }

  const tokenDocs = await db
    .collection("deviceTokens")
    .where("agentId", "==", agentId)
    .get()

  const tokens = tokenDocs.docs.map((doc) => doc.id)
  if (!tokens.length) {
    return res.status(200).json({ ok: true, sent: 0 })
  }

  const preview = String(content).replace(/\s+/g, " ").trim().slice(0, 220)

  const message = {
    tokens,
    data: {
      type: "openclaw_chat",
      agentId,
      sessionKey,
      title: title || `OpenClaw - ${agentId}`,
      content: String(content).slice(0, 1500),
      contentPreview: preview,
    },
    android: {
      priority: "high",
    },
  }

  const result = await admin.messaging().sendEachForMulticast(message)

  const invalidTokens = []
  result.responses.forEach((response, index) => {
    if (!response.success) {
      const code = response.error?.code || ""
      if (
        code.includes("registration-token-not-registered") ||
        code.includes("invalid-registration-token")
      ) {
        invalidTokens.push(tokens[index])
      }
    }
  })

  if (invalidTokens.length) {
    const batch = db.batch()
    invalidTokens.forEach((token) => {
      batch.delete(db.collection("deviceTokens").doc(token))
    })
    await batch.commit()
  }

  logger.info("openClawEvent relay complete", {
    requested: tokens.length,
    successCount: result.successCount,
    failureCount: result.failureCount,
  })

  res.status(200).json({
    ok: true,
    sent: result.successCount,
    failed: result.failureCount,
  })
})
